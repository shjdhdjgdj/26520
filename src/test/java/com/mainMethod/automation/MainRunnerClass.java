package com.mainMethod.automation;

import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Semaphore;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;

import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import Freelance.com.projectSetup.ExcelUtility;
import config.VARIABLES;

public class MainRunnerClass {

    // ── MUST match thread-count in testng.xml ──
    private static final int PARALLEL_COUNT = 2;

    private static Playwright playwright;
    private static Browser     browser;
    private static final String SESSION_FILE = "session.json";

    private static final List<BrowserContext>                  allContexts = Collections.synchronizedList(new ArrayList<>());
    private static final ConcurrentLinkedQueue<BrowserContext> contextPool = new ConcurrentLinkedQueue<>();

    // Semaphore: blocks threads beyond PARALLEL_COUNT — prevents pool exhaustion
    private static Semaphore semaphore;

    private static final ThreadLocal<BrowserContext> tlContext = new ThreadLocal<>();
    private static final ThreadLocal<Page>           tlPage    = new ThreadLocal<>();
    private static final ThreadLocal<PageBean>       tlPom     = new ThreadLocal<>();

    // ─────────────────────────────────────────────────────────────────────────────
    @BeforeSuite
    public void beforeSuite() throws Exception {
        System.out.println("=================================================");
        System.out.println("✅ PLAYWRIGHT PARALLEL — " + PARALLEL_COUNT + " windows");
        System.out.println("=================================================");

        semaphore = new Semaphore(PARALLEL_COUNT, true);  // fair = no starvation

        Properties prop = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            prop.load(fis);
        }

        playwright = Playwright.create();
        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
            .setHeadless(false).setSlowMo(0);

        String browserName = prop.getProperty("browser", "chrome").toLowerCase();
        browser = browserName.equals("firefox")
            ? playwright.firefox().launch(opts)
            : playwright.chromium().launch(opts);
        System.out.println("✅ Browser launched.");

        // ── Login / session ────────────────────────────────────────────────────
        BrowserContext masterContext = browser.newContext();
        Page masterPage = masterContext.newPage();
        boolean sessionLoaded = false;

        if (new java.io.File(SESSION_FILE).exists()) {
            System.out.println("🍪 Trying saved session...");
            masterContext.close();
            masterContext = browser.newContext(
                new Browser.NewContextOptions().setStorageStatePath(Paths.get(SESSION_FILE)));
            masterPage = masterContext.newPage();
            masterPage.navigate(VARIABLES.NEW_REGISTRATION_URL);
            masterPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
            if (!masterPage.url().contains("sign_in") && !masterPage.url().contains("login")) {
                System.out.println("✅ Session valid.");
                sessionLoaded = true;
            } else {
                System.out.println("⚠ Session expired — re-logging in...");
                masterContext.close();
                masterContext = browser.newContext();
                masterPage = masterContext.newPage();
            }
        }

        if (!sessionLoaded) {
            masterPage.navigate(VARIABLES.SIGN_IN_PAGE_URL);
            masterPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
            System.out.println("⏳ Please enter OTP when prompted...");
            new PageBean(masterPage).login(VARIABLES.EMAIL, VARIABLES.PASSWORD, 1, 2);
            masterPage.navigate(VARIABLES.NEW_REGISTRATION_URL);
            masterPage.waitForLoadState(LoadState.DOMCONTENTLOADED);
            System.out.println("✅ Login complete.");
        }

        masterContext.storageState(new BrowserContext.StorageStateOptions().setPath(Paths.get(SESSION_FILE)));
        masterContext.close();
        System.out.println("💾 Session saved.");

        // ── Create exactly PARALLEL_COUNT contexts ─────────────────────────────
        System.out.println("🌐 Creating " + PARALLEL_COUNT + " contexts...");
        for (int i = 1; i <= PARALLEL_COUNT; i++) {
            BrowserContext ctx = createContext(i);
            allContexts.add(ctx);
            contextPool.add(ctx);
        }
        System.out.println("✅ READY — " + PARALLEL_COUNT + " windows.\n");
    }

    private BrowserContext createContext(int index) {
        BrowserContext ctx = browser.newContext(
            new Browser.NewContextOptions().setStorageStatePath(Paths.get(SESSION_FILE)));
        Page page = ctx.newPage();
        page.navigate(VARIABLES.NEW_REGISTRATION_URL);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForSelector(
                "//h4[contains(text(),'SBI GENERAL INSURANCE COMPANY LIMITED')]",
                new Page.WaitForSelectorOptions().setTimeout(15000));
            System.out.println("  ✔ Context " + index + " ready");
        } catch (PlaywrightException e) {
            System.out.println("  ⚠ Context " + index + " retrying...");
            page.reload();
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForSelector(
                "//h4[contains(text(),'SBI GENERAL INSURANCE COMPANY LIMITED')]",
                new Page.WaitForSelectorOptions().setTimeout(15000));
        }
        // Close validation page — each thread opens its own fresh page in setupContext
        try { page.close(); } catch (Exception ignored) {}
        return ctx;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    @BeforeMethod
    public void setupContext() throws InterruptedException {
        // Blocks until a slot is free — guarantees pool never exhausts
        semaphore.acquire();

        BrowserContext ctx = contextPool.poll();
        if (ctx == null) {
            semaphore.release();
            throw new RuntimeException("Pool empty despite semaphore — check PARALLEL_COUNT vs thread-count");
        }

        // Close stale pages and open a fresh one — avoids "adopt" cross-thread errors
        for (Page stale : ctx.pages()) {
            try { stale.close(); } catch (Exception ignored) {}
        }
        Page page = ctx.newPage();

        try {
            page.navigate(VARIABLES.NEW_REGISTRATION_URL);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
            page.waitForSelector(
                "//h4[contains(text(),'SBI GENERAL INSURANCE COMPANY LIMITED')]",
                new Page.WaitForSelectorOptions().setTimeout(15000));
        } catch (PlaywrightException e) {
            System.err.println("[Thread-" + Thread.currentThread().getId() + "] Context broken — replacing");
            allContexts.remove(ctx);
            try { ctx.close(); } catch (Exception ignored) {}
            ctx = createContext(allContexts.size() + 1);
            allContexts.add(ctx);
            // createContext already opened a page — close it and open fresh
            for (Page stale : ctx.pages()) {
                try { stale.close(); } catch (Exception ignored) {}
            }
            page = ctx.newPage();
            page.navigate(VARIABLES.NEW_REGISTRATION_URL);
            page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        }

        tlContext.set(ctx);
        tlPage.set(page);
        tlPom.set(new PageBean(page));
        System.out.println("[Thread-" + Thread.currentThread().getId() + "] ▶ Context acquired");
    }

    @AfterMethod
    public void releaseContext() {
        BrowserContext ctx = tlContext.get();
        Page page = tlPage.get();

        if (ctx != null) {
            try {
                // Close the used page — next thread will open a fresh one
                try { page.close(); } catch (Exception ignored) {}
                contextPool.add(ctx);
            } catch (PlaywrightException e) {
                System.err.println("[Thread-" + Thread.currentThread().getId() + "] Replacing broken context");
                allContexts.remove(ctx);
                try { ctx.close(); } catch (Exception ignored) {}
                BrowserContext fresh = createContext(allContexts.size() + 1);
                // Close the page createContext opened — setupContext will open a fresh one
                for (Page stale : fresh.pages()) {
                    try { stale.close(); } catch (Exception ignored) {}
                }
                allContexts.add(fresh);
                contextPool.add(fresh);
            } finally {
                tlContext.remove();
                tlPage.remove();
                tlPom.remove();
                semaphore.release();
                System.out.println("[Thread-" + Thread.currentThread().getId() + "] ◀ Released");
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    @DataProvider(name = "excelData", parallel = true)
    public Object[][] testMainMethod() {
        return ExcelUtility.getExcelData();
    }

    @Test(dataProvider = "excelData")
    public void runTests(Object[] data) throws InterruptedException {
        int rowIndex = (int) data[0];
        String status = "PASS";

        String FarmrName    = (String) data[3];
        String FathrHusName = (String) data[4];
        String EpicID       = (String) data[5];
        String AadharNo     = (String) data[6];
        String Age          = (String) data[8];
        String Gender       = (String) data[9];
        String Caste        = (String) data[10];
        String MobNo        = (String) data[11];
        String Crop         = (String) data[12];
        String District     = (String) data[13];
        String Block        = (String) data[14];
        String GP           = (String) data[15];
        String Mouza1       = (String) data[16];
        String KhatianNo1   = (String) data[18];
        String PlotNo1      = (String) data[19];
        String AreaInsur1   = (String) data[20];
        String FarmrCat     = (String) data[21];
        String NatureFarmr1 = (String) data[22];
        String IFSCode      = (String) data[23];
        String AccNo        = (String) data[24];
        String Vill         = (String) data[25];
        String Pin          = (String) data[26];
        String AccType      = (String) data[27];
        String Relation     = (String) data[28];
        String EpicIDImg    = (String) data[30];
        String ParchaImg    = (String) data[31];

        Page     page = tlPage.get();
        PageBean pom  = tlPom.get();

        System.out.println("[Thread-" + Thread.currentThread().getId() + "] ▶ Row " + rowIndex + " - " + EpicID);

        try {
            pom.searchPerson(EpicID);

            if (pom.logicToSkip(Crop, GP)) {
                status = "SKIP";
                throw new SkipException("Already exists: " + EpicID);
            }
            if (pom.isAadharPrefilled()) {
                status = "SKIP";
                throw new SkipException("Farmer exists: " + EpicID);
            }

            pom.dataEntry(AadharNo);
            pom.farmerDetails(FarmrName, FathrHusName, Relation, Age, Gender, Caste, MobNo, FarmrCat, EpicIDImg, AadharNo);
            pom.farmerResidentialAddress(District, Block, GP, Vill, Pin);
            pom.cropDetailsEntry(District, Block, Crop, GP, Mouza1, KhatianNo1, PlotNo1, AreaInsur1, NatureFarmr1, ParchaImg);
            pom.bankDetailsEntry(FarmrName, AccNo, AccType, IFSCode);
            pom.submitForm();

            System.out.println("✅ Row " + rowIndex + " done");
        } catch (SkipException e) {
            status = "SKIP";
            throw e;
        } catch (Exception e) {
            status = "FAIL";
            System.err.println("❌ Row " + rowIndex + " failed: " + e.getMessage());
            throw e;
        } finally {
            ExcelUtility.updateTestStatus(rowIndex, status);
            System.out.println("📊 Row " + rowIndex + " = " + status);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    @AfterSuite
    public void afterSuite() {
        System.out.println("🔴 Closing all contexts...");
        for (BrowserContext ctx : allContexts) {
            try { ctx.close(); } catch (Exception ignored) {}
        }
        if (browser != null)    try { browser.close();    } catch (Exception ignored) {}
        if (playwright != null) try { playwright.close(); } catch (Exception ignored) {}
        System.out.println("✅ Done.");
    }
}
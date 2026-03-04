package com.mainMethod.automation;

import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException; // <-- Import for exception

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

    private static final int PARALLEL_COUNT = 4;

    private static Playwright playwright;
    private static Browser     browser;
    private static final String SESSION_FILE = "session.json";

    private static final List<BrowserContext>                  allContexts  = Collections.synchronizedList(new ArrayList<>());
    private static final ConcurrentLinkedQueue<BrowserContext> contextPool  = new ConcurrentLinkedQueue<>();

    private static final ThreadLocal<BrowserContext> tlContext = new ThreadLocal<>();
    private static final ThreadLocal<Page>           tlPage    = new ThreadLocal<>();
    private static final ThreadLocal<PageBean>       tlPom     = new ThreadLocal<>();

    // ─────────────────────────────────────────────────────────────────────────────
    @BeforeSuite
    public void beforeSuite() throws Exception {
        // ... (unchanged, same as previous corrected version) ...
        System.out.println("=================================================");
        System.out.println("✅ PLAYWRIGHT PARALLEL — BrowserContext approach");
        System.out.println("   PARALLEL_COUNT = " + PARALLEL_COUNT);
        System.out.println("=================================================");

        Properties prop = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            prop.load(fis);
        }

        playwright = Playwright.create();
        BrowserType.LaunchOptions opts = new BrowserType.LaunchOptions()
            .setHeadless(false)
            .setSlowMo(300);

        String browser_name = prop.getProperty("browser", "chrome").toLowerCase();
        if (browser_name.equals("firefox")) {
            browser = playwright.firefox().launch(opts);
        } else {
            browser = playwright.chromium().launch(opts);
        }
        System.out.println("✅ Browser launched.");

        // Login once
        System.out.println("🚀 Opening login window...");
        BrowserContext masterContext = browser.newContext();
        Page masterPage = masterContext.newPage();
        masterPage.navigate(VARIABLES.SIGN_IN_PAGE_URL);
        masterPage.waitForLoadState();

        boolean sessionLoaded = false;
        if (new java.io.File(SESSION_FILE).exists()) {
            System.out.println("🍪 Found saved session — trying to reuse...");
            masterContext.close();
            masterContext = browser.newContext(
                new Browser.NewContextOptions().setStorageStatePath(Paths.get(SESSION_FILE))
            );
            masterPage = masterContext.newPage();
            masterPage.navigate(VARIABLES.NEW_REGISTRATION_URL);
            masterPage.waitForLoadState();

            String url = masterPage.url();
            if (!url.contains("login") && !url.contains("sign_in")) {
                System.out.println("✅ Session valid — skipping login.");
                sessionLoaded = true;
            } else {
                System.out.println("⚠ Saved session expired — logging in fresh...");
                masterContext.close();
                masterContext = browser.newContext();
                masterPage = masterContext.newPage();
                masterPage.navigate(VARIABLES.SIGN_IN_PAGE_URL);
            }
        }

        if (!sessionLoaded) {
            System.out.println("⏳ Logging in — please enter OTP when prompted...");
            new PageBean(masterPage).login(VARIABLES.EMAIL, VARIABLES.PASSWORD, 1, 2);
            masterPage.navigate(VARIABLES.NEW_REGISTRATION_URL);
            masterPage.waitForLoadState();
            System.out.println("✅ Login complete.");
        }

        masterContext.storageState(new BrowserContext.StorageStateOptions().setPath(Paths.get(SESSION_FILE)));
        masterContext.close();
        System.out.println("💾 Session saved to " + SESSION_FILE);

        System.out.println("🌐 Creating " + PARALLEL_COUNT + " parallel contexts...");
        for (int i = 1; i <= PARALLEL_COUNT; i++) {
            BrowserContext ctx = browser.newContext(
                new Browser.NewContextOptions().setStorageStatePath(Paths.get(SESSION_FILE))
            );
            Page page = ctx.newPage();
            page.navigate(VARIABLES.NEW_REGISTRATION_URL);
            page.waitForLoadState();

            try {
                page.waitForSelector("//h4[contains(text(),'SBI GENERAL INSURANCE COMPANY LIMITED')]",
                    new Page.WaitForSelectorOptions().setTimeout(15000));
                System.out.println("  ✔ Context " + i + " ready (logged in)");
            } catch (Exception e) {
                System.out.println("  ⚠ Context " + i + " — form not detected, check session");
            }

            allContexts.add(ctx);
            contextPool.add(ctx);
        }
        System.out.println("\n✅ READY — " + PARALLEL_COUNT + " isolated contexts running in parallel.\n");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    @BeforeMethod
    public void acquireContext() throws InterruptedException {
        BrowserContext ctx = null;
        while (ctx == null) {
            ctx = contextPool.poll();
            if (ctx == null) Thread.sleep(300);
        }

        Page page;
        if (ctx.pages().isEmpty()) {
            page = ctx.newPage();
        } else {
            page = ctx.pages().get(0);
            // Verify the page is still valid by checking its URL
            try {
                page.url(); // This will throw if page is detached
            } catch (PlaywrightException e) {
                System.out.println("[Thread-" + Thread.currentThread().getId() + "] Page detached, creating new page in context.");
                page = ctx.newPage();
            }
        }

        tlContext.set(ctx);
        tlPage.set(page);
        tlPom.set(new PageBean(page));
        System.out.println("[Thread-" + Thread.currentThread().getId() + "] ▶ acquired context");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    @AfterMethod
    public void releaseContext() {
        BrowserContext ctx = tlContext.get();
        Page page = tlPage.get();
        boolean contextValid = true;

        if (ctx != null && page != null) {
            try {
                page.navigate(VARIABLES.NEW_REGISTRATION_URL);
                page.waitForSelector("//h4[contains(text(),'SBI GENERAL INSURANCE COMPANY LIMITED')]",
                    new Page.WaitForSelectorOptions().setTimeout(10000));
            } catch (Exception e) {
                System.err.println("[Thread-" + Thread.currentThread().getId() + "] Warning: could not reset page, context may be invalid.");
                contextValid = false;
            }

            if (!contextValid) {
                // Close the broken context
                try { ctx.close(); } catch (Exception ignored) {}
                // Remove it from the global list (safe because allContexts is synchronized)
                allContexts.remove(ctx);

                // Create a brand new context to replace it
                try {
                    BrowserContext newCtx = browser.newContext(
                        new Browser.NewContextOptions().setStorageStatePath(Paths.get(SESSION_FILE))
                    );
                    Page newPage = newCtx.newPage();
                    newPage.navigate(VARIABLES.NEW_REGISTRATION_URL);
                    newPage.waitForSelector("//h4[contains(text(),'SBI GENERAL INSURANCE COMPANY LIMITED')]",
                        new Page.WaitForSelectorOptions().setTimeout(15000));
                    allContexts.add(newCtx);
                    contextPool.add(newCtx);
                    System.out.println("[Thread-" + Thread.currentThread().getId() + "] Replaced invalid context with fresh one.");
                } catch (Exception ex) {
                    System.err.println("[Thread-" + Thread.currentThread().getId() + "] Failed to create replacement context.");
                }
            } else {
                contextPool.add(ctx);
            }

            tlContext.remove();
            tlPage.remove();
            tlPom.remove();
            System.out.println("[Thread-" + Thread.currentThread().getId() + "] ◀ released context");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    @DataProvider(name = "excelData", parallel = true)
    public Object[][] testMainMethod() {
        return ExcelUtility.getExcelData();
    }

    // ─────────────────────────────────────────────────────────────────────────────
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

        System.out.println("[Thread-" + Thread.currentThread().getId() + "] ▶ Processing Row " + rowIndex + " - Epic ID: " + EpicID);

        try {
            checkFormPage(page);

            pom.searchPerson(EpicID);

            if (pom.logicToSkip(Crop, GP)) {
                status = "SKIP";
                System.out.println("⏭️ Row " + rowIndex + " skipped - already exists");
                throw new SkipException("Already exists. Skipping: " + EpicID);
            }

            pom.dataEntry(AadharNo);
            pom.farmerDetails(FarmrName, FathrHusName, Relation, Age, Gender,
                              Caste, MobNo, FarmrCat, EpicIDImg, AadharNo);
            pom.farmerResidentialAddress(District, Block, GP, Vill, Pin);
            pom.cropDetailsEntry(District, Block, Crop, GP, Mouza1, KhatianNo1,
                                 PlotNo1, AreaInsur1, NatureFarmr1, ParchaImg);
            pom.bankDetailsEntry(FarmrName, AccNo, AccType, IFSCode);
            pom.submitForm();

            System.out.println("✅ Row " + rowIndex + " completed successfully");
        } catch (SkipException e) {
            status = "SKIP";
            throw e;
        } catch (Exception e) {
            status = "FAIL";
            System.err.println("❌ Row " + rowIndex + " failed: " + e.getMessage());
            e.printStackTrace();
            throw e;
        } finally {
            ExcelUtility.updateTestStatus(rowIndex, status);
            System.out.println("📊 Excel updated: Row " + rowIndex + " = " + status);
            System.out.println("----------------------------------------");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    private void checkFormPage(Page page) {
        String url = page.url();
        if (url.contains("login") || url.contains("sign_in") || !url.contains(VARIABLES.NEW_REGISTRATION_URL.split("/")[2])) {
            page.navigate(VARIABLES.NEW_REGISTRATION_URL);
            page.waitForLoadState();
        }
        try {
            page.waitForSelector("//h4[contains(text(),'SBI GENERAL INSURANCE COMPANY LIMITED')]",
                new Page.WaitForSelectorOptions().setTimeout(15000));
        } catch (Exception e) {
            System.out.println("⚠ Form header not found, reloading...");
            page.reload();
            page.waitForLoadState();
            page.waitForSelector("//h4[contains(text(),'SBI GENERAL INSURANCE COMPANY LIMITED')]",
                new Page.WaitForSelectorOptions().setTimeout(15000));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    @AfterSuite
    public void afterSuite() {
        System.out.println("🔴 Closing all contexts...");
        for (BrowserContext ctx : allContexts) {
            try { ctx.close(); } catch (Exception ignored) {}
        }
        if (browser != null) try { browser.close(); } catch (Exception ignored) {}
        if (playwright != null) try { playwright.close(); } catch (Exception ignored) {}
        System.out.println("✅ Done.");
    }
}
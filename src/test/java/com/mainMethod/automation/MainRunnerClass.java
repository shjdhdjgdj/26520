package com.mainMethod.automation;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.SkipException;
import org.testng.annotations.*;

import Freelance.com.projectSetup.ExcelUtility;
import config.VARIABLES;
import io.github.bonigarcia.wdm.WebDriverManager;

public class MainRunnerClass {

    // ── Tune only this — everything else is auto-detected ────────────────────────
    private static final int PARALLEL_COUNT = 4;
    private static final String PROFILE_PREFIX = "SeleniumProfile_";

    // ── Resolved at runtime ───────────────────────────────────────────────────────
    private static String chromeUserDataDir; // auto-detected per machine

    // ── Driver pool ───────────────────────────────────────────────────────────────
    private static final List<WebDriver>                  allDrivers     = Collections.synchronizedList(new ArrayList<>());
    private static final ConcurrentLinkedQueue<WebDriver> driverPool     = new ConcurrentLinkedQueue<>();
    private static final List<Path>                       copiedProfiles = Collections.synchronizedList(new ArrayList<>());

    // ── ThreadLocal — each thread owns one Chrome exclusively ────────────────────
    private static final ThreadLocal<WebDriver> tlDriver = new ThreadLocal<>();
    private static final ThreadLocal<PageBean>  tlPom    = new ThreadLocal<>();

    // ─────────────────────────────────────────────────────────────────────────────
    @BeforeSuite
    public void beforeSuite() throws Exception {

        System.out.println("=================================================");
        System.out.println("✅ PARALLEL VERSION 4 — cookie-inject approach");
        System.out.println("   PARALLEL_COUNT = " + PARALLEL_COUNT);
        System.out.println("=================================================");

        // ── Step 1: Auto-detect Chrome User Data path ─────────────────────────────
        chromeUserDataDir = detectChromeUserDataDir();
        System.out.println("📁 Chrome profile: " + chromeUserDataDir);
        WebDriverManager.chromedriver().setup();

        // ── Step 2: Kill Chrome so Default profile is not locked ─────────────────
        System.out.println("🔍 Killing any existing Chrome processes...");
        try {
            Runtime.getRuntime().exec("taskkill /F /IM chrome.exe /T").waitFor();
            Thread.sleep(3000);
            System.out.println("✅ Chrome cleared.");
        } catch (Exception ignored) {}

        // ── Step 3: Launch master on Default — load real session ─────────────────
        System.out.println("🚀 Launching master Chrome (Default profile)...");
        WebDriver masterDriver = launchChrome(chromeUserDataDir, "Default");
        masterDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        masterDriver.get(VARIABLES.NEW_REGISTRATION_URL);
        Thread.sleep(4000);

        String landedUrl = masterDriver.getCurrentUrl();
        boolean needsLogin = landedUrl.contains("login")
                          || landedUrl.contains("sign_in")
                          || landedUrl.contains("signin");

        if (needsLogin) {
            System.out.println("⏳ Logging in...");
            masterDriver.get(VARIABLES.SIGN_IN_PAGE_URL);
            new PageBean(masterDriver).login(VARIABLES.EMAIL, VARIABLES.PASSWORD, 1, 2);
            System.out.println("✅ Login complete.");
            // Navigate to form to ensure session is fully established
            masterDriver.get(VARIABLES.NEW_REGISTRATION_URL);
            Thread.sleep(2000);
        } else {
            System.out.println("✅ Already logged in.");
        }

        // ── Step 4: Harvest all session cookies from master ───────────────────────
        System.out.println("🍪 Harvesting session cookies...");
        Set<org.openqa.selenium.Cookie> sessionCookies = masterDriver.manage().getCookies();
        String currentDomain = masterDriver.getCurrentUrl();
        System.out.println("   Captured " + sessionCookies.size() + " cookies.");

        masterDriver.quit();
        Thread.sleep(1000);
        System.out.println("✅ Master Chrome closed.");

        // ── Step 5: Launch N worker Chromes with FRESH temp profiles ─────────────
        // Fresh profiles = no lock issues, no copy needed, starts instantly
        // We inject the session cookies after launch
        System.out.println("🌐 Launching " + PARALLEL_COUNT + " Chrome instances...");

        for (int i = 1; i <= PARALLEL_COUNT; i++) {
            // Each worker gets its own temp user-data-dir — completely independent
            String tempDir = System.getProperty("java.io.tmpdir") + File.separator + "SeleniumWorker_" + i;
            Path tempPath = Paths.get(tempDir);
            if (Files.exists(tempPath)) deleteDirectory(tempPath);
            Files.createDirectories(tempPath);
            copiedProfiles.add(tempPath); // track for cleanup

            ChromeOptions opts = new ChromeOptions();
            opts.addArguments(
                "--user-data-dir=" + tempDir,   // fresh isolated temp dir per worker
                "--profile-directory=Default",
                "--no-first-run",
                "--no-default-browser-check",
                "--disable-extensions",
                "--disable-notifications",
                "--disable-popup-blocking",
                "--remote-debugging-port=0",
                "--no-sandbox",
                "--disable-gpu",
                "--disable-dev-shm-usage"
            );

            WebDriver d = new ChromeDriver(opts);
            d.manage().window().maximize();
            d.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

            // Navigate to site domain first (cookies require matching domain)
            d.get(VARIABLES.SIGN_IN_PAGE_URL);
            Thread.sleep(2000);

            // Inject all session cookies from master
            for (org.openqa.selenium.Cookie cookie : sessionCookies) {
                try {
                    d.manage().addCookie(cookie);
                } catch (Exception ignored) {} // skip if cookie domain doesn't match exactly
            }

            // Navigate to form — should be logged in now via injected cookies
            d.get(VARIABLES.NEW_REGISTRATION_URL);
            Thread.sleep(3000);

            String workerUrl = d.getCurrentUrl();
            if (workerUrl.contains("login") || workerUrl.contains("sign_in")) {
                System.out.println("  ⚠ Instance " + i + " — cookie injection failed, attempting login...");
                new PageBean(d).login(VARIABLES.EMAIL, VARIABLES.PASSWORD, 1, 2);
            } else {
                System.out.println("  ✔ Instance " + i + " ready (logged in via cookies)");
            }

            allDrivers.add(d);
            driverPool.add(d);
        }

        System.out.println("\n✅ TRUE PARALLEL READY — "
            + PARALLEL_COUNT + " independent Chrome instances running.\n");
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Auto-detect Chrome's User Data directory for Windows / Mac / Linux
    private String detectChromeUserDataDir() {
        String os   = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");

        List<String> candidates = new ArrayList<>();

        if (os.contains("win")) {
            candidates.add(home + "\\AppData\\Local\\Google\\Chrome\\User Data");
            candidates.add(home + "\\AppData\\Local\\Google\\Chrome Beta\\User Data");
            candidates.add(home + "\\AppData\\Local\\Chromium\\User Data");
        } else if (os.contains("mac")) {
            candidates.add(home + "/Library/Application Support/Google/Chrome");
            candidates.add(home + "/Library/Application Support/Chromium");
        } else {
            // Linux
            candidates.add(home + "/.config/google-chrome");
            candidates.add(home + "/.config/chromium");
        }

        for (String path : candidates) {
            File f = new File(path);
            if (f.exists() && f.isDirectory()) {
                // Verify it looks like a real Chrome profile folder
                if (new File(f, "Default").exists()) {
                    return path;
                }
            }
        }

        throw new RuntimeException(
            "❌ Could not auto-detect Chrome profile folder.\n" +
            "   Searched: " + candidates + "\n" +
            "   Please set CHROME_USER_DATA manually in MainRunnerClass.java"
        );
    }

    // ─────────────────────────────────────────────────────────────────────────────
    private WebDriver launchChrome(String userDataDir, String profileDir) {
        ChromeOptions opts = new ChromeOptions();
        opts.addArguments(
            "--user-data-dir=" + userDataDir,
            "--profile-directory=" + profileDir,
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-extensions",
            "--disable-notifications",
            "--disable-popup-blocking",
            "--remote-debugging-port=0",     // avoid port conflicts between instances
            "--no-sandbox",                  // required when launched from subprocess (Maven)
            "--disable-gpu",                 // prevents crash in headless/subprocess context
            "--disable-dev-shm-usage",       // prevents /dev/shm issues on constrained systems
            "--disable-background-networking",
            "--disable-software-rasterizer"
        );
        WebDriver d = new ChromeDriver(opts);
        d.manage().window().maximize();
        return d;
    }

    // ─────────────────────────────────────────────────────────────────────────────
    @BeforeMethod
    public void acquireDriver() throws InterruptedException {
        WebDriver driver = null;
        while (driver == null) {
            driver = driverPool.poll();
            if (driver == null) Thread.sleep(300);
        }
        tlDriver.set(driver);
        tlPom.set(new PageBean(driver));
        System.out.println("[Thread-" + Thread.currentThread().getId() + "] ▶ acquired Chrome");
    }

    @AfterMethod
    public void releaseDriver() {
        WebDriver driver = tlDriver.get();
        if (driver != null) {
            try { driver.navigate().to(VARIABLES.NEW_REGISTRATION_URL); }
            catch (Exception ignored) {}
            driverPool.add(driver);
            tlDriver.remove();
            tlPom.remove();
            System.out.println("[Thread-" + Thread.currentThread().getId() + "] ◀ released Chrome");
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

        String FarmrName    = (String) data[2];
        String FathrHusName = (String) data[3];
        String EpicID       = (String) data[4];
        String AadharNo     = (String) data[5];
        String Age          = (String) data[7];
        String Gender       = (String) data[8];
        String Caste        = (String) data[9];
        String MobNo        = (String) data[10];
        String Crop         = (String) data[11];
        String District     = (String) data[12];
        String Block        = (String) data[13];
        String GP           = (String) data[14];
        String Mouza1       = (String) data[15];
        String KhatianNo1   = (String) data[17];
        String PlotNo1      = (String) data[18];
        String AreaInsur1   = (String) data[19];
        String FarmrCat     = (String) data[20];
        String NatureFarmr1 = (String) data[21];
        String IFSCode      = (String) data[22];
        String AccNo        = (String) data[23];
        String Vill         = (String) data[24];
        String Pin          = (String) data[25];
        String AccType      = (String) data[26];
        String Relation     = (String) data[27];
        String EpicIDImg    = (String) data[29];
        String ParchaImg    = (String) data[30];

        WebDriver driver = tlDriver.get(); // this thread's exclusive Chrome
        PageBean  pom    = tlPom.get();

        System.out.println("[Thread-" + Thread.currentThread().getId()
            + "] Processing: " + EpicID);

        checkElementWithRetries(driver, VARIABLES.NEW_REGISTRATION_URL,
            "//h4[contains(text(),'SBI GENERAL INSURANCE COMPANY LIMITED')]", 10, 3);

        pom.searchPerson(EpicID);

        if (pom.logicToSkip(Crop, GP)) {
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
    }

    // ─────────────────────────────────────────────────────────────────────────────
    public void checkElementWithRetries(WebDriver driver, String url, String xpath,
                                        int maxRetries, int maxReloads)
            throws InterruptedException {
        boolean found = false;
        int retries = 0, reloads = 0;

        while (!found && reloads < maxReloads) {
            while (retries < maxRetries) {
                try {
                    if (driver.findElement(By.xpath(xpath)).isDisplayed()) {
                        found = true;
                        break;
                    }
                    driver.navigate().refresh();
                    Thread.sleep(2000);
                } catch (NoSuchElementException e) {
                    if (++retries >= maxRetries) {
                        driver.get(url);
                        retries = 0;
                        reloads++;
                        break;
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    @AfterSuite
    public void afterSuite() {
        System.out.println("🔴 Quitting all Chrome instances...");
        for (WebDriver d : allDrivers) {
            try { d.quit(); } catch (Exception ignored) {}
        }
        System.out.println("🧹 Cleaning up copied profiles...");
        // also clean up master profile copy
        try {
            Path masterCleanup = Paths.get(chromeUserDataDir, PROFILE_PREFIX + "master");
            if (Files.exists(masterCleanup)) {
                deleteDirectory(masterCleanup);
                System.out.println("  ✔ Deleted: " + PROFILE_PREFIX + "master");
            }
        } catch (Exception ignored) {}
        for (Path p : copiedProfiles) {
            try {
                deleteDirectory(p);
                System.out.println("  ✔ Deleted: " + p.getFileName());
            } catch (Exception e) {
                System.out.println("  ⚠ Could not delete: " + p.getFileName());
            }
        }
        System.out.println("✅ Done.");
    }

    // ── File utilities ────────────────────────────────────────────────────────────
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try {
                    Files.copy(file, target.resolve(source.relativize(file)),
                        StandardCopyOption.REPLACE_EXISTING);
                } catch (IOException ignored) {} // skip locked files (e.g. Chrome lock)
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private void deleteDirectory(Path path) throws IOException {
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                try { Files.delete(file); } catch (IOException ignored) {}
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                try { Files.delete(dir); } catch (IOException ignored) {}
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

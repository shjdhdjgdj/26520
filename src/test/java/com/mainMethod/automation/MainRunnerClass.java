package com.mainMethod.automation;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
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

        // ── Step 1: Auto-detect Chrome User Data path for this machine ────────────
        chromeUserDataDir = detectChromeUserDataDir();
        System.out.println("📁 Chrome profile path: " + chromeUserDataDir);

        WebDriverManager.chromedriver().setup();

        // ── Step 2: Kill any Chrome already using the Default profile ─────────────
        // If Chrome is open with Default profile, Selenium cannot attach to same profile
        System.out.println("🔍 Checking for existing Chrome processes...");
        try {
            Runtime.getRuntime().exec("taskkill /F /IM chrome.exe /T").waitFor();
            Thread.sleep(2000); // give Chrome time to fully release profile lock
            System.out.println("✅ Existing Chrome closed (or none was running).");
        } catch (Exception ignored) {}

        // ── Step 3: Make a temp copy of Default profile for master driver ─────────
        // Never open Default directly — copy it so we never conflict with user's Chrome
        String masterProfileName = PROFILE_PREFIX + "master";
        Path sourceProfile = Paths.get(chromeUserDataDir, "Default");
        Path masterProfilePath = Paths.get(chromeUserDataDir, masterProfileName);

        System.out.println("📂 Preparing master profile copy...");
        if (Files.exists(masterProfilePath)) deleteDirectory(masterProfilePath);
        copyDirectory(sourceProfile, masterProfilePath);

        // ── Step 4: Launch master Chrome using the COPY (not Default directly) ────
        System.out.println("🚀 Launching master Chrome...");
        ChromeOptions masterOpts = new ChromeOptions();
        masterOpts.addArguments(
            "--user-data-dir=" + chromeUserDataDir,
            "--profile-directory=" + masterProfileName,
            "--no-first-run",
            "--no-default-browser-check",
            "--disable-extensions",
            "--disable-notifications",
            "--disable-popup-blocking",
            "--remote-debugging-port=0"  // avoid port conflicts
        );
        WebDriver masterDriver = new ChromeDriver(masterOpts);
        masterDriver.manage().window().maximize();
        masterDriver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        // ── Step 5: Check if already logged in ───────────────────────────────────
        masterDriver.get(VARIABLES.NEW_REGISTRATION_URL);
        Thread.sleep(3000);

        String landedUrl = masterDriver.getCurrentUrl();
        boolean needsLogin = landedUrl.contains("login")
                          || landedUrl.contains("sign_in")
                          || landedUrl.contains("signin");

        if (needsLogin) {
            System.out.println("⏳ Session expired or first run — logging in...");
            masterDriver.get(VARIABLES.SIGN_IN_PAGE_URL);
            new PageBean(masterDriver).login(VARIABLES.EMAIL, VARIABLES.PASSWORD, 1, 2);
            System.out.println("✅ Login complete. Session saved to master profile copy.");
        } else {
            System.out.println("✅ Already logged in — skipping login entirely.");
        }

        // ── Step 6: Quit master — flush session to disk, then copy to worker profiles
        masterDriver.quit();
        Thread.sleep(2000);
        System.out.println("✅ Master Chrome closed. Session flushed to disk.");

        // ── Step 7: Copy master profile → SeleniumProfile_1 ... _N ──────────────
        // Use the master copy (which now has fresh session) as source
        System.out.println("📂 Copying profile " + PARALLEL_COUNT + " times...");

        for (int i = 1; i <= PARALLEL_COUNT; i++) {
            String profileName = PROFILE_PREFIX + i;
            Path   destProfile = Paths.get(chromeUserDataDir, profileName);

            if (Files.exists(destProfile)) deleteDirectory(destProfile);
            copyDirectory(masterProfilePath, destProfile);  // copy from master (has session)
            copiedProfiles.add(destProfile);
            System.out.println("  ✔ " + profileName);
        }

        // ── Step 6: Launch N independent Chrome instances — all pre-logged-in ─────
        System.out.println("🌐 Launching " + PARALLEL_COUNT + " Chrome instances...");
        for (int i = 1; i <= PARALLEL_COUNT; i++) {
            WebDriver d = launchChrome(chromeUserDataDir, PROFILE_PREFIX + i);
            d.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            d.get(VARIABLES.NEW_REGISTRATION_URL);
            allDrivers.add(d);
            driverPool.add(d);
            System.out.println("  ✔ Instance " + i + " ready");
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
            "--disable-popup-blocking"
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
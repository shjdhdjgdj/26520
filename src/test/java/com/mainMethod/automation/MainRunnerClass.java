package com.mainMethod.automation;

import java.io.FileInputStream;
import java.nio.file.Paths;
import java.util.Properties;

import org.testng.SkipException;
import org.testng.annotations.*;

import com.microsoft.playwright.*;

import Freelance.com.projectSetup.ExcelUtility;
import config.VARIABLES;

public class MainRunnerClass {

    private static Playwright playwright;
    private static Browser browser;

    private static final String SESSION_FILE = "session.json";

    private static ThreadLocal<BrowserContext> tlContext = new ThreadLocal<>();
    private static ThreadLocal<Page> tlPage = new ThreadLocal<>();
    private static ThreadLocal<PageBean> tlPom = new ThreadLocal<>();


    // =====================================================
    // SUITE START
    // =====================================================

    @BeforeSuite
    public void beforeSuite() throws Exception {

        System.out.println("=========================================");
        System.out.println("PLAYWRIGHT TRUE PARALLEL EXECUTION");
        System.out.println("THREADS = 2");
        System.out.println("=========================================");

        Properties prop = new Properties();
        prop.load(new FileInputStream("config.properties"));

        playwright = Playwright.create();

        BrowserType.LaunchOptions options =
                new BrowserType.LaunchOptions().setHeadless(false);

        String browserName = prop.getProperty("browser", "chrome");

        if(browserName.equalsIgnoreCase("firefox"))
            browser = playwright.firefox().launch(options);
        else
            browser = playwright.chromium().launch(options);

        loginAndSaveSession();
    }


    // =====================================================
    // LOGIN ONCE AND SAVE SESSION
    // =====================================================

    private void loginAndSaveSession() throws Exception {

        if(new java.io.File(SESSION_FILE).exists()) {

            System.out.println("Using existing session.json");
            return;
        }

        BrowserContext context = browser.newContext();
        Page page = context.newPage();

        page.navigate(VARIABLES.SIGN_IN_PAGE_URL);

        System.out.println("Please enter OTP for login...");

        PageBean login = new PageBean(page);
        login.login(VARIABLES.EMAIL, VARIABLES.PASSWORD, 1, 2);

        page.navigate(VARIABLES.NEW_REGISTRATION_URL);
        page.waitForLoadState();

        context.storageState(
                new BrowserContext.StorageStateOptions()
                        .setPath(Paths.get(SESSION_FILE)));

        context.close();

        System.out.println("Session saved.");
    }


    // =====================================================
    // THREAD SETUP
    // =====================================================

    @BeforeMethod
    public void setupThread() {

        BrowserContext context =
                browser.newContext(
                        new Browser.NewContextOptions()
                                .setStorageStatePath(Paths.get(SESSION_FILE)));

        Page page = context.newPage();

        page.navigate(VARIABLES.NEW_REGISTRATION_URL);
        page.waitForLoadState();

        tlContext.set(context);
        tlPage.set(page);
        tlPom.set(new PageBean(page));

        System.out.println("Thread "
                + Thread.currentThread().getId()
                + " started");
    }


    // =====================================================
    // DATA PROVIDER
    // =====================================================

    @DataProvider(name="excelData", parallel=true)
    public Object[][] getData(){

        return ExcelUtility.getExcelData();
    }


    // =====================================================
    // MAIN TEST
    // =====================================================

    @Test(dataProvider="excelData")
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

        Page page = tlPage.get();
        PageBean pom = tlPom.get();

        System.out.println("Processing Row " + rowIndex + " Epic: " + EpicID);

        try {

            pom.searchPerson(EpicID);

            if (pom.logicToSkip(Crop, GP)) {

                status = "SKIP";
                throw new SkipException("Already exists");
            }

            if (pom.isAadharPrefilled()) {

                status = "SKIP";
                throw new SkipException("Farmer exists");
            }

            pom.dataEntry(AadharNo);

            pom.farmerDetails(
                    FarmrName,
                    FathrHusName,
                    Relation,
                    Age,
                    Gender,
                    Caste,
                    MobNo,
                    FarmrCat,
                    EpicIDImg,
                    AadharNo
            );

            pom.farmerResidentialAddress(
                    District,
                    Block,
                    GP,
                    Vill,
                    Pin
            );

            pom.cropDetailsEntry(
                    District,
                    Block,
                    Crop,
                    GP,
                    Mouza1,
                    KhatianNo1,
                    PlotNo1,
                    AreaInsur1,
                    NatureFarmr1,
                    ParchaImg
            );

            pom.bankDetailsEntry(
                    FarmrName,
                    AccNo,
                    AccType,
                    IFSCode
            );

            pom.submitForm();

            System.out.println("Row " + rowIndex + " completed.");

        }
        catch (SkipException e){

            status = "SKIP";
            throw e;
        }
        catch (Exception e){

            status = "FAIL";
            e.printStackTrace();
        }
        finally{

            ExcelUtility.updateTestStatus(rowIndex, status);
        }
    }


    // =====================================================
    // THREAD CLEANUP
    // =====================================================

    @AfterMethod
    public void closeThread(){

        BrowserContext context = tlContext.get();

        if(context != null)
            context.close();

        tlContext.remove();
        tlPage.remove();
        tlPom.remove();

        System.out.println("Thread finished.");
    }


    // =====================================================
    // SUITE END
    // =====================================================

    @AfterSuite
    public void afterSuite(){

        browser.close();
        playwright.close();

        System.out.println("Automation finished.");
    }
}
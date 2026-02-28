package com.mainMethod.automation;

import java.time.Duration;
import java.util.NoSuchElementException;
import java.io.FileInputStream;
import java.util.Properties;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.SkipException;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.AfterSuite;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import Freelance.com.projectSetup.ExcelUtility;
import config.VARIABLES;
import io.github.bonigarcia.wdm.WebDriverManager;

public class MainRunnerClass {

    private WebDriver driver;
    static WebDriverWait wait;
    private PageBean pom;
    
    // Track current row for static counter approach (alternative)
    private static int currentRow = 1;

    @BeforeSuite
    public void beforeSuite() throws InterruptedException {

        Properties prop = new Properties();
        try (FileInputStream fis = new FileInputStream("config.properties")) {
            prop.load(fis);
        } catch (Exception e) {
            throw new RuntimeException("config.properties not found in AutomationClient folder", e);
        }
        String browser = prop.getProperty("browser");

        if (browser.equalsIgnoreCase("chrome")) {
            WebDriverManager.chromedriver().setup();
            driver = new ChromeDriver();
        } else if (browser.equalsIgnoreCase("firefox")) {
            WebDriverManager.firefoxdriver().setup();
            driver = new FirefoxDriver();
        } else {
            throw new RuntimeException("Invalid browser in config.properties");
        }

        driver.manage().window().maximize();
        driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));

        pom = new PageBean(driver);
        try {
            driver.get(VARIABLES.SIGN_IN_PAGE_URL);
        } catch (NoSuchElementException e) {
            checkElementWithRetries(VARIABLES.SIGN_IN_PAGE_URL, "//*[contains(text(),'Insurance Log In')]", 10, 3);
        }

        wait = new WebDriverWait(driver, Duration.ofSeconds(3));

        try {
            pom.login(VARIABLES.EMAIL, VARIABLES.PASSWORD, 1, 2);
            openNewTab();
            driver.get(VARIABLES.NEW_REGISTRATION_URL);
        } catch (NoSuchElementException | InterruptedException e) {
            e.printStackTrace();
            System.out.println("Error");
            checkElementWithRetries(VARIABLES.NEW_REGISTRATION_URL,
                    "//h4[contains(text(),'SBI GENERAL INSURANCE COMPANY LIMITED')]", 5, 5);
        }
    }

    public void checkElementWithRetries(String url, String xpath, int maxRetries, int maxTabSwitches)
            throws InterruptedException {
        boolean error = true;
        int retryCount = 0;
        int tabCount = 0;

        while (error && tabCount < maxTabSwitches) {
            while (retryCount < maxRetries) {
                try {
                    if (driver.findElement(By.xpath(xpath)).isDisplayed()) {
                        error = false;
                        break;
                    } else {
                        driver.navigate().refresh();
                        Thread.sleep(2000);
                    }
                } catch (NoSuchElementException e) {
                    retryCount++;
                    if (retryCount >= maxRetries) {
                        System.out.println("Max retries reached. Element not found.");
                        openNewTab();
                        driver.get(url);
                        retryCount = 0;
                        tabCount++;
                        if (tabCount >= maxTabSwitches) {
                            System.out.println("Max tab switches reached. Exiting.");
                            error = false;
                            break;
                        }
                        break;
                    }
                }
            }
        }
    }

    private void openNewTab() {
        ((JavascriptExecutor) driver).executeScript("window.open('about:blank', '_blank');");
        String originalWindow = driver.getWindowHandle();
        for (String windowHandle : driver.getWindowHandles()) {
            if (!windowHandle.equals(originalWindow)) {
                driver.switchTo().window(windowHandle);
                break;
            }
        }
    }

    @DataProvider(name = "excelData")
    public Object[][] testMainMethod() throws InterruptedException {
        return ExcelUtility.getExcelData();
    }

    @Test(dataProvider = "excelData")
    public void runTests(Object[] data) throws InterruptedException {
        // Extract row index from position 0 (added in ExcelUtility)
        int rowIndex = (int) data[0];
        String status = "PASS";
        
        // Alternative: Use static counter if DataProvider not modified
        // int rowIndex = currentRow++;

        try {
            // Extract all fields with shifted indices (+1 because data[0] is row index)
            String FarmrName = (String) data[3];      // was index 2
            String FathrHusName = (String) data[4];   // was index 3
            String EpicID = (String) data[5];         // was index 4
            String AadharNo = (String) data[6];       // was index 5
            String Age = (String) data[8];            // was index 7
            String Gender = (String) data[9];         // was index 8
            String Caste = (String) data[10];         // was index 9
            String MobNo = (String) data[11];         // was index 10
            String Crop = (String) data[12];          // was index 11
            String District = (String) data[13];      // was index 12
            String Block = (String) data[14];         // was index 13
            String GP = (String) data[15];            // was index 14
            String Mouza1 = (String) data[16];        // was index 15
            String KhatianNo1 = (String) data[18];    // was index 17
            String PlotNo1 = (String) data[19];       // was index 18
            String AreaInsur1 = (String) data[20];    // was index 19
            String FarmrCat = (String) data[21];      // was index 20
            String NatureFarmr1 = (String) data[22];  // was index 21
            String IFSCode = (String) data[23];       // was index 22
            String AccNo = (String) data[24];         // was index 23
            String Vill = (String) data[25];          // was index 24
            String Pin = (String) data[26];           // was index 25
            String AccType = (String) data[27];       // was index 26
            String Relation = (String) data[28];      // was index 27
            String EpicIDImg = (String) data[30];     // was index 29
            String ParchaImg = (String) data[31];     // was index 30

            // Log which row is being processed
            System.out.println("▶️ Processing Row " + rowIndex + " - Epic ID: " + EpicID + " - Farmer: " + FarmrName);

            // Verify page is loaded
            checkElementWithRetries(VARIABLES.NEW_REGISTRATION_URL,
                    "//h4[contains(text(),'SBI GENERAL INSURANCE COMPANY LIMITED')]", 10, 5);

            // Search by Epic ID
            pom.searchPerson(EpicID);

            // Check if record already exists
            if (pom.logicToSkip(Crop, GP)) {
                status = "SKIP";
                System.out.println("⏭️ Skipping Row " + rowIndex + " - Consumer already exists");
                throw new SkipException("Consumer already exists. Skipping test.");
            }

            // Fill Aadhar number
            pom.dataEntry(AadharNo);

            // Fill farmer details
            pom.farmerDetails(FarmrName, FathrHusName, Relation, Age, Gender, Caste, MobNo, FarmrCat, EpicIDImg,
                    AadharNo);

            // Fill residential address
            pom.farmerResidentialAddress(District, Block, GP, Vill, Pin);

            // Fill crop details
            pom.cropDetailsEntry(District, Block, Crop, GP, Mouza1, KhatianNo1, PlotNo1, AreaInsur1, NatureFarmr1,
                    ParchaImg);

            // Fill bank details
            pom.bankDetailsEntry(FarmrName, AccNo, AccType, IFSCode);

            // Submit the form
            pom.submitForm();

            System.out.println("✅ Row " + rowIndex + " completed successfully");

        } catch (SkipException e) {
            status = "SKIP";
            throw e;
        } catch (Exception e) {
            status = "FAIL";
            System.err.println("❌ Row " + rowIndex + " failed: " + e.getMessage());
            throw e;
        } finally {
            // Update Excel with test status
            // Optional: Add delay before writing status if needed
            // Thread.sleep(10000);
            
            ExcelUtility.updateTestStatus(rowIndex, status);
            System.out.println("📊 Excel updated: Row " + rowIndex + " = " + status);
            System.out.println("----------------------------------------");
        }
    }

    @AfterMethod
    public void pageRefresh() {
        try {
            driver.navigate().refresh();
            Thread.sleep(2000);
        } catch (Exception e) {
            System.err.println("Error refreshing page: " + e.getMessage());
        }
    }

    @AfterSuite
    public void afterSuite() {
        if (driver != null) {
            driver.quit();
            System.out.println("✅ Browser closed. Test suite completed.");
        }
    }
}
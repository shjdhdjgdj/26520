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

	@BeforeSuite
	public void beforeSuite() throws InterruptedException {

		Properties prop = new Properties();
		try (FileInputStream fis = new FileInputStream("config.properties")) {
			prop.load(fis);
			} catch (Exception e) {
				throw new RuntimeException("config.properties not found in AutomationClient folder", e);
			}
			String browser = prop.getProperty("browser");
			
		if(browser.equalsIgnoreCase("chrome")) {
   		 	WebDriverManager.chromedriver().setup();
    		driver = new ChromeDriver();
		}
		else if(browser.equalsIgnoreCase("firefox")) {
    		WebDriverManager.firefoxdriver().setup();
    		driver = new FirefoxDriver();
		}
		else {
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
			pom.login(VARIABLES.EMAIL, VARIABLES.PASSWORD, 2, 1);
			openNewTab();
			driver.get(VARIABLES.NEW_REGISTRATION_URL);
		} catch (NoSuchElementException | InterruptedException e) {
			e.printStackTrace();
			System.out.println("Error");
			checkElementWithRetries(VARIABLES.NEW_REGISTRATION_URL, "//h4[contains(text(),'SBI GENERAL INSURANCE COMPANY LIMITED')]",
					5, 5);
		} 
	}

	public void checkElementWithRetries(String url, String xpath, int maxRetries, int maxTabSwitches)
			throws InterruptedException {
		boolean error = true;
		int retryCount = 0;
		int tabCount = 0;

		// Outer loop to manage the number of tabs
		while (error && tabCount < maxTabSwitches) {
			// Inner loop to handle refreshing and checking the element
			while (retryCount < maxRetries) {
				try {
					// Try to find the element and check if it's displayed
					if (driver.findElement(By.xpath(xpath)).isDisplayed()) {
						error = false; // Element found, exit the loop
						break;
					} else {
						driver.navigate().refresh(); // refresh the page
						Thread.sleep(2000); // wait for 2 seconds before trying again
					}
				} catch (NoSuchElementException e) {
					retryCount++; // increment retry count
					if (retryCount >= maxRetries) {
						System.out.println("Max retries reached. Element not found.");

						// Open a new tab and try loading the page again
						openNewTab(); // Call the function to open a new tab
						driver.get(url); // Load the page in the new tab (use the provided URL)

						// Reset retry count for the new tab
						retryCount = 0;
						tabCount++; // Increment tab count

						// Check if max tab switches have been reached
						if (tabCount >= maxTabSwitches) {
							System.out.println("Max tab switches reached. Exiting.");
							error = false; // Exit the loop after reaching max tab switches
							break; // Break from the outer loop
						}
						break; // Break from the inner while loop to start a new tab
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
		String FarmrName = (String) data[2];
		String FathrHusName = (String) data[3];
		String EpicID = (String) data[4];
		String AadharNo = (String) data[5];
		String Age = (String) data[7];
		String Gender = (String) data[8];
		String Caste = (String) data[9];
		String MobNo = (String) data[10];
		String Crop = (String) data[11];
		String District = (String) data[12];
		String Block = (String) data[13];
		String GP = (String) data[14];
		String Mouza1 = (String) data[15];
		String KhatianNo1 = (String) data[17];
		String PlotNo1 = (String) data[18];
		String AreaInsur1 = (String) data[19];
		String FarmrCat = (String) data[20];
		String NatureFarmr1 = (String) data[21];
		String IFSCode = (String) data[22];
		String AccNo = (String) data[23];
		String Vill = (String) data[24];
		String Pin = (String) data[25];
		String AccType = (String) data[26];
		String Relation = (String) data[27];
		String EpicIDImg = (String) data[29];
		String ParchaImg = (String) data[30];

		checkElementWithRetries(VARIABLES.NEW_REGISTRATION_URL, "//h4[contains(text(),'SBI GENERAL INSURANCE COMPANY LIMITED')]", 10,
				5);
		pom.searchPerson(EpicID);
		if (pom.logicToSkip(Crop, GP)) {
			throw new SkipException("Consumer already exists. Skipping test.");
		}
		pom.dataEntry(AadharNo);
		pom.farmerDetails(FarmrName, FathrHusName, Relation, Age, Gender, Caste, MobNo, FarmrCat, EpicIDImg,
				AadharNo);
		pom.farmerResidentialAddress(District, Block, GP, Vill, Pin);
		pom.cropDetailsEntry(District, Block, Crop, GP, Mouza1, KhatianNo1, PlotNo1, AreaInsur1, NatureFarmr1,
				ParchaImg);
		pom.bankDetailsEntry(FarmrName, AccNo, AccType, IFSCode);
		pom.submitForm();
	}

	@AfterMethod
	public void pageRefresh() {
		driver.navigate().refresh();
	}

	@AfterSuite
	public void afterSuite() {
		driver.quit();
	}
}
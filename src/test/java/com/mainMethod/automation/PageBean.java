package com.mainMethod.automation;

import java.io.File;
import java.io.FileNotFoundException;
import java.time.Duration;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.StaleElementReferenceException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.FindBy;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.openqa.selenium.JavascriptExecutor;

import config.VARIABLES;

public class PageBean {

	private WebDriver driver;
	private WebDriverWait wait;

	public PageBean(WebDriver driver) {
		this.driver = driver;
		this.wait = new WebDriverWait(driver, Duration.ofSeconds(10));
		PageFactory.initElements(driver, this);
	}

	/*---------------------------------- Utility Method (VERY IMPORTANT) -----------------------------*/

	// Clears Bengali digits / multiple fetched numbers → inserts correct Excel value
	public void clearAndType(WebElement element, String value) {
	    try {
	        wait.until(ExpectedConditions.elementToBeClickable(element));
	        element.click();

	        // Clear normally
	        element.clear();

	        // Extra clearing — ensures removal of Bengali or multi-values
	        element.sendKeys(Keys.CONTROL + "a");
	        element.sendKeys(Keys.DELETE);

	        // Enter correct Excel value
	        element.sendKeys(value);

	    } catch (StaleElementReferenceException e) {
	        try {
	            Thread.sleep(300);
	            element.clear();
	            element.sendKeys(Keys.CONTROL + "a");
	            element.sendKeys(Keys.DELETE);
	            element.sendKeys(value);
	        } catch (Exception ignored) {}
	    }
	}

	/*---------------------------------- Login Page ------------------------------------------------*/

	@FindBy(id = "inputUserName")
	private WebElement email;

	@FindBy(id = "inputPassword")
	private WebElement passWord;

	@FindBy(id = "insurance_user_season")
	private WebElement seasonDropdown;

	@FindBy(id = "user_session")
	private WebElement sessionDropdown;

	@FindBy(xpath = "//input[@type='checkbox']")
	private WebElement checkbox;

	@FindBy(id = "generate_otp")
	private WebElement generateOtp;

	@FindBy(xpath = "//button[@class=\"btn btn-group btn-default btn-animated btn_login\"]")
	private WebElement loginButton;

	@FindBy(id = "otp")
	private WebElement otp;

	/*---------------------------------------------- Form page ---------------------------------------*/
	@FindBy(xpath = "//*[@id= 'navbar-collapse-1']/div[2]/ul/li[3]/a")
	private WebElement listToGo;

	@FindBy(xpath = "//*[@id=\"navbar-collapse-1\"]/div[2]/ul/li[3]/ul/li[1]/a")
	private WebElement list;

	/*------------------------------------------- Search section ---------------------------------------*/
	@FindBy(id = "insure_voter_id")
	private WebElement voterCardNumber;

	@FindBy(id = "insur_search")
	private WebElement searchButton;

	@FindBy(xpath = "//tbody[@id='tbodycrop']/tr/td[2]")
	private WebElement already_existing_crop;

	@FindBy(xpath = "//tbody[@id='tbodycrop']/tr/td[5]")
	private WebElement already_existing_gram_panchayat;

	@FindBy(id = "insure_aadhar_no")
	private WebElement aadharCardNumber;

	@FindBy(id = "insure_app_type")
	private WebElement applicationSource;

	/*------------------------------------- Farmer details section ---------------------------------------*/
	@FindBy(id = "insure_name")
	private WebElement nameAsPerEpic;

	@FindBy(id = "insure_f_name")
	private WebElement fatherOrHusbandName;

	@FindBy(id = "insure_f_relation")
	private WebElement relationWithFarmerDropDown;

	@FindBy(id = "insure_age")
	private WebElement ageDropDown;

	@FindBy(id = "insure_gender")
	private WebElement genderDropDown;

	@FindBy(id = "insure_caste")
	private WebElement casteDropDown;

	@FindBy(id = "insure_mobile_no")
	private WebElement mobileNumber;

	@FindBy(id = "insure_f_category")
	private WebElement farmerCategoryDropDown;

	@FindBy(id = "insure_nominee_name")
	private WebElement nomineeName;

	@FindBy(id = "insure_id_proof")
	private WebElement voterIDUpload;

	@FindBy(id = "insure_aadhar_doc")
	private WebElement aadharIDUpload;

	/*-------------------------------- Farmers Residential address section ---------------------------------*/

	@FindBy(id = "f_district")
	private WebElement farmersResidentialAddressDistrictDropDown;

	@FindBy(id = "block_id")
	private WebElement farmersResidentialAddressblockDropDown;

	@FindBy(id = "gp_id")
	private WebElement farmersResidentialAddressgramPanchayatDropDown;

	@FindBy(id = "vill_id")
	private WebElement farmersResidentialAddressvillageDropDown;

	@FindBy(id = "pin_code")
	private WebElement pinCode;

	/*------------------------------------- Crop Details Section --------------------------------------------*/

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_district_id")
	private WebElement cropDetailsDistrictDropDown;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_block_id")
	private WebElement cropDetailsBlockDropDown;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_crop_id")
	private WebElement cropDetailsCropDropDown;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_gram_panchayat_id")
	private WebElement cropDetailsGramPanchayatInitial;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_gram_panchayat_id")
	private WebElement cropDetailsGramPanchayatFinal;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_mouza_id")
	private WebElement cropDetailsMouzaDropDown;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_khatian_no")
	private WebElement cropDetailskhaitanNumber;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_plot_no")
	private WebElement cropDetailsPlotNumber;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_inc_land_in_acer")
	private WebElement cropDetailsAreaInAcre;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_area_insured")
	private WebElement cropDetailsAreaInDecimal;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_nature_of_farmer")
	private WebElement cropDetailsNatureOfFarmerDropDown;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_form18_document")
	private WebElement cropDetailsNonOwnerCultivatorCertificateUpload;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_parcha_document")
	private WebElement cropDetailsParchaUpload;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_land_document")
	private WebElement landDocumentProofUpload;

	/*--------------------------------- Bank Details Section ---------------------------------------*/
	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_account_holder_name")
	private WebElement bankDetailsAccountHolderName;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_account_number")
	private WebElement bankDetailsAccountNumber;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_account_type")
	private WebElement accountTypeDropDown;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_account_ifsc")
	private WebElement ifsCode;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_bank_name")
	private WebElement bankName;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_branch_name")
	private WebElement branchName;

	@FindBy(id = "insurance_farmer_insurance_applications_attributes_0_bank_document")
	private WebElement bankDocumentProofUpload;

	@FindBy(id = "before_insure_submit")
	private WebElement submitButton;

	/*-------------------------------------------------------------*/
	public void gotoPage() {
		Actions action = new Actions(driver);
		action.moveToElement(listToGo).perform();
		list.click();
	}

	/*-------------------------------------------------------------*/

	public void login(String s1, String s2, int index1, int index2) throws InterruptedException {
		email.sendKeys(s1);
		passWord.sendKeys(s2);

		Select dropdown1 = new Select(seasonDropdown);
		dropdown1.selectByIndex(index2);

		wait.until(driver1 -> {
			Select dropDown2 = new Select(sessionDropdown);
			return dropDown2.getOptions().size() > 1;
		});
		Select dropdown2 = new Select(sessionDropdown);
		dropdown2.selectByIndex(index1);

		Thread.sleep(2000);
		generateOtp.click();
		Thread.sleep(30000);

		if (!checkbox.isSelected()) {
			checkbox.click();
		}

		loginButton.click();
	}

	/*-------------------------------------------- Search section ----------------------------------------*/

	public void searchPerson(String voterCard) {
		wait.until(ExpectedConditions.presenceOfElementLocated(By.id("insure_voter_id")));
		int i = 0;
		while (i < 2) {
			if (checkbox.isSelected()) {
				checkbox.click();
			}
			voterCardNumber.clear();
			voterCardNumber.sendKeys(voterCard);
			searchButton.click();
			i++;
		}
	}

	/*-------------------------------- Logic to skip ----------------------------------------*/
	public boolean logicToSkip(String crop, String gramPanchayat) {
		try {
			driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(1));
			if (already_existing_crop.isDisplayed() && already_existing_gram_panchayat.isDisplayed()) {
				if (already_existing_crop.getText().equals(crop)
						&& already_existing_gram_panchayat.getText().equals(gramPanchayat)) {
					return true;
				}
			}
		} catch (Exception ignored) {}
		return false;
	}

	/*-------------------------------------- Aadhar card entry ----------------------------------------*/
	public void dataEntry(String aadharCard) {
		if (aadharCardNumber.getText().equals("")) {
			aadharCardNumber.sendKeys(aadharCard);
		}
		wait.until(ExpectedConditions.elementToBeClickable(applicationSource));
		Select dropDown = new Select(applicationSource);
		dropDown.selectByIndex(1);
	}

	/*----------------------------------------- Farmer Details section --------------------------------------*/
	public void farmerDetails(String name, String fatherHusbandName, String relationWithFarmer, String age,
			String gender, String caste, String mobileNum, String farmerCategory, String epicIDImage,
			String aadharImg) {

		wait.until(ExpectedConditions.elementToBeClickable(nameAsPerEpic));
		nameAsPerEpic.sendKeys(name);
		fatherOrHusbandName.sendKeys(fatherHusbandName);

		Select dropdown1 = new Select(relationWithFarmerDropDown);
		dropdown1.selectByValue(relationWithFarmer);

		Select dropDown2 = new Select(ageDropDown);
		dropDown2.selectByValue(age);

		Select dropDown3 = new Select(genderDropDown);
		dropDown3.selectByValue(gender);

		Select dropDown4 = new Select(casteDropDown);
		dropDown4.selectByValue(caste);

		mobileNumber.sendKeys(mobileNum);

		Select dropDown5 = new Select(farmerCategoryDropDown);
		dropDown5.selectByValue(farmerCategory);

		try {
		    Thread.sleep(1000);
		} catch (InterruptedException e) {
		    e.printStackTrace();
		}
		nomineeName.clear();

		/* File Upload Logic (unchanged) */
		try {
			File file1 = new File(VARIABLES.VOTER_FILE_PATH + File.separator + epicIDImage + ".jpg");
			File file2 = new File(VARIABLES.VOTER_FILE_PATH + File.separator + epicIDImage + ".jpeg");
			File file3 = new File(VARIABLES.VOTER_FILE_PATH + File.separator + epicIDImage + ".png");
			File file4 = new File(VARIABLES.VOTER_FILE_PATH + File.separator + epicIDImage + ".pdf");

			if (file1.exists()) voterIDUpload.sendKeys(file1.getAbsolutePath());
			else if (file2.exists()) voterIDUpload.sendKeys(file2.getAbsolutePath());
			else if (file3.exists()) voterIDUpload.sendKeys(file3.getAbsolutePath());
			else if (file4.exists()) voterIDUpload.sendKeys(file4.getAbsolutePath());
			else throw new FileNotFoundException("Voter ID not found: " + epicIDImage);

		} catch (Exception e) { e.printStackTrace(); }

		try {
			File file1 = new File(VARIABLES.AADHAR_FILE_PATH + File.separator + aadharImg + ".jpg");
			File file2 = new File(VARIABLES.AADHAR_FILE_PATH + File.separator + aadharImg + ".jpeg");
			File file3 = new File(VARIABLES.AADHAR_FILE_PATH + File.separator + aadharImg + ".png");
			File file4 = new File(VARIABLES.AADHAR_FILE_PATH + File.separator + aadharImg + ".pdf");

			if (file1.exists()) aadharIDUpload.sendKeys(file1.getAbsolutePath());
			else if (file2.exists()) aadharIDUpload.sendKeys(file2.getAbsolutePath());
			else if (file3.exists()) aadharIDUpload.sendKeys(file3.getAbsolutePath());
			else if (file4.exists()) aadharIDUpload.sendKeys(file4.getAbsolutePath());
			else throw new FileNotFoundException("Aadhar not found: " + aadharImg);

		} catch (Exception e) { e.printStackTrace(); }
	}

	/*---------------------------- Farmers Residential address section ---------------------------------*/
	public void farmerResidentialAddress(String district, String block, String gramPanchayat, String village,
			String pin) throws InterruptedException {

		Select dropDown1 = new Select(farmersResidentialAddressDistrictDropDown);
		dropDown1.selectByVisibleText(district);

		wait.until(driver1 -> {
			Select dropDown2 = new Select(farmersResidentialAddressblockDropDown);
			return dropDown2.getOptions().size() > 1;
		});
		new Select(farmersResidentialAddressblockDropDown).selectByVisibleText(block);

		wait.until(driver1 -> {
			Select dropDown3 = new Select(farmersResidentialAddressgramPanchayatDropDown);
			return dropDown3.getOptions().size() > 1;
		});
		new Select(farmersResidentialAddressgramPanchayatDropDown).selectByVisibleText(gramPanchayat);

		wait.until(driver1 -> {
			Select dropDown4 = new Select(farmersResidentialAddressvillageDropDown);
			return dropDown4.getOptions().size() > 1;
		});
		Thread.sleep(2000);
		new Select(farmersResidentialAddressvillageDropDown).selectByIndex(1);

		pinCode.sendKeys(pin);
	}

	/*--------------------------------------- Crop details entry ----------------------------------------*/
	public void cropDetailsEntry(String district, String block, String crop, String gpInitial, String mouza,
			String khatianNumber, String plotNumber, String areaInAcre1, String natureOfFarmer, String parchaImg)
			throws InterruptedException {

		new Select(cropDetailsDistrictDropDown).selectByVisibleText(district);

		wait.until(driver1 -> {
			Select dropDown = new Select(cropDetailsBlockDropDown);
			return dropDown.getOptions().size() > 1;
		});
		new Select(cropDetailsBlockDropDown).selectByVisibleText(block);

		wait.until(driver1 -> {
			Select dropDown = new Select(cropDetailsCropDropDown);
			return dropDown.getOptions().size() > 1;
		});
		new Select(cropDetailsCropDropDown).selectByVisibleText(crop);

		if (cropDetailsGramPanchayatInitial.isEnabled()) {
			wait.until(driver1 -> {
				Select dropDown = new Select(cropDetailsGramPanchayatInitial);
				return dropDown.getOptions().size() > 1;
			});
			new Select(cropDetailsGramPanchayatInitial).selectByVisibleText(gpInitial);
		}

		if (cropDetailsGramPanchayatFinal.isEnabled()) {
			wait.until(driver1 -> {
				Select dropDown = new Select(cropDetailsGramPanchayatFinal);
				return dropDown.getOptions().size() > 1;
			});
			new Select(cropDetailsGramPanchayatFinal).selectByVisibleText(gpInitial);
		}

		wait.until(driver1 -> {
			Select dropDown = new Select(cropDetailsMouzaDropDown);
			return dropDown.getOptions().size() > 1;
		});
		new Select(cropDetailsMouzaDropDown).selectByVisibleText(mouza);

		clearAndType(cropDetailskhaitanNumber, khatianNumber);
		clearAndType(cropDetailsPlotNumber, plotNumber);
		Thread.sleep(500);
		cropDetailskhaitanNumber.clear();
		cropDetailsPlotNumber.clear();
		clearAndType(cropDetailskhaitanNumber, khatianNumber);
		clearAndType(cropDetailsPlotNumber, plotNumber);

		cropDetailsAreaInAcre.sendKeys(areaInAcre1);

		cropDetailsNatureOfFarmerDropDown.click();

		Double area = Double.parseDouble(areaInAcre1);
		if (area >= 1) {
			wait.until(ExpectedConditions.alertIsPresent());
			driver.switchTo().alert().accept();
		}

		wait.until(driver1 -> {
			Select dropDown = new Select(cropDetailsNatureOfFarmerDropDown);
			return dropDown.getOptions().size() > 1;
		});
		new Select(cropDetailsNatureOfFarmerDropDown).selectByVisibleText(natureOfFarmer);

		/* Upload parcha & land document */
		try {
			File f1 = new File(VARIABLES.PARCHA_FILE_PATH + File.separator + parchaImg + ".jpg");
			File f2 = new File(VARIABLES.PARCHA_FILE_PATH + File.separator + parchaImg + ".jpeg");
			File f3 = new File(VARIABLES.PARCHA_FILE_PATH + File.separator + parchaImg + ".png");
			File f4 = new File(VARIABLES.PARCHA_FILE_PATH + File.separator + parchaImg + ".pdf");

			if (f1.exists()) cropDetailsParchaUpload.sendKeys(f1.getAbsolutePath());
			else if (f2.exists()) cropDetailsParchaUpload.sendKeys(f2.getAbsolutePath());
			else if (f3.exists()) cropDetailsParchaUpload.sendKeys(f3.getAbsolutePath());
			else if (f4.exists()) cropDetailsParchaUpload.sendKeys(f4.getAbsolutePath());
			else throw new FileNotFoundException("Parcha not found: " + parchaImg);

		} catch (Exception e) { e.printStackTrace(); }

		try {
			File f1 = new File(VARIABLES.PARCHA_FILE_PATH + File.separator + parchaImg + ".jpg");
			File f2 = new File(VARIABLES.PARCHA_FILE_PATH + File.separator + parchaImg + ".jpeg");
			File f3 = new File(VARIABLES.PARCHA_FILE_PATH + File.separator + parchaImg + ".png");
			File f4 = new File(VARIABLES.PARCHA_FILE_PATH + File.separator + parchaImg + ".pdf");

			if (f1.exists()) landDocumentProofUpload.sendKeys(f1.getAbsolutePath());
			else if (f2.exists()) landDocumentProofUpload.sendKeys(f2.getAbsolutePath());
			else if (f3.exists()) landDocumentProofUpload.sendKeys(f3.getAbsolutePath());
			else if (f4.exists()) landDocumentProofUpload.sendKeys(f4.getAbsolutePath());
			else throw new FileNotFoundException("Land document not found: " + parchaImg);

		} catch (Exception e) { e.printStackTrace(); }
	}

	/*----------------------------------------- Bank details entry ------------------------------------------------*/
	public void bankDetailsEntry(String name, String accountNumber, String accountType, String ifscCode)
	        throws InterruptedException {

	    // 1. Account Holder Name
	    // Check if enabled AND not read-only
	    if (bankDetailsAccountHolderName.isEnabled() && bankDetailsAccountHolderName.getAttribute("readonly") == null) {
	        bankDetailsAccountHolderName.sendKeys(name);
	    }

	    // 2. Account Number (The one causing your error)
	    if (bankDetailsAccountNumber.isEnabled() && bankDetailsAccountNumber.getAttribute("readonly") == null) {
	        bankDetailsAccountNumber.clear();
	        bankDetailsAccountNumber.sendKeys(accountNumber);
	    }

	    // 3. Account Type
	    // Select dropdowns usually just need isEnabled()
	    if (accountTypeDropDown.isEnabled()) {
	        new Select(accountTypeDropDown).selectByValue(accountType);
	    }

	    // 4. IFSC Code
	    if (ifsCode.isEnabled() && ifsCode.getAttribute("readonly") == null) {
	        ifsCode.clear();
	        ifsCode.sendKeys(ifscCode);

	        // Click bank name only if interactive
	        if (bankName.isEnabled()) {
	            bankName.click();
	            Thread.sleep(500);
	        }
	    }

	    // 5. Document Upload
	    if (bankDocumentProofUpload.isEnabled() && bankDocumentProofUpload.getAttribute("readonly") == null) {
	        try {
	            File f1 = new File(VARIABLES.BANK_FILE_PATH + File.separator + accountNumber + ".jpg");
	            File f2 = new File(VARIABLES.BANK_FILE_PATH + File.separator + accountNumber + ".jpeg");
	            File f3 = new File(VARIABLES.BANK_FILE_PATH + File.separator + accountNumber + ".png");
	            File f4 = new File(VARIABLES.BANK_FILE_PATH + File.separator + accountNumber + ".pdf");

	            if (f1.exists()) bankDocumentProofUpload.sendKeys(f1.getAbsolutePath());
	            else if (f2.exists()) bankDocumentProofUpload.sendKeys(f2.getAbsolutePath());
	            else if (f3.exists()) bankDocumentProofUpload.sendKeys(f3.getAbsolutePath());
	            else if (f4.exists()) bankDocumentProofUpload.sendKeys(f4.getAbsolutePath());
	            // Optional: Log if no file found, but don't throw exception if we want to continue
	            
	        } catch (Exception e) {
	            e.printStackTrace();
	        }
	    }
	}
	/*------------------------------------------------ Submit form -----------------------------------------------*/
	public void submitForm() {
		submitButton.click();
	}
}
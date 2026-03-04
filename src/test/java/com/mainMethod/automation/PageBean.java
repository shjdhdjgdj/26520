package com.mainMethod.automation;

import java.io.File;
import java.nio.file.Paths;

import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.SelectOption;

import config.VARIABLES;

public class PageBean {

    private final Page page;

    public PageBean(Page page) {
        this.page = page;
    }

    /*─────────────────────── Utility ───────────────────────────────────────────*/

    private void clearAndType(String selector, String value) {
        Locator el = page.locator(selector);
        el.waitFor();
        el.click();
        el.selectText();
        el.fill("");
        el.type(value); // using type for Bengali support
        System.out.println("[DEBUG] clearAndType: " + selector + " = " + value);
    }

    private void waitForOptions(String selector) {
        page.waitForFunction("sel => document.querySelector(sel).options.length > 1", selector);
    }

    private void selectByText(String selector, String text) {
        waitForOptions(selector);
        page.selectOption(selector, new SelectOption().setLabel(text));
        System.out.println("[DEBUG] selectByText: " + selector + " = " + text);
    }

    private void selectByValue(String selector, String value) {
        page.selectOption(selector, new SelectOption().setValue(value));
        System.out.println("[DEBUG] selectByValue: " + selector + " = " + value);
    }

    private void selectByIndex(String selector, int index) {
        waitForOptions(selector);
        page.selectOption(selector, new SelectOption().setIndex(index));
        System.out.println("[DEBUG] selectByIndex: " + selector + " = " + index);
    }

    private String findFile(String basePath, String name) {
        for (String ext : new String[]{".jpg", ".jpeg", ".png", ".pdf"}) {
            File f = new File(basePath + File.separator + name + ext);
            if (f.exists()) {
                System.out.println("[DEBUG] Found file: " + f.getAbsolutePath());
                return f.getAbsolutePath();
            }
        }
        System.out.println("⚠ File not found: " + basePath + File.separator + name);
        return null;
    }

    /*─────────────────────── Login ─────────────────────────────────────────────*/

    public void login(String emailVal, String passwordVal, int sessionIndex, int seasonIndex) throws InterruptedException {
        page.locator("#inputUserName").fill(emailVal);
        page.locator("#inputPassword").fill(passwordVal);

        page.selectOption("#insurance_user_season", new SelectOption().setIndex(seasonIndex));
        waitForOptions("#user_session");
        page.selectOption("#user_session", new SelectOption().setIndex(sessionIndex));

        Thread.sleep(2000);
        page.locator("#generate_otp").click();
        Thread.sleep(30000); // OTP entry

        Locator cb = page.locator("//input[@type='checkbox']");
        if (!cb.isChecked()) cb.check();

        page.locator("//button[@class='btn btn-group btn-default btn-animated btn_login']").click();
        page.waitForLoadState();
        System.out.println("[DEBUG] Login completed.");
    }

    /*─────────────────────── Search ────────────────────────────────────────────*/

    public void searchPerson(String voterCard) {
        page.waitForSelector("#insure_voter_id");
        // Uncheck any checkbox that might interfere
        Locator checkbox = page.locator("//input[@type='checkbox']").first(); // target first checkbox
        if (checkbox.isVisible() && checkbox.isChecked()) {
            checkbox.uncheck();
            System.out.println("[DEBUG] Unchecked checkbox.");
        }

        page.locator("#insure_voter_id").fill(voterCard);
        page.locator("#insur_search").click();
        System.out.println("[DEBUG] Search clicked for: " + voterCard);

        // Wait for search results to load (either crop table or no data message)
        try {
            page.waitForSelector("//tbody[@id='tbodycrop']/tr", new Page.WaitForSelectorOptions().setTimeout(10000));
            System.out.println("[DEBUG] Search results loaded.");
        } catch (Exception e) {
            System.out.println("[DEBUG] No crop rows found, maybe no data.");
        }
    }

    /*─────────────────────── Skip Logic ────────────────────────────────────────*/

    public boolean logicToSkip(String crop, String gramPanchayat) {
        try {
            Locator cropCell = page.locator("//tbody[@id='tbodycrop']/tr/td[2]").first();
            Locator gpCell   = page.locator("//tbody[@id='tbodycrop']/tr/td[5]").first();
            if (cropCell.isVisible() && gpCell.isVisible()) {
                String existingCrop = cropCell.innerText().trim();
                String existingGp   = gpCell.innerText().trim();
                System.out.println("[DEBUG] Existing record: Crop=" + existingCrop + ", GP=" + existingGp);
                return existingCrop.equals(crop) && existingGp.equals(gramPanchayat);
            }
        } catch (Exception e) {
            System.out.println("[DEBUG] logicToSkip exception (normal if no records): " + e.getMessage());
        }
        return false;
    }

    /*─────────────────────── Aadhar / App Source ───────────────────────────────*/

    public void dataEntry(String aadharCard) {
        Locator aadhar = page.locator("#insure_aadhar_no");
        String currentValue = aadhar.inputValue();
        if (currentValue == null || currentValue.trim().isEmpty()) {
            aadhar.fill(aadharCard);
            System.out.println("[DEBUG] Aadhar filled: " + aadharCard);
        } else {
            System.out.println("[DEBUG] Aadhar already has value: " + currentValue);
        }

        page.waitForSelector("#insure_app_type");
        page.selectOption("#insure_app_type", new SelectOption().setIndex(1));
        System.out.println("[DEBUG] App type selected.");
    }

    /*─────────────────────── Farmer Details ────────────────────────────────────*/

    public void farmerDetails(String name, String fatherHusbandName,
                              String relationWithFarmer, String age,
                              String gender, String caste, String mobileNum,
                              String farmerCategory, String epicIDImage,
                              String aadharImg) {

        page.waitForSelector("#insure_name");
        page.locator("#insure_name").fill(name);
        page.locator("#insure_f_name").fill(fatherHusbandName);

        selectByValue("#insure_f_relation", relationWithFarmer);
        selectByValue("#insure_age",        age);
        selectByValue("#insure_gender",     gender);
        selectByValue("#insure_caste",      caste);

        page.locator("#insure_mobile_no").fill(mobileNum);
        selectByValue("#insure_f_category", farmerCategory);

        try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        page.locator("#insure_nominee_name").fill("");

        // Voter ID upload
        String voterFile = findFile(VARIABLES.VOTER_FILE_PATH, epicIDImage);
        if (voterFile != null) {
            page.locator("#insure_id_proof").setInputFiles(Paths.get(voterFile));
            System.out.println("[DEBUG] Voter ID uploaded.");
        }

        // Aadhar upload
        String aadharFile = findFile(VARIABLES.AADHAR_FILE_PATH, aadharImg);
        if (aadharFile != null) {
            page.locator("#insure_aadhar_doc").setInputFiles(Paths.get(aadharFile));
            System.out.println("[DEBUG] Aadhar doc uploaded.");
        }
    }

    /*─────────────────────── Residential Address ───────────────────────────────*/

    public void farmerResidentialAddress(String district, String block,
                                         String gramPanchayat, String village,
                                         String pin) {
        selectByText("#f_district", district);
        selectByText("#block_id",   block);
        selectByText("#gp_id",      gramPanchayat);
        selectByIndex("#vill_id",   1);
        page.locator("#pin_code").fill(pin);
        System.out.println("[DEBUG] Residential address filled.");
    }

    /*─────────────────────── Crop Details ──────────────────────────────────────*/

    public void cropDetailsEntry(String district, String block, String crop,
                                  String gpInitial, String mouza,
                                  String khatianNumber, String plotNumber,
                                  String areaInAcre1, String natureOfFarmer,
                                  String parchaImg) throws InterruptedException {

        selectByText("#insurance_farmer_insurance_applications_attributes_0_district_id", district);
        selectByText("#insurance_farmer_insurance_applications_attributes_0_block_id",    block);
        selectByText("#insurance_farmer_insurance_applications_attributes_0_crop_id",     crop);

        String gpInitialSel = "#insurance_farmer_insurance_applications_attributes_0_gram_panchayat_id";
        if (page.locator(gpInitialSel).isEnabled()) {
            selectByText(gpInitialSel, gpInitial);
        }

        String gpFinalSel = "#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_gram_panchayat_id";
        if (page.locator(gpFinalSel).isEnabled()) {
            selectByText(gpFinalSel, gpInitial);
        }

        selectByText("#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_mouza_id", mouza);

        String khatianSel = "#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_khatian_no";
        String plotSel    = "#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_plot_no";
        clearAndType(khatianSel, khatianNumber);
        clearAndType(plotSel,    plotNumber);
        Thread.sleep(500);
        clearAndType(khatianSel, khatianNumber);
        clearAndType(plotSel,    plotNumber);

        String areaSel = "#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_inc_land_in_acer";
        double area = Double.parseDouble(areaInAcre1);

        // Register persistent dialog handler before any action that might trigger it
        page.onDialog(Dialog::accept);
        System.out.println("[DEBUG] Dialog handler registered.");

        page.locator(areaSel).fill(areaInAcre1);

        String natureSel = "#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_nature_of_farmer";
        page.locator(natureSel).click();
        selectByText(natureSel, natureOfFarmer);

        String parchaFile = findFile(VARIABLES.PARCHA_FILE_PATH, parchaImg);
        if (parchaFile != null) {
            page.locator("#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_parcha_document")
                .setInputFiles(Paths.get(parchaFile));
            page.locator("#insurance_farmer_insurance_applications_attributes_0_land_document")
                .setInputFiles(Paths.get(parchaFile));
            System.out.println("[DEBUG] Parcha and land documents uploaded.");
        }
    }

    /*─────────────────────── Bank Details ──────────────────────────────────────*/

    public void bankDetailsEntry(String name, String accountNumber,
                                  String accountType, String ifscCode)
            throws InterruptedException {

        Locator holderName = page.locator("#insurance_farmer_insurance_applications_attributes_0_account_holder_name");
        if (holderName.isEnabled()) {
            holderName.fill(name);
            System.out.println("[DEBUG] Account holder name filled.");
        }

        Locator accNum = page.locator("#insurance_farmer_insurance_applications_attributes_0_account_number");
        if (accNum.isEnabled()) {
            accNum.fill(accountNumber);
            System.out.println("[DEBUG] Account number filled.");
        }

        Locator accType = page.locator("#insurance_farmer_insurance_applications_attributes_0_account_type");
        if (accType.isEnabled()) {
            page.selectOption("#insurance_farmer_insurance_applications_attributes_0_account_type",
                new SelectOption().setValue(accountType));
            System.out.println("[DEBUG] Account type selected.");
        }

        Locator ifsc = page.locator("#insurance_farmer_insurance_applications_attributes_0_account_ifsc");
        if (ifsc.isEnabled()) {
            ifsc.fill(ifscCode);
            System.out.println("[DEBUG] IFSC code filled.");
            Locator bank = page.locator("#insurance_farmer_insurance_applications_attributes_0_bank_name");
            if (bank.isEnabled()) {
                bank.click();
                Thread.sleep(500);
                System.out.println("[DEBUG] Bank name clicked.");
            }
        }

        Locator bankDoc = page.locator("#insurance_farmer_insurance_applications_attributes_0_bank_document");
        if (bankDoc.isEnabled()) {
            String bankFile = findFile(VARIABLES.BANK_FILE_PATH, accountNumber);
            if (bankFile != null) {
                bankDoc.setInputFiles(Paths.get(bankFile));
                System.out.println("[DEBUG] Bank document uploaded.");
            }
        }
    }

    /*─────────────────────── Submit ────────────────────────────────────────────*/

    public void submitForm() {
        page.locator("#before_insure_submit").click();
        page.waitForLoadState();
        System.out.println("[DEBUG] Form submitted.");
    }
}
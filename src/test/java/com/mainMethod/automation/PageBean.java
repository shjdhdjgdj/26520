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

    // Handles Bengali digits / stale values — select all then type
    private void clearAndType(String selector, String value) {
        Locator el = page.locator(selector);
        el.waitFor();
        el.click();
        el.selectText();
        el.fill("");
        el.type(value);  // char-by-char — more reliable than fill() for Bengali edge cases
    }

    // Wait for <select> to have more than 1 option (dynamic cascading dropdowns)
    private void waitForOptions(String selector) {
        page.waitForFunction(
            "sel => document.querySelector(sel).options.length > 1",
            selector
        );
    }

    private void selectByText(String selector, String text) {
        waitForOptions(selector);
        page.selectOption(selector, new SelectOption().setLabel(text));
    }

    private void selectByValue(String selector, String value) {
        page.selectOption(selector, new SelectOption().setValue(value));
    }

    private void selectByIndex(String selector, int index) {
        waitForOptions(selector);
        page.selectOption(selector, new SelectOption().setIndex(index));
    }

    // Find first existing file across .jpg/.jpeg/.png/.pdf
    private String findFile(String basePath, String name) {
        for (String ext : new String[]{".jpg", ".jpeg", ".png", ".pdf"}) {
            File f = new File(basePath + File.separator + name + ext);
            if (f.exists()) return f.getAbsolutePath();
        }
        System.out.println("⚠ File not found: " + basePath + File.separator + name);
        return null;
    }

    /*─────────────────────── Login ─────────────────────────────────────────────*/

    public void login(String emailVal, String passwordVal,
                      int sessionIndex, int seasonIndex) throws InterruptedException {

        page.locator("#inputUserName").fill(emailVal);
        page.locator("#inputPassword").fill(passwordVal);

        page.selectOption("#insurance_user_season",
            new SelectOption().setIndex(seasonIndex));

        waitForOptions("#user_session");
        page.selectOption("#user_session",
            new SelectOption().setIndex(sessionIndex));

        Thread.sleep(2000);
        page.locator("#generate_otp").click();
        Thread.sleep(30000); // user enters OTP manually in this window

        Locator cb = page.locator("//input[@type='checkbox']");
        if (!cb.isChecked()) cb.check();

        page.locator("//button[@class='btn btn-group btn-default btn-animated btn_login']").click();
        page.waitForLoadState();
    }

    /*─────────────────────── Search ────────────────────────────────────────────*/

    public void searchPerson(String voterCard) {
        page.waitForSelector("#insure_voter_id");
        for (int i = 0; i < 2; i++) {
            Locator cb = page.locator("//input[@type='checkbox']");
            if (cb.isVisible() && cb.isChecked()) cb.uncheck();
            page.locator("#insure_voter_id").fill(voterCard);
            page.locator("#insur_search").click();
        }
    }

    /*─────────────────────── Skip Logic ────────────────────────────────────────*/

    public boolean logicToSkip(String crop, String gramPanchayat) {
        try {
            Locator cropCell = page.locator("//tbody[@id='tbodycrop']/tr/td[2]");
            Locator gpCell   = page.locator("//tbody[@id='tbodycrop']/tr/td[5]");
            if (cropCell.isVisible() && gpCell.isVisible()) {
                return cropCell.innerText().trim().equals(crop)
                    && gpCell.innerText().trim().equals(gramPanchayat);
            }
        } catch (Exception ignored) {}
        return false;
    }

    /*─────────────────────── Aadhar / App Source ───────────────────────────────*/

    public void dataEntry(String aadharCard) {
        Locator aadhar = page.locator("#insure_aadhar_no");
        if (aadhar.inputValue().isEmpty()) {
            aadhar.fill(aadharCard);
        }
        page.waitForSelector("#insure_app_type");
        page.selectOption("#insure_app_type", new SelectOption().setIndex(1));
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
        if (voterFile != null)
            page.locator("#insure_id_proof").setInputFiles(Paths.get(voterFile));

        // Aadhar upload
        String aadharFile = findFile(VARIABLES.AADHAR_FILE_PATH, aadharImg);
        if (aadharFile != null)
            page.locator("#insure_aadhar_doc").setInputFiles(Paths.get(aadharFile));
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

        // Initial GP — only if enabled
        String gpInitialSel = "#insurance_farmer_insurance_applications_attributes_0_gram_panchayat_id";
        if (page.locator(gpInitialSel).isEnabled()) {
            selectByText(gpInitialSel, gpInitial);
        }

        // Final GP — only if enabled
        String gpFinalSel = "#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_gram_panchayat_id";
        if (page.locator(gpFinalSel).isEnabled()) {
            selectByText(gpFinalSel, gpInitial);
        }

        selectByText("#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_mouza_id", mouza);

        // Khatian + Plot — double-enter (Bengali-safe)
        String khatianSel = "#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_khatian_no";
        String plotSel    = "#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_plot_no";
        clearAndType(khatianSel, khatianNumber);
        clearAndType(plotSel,    plotNumber);
        Thread.sleep(500);
        clearAndType(khatianSel, khatianNumber);
        clearAndType(plotSel,    plotNumber);

        // Area input — alert fires if >= 1
        String areaSel = "#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_inc_land_in_acer";

        // Register dialog handler BEFORE the action that triggers it
        double area = Double.parseDouble(areaInAcre1);
        if (area >= 1) {
            page.onDialog(Dialog::accept);
        }

        page.locator(areaSel).fill(areaInAcre1);

        // Click nature dropdown (may trigger the alert)
        String natureSel = "#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_nature_of_farmer";
        page.locator(natureSel).click();
        selectByText(natureSel, natureOfFarmer);

        // Parcha + Land document (same file)
        String parchaFile = findFile(VARIABLES.PARCHA_FILE_PATH, parchaImg);
        if (parchaFile != null) {
            page.locator("#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_parcha_document")
                .setInputFiles(Paths.get(parchaFile));
            page.locator("#insurance_farmer_insurance_applications_attributes_0_land_document")
                .setInputFiles(Paths.get(parchaFile));
        }
    }

    /*─────────────────────── Bank Details ──────────────────────────────────────*/

    public void bankDetailsEntry(String name, String accountNumber,
                                  String accountType, String ifscCode)
            throws InterruptedException {

        Locator holderName = page.locator(
            "#insurance_farmer_insurance_applications_attributes_0_account_holder_name");
        if (holderName.isEnabled() && holderName.getAttribute("readonly") == null)
            holderName.fill(name);

        Locator accNum = page.locator(
            "#insurance_farmer_insurance_applications_attributes_0_account_number");
        if (accNum.isEnabled() && accNum.getAttribute("readonly") == null)
            accNum.fill(accountNumber);

        Locator accType = page.locator(
            "#insurance_farmer_insurance_applications_attributes_0_account_type");
        if (accType.isEnabled())
            page.selectOption(
                "#insurance_farmer_insurance_applications_attributes_0_account_type",
                new SelectOption().setValue(accountType));

        Locator ifsc = page.locator(
            "#insurance_farmer_insurance_applications_attributes_0_account_ifsc");
        if (ifsc.isEnabled() && ifsc.getAttribute("readonly") == null) {
            ifsc.fill(ifscCode);
            Locator bank = page.locator(
                "#insurance_farmer_insurance_applications_attributes_0_bank_name");
            if (bank.isEnabled()) {
                bank.click();
                Thread.sleep(500);
            }
        }

        Locator bankDoc = page.locator(
            "#insurance_farmer_insurance_applications_attributes_0_bank_document");
        if (bankDoc.isEnabled() && bankDoc.getAttribute("readonly") == null) {
            String bankFile = findFile(VARIABLES.BANK_FILE_PATH, accountNumber);
            if (bankFile != null)
                bankDoc.setInputFiles(Paths.get(bankFile));
        }
    }

    /*─────────────────────── Submit ────────────────────────────────────────────*/

    public void submitForm() {
        page.locator("#before_insure_submit").click();
        page.waitForLoadState();
    }
}

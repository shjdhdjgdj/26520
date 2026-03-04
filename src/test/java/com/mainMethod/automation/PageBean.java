package com.mainMethod.automation;

import java.io.File;
import java.nio.file.Paths;

import com.microsoft.playwright.Dialog;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;          // ✅ FIXED: was missing
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
        el.waitFor(new Locator.WaitForOptions().setTimeout(5000));   // ✅ FIXED: Locator.WaitForOptions, double ms
        if (isEditable(el)) {
            el.click();
            el.selectText();
            el.fill("");
            el.type(value);
            System.out.println("[DEBUG] clearAndType: " + selector + " = " + value);
        } else {
            System.out.println("[DEBUG] clearAndType: " + selector + " is not editable, skipping.");
        }
    }

    private boolean isEditable(Locator locator) {
        String readonly = locator.getAttribute("readonly");
        return readonly == null && locator.isEnabled();
    }

    private void waitForOptions(String selector) {
        page.waitForFunction("sel => document.querySelector(sel).options.length > 1", selector,
            new Page.WaitForFunctionOptions().setTimeout(10000));   // ✅ FIXED: double ms
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

        page.waitForTimeout(2000);   // ✅ FIXED: double ms throughout
        page.locator("#generate_otp").click();
        page.waitForTimeout(30000);  // 30s for manual OTP entry

        Locator cb = page.locator("//input[@type='checkbox']").first();
        if (!cb.isChecked()) cb.check();

        page.locator("//button[@class='btn btn-group btn-default btn-animated btn_login']").click();
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        System.out.println("[DEBUG] Login completed.");
    }

    /*─────────────────────── Search ────────────────────────────────────────────*/

    public void searchPerson(String voterCard) {
        page.waitForSelector("#insure_voter_id", new Page.WaitForSelectorOptions().setTimeout(10000));
        Locator checkbox = page.locator("//input[@type='checkbox']").first();
        if (checkbox.isVisible() && checkbox.isChecked()) {
            checkbox.uncheck();
            System.out.println("[DEBUG] Unchecked checkbox.");
        }

        page.locator("#insure_voter_id").fill(voterCard);
        page.locator("#insur_search").click();
        System.out.println("[DEBUG] Search clicked for: " + voterCard);

        int retries = 2;
        while (retries > 0) {
            try {
                page.waitForSelector("//tbody[@id='tbodycrop']/tr", new Page.WaitForSelectorOptions().setTimeout(20000));
                System.out.println("[DEBUG] Search results loaded.");
                break;
            } catch (PlaywrightException e) {
                System.out.println("[DEBUG] No crop rows after attempt: " + e.getMessage());
                if (retries == 1) {
                    System.out.println("[DEBUG] No crop rows found, maybe no data.");
                } else {
                    page.locator("#insur_search").click();
                }
                retries--;
            }
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

    /*─────────────────────── Aadhar pre-filled check ───────────────────────────*/

    public boolean isAadharPrefilled() {
        Locator aadhar = page.locator("#insure_aadhar_no");
        String value = aadhar.inputValue();
        return value != null && !value.trim().isEmpty();
    }

    /*─────────────────────── Aadhar Entry ──────────────────────────────────────*/

    public void dataEntry(String aadharCard) {
        Locator aadhar = page.locator("#insure_aadhar_no");
        if (!isEditable(aadhar)) {
            System.out.println("[DEBUG] Aadhar field is readonly, cannot fill.");
            return;
        }
        String currentValue = aadhar.inputValue();
        if (currentValue == null || currentValue.trim().isEmpty()) {
            aadhar.fill(aadharCard);
            System.out.println("[DEBUG] Aadhar filled: " + aadharCard);
        } else {
            System.out.println("[DEBUG] Aadhar already filled: " + currentValue);
        }
    }

    private void fillIfEditable(String selector, String value) {
        Locator loc = page.locator(selector);
        if (isEditable(loc)) {
            loc.fill(value);
            System.out.println("[DEBUG] Filled " + selector + " = " + value);
        } else {
            System.out.println("[DEBUG] " + selector + " is not editable, skipping.");
        }
    }

    /*─────────────────────── Farmer Details ────────────────────────────────────*/

    public void farmerDetails(String name, String fatherName, String relation, String age, String gender,
                              String caste, String mobile, String category, String epicImg, String aadharNo) {
        fillIfEditable("#insure_name", name);
        fillIfEditable("#insure_f_name", fatherName);
        selectByText("#insure_f_relation", relation);
        selectByText("#insure_age", age);
        selectByText("#insure_gender", gender);
        selectByText("#insure_caste", caste);
        fillIfEditable("#insure_mobile_no", mobile);
        selectByText("#insure_f_category", category);

        String epicFile = findFile(VARIABLES.VOTER_FILE_PATH, epicImg);
        if (epicFile != null) {
            page.locator("#insure_id_proof").setInputFiles(Paths.get(epicFile));
            System.out.println("[DEBUG] Epic uploaded.");
        }
        String aadharFile = findFile(VARIABLES.AADHAR_FILE_PATH, aadharNo);
        if (aadharFile != null) {
            page.locator("#insure_aadhar_doc").setInputFiles(Paths.get(aadharFile));
            System.out.println("[DEBUG] Aadhar uploaded.");
        }
    }

    /*─────────────────────── Residential Address ───────────────────────────────*/

    public void farmerResidentialAddress(String district, String block,
                                         String gramPanchayat, String village, String pin) {
        selectByText("#f_district", district);
        selectByText("#block_id",   block);
        selectByText("#gp_id",      gramPanchayat);
        selectByIndex("#vill_id",   1);
        fillIfEditable("#pin_code", pin);
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
        if (page.locator(gpInitialSel).isEnabled()) selectByText(gpInitialSel, gpInitial);

        String gpFinalSel = "#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_gram_panchayat_id";
        if (page.locator(gpFinalSel).isEnabled()) selectByText(gpFinalSel, gpInitial);

        selectByText("#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_mouza_id", mouza);

        String khatianSel = "#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_khatian_no";
        String plotSel    = "#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_plot_no";
        clearAndType(khatianSel, khatianNumber);
        clearAndType(plotSel,    plotNumber);
        page.waitForTimeout(500);   // ✅ FIXED: double ms
        clearAndType(khatianSel, khatianNumber);
        clearAndType(plotSel,    plotNumber);

        String areaSel = "#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_inc_land_in_acer";

        page.onDialog(Dialog::accept);
        System.out.println("[DEBUG] Dialog handler registered.");

        fillIfEditable(areaSel, areaInAcre1);
        page.waitForTimeout(500);

        String natureSel = "#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_nature_of_farmer";
        page.locator(natureSel).click();
        selectByText(natureSel, natureOfFarmer);

        String parchaFile = findFile(VARIABLES.PARCHA_FILE_PATH, parchaImg);
        if (parchaFile != null) {
            Locator parchaDoc = page.locator("#insurance_farmer_insurance_applications_attributes_0_insurance_lands_attributes_0_parcha_document");
            if (isEditable(parchaDoc)) {
                parchaDoc.setInputFiles(Paths.get(parchaFile));
                System.out.println("[DEBUG] Parcha uploaded.");
            }
            Locator landDoc = page.locator("#insurance_farmer_insurance_applications_attributes_0_land_document");
            if (isEditable(landDoc)) {
                landDoc.setInputFiles(Paths.get(parchaFile));
                System.out.println("[DEBUG] Land document uploaded.");
            }
        }
    }

    /*─────────────────────── Bank Details ──────────────────────────────────────*/

    public void bankDetailsEntry(String name, String accountNumber,
                                  String accountType, String ifscCode) throws InterruptedException {

        fillIfEditable("#insurance_farmer_insurance_applications_attributes_0_account_holder_name", name);
        fillIfEditable("#insurance_farmer_insurance_applications_attributes_0_account_number", accountNumber);

        Locator accType = page.locator("#insurance_farmer_insurance_applications_attributes_0_account_type");
        if (accType.isEnabled()) {
            page.selectOption("#insurance_farmer_insurance_applications_attributes_0_account_type",
                new SelectOption().setValue(accountType));
            System.out.println("[DEBUG] Account type selected.");
        }

        fillIfEditable("#insurance_farmer_insurance_applications_attributes_0_account_ifsc", ifscCode);

        Locator bank = page.locator("#insurance_farmer_insurance_applications_attributes_0_bank_name");
        if (bank.isEnabled()) {
            bank.click();
            page.waitForTimeout(500);
            System.out.println("[DEBUG] Bank name clicked.");
        }

        String bankFile = findFile(VARIABLES.BANK_FILE_PATH, accountNumber);
        if (bankFile != null) {
            Locator bankDoc = page.locator("#insurance_farmer_insurance_applications_attributes_0_bank_document");
            if (isEditable(bankDoc)) {
                bankDoc.setInputFiles(Paths.get(bankFile));
                System.out.println("[DEBUG] Bank document uploaded.");
            }
        }
    }

    /*─────────────────────── Submit ────────────────────────────────────────────*/

    public void submitForm() {
        page.locator("#before_insure_submit").click();
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        System.out.println("[DEBUG] Form submitted.");
    }
}
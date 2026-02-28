package Freelance.com.projectSetup;

import config.VARIABLES;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.poi.ss.usermodel.*;
import org.testng.annotations.DataProvider;

public class ExcelUtility {

    @DataProvider(name = "excelData")
    public static Object[][] getExcelData() {
        File file = new File(VARIABLES.EXCEL_FILE_PATH);
        Workbook workBook = null;
        Object[][] data = null;

        try (FileInputStream excelFile = new FileInputStream(file)) {
            workBook = WorkbookFactory.create(excelFile);
            Sheet sheet = workBook.getSheet(VARIABLES.SHEET_NAME);

            int totalRows = sheet.getLastRowNum();
            int totalColumns = sheet.getRow(0).getLastCellNum();

            ArrayList<Object[]> dataList = new ArrayList<>();
            DataFormatter formatter = new DataFormatter();

            for (int i = 1; i <= totalRows; i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue; // Skip empty rows
                
                Object[] rowData = new Object[totalColumns + 1]; // +1 for row index

                // Store the actual Excel row number (1-based) at position 0
                rowData[0] = i;

                for (int j = 0; j < totalColumns; j++) {
                    Cell cell = row.getCell(j);
                    rowData[j + 1] = formatter.formatCellValue(cell);
                }
                dataList.add(rowData);
            }

            data = new Object[dataList.size()][];
            dataList.toArray(data);
            
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException("Error reading excel file: " + e.getMessage());
        } finally {
            if (workBook != null) {
                try {
                    workBook.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return data;
    }

    /**
     * Update test status in Excel file
     * @param rowIndex The Excel row number (1-based)
     * @param status PASS/FAIL/SKIP
     */
    public static synchronized void updateTestStatus(int rowIndex, String status) {
        File file = new File(VARIABLES.EXCEL_FILE_PATH);
        Workbook workBook = null;
        FileInputStream fis = null;
        FileOutputStream fos = null;

        try {
            // Read the existing file
            fis = new FileInputStream(file);
            workBook = WorkbookFactory.create(fis);
            fis.close(); // Close input stream before writing

            Sheet sheet = workBook.getSheet(VARIABLES.SHEET_NAME);
            
            // Get the row (rowIndex is 1-based Excel row number)
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }

            // Use column 55 (column BD) for status - change this number as needed
            // A=0, B=1, C=2, ... Z=25, AA=26, AB=27, ... BD=55
            int statusColumn = 55;
            
            // Create or get the status cell
            Cell statusCell = row.getCell(statusColumn, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            
            // Set the status value
            statusCell.setCellValue(status);
            
            // Add color coding based on status
            CellStyle style = workBook.createCellStyle();
            Font font = workBook.createFont();
            
            if ("PASS".equalsIgnoreCase(status)) {
                style.setFillForegroundColor(IndexedColors.GREEN.getIndex());
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                font.setColor(IndexedColors.WHITE.getIndex());
            } else if ("FAIL".equalsIgnoreCase(status)) {
                style.setFillForegroundColor(IndexedColors.RED.getIndex());
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                font.setColor(IndexedColors.WHITE.getIndex());
            } else if ("SKIP".equalsIgnoreCase(status)) {
                style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                font.setColor(IndexedColors.BLACK.getIndex());
            }
            
            style.setFont(font);
            statusCell.setCellStyle(style);

            // Auto-size the status column for better visibility
            sheet.autoSizeColumn(statusColumn);

            // Write back to file
            fos = new FileOutputStream(file);
            workBook.write(fos);
            fos.close();

            System.out.println("✅ Excel updated: Row " + rowIndex + " = " + status);

        } catch (Exception e) {
            System.err.println("❌ Error updating Excel status for row " + rowIndex + ": " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                if (fis != null) fis.close();
                if (fos != null) fos.close();
                if (workBook != null) workBook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Overloaded method to update status with custom column
     */
    public static synchronized void updateTestStatus(int rowIndex, String status, int statusColumn) {
        File file = new File(VARIABLES.EXCEL_FILE_PATH);
        Workbook workBook = null;
        FileInputStream fis = null;
        FileOutputStream fos = null;

        try {
            fis = new FileInputStream(file);
            workBook = WorkbookFactory.create(fis);
            fis.close();

            Sheet sheet = workBook.getSheet(VARIABLES.SHEET_NAME);
            Row row = sheet.getRow(rowIndex);
            if (row == null) {
                row = sheet.createRow(rowIndex);
            }

            Cell statusCell = row.getCell(statusColumn, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            statusCell.setCellValue(status);
            
            // Simple color coding
            CellStyle style = workBook.createCellStyle();
            if ("PASS".equalsIgnoreCase(status)) {
                style.setFillForegroundColor(IndexedColors.GREEN.getIndex());
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            } else if ("FAIL".equalsIgnoreCase(status)) {
                style.setFillForegroundColor(IndexedColors.RED.getIndex());
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            } else if ("SKIP".equalsIgnoreCase(status)) {
                style.setFillForegroundColor(IndexedColors.YELLOW.getIndex());
                style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            statusCell.setCellStyle(style);

            fos = new FileOutputStream(file);
            workBook.write(fos);
            fos.close();

        } catch (Exception e) {
            System.err.println("Error updating Excel status: " + e.getMessage());
        } finally {
            try {
                if (fis != null) fis.close();
                if (fos != null) fos.close();
                if (workBook != null) workBook.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
package Freelance.com.projectSetup;
import config.VARIABLES;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.testng.annotations.DataProvider;

public class ExcelUtility {

	@DataProvider(name="excelData")
	public static Object[][] getExcelData(){
		File file = new File(VARIABLES.EXCEL_FILE_PATH);
		Workbook workBook = null;
		Object[][] data=null;
		
		try(FileInputStream excelFile = new FileInputStream(file)){
			workBook = WorkbookFactory.create(excelFile);
			Sheet sheet = workBook.getSheet(VARIABLES.SHEET_NAME);
			
			int totalRows = sheet.getLastRowNum();
			int totalColumns = sheet.getRow(0).getLastCellNum();
			
			ArrayList<Object[]> dataList = new ArrayList<>();
			DataFormatter formatter = new DataFormatter();
			
			for(int i=1;i<=totalRows;i++) {
				Row row = sheet.getRow(i);
				Object[] rowData = new Object[totalColumns];
				
				for(int j=0;j<totalColumns;j++) {
					rowData[j] = formatter.formatCellValue(row.getCell(j));
				}
				dataList.add(rowData);
			}
			
			data = new Object[dataList.size()][];
			dataList.toArray(data);
		}catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException("Error reading excel file: "+e.getMessage());
		}finally {
			if(workBook!=null) {
				try {
					workBook.close();
				}catch(IOException e) {
					e.printStackTrace();
				}
			}
		}
		
		return data;
	}
}

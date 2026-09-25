package com.retailzw.service;

import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.nio.file.*;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;

class ProductImportSampleTest {
    @Test void downloadableSampleUsesImporterHeadersAndTextBarcodes() throws Exception {
        var importer = new ProductImportService(null,null,null,null,null,null,null);
        try (var input=Files.newInputStream(Path.of("src/main/resources/static/templates/RetailZim-Product-Import-Sample.xlsx"));
             var workbook=WorkbookFactory.create(input)) {
            Sheet sheet=workbook.getSheet("Products Import");
            assertThat(sheet).isNotNull();
            Map<String,Integer> headers=ReflectionTestUtils.invokeMethod(importer,"findHeaders",sheet,new DataFormatter());
            assertThat(headers).containsKeys("name","sku","barcode","costusd","sellingusd","costzwg","sellingzwg","openingstock","taxrate","taxable");
            assertThat(sheet.getLastRowNum()).isEqualTo(2);
            assertThat(sheet.getRow(1).getCell(2).getCellType()).isEqualTo(CellType.STRING);
            assertThat(sheet.getRow(1).getCell(2).getStringCellValue()).isEqualTo("0001234567890");
            assertThat(sheet.getRow(1).getCell(5).getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(workbook.getSheet("Instructions")).isNotNull();
        }
    }
}

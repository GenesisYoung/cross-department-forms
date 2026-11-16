package com.factory.collab.service;

import com.factory.collab.config.ApiException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.HttpStatus;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ExcelWorkbookReaderTest {
    private final long originalLimit = ZipSecureFile.getMaxFileCount();

    @AfterEach
    void restorePoiLimit() {
        ZipSecureFile.setMaxFileCount(originalLimit);
    }

    @Test
    void configuredLimitAllowsWorkbookWithMoreThan1000Entries() throws Exception {
        byte[] file = workbook(1, 1100);
        ExcelWorkbookReader restricted = new ExcelWorkbookReader(1000);
        ApiException error = assertThrows(ApiException.class,
                () -> restricted.open(new ByteArrayInputStream(file)));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
        assertTrue(error.getMessage().contains("collab.excel.max-file-count"));

        double inflateRatio = ZipSecureFile.getMinInflateRatio();
        long entrySize = ZipSecureFile.getMaxEntrySize();
        ExcelWorkbookReader expanded = new ExcelWorkbookReader(10000);
        try (Workbook wb = expanded.open(new ByteArrayInputStream(file))) {
            assertEquals("value", wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue());
        }
        assertEquals(inflateRatio, ZipSecureFile.getMinInflateRatio());
        assertEquals(entrySize, ZipSecureFile.getMaxEntrySize());
    }

    @Test
    void fileCountLimitDoesNotLimitWorksheetRows() throws Exception {
        ExcelWorkbookReader reader = new ExcelWorkbookReader(1000);
        try (Workbook wb = reader.open(new ByteArrayInputStream(workbook(1500, 0)))) {
            assertEquals(1500, wb.getSheetAt(0).getPhysicalNumberOfRows());
        }
    }

    @Test
    void springAppliesConfiguredLimit() {
        new ApplicationContextRunner().withUserConfiguration(ExcelWorkbookReader.class)
                .withPropertyValues("collab.excel.max-file-count=2000")
                .run(context -> {
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(ExcelWorkbookReader.class));
                    assertEquals(2000, ZipSecureFile.getMaxFileCount());
                });
    }

    @Test
    void rejectsNonpositiveLimit() {
        assertThrows(IllegalArgumentException.class, () -> new ExcelWorkbookReader(0));
        assertThrows(IllegalArgumentException.class, () -> new ExcelWorkbookReader(-1));
    }

    private byte[] workbook(int rows, int extraEntries) throws Exception {
        ByteArrayOutputStream original = new ByteArrayOutputStream();
        try (Workbook wb = new XSSFWorkbook()) {
            var sheet = wb.createSheet("Data");
            for (int r = 0; r < rows; r++) sheet.createRow(r).createCell(0).setCellValue("value");
            wb.write(original);
        }
        ByteArrayOutputStream result = new ByteArrayOutputStream();
        try (ZipInputStream input = new ZipInputStream(new ByteArrayInputStream(original.toByteArray()));
             ZipOutputStream output = new ZipOutputStream(result)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                output.putNextEntry(new ZipEntry(entry.getName()));
                input.transferTo(output);
                output.closeEntry();
            }
            for (int i = 0; i < extraEntries; i++) {
                output.putNextEntry(new ZipEntry("extras/entry-" + i + ".xml"));
                output.write("<extra/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
        return result.toByteArray();
    }
}

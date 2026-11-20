package com.factory.collab.service;

import com.factory.collab.config.ApiException;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/** Applies the process-wide POI limit once at startup, before imports can run. */
@Component
public class ExcelWorkbookReader {
    public ExcelWorkbookReader(@Value("${collab.excel.max-file-count:10000}") long maxFileCount) {
        if (maxFileCount < 1) {
            throw new IllegalArgumentException("collab.excel.max-file-count must be at least 1");
        }
        ZipSecureFile.setMaxFileCount(maxFileCount);
    }

    public Workbook open(InputStream input) throws IOException {
        try {
            return WorkbookFactory.create(input);
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("MAX_FILE_COUNT:")) {
                throw ApiException.badRequest("error.excelEntries", ZipSecureFile.getMaxFileCount());
            }
            throw e;
        }
    }
}

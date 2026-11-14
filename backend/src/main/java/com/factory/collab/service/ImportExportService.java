package com.factory.collab.service;

import com.factory.collab.config.ApiException;
import com.factory.collab.config.AuthUser;
import com.factory.collab.domain.*;
import com.factory.collab.dto.Dtos.*;
import com.factory.collab.dto.TemplateDefinition;
import com.factory.collab.messaging.EventPublisher;
import com.factory.collab.messaging.SheetEvent;
import com.factory.collab.repo.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Imports .xlsx / .xls / .csv files into new sheets and exports sheets as .xlsx (formulas preserved). */
@Service
@RequiredArgsConstructor
public class ImportExportService {
    private final SheetService sheetService;
    private final PermissionService permissions;
    private final CellRepository cellRepository;
    private final SheetColumnRepository columnRepository;
    private final SheetViews views;
    private final EventPublisher events;
    private final ObjectMapper mapper;

    @Value("${collab.sheet.max-rows}")
    private int maxRows;
    @Value("${collab.sheet.max-cols}")
    private int maxCols;

    // ------------------------------------------------------------------ import

    @Transactional
    public List<SheetSummary> importFile(MultipartFile file, String name, Long departmentId, AuthUser user) throws IOException {
        sheetService.requireCreator(user);
        Department dept = sheetService.resolveOwnerDepartment(departmentId, user);
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("import");
        String base = (name == null || name.isBlank()) ? filename.replaceFirst("\\.[^.]+$", "") : name.trim();
        String lower = filename.toLowerCase(Locale.ROOT);

        Map<String, TemplateDefinition> parsed = new LinkedHashMap<>();
        if (lower.endsWith(".csv") || lower.endsWith(".txt")) {
            parsed.put(base, parseCsv(file.getInputStream()));
        } else if (lower.endsWith(".xlsx") || lower.endsWith(".xls") || lower.endsWith(".xlsm")) {
            try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
                for (int i = 0; i < wb.getNumberOfSheets(); i++) {
                    Sheet ws = wb.getSheetAt(i);
                    TemplateDefinition def = parseWorksheet(ws);
                    if (def.cells().isEmpty()) continue;
                    String sheetName = wb.getNumberOfSheets() == 1 ? base : base + " - " + ws.getSheetName();
                    parsed.put(SheetLabels.truncate(sheetName, 160), def);
                }
            } catch (org.apache.poi.EmptyFileException | org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException e) {
                throw ApiException.badRequest("The file is empty or not a valid Excel workbook");
            }
        } else {
            throw ApiException.badRequest("Supported formats: .xlsx, .xls, .csv");
        }
        if (parsed.isEmpty()) throw ApiException.badRequest("No data found in the file");

        List<SheetSummary> out = new ArrayList<>();
        for (var entry : parsed.entrySet()) {
            var sheet = sheetService.createFromDefinition(entry.getKey(), "Imported from " + filename, dept, null, entry.getValue(), user);
            events.publish(new SheetEvent(SheetEvent.Type.SHEET_CREATED, sheet.getId(), sheet.getName(), 0,
                    UUID.randomUUID().toString(), null, user.id(), user.displayName(), user.departmentCode(),
                    user.departmentName(), List.of(), null, null, Instant.now()));
            out.add(views.summary(sheet, permissions.levelFor(user, sheet), user));
        }
        return out;
    }

    private TemplateDefinition parseWorksheet(Sheet ws) {
        DataFormatter fallback = new DataFormatter();
        List<CellDto> cells = new ArrayList<>();
        int maxRow = -1, maxCol = -1;
        for (Row row : ws) {
            if (row.getRowNum() >= maxRows) break;
            for (org.apache.poi.ss.usermodel.Cell c : row) {
                if (c.getColumnIndex() >= maxCols) continue;
                String value = readValue(c, fallback);
                String style = readStyle(c);
                if ((value == null || value.isEmpty()) && style == null) continue;
                cells.add(new CellDto(c.getRowIndex(), c.getColumnIndex(), value, style));
                maxRow = Math.max(maxRow, c.getRowIndex());
                maxCol = Math.max(maxCol, c.getColumnIndex());
            }
        }
        int cols = Math.max(maxCol + 1, 8);
        List<ColumnDto> columns = new ArrayList<>();
        for (int c = 0; c <= maxCol; c++) {
            int px = Math.round(ws.getColumnWidth(c) / 256f * 7.5f) + 10;
            if (px != Math.round(ws.getDefaultColumnWidth() * 7.5f) + 10) {
                columns.add(new ColumnDto(c, null, Math.max(40, Math.min(px, 600)), false, List.of()));
            }
        }
        return new TemplateDefinition(Math.min(Math.max(maxRow + 21, 50), maxRows), Math.min(cols, maxCols), 1, columns, cells);
    }

    private static String readValue(org.apache.poi.ss.usermodel.Cell c, DataFormatter fallback) {
        return switch (c.getCellType()) {
            case FORMULA -> "=" + c.getCellFormula();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(c)) {
                    LocalDateTime dt = c.getLocalDateTimeCellValue();
                    yield dt.toLocalTime().toSecondOfDay() == 0
                            ? dt.toLocalDate().toString()
                            : dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
                }
                yield BigDecimal.valueOf(c.getNumericCellValue()).stripTrailingZeros().toPlainString();
            }
            case STRING -> c.getStringCellValue();
            case BOOLEAN -> c.getBooleanCellValue() ? "TRUE" : "FALSE";
            case BLANK -> null;
            default -> fallback.formatCellValue(c);
        };
    }

    private String readStyle(org.apache.poi.ss.usermodel.Cell c) {
        CellStyle cs = c.getCellStyle();
        if (cs == null) return null;
        ObjectNode s = mapper.createObjectNode();
        Font font = c.getSheet().getWorkbook().getFontAt(cs.getFontIndex());
        if (font != null && font.getBold()) s.put("b", true);
        if (font != null && font.getItalic()) s.put("i", true);
        if (cs.getAlignment() == HorizontalAlignment.CENTER) s.put("al", "center");
        if (cs.getAlignment() == HorizontalAlignment.RIGHT) s.put("al", "right");
        if (cs instanceof XSSFCellStyle x && x.getFillPattern() == FillPatternType.SOLID_FOREGROUND) {
            XSSFColor color = x.getFillForegroundXSSFColor();
            if (color != null && color.getRGB() != null) {
                byte[] rgb = color.getRGB();
                s.put("bg", String.format("#%02x%02x%02x", rgb[0] & 0xff, rgb[1] & 0xff, rgb[2] & 0xff));
            }
        }
        return s.isEmpty() ? null : s.toString();
    }

    static TemplateDefinition parseCsv(InputStream in) throws IOException {
        List<CellDto> cells = new ArrayList<>();
        int maxRow = -1, maxCol = -1;
        try (Reader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            List<List<String>> rows = CsvParser.parse(reader);
            for (int r = 0; r < rows.size(); r++) {
                List<String> row = rows.get(r);
                for (int c = 0; c < row.size(); c++) {
                    String v = row.get(c);
                    if (v == null || v.isEmpty()) continue;
                    cells.add(new CellDto(r, c, v, r == 0 ? "{\"b\":true}" : null));
                    maxRow = Math.max(maxRow, r);
                    maxCol = Math.max(maxCol, c);
                }
            }
        }
        return new TemplateDefinition(Math.max(maxRow + 21, 50), Math.max(maxCol + 1, 8), 1, List.of(), cells);
    }

    // ------------------------------------------------------------------ export

    @Transactional(readOnly = true)
    public byte[] exportXlsx(Long sheetId, AuthUser user) throws IOException {
        var sheet = sheetService.load(sheetId);
        permissions.require(user, sheet, AccessLevel.VIEW);
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet ws = wb.createSheet(safeSheetName(sheet.getName()));
            Map<String, CellStyle> styleCache = new HashMap<>();
            for (var cell : cellRepository.findBySheetId(sheetId)) {
                Row row = Optional.ofNullable(ws.getRow(cell.getRowIdx())).orElseGet(() -> ws.createRow(cell.getRowIdx()));
                org.apache.poi.ss.usermodel.Cell xc = row.createCell(cell.getColIdx());
                writeValue(xc, cell.getRawValue());
                if (cell.getStyle() != null) xc.setCellStyle(styleCache.computeIfAbsent(cell.getStyle(), s -> toPoiStyle(wb, s)));
            }
            for (var col : columnRepository.findBySheetIdOrderByColIndex(sheetId)) {
                if (col.getWidth() != null) ws.setColumnWidth(col.getColIndex(), Math.min(255 * 256, (int) (col.getWidth() / 7.5 * 256)));
            }
            if (sheet.getHeaderRows() > 0) ws.createFreezePane(0, sheet.getHeaderRows());
            wb.write(out);
            return out.toByteArray();
        }
    }

    private static void writeValue(org.apache.poi.ss.usermodel.Cell xc, String raw) {
        if (raw == null) return;
        if (raw.startsWith("=") && raw.length() > 1) {
            try {
                xc.setCellFormula(raw.substring(1));
                return;
            } catch (Exception ignored) {
                // Formula POI cannot parse: keep the text so nothing is lost.
            }
        }
        try {
            if (raw.matches("-?\\d+(\\.\\d+)?")) {
                xc.setCellValue(Double.parseDouble(raw));
                return;
            }
        } catch (NumberFormatException ignored) { }
        if ("TRUE".equalsIgnoreCase(raw) || "FALSE".equalsIgnoreCase(raw)) {
            xc.setCellValue(Boolean.parseBoolean(raw));
            return;
        }
        xc.setCellValue(raw);
    }

    private CellStyle toPoiStyle(XSSFWorkbook wb, String json) {
        XSSFCellStyle cs = wb.createCellStyle();
        try {
            var node = mapper.readTree(json);
            if (node.path("b").asBoolean() || node.path("i").asBoolean()) {
                Font f = wb.createFont();
                f.setBold(node.path("b").asBoolean());
                f.setItalic(node.path("i").asBoolean());
                cs.setFont(f);
            }
            switch (node.path("al").asText("")) {
                case "center" -> cs.setAlignment(HorizontalAlignment.CENTER);
                case "right" -> cs.setAlignment(HorizontalAlignment.RIGHT);
                case "left" -> cs.setAlignment(HorizontalAlignment.LEFT);
                default -> { }
            }
            String bg = node.path("bg").asText("");
            if (bg.matches("#[0-9a-fA-F]{6}")) {
                byte[] rgb = new byte[]{(byte) Integer.parseInt(bg.substring(1, 3), 16),
                        (byte) Integer.parseInt(bg.substring(3, 5), 16), (byte) Integer.parseInt(bg.substring(5, 7), 16)};
                cs.setFillForegroundColor(new XSSFColor(rgb, null));
                cs.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
        } catch (IOException ignored) { }
        return cs;
    }

    private static String safeSheetName(String name) {
        String s = name.replaceAll("[\\\\/?*\\[\\]:]", " ").trim();
        if (s.isEmpty()) s = "Sheet1";
        return s.length() > 31 ? s.substring(0, 31) : s;
    }
}

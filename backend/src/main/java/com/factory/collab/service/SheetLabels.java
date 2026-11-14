package com.factory.collab.service;

import com.factory.collab.domain.Cell;
import com.factory.collab.domain.Sheet;
import com.factory.collab.domain.SheetColumn;

import java.util.Map;

/** Human-readable names for a column / row, used in the audit log and notification messages. */
final class SheetLabels {
    private SheetLabels() {}

    static long key(int r, int c) {
        return ((long) r << 32) | (c & 0xffffffffL);
    }

    static String column(Sheet sheet, Map<Integer, SheetColumn> columns, Map<Long, Cell> cells, int c) {
        SheetColumn meta = columns.get(c);
        if (meta != null && meta.getTitle() != null && !meta.getTitle().isBlank()) return meta.getTitle();
        if (sheet.getHeaderRows() > 0) {
            Cell header = cells.get(key(sheet.getHeaderRows() - 1, c));
            if (header != null && header.getRawValue() != null && !header.getRawValue().isBlank()
                    && !header.getRawValue().startsWith("=")) {
                return truncate(header.getRawValue(), 120);
            }
        }
        return "Column " + CellRefs.columnName(c);
    }

    /** Identifies a data row by its first two non-formula values, e.g. "PO-2401 · M-1002". */
    static String row(Sheet sheet, Map<Long, Cell> cells, int r) {
        if (r < sheet.getHeaderRows()) return "Header";
        StringBuilder sb = new StringBuilder();
        int found = 0;
        for (int c = 0; c < Math.min(sheet.getColCount(), 6) && found < 2; c++) {
            Cell cell = cells.get(key(r, c));
            if (cell != null && cell.getRawValue() != null && !cell.getRawValue().isBlank() && !cell.getRawValue().startsWith("=")) {
                if (found > 0) sb.append(" · ");
                sb.append(cell.getRawValue().trim());
                found++;
            }
        }
        String label = "Row " + (r + 1) + (sb.isEmpty() ? "" : " (" + sb + ")");
        return truncate(label, 200);
    }

    static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }
}

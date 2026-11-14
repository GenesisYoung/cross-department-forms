package com.factory.collab.service;

/** A1-notation helpers (0-based indices in code, 1-based rows in A1). */
public final class CellRefs {
    private CellRefs() {}

    public static String columnName(int col) {
        StringBuilder sb = new StringBuilder();
        int n = col + 1;
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    public static int columnIndex(String letters) {
        int n = 0;
        for (char ch : letters.toUpperCase().toCharArray()) n = n * 26 + (ch - 'A' + 1);
        return n - 1;
    }

    public static String ref(int row, int col) {
        return columnName(col) + (row + 1);
    }
}

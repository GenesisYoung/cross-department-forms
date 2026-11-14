package com.factory.collab.service;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/** Minimal RFC 4180 parser: quoted fields, escaped quotes, embedded newlines, ',' ';' or tab separators. */
final class CsvParser {
    private CsvParser() {}

    static List<List<String>> parse(Reader reader) throws IOException {
        StringBuilder all = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = reader.read(buf)) > 0) all.append(buf, 0, n);
        String text = all.toString();
        if (text.startsWith("﻿")) text = text.substring(1);
        char sep = detectSeparator(text);

        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else quoted = false;
                } else field.append(ch);
            } else if (ch == '"' && field.isEmpty()) {
                quoted = true;
            } else if (ch == sep) {
                row.add(field.toString()); field.setLength(0);
            } else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                row.add(field.toString()); field.setLength(0);
                rows.add(row); row = new ArrayList<>();
            } else field.append(ch);
        }
        if (!field.isEmpty() || !row.isEmpty()) { row.add(field.toString()); rows.add(row); }
        return rows;
    }

    private static char detectSeparator(String text) {
        int end = text.indexOf('\n');
        String first = end < 0 ? text : text.substring(0, end);
        int commas = first.length() - first.replace(",", "").length();
        int semis = first.length() - first.replace(";", "").length();
        int tabs = first.length() - first.replace("\t", "").length();
        if (tabs > commas && tabs > semis) return '\t';
        return semis > commas ? ';' : ',';
    }
}

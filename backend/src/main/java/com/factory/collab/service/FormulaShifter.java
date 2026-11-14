package com.factory.collab.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites A1 references inside formulas when rows/columns are inserted or deleted, like Excel does:
 * references after the insertion point move, references to deleted cells become #REF!, and ranges
 * that partially overlap a deletion shrink.
 */
public final class FormulaShifter {
    private FormulaShifter() {}

    public enum Axis { ROW, COL }

    // A1 or A1:B2 (with optional $), not part of a longer identifier and not a function call like LOG10(
    private static final Pattern REF = Pattern.compile(
            "(?<![A-Za-z0-9_.!])(\\$?)([A-Za-z]{1,3})(\\$?)(\\d+)(?::(\\$?)([A-Za-z]{1,3})(\\$?)(\\d+))?(?![A-Za-z0-9_(])");

    /** Insert {@code count} rows/cols before index {@code at} (0-based). */
    public static String insert(String formula, Axis axis, int at, int count) {
        return rewrite(formula, axis, (i) -> i >= at ? i + count : i, null);
    }

    /** Delete {@code count} rows/cols starting at index {@code from} (0-based). */
    public static String delete(String formula, Axis axis, int from, int count) {
        int end = from + count; // exclusive
        return rewrite(formula, axis, (i) -> {
            if (i < from) return i;
            if (i >= end) return i - count;
            return -1;
        }, new int[]{from, end});
    }

    private interface IndexMap { int apply(int i); }

    private static String rewrite(String formula, Axis axis, IndexMap map, int[] deletedRange) {
        if (formula == null || !formula.startsWith("=")) return formula;
        StringBuilder out = new StringBuilder();
        // Split on string literals so text like "A1" inside quotes is left alone.
        int i = 0;
        boolean inString = false;
        StringBuilder chunk = new StringBuilder();
        while (i < formula.length()) {
            char ch = formula.charAt(i);
            if (ch == '"') {
                if (!inString) {
                    out.append(rewriteChunk(chunk.toString(), axis, map, deletedRange));
                    chunk.setLength(0);
                    inString = true;
                    out.append(ch);
                } else {
                    out.append(ch);
                    if (i + 1 < formula.length() && formula.charAt(i + 1) == '"') { // escaped quote
                        out.append('"');
                        i++;
                    } else {
                        inString = false;
                    }
                }
            } else if (inString) {
                out.append(ch);
            } else {
                chunk.append(ch);
            }
            i++;
        }
        out.append(rewriteChunk(chunk.toString(), axis, map, deletedRange));
        return out.toString();
    }

    private static String rewriteChunk(String s, Axis axis, IndexMap map, int[] deleted) {
        Matcher m = REF.matcher(s);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            int r1 = Integer.parseInt(m.group(4)) - 1;
            int c1 = CellRefs.columnIndex(m.group(2));
            String replacement;
            if (m.group(6) == null) {
                int idx = axis == Axis.ROW ? r1 : c1;
                int n = map.apply(idx);
                if (n < 0) {
                    replacement = "#REF!";
                } else {
                    replacement = axis == Axis.ROW
                            ? m.group(1) + m.group(2).toUpperCase() + m.group(3) + (n + 1)
                            : m.group(1) + CellRefs.columnName(n) + m.group(3) + (r1 + 1);
                }
            } else {
                int r2 = Integer.parseInt(m.group(8)) - 1;
                int c2 = CellRefs.columnIndex(m.group(6));
                int a = axis == Axis.ROW ? r1 : c1;
                int b = axis == Axis.ROW ? r2 : c2;
                int lo = Math.min(a, b), hi = Math.max(a, b);
                int nlo, nhi;
                if (deleted == null) {
                    nlo = map.apply(lo);
                    nhi = map.apply(hi);
                } else {
                    nlo = map.apply(lo) >= 0 ? map.apply(lo) : deleted[0];               // start fell in deleted block
                    nhi = map.apply(hi) >= 0 ? map.apply(hi) : deleted[0] - 1;           // end fell in deleted block
                }
                if (nhi < nlo) {
                    replacement = "#REF!";
                } else if (axis == Axis.ROW) {
                    replacement = m.group(1) + m.group(2).toUpperCase() + m.group(3) + (nlo + 1) + ":"
                            + m.group(5) + m.group(6).toUpperCase() + m.group(7) + (nhi + 1);
                } else {
                    replacement = m.group(1) + CellRefs.columnName(nlo) + m.group(3) + (r1 + 1) + ":"
                            + m.group(5) + CellRefs.columnName(nhi) + m.group(7) + (r2 + 1);
                }
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}

package com.factory.collab.service;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static com.factory.collab.service.FormulaShifter.Axis.COL;
import static com.factory.collab.service.FormulaShifter.Axis.ROW;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FormulaShifterTest {

    @Test
    void insertRowsShiftsReferencesAtOrAfterIndex() {
        // insert 2 rows before row index 2 (A3)
        assertEquals("=A2+A5", FormulaShifter.insert("=A2+A3", ROW, 2, 2));
        assertEquals("=SUM(B2:B12)", FormulaShifter.insert("=SUM(B2:B10)", ROW, 4, 2));
        assertEquals("=$A$5*2", FormulaShifter.insert("=$A$3*2", ROW, 0, 2));
    }

    @Test
    void deleteRowsProducesRefErrorAndShrinksRanges() {
        assertEquals("=#REF!+A3", FormulaShifter.delete("=A3+A4", ROW, 2, 1));
        assertEquals("=SUM(A2:A8)", FormulaShifter.delete("=SUM(A2:A10)", ROW, 3, 2));
        assertEquals("=SUM(A1:A4)", FormulaShifter.delete("=SUM(A2:A5)", ROW, 0, 1));
        assertEquals("=SUM(#REF!)", FormulaShifter.delete("=SUM(A3:A4)", ROW, 2, 2));
    }

    @Test
    void columnShifts() {
        assertEquals("=E2-G2", FormulaShifter.insert("=E2-F2", COL, 5, 1));
        assertEquals("=MAX(0,G2-F2)", FormulaShifter.delete("=MAX(0,H2-G2)", COL, 1, 1));
        assertEquals("=SUM(A1:B1)", FormulaShifter.delete("=SUM(A1:C1)", COL, 1, 1));
        assertEquals("=AA1", FormulaShifter.insert("=Z1", COL, 0, 1));
    }

    @Test
    void leavesStringsFunctionsAndPlainValuesAlone() {
        assertEquals("=IF(A3>0,\"A2 ok\",LOG10(A3))", FormulaShifter.insert("=IF(A2>0,\"A2 ok\",LOG10(A2))", ROW, 0, 1));
        assertEquals("A2", FormulaShifter.insert("A2", ROW, 0, 1));
    }

    @Test
    void csvParserHandlesQuotesAndSeparators() throws Exception {
        List<List<String>> rows = CsvParser.parse(new StringReader("﻿a,\"b,c\",\"d \"\"q\"\"\"\r\n1,2,\"multi\nline\"\n"));
        assertEquals(List.of("a", "b,c", "d \"q\""), rows.get(0));
        assertEquals(List.of("1", "2", "multi\nline"), rows.get(1));
        assertEquals(List.of(List.of("x", "y")), CsvParser.parse(new StringReader("x;y")));
    }
}

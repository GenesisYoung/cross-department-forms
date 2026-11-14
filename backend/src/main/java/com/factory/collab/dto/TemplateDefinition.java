package com.factory.collab.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.util.ArrayList;
import java.util.List;

/** Serialised as JSON into {@code sheet_template.definition}. */
public record TemplateDefinition(@Min(1) @Max(5000) int rowCount,
                                 @Min(1) @Max(200) int colCount,
                                 @Min(0) @Max(20) int headerRows,
                                 List<Dtos.ColumnDto> columns,
                                 List<Dtos.CellDto> cells) {
    public TemplateDefinition {
        columns = columns == null ? new ArrayList<>() : columns;
        cells = cells == null ? new ArrayList<>() : cells;
    }
}

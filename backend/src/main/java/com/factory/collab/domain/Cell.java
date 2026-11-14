package com.factory.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A single non-empty cell. {@code rawValue} is what the user typed (a literal or a formula starting with '='). */
@Entity
@Table(name = "sheet_cell")
@Getter
@Setter
@NoArgsConstructor
public class Cell {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sheet_id", nullable = false)
    private Long sheetId;

    @Column(name = "row_idx", nullable = false)
    private int rowIdx;

    @Column(name = "col_idx", nullable = false)
    private int colIdx;

    @Column(name = "raw_value", columnDefinition = "TEXT")
    private String rawValue;

    /** Compact JSON, e.g. {"b":true,"bg":"#fff3c4","al":"right","fmt":"0.00"}. */
    @Column(length = 500)
    private String style;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Cell(Long sheetId, int rowIdx, int colIdx) {
        this.sheetId = sheetId;
        this.rowIdx = rowIdx;
        this.colIdx = colIdx;
    }

    public boolean isBlank() {
        return (rawValue == null || rawValue.isEmpty()) && (style == null || style.isEmpty() || "{}".equals(style));
    }
}

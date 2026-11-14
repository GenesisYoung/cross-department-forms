package com.factory.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "cell_change")
@Getter
@Setter
@NoArgsConstructor
public class CellChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sheet_id", nullable = false)
    private Long sheetId;

    @Column(name = "batch_id", nullable = false, length = 36)
    private String batchId;

    @Column(name = "row_idx", nullable = false)
    private int rowIdx;

    @Column(name = "col_idx", nullable = false)
    private int colIdx;

    @Column(name = "column_label", length = 120)
    private String columnLabel;

    @Column(name = "row_label", length = 200)
    private String rowLabel;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(nullable = false)
    private boolean watched;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(name = "changed_at", nullable = false)
    private Instant changedAt = Instant.now();
}

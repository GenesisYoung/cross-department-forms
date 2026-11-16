package com.factory.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "sheet")
@Getter
@Setter
@NoArgsConstructor
public class Sheet {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "owner_department_id")
    private Department ownerDepartment;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "row_count", nullable = false)
    private int rowCount;

    @Column(name = "col_count", nullable = false)
    private int colCount;

    /** Rows at the top that only the owner can edit (column headers). */
    @Column(name = "header_rows", nullable = false)
    private int headerRows = 1;

    /** Incremented on every change; lets clients detect that they missed updates. */
    @Column(nullable = false)
    private long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    /** Id of the first tab when this sheet is one tab of a multi-sheet workbook; null for standalone sheets. */
    @Column(name = "workbook_id")
    private Long workbookId;

    @Column(name = "tab_name", length = 100)
    private String tabName;

    @Column(name = "tab_index", nullable = false)
    private int tabIndex;

    public boolean inWorkbook() {
        return workbookId != null;
    }

    /** Name used in notifications and history: "Workbook › Tab" for workbook tabs. */
    public String label() {
        return tabName == null ? name : name + " › " + tabName;
    }

    public void touch() {
        version++;
        updatedAt = Instant.now();
    }
}

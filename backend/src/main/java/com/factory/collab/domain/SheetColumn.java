package com.factory.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Arrays;
import java.util.List;

@Entity
@Table(name = "sheet_column")
@Getter
@Setter
@NoArgsConstructor
public class SheetColumn {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sheet_id", nullable = false)
    private Long sheetId;

    @Column(name = "col_index", nullable = false)
    private int colIndex;

    @Column(length = 120)
    private String title;

    private Integer width;

    /** Changes in a watched column notify every department that can see the sheet, as IMPORTANT. */
    @Column(nullable = false)
    private boolean watched;

    /** Comma-separated department codes allowed to edit this column. Empty = sheet-level rules apply. */
    @Column(name = "edit_departments")
    private String editDepartments;

    public SheetColumn(Long sheetId, int colIndex) {
        this.sheetId = sheetId;
        this.colIndex = colIndex;
    }

    public List<String> editDepartmentList() {
        if (editDepartments == null || editDepartments.isBlank()) return List.of();
        return Arrays.stream(editDepartments.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    public void setEditDepartmentList(List<String> codes) {
        this.editDepartments = (codes == null || codes.isEmpty()) ? null : String.join(",", codes);
    }
}

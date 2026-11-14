package com.factory.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "sheet_permission")
@Getter
@Setter
@NoArgsConstructor
public class SheetPermission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sheet_id", nullable = false)
    private Long sheetId;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "department_id")
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AccessLevel level;

    public SheetPermission(Long sheetId, Department department, AccessLevel level) {
        this.sheetId = sheetId;
        this.department = department;
        this.level = level;
    }
}

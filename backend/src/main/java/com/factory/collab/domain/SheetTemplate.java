package com.factory.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A reusable sheet layout. {@code definition} is a JSON {@link com.factory.collab.dto.TemplateDefinition}. */
@Entity
@Table(name = "sheet_template")
@Getter
@Setter
@NoArgsConstructor
public class SheetTemplate {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id")
    private Department department;

    @Lob
    @Column(nullable = false, columnDefinition = "LONGTEXT")
    private String definition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}

package com.factory.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "notification")
@Getter
@Setter
@NoArgsConstructor
public class Notification {
    public enum Type { CELLS_CHANGED, STRUCTURE_CHANGED, SHEET_SHARED, SHEET_CREATED }
    public enum Severity { INFO, IMPORTANT }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "recipient_id", nullable = false)
    private Long recipientId;

    @Column(name = "sheet_id")
    private Long sheetId;

    @Column(name = "batch_id", length = 36)
    private String batchId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Severity severity;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    /** Immutable language snapshots: background workers must not inherit the editor's language. */
    @Column(name = "localized_text", columnDefinition = "LONGTEXT")
    private String localizedText;

    @Column(name = "actor_name", length = 100)
    private String actorName;

    @Column(name = "actor_department", length = 100)
    private String actorDepartment;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Doubles as the acknowledgement timestamp for IMPORTANT notifications. */
    @Column(name = "read_at")
    private Instant readAt;
}

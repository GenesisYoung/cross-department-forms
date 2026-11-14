package com.factory.collab.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** A user who wants to be notified about every change to a sheet (not only watched columns). */
@Entity
@Table(name = "sheet_subscription")
@Getter
@Setter
@NoArgsConstructor
public class SheetSubscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sheet_id", nullable = false)
    private Long sheetId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public SheetSubscription(Long sheetId, Long userId) {
        this.sheetId = sheetId;
        this.userId = userId;
    }
}

package com.factory.collab.repo;

import com.factory.collab.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndReadAtIsNullOrderByCreatedAtDesc(Long recipientId, Pageable pageable);

    long countByRecipientIdAndReadAtIsNull(Long recipientId);

    long countByRecipientIdAndReadAtIsNullAndSeverity(Long recipientId, Notification.Severity severity);

    List<Notification> findByBatchIdIn(Collection<String> batchIds);

    @Modifying
    @Query("update Notification n set n.readAt = :now where n.recipientId = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("now") Instant now);
}

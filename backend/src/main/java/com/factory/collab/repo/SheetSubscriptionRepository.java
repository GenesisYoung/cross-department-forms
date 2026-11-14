package com.factory.collab.repo;

import com.factory.collab.domain.SheetSubscription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SheetSubscriptionRepository extends JpaRepository<SheetSubscription, Long> {
    List<SheetSubscription> findBySheetId(Long sheetId);

    Optional<SheetSubscription> findBySheetIdAndUserId(Long sheetId, Long userId);

    List<SheetSubscription> findByUserId(Long userId);
}

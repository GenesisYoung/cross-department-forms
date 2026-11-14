package com.factory.collab.repo;

import com.factory.collab.domain.CellChange;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CellChangeRepository extends JpaRepository<CellChange, Long> {
    List<CellChange> findBySheetIdOrderByChangedAtDescIdDesc(Long sheetId, Pageable pageable);

    List<CellChange> findBySheetIdAndRowIdxAndColIdxOrderByChangedAtDescIdDesc(Long sheetId, int row, int col);

    List<CellChange> findBySheetIdInOrderByChangedAtDescIdDesc(Collection<Long> sheetIds, Pageable pageable);

    List<CellChange> findBySheetIdInAndWatchedTrueOrderByChangedAtDescIdDesc(Collection<Long> sheetIds, Pageable pageable);

    List<CellChange> findByBatchId(String batchId);

    @Query("select count(c) from CellChange c where c.sheetId = :sheetId and c.changedAt >= :since")
    long countSince(@Param("sheetId") Long sheetId, @Param("since") java.time.Instant since);
}

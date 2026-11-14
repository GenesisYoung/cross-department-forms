package com.factory.collab.repo;

import com.factory.collab.domain.SheetColumn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SheetColumnRepository extends JpaRepository<SheetColumn, Long> {
    List<SheetColumn> findBySheetIdOrderByColIndex(Long sheetId);

    Optional<SheetColumn> findBySheetIdAndColIndex(Long sheetId, int colIndex);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE sheet_column SET col_index = col_index + :delta WHERE sheet_id = :sheetId AND col_index >= :from ORDER BY col_index DESC", nativeQuery = true)
    int shiftRight(@Param("sheetId") Long sheetId, @Param("from") int from, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE sheet_column SET col_index = col_index - :delta WHERE sheet_id = :sheetId AND col_index >= :from ORDER BY col_index ASC", nativeQuery = true)
    int shiftLeft(@Param("sheetId") Long sheetId, @Param("from") int from, @Param("delta") int delta);

    @Modifying(flushAutomatically = true)
    @Query("delete from SheetColumn c where c.sheetId = :sheetId and c.colIndex >= :from and c.colIndex < :to")
    int deleteRange(@Param("sheetId") Long sheetId, @Param("from") int from, @Param("to") int to);
}

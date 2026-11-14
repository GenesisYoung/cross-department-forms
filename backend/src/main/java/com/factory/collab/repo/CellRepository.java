package com.factory.collab.repo;

import com.factory.collab.domain.Cell;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CellRepository extends JpaRepository<Cell, Long> {
    List<Cell> findBySheetId(Long sheetId);

    List<Cell> findBySheetIdAndRowIdx(Long sheetId, int rowIdx);

    List<Cell> findBySheetIdAndRowIdxLessThan(Long sheetId, int rowIdx);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Cell c where c.sheetId = :sheetId and c.rowIdx >= :from and c.rowIdx < :to")
    int deleteRows(@Param("sheetId") Long sheetId, @Param("from") int from, @Param("to") int to);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Cell c where c.sheetId = :sheetId and c.colIdx >= :from and c.colIdx < :to")
    int deleteCols(@Param("sheetId") Long sheetId, @Param("from") int from, @Param("to") int to);

    // MySQL checks the unique key row-by-row, so shifts must run in an order that never collides.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE sheet_cell SET row_idx = row_idx + :delta WHERE sheet_id = :sheetId AND row_idx >= :from ORDER BY row_idx DESC", nativeQuery = true)
    int shiftRowsDown(@Param("sheetId") Long sheetId, @Param("from") int from, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE sheet_cell SET row_idx = row_idx - :delta WHERE sheet_id = :sheetId AND row_idx >= :from ORDER BY row_idx ASC", nativeQuery = true)
    int shiftRowsUp(@Param("sheetId") Long sheetId, @Param("from") int from, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE sheet_cell SET col_idx = col_idx + :delta WHERE sheet_id = :sheetId AND col_idx >= :from ORDER BY col_idx DESC", nativeQuery = true)
    int shiftColsRight(@Param("sheetId") Long sheetId, @Param("from") int from, @Param("delta") int delta);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = "UPDATE sheet_cell SET col_idx = col_idx - :delta WHERE sheet_id = :sheetId AND col_idx >= :from ORDER BY col_idx ASC", nativeQuery = true)
    int shiftColsLeft(@Param("sheetId") Long sheetId, @Param("from") int from, @Param("delta") int delta);

    @Query("select c from Cell c where c.sheetId = :sheetId and c.rawValue like '=%'")
    List<Cell> findFormulaCells(@Param("sheetId") Long sheetId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from Cell c where c.sheetId = :sheetId")
    int deleteAllInSheet(@Param("sheetId") Long sheetId);
}

package com.factory.collab.repo;

import com.factory.collab.domain.SheetPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SheetPermissionRepository extends JpaRepository<SheetPermission, Long> {
    List<SheetPermission> findBySheetId(Long sheetId);

    List<SheetPermission> findByDepartmentId(Long departmentId);

    Optional<SheetPermission> findBySheetIdAndDepartmentId(Long sheetId, Long departmentId);

    @Modifying
    @Query("delete from SheetPermission p where p.sheetId = :sheetId")
    void deleteBySheetId(@Param("sheetId") Long sheetId);
}

package com.factory.collab.repo;

import com.factory.collab.domain.Sheet;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface SheetRepository extends JpaRepository<Sheet, Long> {
    List<Sheet> findAllByOrderByUpdatedAtDesc();

    /** Serialises writers on one sheet so version numbers and row shifts stay consistent. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sheet s where s.id = :id")
    Optional<Sheet> findForUpdate(@Param("id") Long id);
}

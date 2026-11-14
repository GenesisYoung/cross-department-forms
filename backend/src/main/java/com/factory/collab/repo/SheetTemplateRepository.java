package com.factory.collab.repo;

import com.factory.collab.domain.SheetTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SheetTemplateRepository extends JpaRepository<SheetTemplate, Long> {
    List<SheetTemplate> findAllByOrderByNameAsc();
}

package com.factory.collab.service;

import com.factory.collab.config.ApiException;
import com.factory.collab.config.AuthUser;
import com.factory.collab.domain.*;
import com.factory.collab.dto.Dtos.*;
import com.factory.collab.dto.TemplateDefinition;
import com.factory.collab.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class TemplateService {
    private final SheetTemplateRepository templateRepository;
    private final SheetColumnRepository columnRepository;
    private final CellRepository cellRepository;
    private final UserRepository userRepository;
    private final SheetService sheetService;
    private final PermissionService permissions;
    private final TemplateCodec codec;

    @Transactional(readOnly = true)
    public List<TemplateDto> list() {
        return templateRepository.findAllByOrderByNameAsc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public TemplateDto get(Long id) {
        return toDto(templateRepository.findById(id).orElseThrow(() -> ApiException.notFound("Template")));
    }

    @Transactional
    public TemplateDto create(SaveTemplateRequest req, AuthUser user) {
        sheetService.requireCreator(user);
        SheetTemplate t = new SheetTemplate();
        apply(t, req, user);
        t.setCreatedBy(userRepository.getReferenceById(user.id()));
        return toDto(templateRepository.save(t));
    }

    @Transactional
    public TemplateDto update(Long id, SaveTemplateRequest req, AuthUser user) {
        SheetTemplate t = templateRepository.findById(id).orElseThrow(() -> ApiException.notFound("Template"));
        requireManage(t, user);
        apply(t, req, user);
        return toDto(t);
    }

    @Transactional
    public void delete(Long id, AuthUser user) {
        SheetTemplate t = templateRepository.findById(id).orElseThrow(() -> ApiException.notFound("Template"));
        requireManage(t, user);
        templateRepository.delete(t);
    }

    /** Captures a sheet's layout (and optionally its data) as a new template. */
    @Transactional
    public TemplateDto saveSheetAsTemplate(Long sheetId, SaveAsTemplateRequest req, AuthUser user) {
        sheetService.requireCreator(user);
        Sheet sheet = sheetService.load(sheetId);
        permissions.require(user, sheet, AccessLevel.VIEW);
        List<ColumnDto> cols = columnRepository.findBySheetIdOrderByColIndex(sheetId).stream().map(ColumnDto::of).toList();
        List<Cell> source = req.includeData()
                ? cellRepository.findBySheetId(sheetId)
                : cellRepository.findBySheetIdAndRowIdxLessThan(sheetId, sheet.getHeaderRows());
        TemplateDefinition def = new TemplateDefinition(sheet.getRowCount(), sheet.getColCount(), sheet.getHeaderRows(),
                cols, source.stream().map(CellDto::of).toList());
        SheetTemplate t = new SheetTemplate();
        t.setName(req.name());
        t.setDescription(req.description());
        t.setDepartment(sheet.getOwnerDepartment());
        t.setDefinition(codec.encode(def));
        t.setCreatedBy(userRepository.getReferenceById(user.id()));
        return toDto(templateRepository.save(t));
    }

    private void apply(SheetTemplate t, SaveTemplateRequest req, AuthUser user) {
        t.setName(req.name());
        t.setDescription(req.description());
        t.setDepartment(sheetService.resolveOwnerDepartment(req.departmentId(), user));
        TemplateDefinition d = req.definition();
        for (CellDto c : d.cells()) {
            if (c.r() >= d.rowCount() || c.c() >= d.colCount()) throw ApiException.badRequest("error.templateCell");
        }
        t.setDefinition(codec.encode(d));
    }

    private void requireManage(SheetTemplate t, AuthUser user) {
        boolean ok = user.role() == Role.ADMIN
                || (user.role() == Role.MANAGER && t.getDepartment() != null && t.getDepartment().getId().equals(user.departmentId()));
        if (!ok) throw ApiException.forbidden("error.templateOwner");
    }

    private TemplateDto toDto(SheetTemplate t) {
        return new TemplateDto(t.getId(), t.getName(), t.getDescription(), DepartmentDto.of(t.getDepartment()),
                t.getCreatedBy() == null ? null : t.getCreatedBy().getDisplayName(), t.getCreatedAt(), codec.decode(t.getDefinition()));
    }
}

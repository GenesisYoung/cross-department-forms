package com.factory.collab.web;

import com.factory.collab.config.CurrentUser;
import com.factory.collab.dto.Dtos.*;
import com.factory.collab.service.TemplateService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
@RequiredArgsConstructor
public class TemplateController {
    private final TemplateService templateService;

    @GetMapping
    public List<TemplateDto> list() {
        return templateService.list();
    }

    @GetMapping("/{id}")
    public TemplateDto get(@PathVariable Long id) {
        return templateService.get(id);
    }

    @PostMapping
    public TemplateDto create(@Valid @RequestBody SaveTemplateRequest req) {
        return templateService.create(req, CurrentUser.get());
    }

    @PutMapping("/{id}")
    public TemplateDto update(@PathVariable Long id, @Valid @RequestBody SaveTemplateRequest req) {
        return templateService.update(id, req, CurrentUser.get());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        templateService.delete(id, CurrentUser.get());
    }
}

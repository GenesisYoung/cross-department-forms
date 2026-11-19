package com.factory.collab.web;

import com.factory.collab.config.CurrentUser;
import com.factory.collab.dto.Dtos.*;
import com.factory.collab.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sheets")
@RequiredArgsConstructor
public class SheetController {
    private final SheetService sheetService;
    private final CellService cellService;
    private final TemplateService templateService;
    private final ImportExportService importExportService;

    @GetMapping
    public List<SheetSummary> list() {
        return sheetService.list(CurrentUser.get());
    }

    @PostMapping
    public SheetDetail create(@Valid @RequestBody CreateSheetRequest req) {
        return sheetService.create(req, CurrentUser.get());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<SheetSummary> importFile(@RequestParam("file") MultipartFile file,
                                         @RequestParam(value = "name", required = false) String name,
                                         @RequestParam(value = "departmentId", required = false) Long departmentId) throws IOException {
        return importExportService.importFile(file, name, departmentId, CurrentUser.get());
    }

    @GetMapping("/{id}")
    public SheetDetail get(@PathVariable Long id) {
        return sheetService.get(id, CurrentUser.get());
    }

    @PutMapping("/{id}")
    public SheetDetail update(@PathVariable Long id, @Valid @RequestBody UpdateSheetRequest req) {
        return sheetService.updateMeta(id, req, CurrentUser.get());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        sheetService.delete(id, CurrentUser.get());
    }

    @PatchMapping("/{id}/cells")
    public UpdateCellsResponse updateCells(@PathVariable Long id, @Valid @RequestBody UpdateCellsRequest req) {
        return cellService.update(id, req, CurrentUser.get());
    }

    @PostMapping("/{id}/structure")
    public SheetDetail structure(@PathVariable Long id, @Valid @RequestBody StructureRequest req) {
        return sheetService.changeStructure(id, req, CurrentUser.get());
    }

    @PostMapping("/{id}/tabs")
    public SheetDetail addTab(@PathVariable Long id, @Valid @RequestBody(required = false) TabRequest req) {
        return sheetService.addTab(id, req == null ? null : req.name(), CurrentUser.get());
    }

    @PutMapping("/{id}/tab")
    public SheetDetail renameTab(@PathVariable Long id, @Valid @RequestBody TabRequest req) {
        return sheetService.renameTab(id, req.name(), CurrentUser.get());
    }

    @DeleteMapping("/{id}/tab")
    public SheetDetail deleteTab(@PathVariable Long id) {
        return sheetService.deleteTab(id, CurrentUser.get());
    }

    @PutMapping("/{id}/columns/{col}")
    public ColumnDto updateColumn(@PathVariable Long id, @PathVariable int col, @Valid @RequestBody UpdateColumnRequest req) {
        return sheetService.updateColumn(id, col, req, CurrentUser.get());
    }

    @PutMapping("/{id}/columns/{col}/width")
    public void setWidth(@PathVariable Long id, @PathVariable int col, @Valid @RequestBody ColumnWidthRequest req) {
        sheetService.setColumnWidth(id, col, req.width(), CurrentUser.get());
    }

    @GetMapping("/{id}/permissions")
    public List<PermissionDto> permissions(@PathVariable Long id) {
        return sheetService.get(id, CurrentUser.get()).sharedWith();
    }

    @PutMapping("/{id}/permissions")
    public List<PermissionDto> setPermissions(@PathVariable Long id, @Valid @RequestBody SetPermissionsRequest req) {
        return sheetService.setPermissions(id, req, CurrentUser.get());
    }

    @PutMapping("/{id}/subscription")
    public Map<String, Boolean> subscribe(@PathVariable Long id) {
        return Map.of("subscribed", sheetService.setSubscribed(id, true, CurrentUser.get()));
    }

    @DeleteMapping("/{id}/subscription")
    public Map<String, Boolean> unsubscribe(@PathVariable Long id) {
        return Map.of("subscribed", sheetService.setSubscribed(id, false, CurrentUser.get()));
    }

    @GetMapping("/{id}/history")
    public List<ChangeDto> history(@PathVariable Long id, @RequestParam(defaultValue = "100") int limit) {
        return sheetService.history(id, limit, CurrentUser.get());
    }

    @GetMapping("/{id}/cells/history")
    public List<ChangeDto> cellHistory(@PathVariable Long id, @RequestParam int row, @RequestParam int col) {
        return sheetService.cellHistory(id, row, col, CurrentUser.get());
    }

    @PostMapping("/{id}/save-as-template")
    public TemplateDto saveAsTemplate(@PathVariable Long id, @Valid @RequestBody SaveAsTemplateRequest req) {
        return templateService.saveSheetAsTemplate(id, req, CurrentUser.get());
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(@PathVariable Long id) throws IOException {
        var user = CurrentUser.get();
        String name = sheetService.load(id).getName() + ".xlsx";
        byte[] bytes = importExportService.exportXlsx(id, user);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }
}

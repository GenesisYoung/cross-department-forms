package com.factory.collab.config;

import com.factory.collab.domain.*;
import com.factory.collab.dto.Dtos.CellDto;
import com.factory.collab.dto.Dtos.ColumnDto;
import com.factory.collab.dto.TemplateDefinition;
import com.factory.collab.repo.*;
import com.factory.collab.service.SheetService;
import com.factory.collab.service.TemplateCodec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Seeds an empty database with the three core departments, demo users, a template per department and one
 * live sheet each, wired together the way the factory works:
 * Warehouse inventory → PMC production plan → Purchase order tracking (with PMC/Warehouse-editable columns).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements ApplicationRunner {
    public static final String DEMO_PASSWORD = "password123";

    private final DepartmentRepository departmentRepository;
    private final UserRepository userRepository;
    private final SheetTemplateRepository templateRepository;
    private final SheetPermissionRepository permissionRepository;
    private final SheetService sheetService;
    private final TemplateCodec codec;
    private final PasswordEncoder passwordEncoder;

    @Value("${collab.seed.enabled}")
    private boolean enabled;

    private static final String HEADER = "{\"b\":true,\"bg\":\"#e8eef9\"}";

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!enabled || departmentRepository.count() > 0) return;
        log.info("Seeding demo data (departments, users, templates, sheets)");

        Department it = departmentRepository.save(new Department("IT", "IT / Administration", "System administrators"));
        Department wh = departmentRepository.save(new Department("WAREHOUSE", "Warehouse", "Raw material & finished goods inventory"));
        Department pmc = departmentRepository.save(new Department("PMC", "PMC", "Production & Material Control: production plans and material requirements"));
        Department pur = departmentRepository.save(new Department("PURCHASE", "Purchase", "Purchase orders and supplier follow-up"));

        User admin = user("admin", "System Admin", it, Role.ADMIN);
        User whMgr = user("wh.manager", "Wendy (Warehouse Mgr)", wh, Role.MANAGER);
        user("wh.clerk", "Will (Warehouse Clerk)", wh, Role.MEMBER);
        User pmcMgr = user("pmc.manager", "Paula (PMC Mgr)", pmc, Role.MANAGER);
        user("pmc.planner", "Peter (PMC Planner)", pmc, Role.MEMBER);
        User purMgr = user("pur.manager", "Bella (Purchase Mgr)", pur, Role.MANAGER);
        user("pur.buyer", "Ben (Buyer)", pur, Role.MEMBER);

        LocalDate today = LocalDate.now();

        // ---------------- Warehouse inventory
        TemplateDefinition inventoryT = definition(200, 11,
                new String[]{"Material Code", "Description", "Spec", "Unit", "On Hand", "Allocated", "Available", "Safety Stock", "Shortage", "Location", "Last Count"},
                new int[]{120, 200, 120, 60, 90, 90, 90, 100, 90, 90, 110},
                new boolean[]{false, false, false, false, true, false, false, false, false, false, false},
                null);
        SheetTemplate invTemplate = template("Inventory Ledger", "Warehouse stock with available & shortage formulas", wh, inventoryT, admin);
        Object[][] inventory = {
                {"M-1001", "Steel bracket", "SPCC 2.0mm", "pcs", 1200, 800},
                {"M-1002", "M6 hex bolt", "Grade 8.8", "pcs", 5400, 6000},
                {"M-1003", "ABS housing (black)", "UL94-HB", "pcs", 310, 250},
                {"M-1004", "Power cord 1.5m", "VDE", "pcs", 90, 400},
                {"M-1005", "Carton 40x30x20", "5-ply", "pcs", 2000, 650},
                {"M-1006", "PCB main board", "Rev C", "pcs", 480, 450},
        };
        int[] safety = {500, 3000, 200, 300, 800, 200};
        List<CellDto> invCells = new ArrayList<>(inventoryT.cells());
        for (int i = 0; i < inventory.length; i++) {
            int r = i + 1, n = r + 1;
            Object[] row = inventory[i];
            for (int c = 0; c < 6; c++) invCells.add(cell(r, c, String.valueOf(row[c])));
            invCells.add(cell(r, 6, "=E" + n + "-F" + n));
            invCells.add(cell(r, 7, String.valueOf(safety[i])));
            invCells.add(new CellDto(r, 8, "=MAX(0,H" + n + "-G" + n + ")", "{\"b\":true,\"fc\":\"#c0392b\"}"));
            invCells.add(cell(r, 9, "A-0" + (i + 1)));
            invCells.add(cell(r, 10, today.minusDays(i + 1).toString()));
        }
        Sheet inv = sheet("Raw Material Inventory", "Live stock levels maintained by the Warehouse", wh, invTemplate, inventoryT, invCells, whMgr);
        share(inv, pmc, AccessLevel.VIEW);
        share(inv, pur, AccessLevel.VIEW);

        // ---------------- PMC production plan
        TemplateDefinition planT = definition(200, 11,
                new String[]{"Work Order", "Product", "Order Qty", "Material Code", "Usage / Unit", "Material Needed", "Planned Start", "Due Date", "Line", "Status", "Remarks"},
                new int[]{110, 170, 90, 120, 100, 120, 110, 110, 70, 110, 220},
                new boolean[]{false, false, true, false, false, false, true, true, false, true, false},
                null);
        SheetTemplate planTemplate = template("Production Plan", "Weekly work orders with material requirement formulas", pmc, planT, admin);
        Object[][] plan = {
                {"WO-2411-01", "Desk lamp DL-20", 500, "M-1001", 2, 3, 12, "L1", "Released"},
                {"WO-2411-02", "Desk lamp DL-20", 500, "M-1004", 1, 3, 12, "L1", "Released"},
                {"WO-2411-03", "Fan FN-300", 300, "M-1003", 1, 6, 15, "L2", "Planned"},
                {"WO-2411-04", "Fan FN-300", 300, "M-1006", 1, 6, 15, "L2", "Material short"},
                {"WO-2411-05", "Heater HT-9", 800, "M-1002", 8, 10, 20, "L3", "Planned"},
        };
        List<CellDto> planCells = new ArrayList<>(planT.cells());
        for (int i = 0; i < plan.length; i++) {
            int r = i + 1, n = r + 1;
            Object[] row = plan[i];
            planCells.add(cell(r, 0, (String) row[0]));
            planCells.add(cell(r, 1, (String) row[1]));
            planCells.add(cell(r, 2, String.valueOf(row[2])));
            planCells.add(cell(r, 3, (String) row[3]));
            planCells.add(cell(r, 4, String.valueOf(row[4])));
            planCells.add(cell(r, 5, "=C" + n + "*E" + n));
            planCells.add(cell(r, 6, today.plusDays((int) row[5]).toString()));
            planCells.add(cell(r, 7, today.plusDays((int) row[6]).toString()));
            planCells.add(cell(r, 8, (String) row[7]));
            planCells.add(cell(r, 9, (String) row[8]));
        }
        planCells.add(new CellDto(7, 4, "Total needed", "{\"b\":true,\"al\":\"right\"}"));
        planCells.add(new CellDto(7, 5, "=SUM(F2:F6)", "{\"b\":true}"));
        Sheet planSheet = sheet("Production Plan – Week " + today.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                "Released and planned work orders", pmc, planTemplate, planT, planCells, pmcMgr);
        share(planSheet, wh, AccessLevel.VIEW);
        share(planSheet, pur, AccessLevel.VIEW);

        // ---------------- Purchase order tracking
        TemplateDefinition poT = definition(200, 13,
                new String[]{"PO No.", "Material Code", "Description", "Supplier", "Qty Ordered", "Unit Price", "Amount", "Required By (PMC)", "Material Deadline (Supplier ETA)", "Received Qty", "Outstanding", "Status", "Remarks"},
                new int[]{100, 110, 170, 150, 90, 80, 100, 130, 190, 100, 100, 110, 200},
                new boolean[]{false, false, false, false, true, false, false, true, true, true, false, true, false},
                new String[][]{null, null, null, null, null, null, null, {"PMC"}, {"PURCHASE"}, {"WAREHOUSE"}, null, null, null});
        SheetTemplate poTemplate = template("Purchase Order Tracking", "PO follow-up; PMC sets required dates, Purchase commits deadlines, Warehouse books receipts", pur, poT, admin);
        Object[][] pos = {
                {"PO-24110", "M-1004", "Power cord 1.5m", "Shenzhen Cable Co.", 400, 1.85, 3, 2, 0, "Confirmed"},
                {"PO-24111", "M-1006", "PCB main board", "Dongguan PCB Ltd.", 300, 6.40, 5, 5, 0, "In production"},
                {"PO-24112", "M-1002", "M6 hex bolt", "Ningbo Fasteners", 7000, 0.04, 8, 9, 0, "Confirmed"},
                {"PO-24105", "M-1001", "Steel bracket", "Foshan Metalworks", 1000, 0.55, -2, -3, 1000, "Received"},
        };
        List<CellDto> poCells = new ArrayList<>(poT.cells());
        for (int i = 0; i < pos.length; i++) {
            int r = i + 1, n = r + 1;
            Object[] row = pos[i];
            for (int c = 0; c < 4; c++) poCells.add(cell(r, c, (String) row[c]));
            poCells.add(cell(r, 4, String.valueOf(row[4])));
            poCells.add(cell(r, 5, String.valueOf(row[5])));
            poCells.add(cell(r, 6, "=ROUND(E" + n + "*F" + n + ",2)"));
            poCells.add(cell(r, 7, today.plusDays((int) row[6]).toString()));
            poCells.add(cell(r, 8, today.plusDays((int) row[7]).toString()));
            poCells.add(cell(r, 9, String.valueOf(row[8])));
            poCells.add(cell(r, 10, "=E" + n + "-J" + n));
            poCells.add(cell(r, 11, (String) row[9]));
        }
        poCells.add(new CellDto(1, 12, "=IF(I2>H2,\"LATE vs required date\",\"OK\")", null));
        poCells.add(new CellDto(2, 12, "=IF(I3>H3,\"LATE vs required date\",\"OK\")", null));
        poCells.add(new CellDto(3, 12, "=IF(I4>H4,\"LATE vs required date\",\"OK\")", null));
        Sheet po = sheet("Purchase Order Tracking", "Open POs and material deadlines", pur, poTemplate, poT, poCells, purMgr);
        share(po, pmc, AccessLevel.VIEW);       // PMC may only edit "Required By" (column rule)
        share(po, wh, AccessLevel.VIEW);        // Warehouse may only edit "Received Qty" (column rule)

        log.info("Demo users: admin, wh.manager, wh.clerk, pmc.manager, pmc.planner, pur.manager, pur.buyer — password '{}'", DEMO_PASSWORD);
    }

    private User user(String username, String name, Department dept, Role role) {
        User u = new User();
        u.setUsername(username);
        u.setDisplayName(name);
        u.setDepartment(dept);
        u.setRole(role);
        u.setPasswordHash(passwordEncoder.encode(DEMO_PASSWORD));
        return userRepository.save(u);
    }

    private static CellDto cell(int r, int c, String v) {
        return new CellDto(r, c, v, null);
    }

    private static TemplateDefinition definition(int rows, int cols, String[] headers, int[] widths, boolean[] watched, String[][] editDepts) {
        List<ColumnDto> columns = new ArrayList<>();
        List<CellDto> cells = new ArrayList<>();
        for (int c = 0; c < headers.length; c++) {
            List<String> depts = editDepts == null || editDepts[c] == null ? List.of() : List.of(editDepts[c]);
            columns.add(new ColumnDto(c, headers[c], widths[c], watched[c], depts));
            cells.add(new CellDto(0, c, headers[c], HEADER));
        }
        return new TemplateDefinition(rows, cols, 1, columns, cells);
    }

    private SheetTemplate template(String name, String description, Department dept, TemplateDefinition def, User by) {
        SheetTemplate t = new SheetTemplate();
        t.setName(name);
        t.setDescription(description);
        t.setDepartment(dept);
        t.setDefinition(codec.encode(def));
        t.setCreatedBy(by);
        return templateRepository.save(t);
    }

    private Sheet sheet(String name, String description, Department dept, SheetTemplate template, TemplateDefinition layout,
                        List<CellDto> cells, User owner) {
        TemplateDefinition def = new TemplateDefinition(layout.rowCount(), layout.colCount(), layout.headerRows(), layout.columns(), cells);
        return sheetService.createFromDefinition(name, description, dept, template.getId(), def, AuthUser.of(owner));
    }

    private void share(Sheet sheet, Department dept, AccessLevel level) {
        permissionRepository.save(new SheetPermission(sheet.getId(), dept, level));
    }
}

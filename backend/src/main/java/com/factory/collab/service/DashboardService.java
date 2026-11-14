package com.factory.collab.service;

import com.factory.collab.config.AuthUser;
import com.factory.collab.domain.*;
import com.factory.collab.dto.Dtos.*;
import com.factory.collab.repo.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final SheetService sheetService;
    private final SheetRepository sheetRepository;
    private final DepartmentRepository departmentRepository;
    private final CellChangeRepository changeRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;
    private final PermissionService permissions;
    private final SheetViews views;

    @Transactional(readOnly = true)
    public DashboardDto build(AuthUser user) {
        List<SheetSummary> sheets = sheetService.list(user);
        Map<Long, List<SheetSummary>> byDept = sheets.stream()
                .collect(Collectors.groupingBy(s -> s.ownerDepartment().id(), LinkedHashMap::new, Collectors.toList()));

        List<DepartmentPanel> panels = new ArrayList<>();
        // The user's own department first, then everyone else that shares something with them.
        List<Department> depts = new ArrayList<>(departmentRepository.findAll());
        depts.sort(Comparator.comparing((Department d) -> !d.getId().equals(user.departmentId())).thenComparing(Department::getName));
        for (Department d : depts) {
            List<SheetSummary> list = byDept.getOrDefault(d.getId(), List.of());
            if (list.isEmpty() && !d.getId().equals(user.departmentId())) continue;
            panels.add(new DepartmentPanel(DepartmentDto.of(d), list, list.stream().mapToLong(SheetSummary::changesLast24h).sum()));
        }

        Map<Long, String> names = sheets.stream().collect(Collectors.toMap(SheetSummary::id, SheetSummary::name));
        List<ChangeDto> recent = names.isEmpty() ? List.of()
                : views.changes(changeRepository.findBySheetIdInOrderByChangedAtDescIdDesc(names.keySet(), PageRequest.of(0, 30)), names, false);
        List<ChangeDto> watched = names.isEmpty() ? List.of()
                : views.changes(changeRepository.findBySheetIdInAndWatchedTrueOrderByChangedAtDescIdDesc(names.keySet(), PageRequest.of(0, 20)), names, true);
        List<NotificationDto> important = notificationService.list(user, true, 0, 50).stream()
                .filter(n -> n.severity() == Notification.Severity.IMPORTANT).limit(10).toList();

        UserDto me = UserDto.of(userRepository.findById(user.id()).orElseThrow());
        return new DashboardDto(me, panels, recent, watched, important, notificationService.count(user));
    }
}

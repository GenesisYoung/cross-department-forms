package com.factory.collab.web;

import com.factory.collab.config.CurrentUser;
import com.factory.collab.dto.Dtos.*;
import com.factory.collab.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Secured by SecurityConfig: /api/admin/** requires ROLE_ADMIN. */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserService userService;

    @GetMapping("/users")
    public List<UserDto> users() {
        return userService.listUsers();
    }

    @PostMapping("/users")
    public UserDto createUser(@Valid @RequestBody SaveUserRequest req) {
        return userService.createUser(req);
    }

    @PutMapping("/users/{id}")
    public UserDto updateUser(@PathVariable Long id, @Valid @RequestBody SaveUserRequest req) {
        return userService.updateUser(id, req, CurrentUser.get());
    }

    @PostMapping("/departments")
    public DepartmentDto createDepartment(@Valid @RequestBody SaveDepartmentRequest req) {
        return userService.saveDepartment(null, req);
    }

    @PutMapping("/departments/{id}")
    public DepartmentDto updateDepartment(@PathVariable Long id, @Valid @RequestBody SaveDepartmentRequest req) {
        return userService.saveDepartment(id, req);
    }
}

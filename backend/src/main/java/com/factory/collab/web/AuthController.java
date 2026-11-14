package com.factory.collab.web;

import com.factory.collab.config.CurrentUser;
import com.factory.collab.dto.Dtos.*;
import com.factory.collab.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {
    private final UserService userService;

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return userService.login(req);
    }

    @GetMapping("/auth/me")
    public UserDto me() {
        return userService.me(CurrentUser.get());
    }

    @PostMapping("/auth/password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(CurrentUser.get(), req);
    }

    /** Department list is needed by every user (sharing dialogs, column rules). */
    @GetMapping("/departments")
    public List<DepartmentDto> departments() {
        return userService.listDepartments();
    }
}

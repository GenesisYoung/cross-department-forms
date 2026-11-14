package com.factory.collab.service;

import com.factory.collab.config.ApiException;
import com.factory.collab.config.AuthUser;
import com.factory.collab.config.JwtService;
import com.factory.collab.domain.Department;
import com.factory.collab.domain.User;
import com.factory.collab.dto.Dtos.*;
import com.factory.collab.repo.DepartmentRepository;
import com.factory.collab.repo.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final DepartmentRepository departmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest req) {
        User u = userRepository.findByUsername(req.username().trim())
                .filter(User::isEnabled)
                .filter(x -> passwordEncoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password"));
        return new LoginResponse(jwtService.issue(u), UserDto.of(u));
    }

    @Transactional(readOnly = true)
    public UserDto me(AuthUser user) {
        return UserDto.of(userRepository.findById(user.id()).orElseThrow(() -> ApiException.notFound("User")));
    }

    @Transactional
    public void changePassword(AuthUser user, ChangePasswordRequest req) {
        User u = userRepository.findById(user.id()).orElseThrow();
        if (!passwordEncoder.matches(req.currentPassword(), u.getPasswordHash())) {
            throw ApiException.badRequest("Current password is incorrect");
        }
        u.setPasswordHash(passwordEncoder.encode(req.newPassword()));
    }

    // ---------------------------------------------------------------- admin

    @Transactional(readOnly = true)
    public List<UserDto> listUsers() {
        return userRepository.findAllByOrderByDepartmentIdAscUsernameAsc().stream().map(UserDto::of).toList();
    }

    @Transactional
    public UserDto createUser(SaveUserRequest req) {
        if (userRepository.existsByUsername(req.username())) throw ApiException.badRequest("Username already exists");
        if (req.password() == null) throw ApiException.badRequest("Password is required");
        User u = new User();
        u.setUsername(req.username().trim());
        apply(u, req);
        return UserDto.of(userRepository.save(u));
    }

    @Transactional
    public UserDto updateUser(Long id, SaveUserRequest req, AuthUser actor) {
        User u = userRepository.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (!u.getUsername().equals(req.username()) && userRepository.existsByUsername(req.username())) {
            throw ApiException.badRequest("Username already exists");
        }
        if (u.getId().equals(actor.id()) && Boolean.FALSE.equals(req.enabled())) {
            throw ApiException.badRequest("You cannot disable your own account");
        }
        u.setUsername(req.username().trim());
        apply(u, req);
        return UserDto.of(u);
    }

    private void apply(User u, SaveUserRequest req) {
        Department d = departmentRepository.findById(req.departmentId()).orElseThrow(() -> ApiException.notFound("Department"));
        u.setDisplayName(req.displayName().trim());
        u.setDepartment(d);
        u.setRole(req.role());
        if (req.enabled() != null) u.setEnabled(req.enabled());
        if (req.password() != null && !req.password().isBlank()) u.setPasswordHash(passwordEncoder.encode(req.password()));
    }

    @Transactional(readOnly = true)
    public List<DepartmentDto> listDepartments() {
        return departmentRepository.findAll().stream().map(DepartmentDto::of).toList();
    }

    @Transactional
    public DepartmentDto saveDepartment(Long id, SaveDepartmentRequest req) {
        Department d = id == null ? new Department() : departmentRepository.findById(id).orElseThrow(() -> ApiException.notFound("Department"));
        departmentRepository.findByCode(req.code()).filter(x -> !x.getId().equals(id))
                .ifPresent(x -> { throw ApiException.badRequest("Department code already exists"); });
        if (id != null && !d.getCode().equals(req.code())) {
            // Column edit rules reference departments by code.
            throw ApiException.badRequest("A department's code cannot be changed");
        }
        d.setCode(req.code());
        d.setName(req.name());
        d.setDescription(req.description());
        return DepartmentDto.of(departmentRepository.save(d));
    }
}

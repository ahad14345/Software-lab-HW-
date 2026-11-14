package com.example.university.controller;

import com.example.university.model.User;
import com.example.university.service.UserService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherController {

    private final UserService userService;

    @GetMapping
    public List<User> getAll(Authentication authentication) {
        return isAdmin(authentication)
                ? userService.findAll(User.Role.TEACHER)
                : userService.findOwn(User.Role.TEACHER, authentication);
    }

    @GetMapping("/{id}")
    public User getById(@PathVariable Long id, Authentication authentication) {
        User user = userService.findById(id);
        if (user.getRole() != User.Role.TEACHER
                || (!isAdmin(authentication) && !user.getUsername().equals(authentication.getName()))) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.NOT_FOUND, "Teacher not found");
        }
        return user;
    }

    @PostMapping
    public ResponseEntity<User> create(@RequestBody User user, Authentication authentication) {
        if (!isAdmin(authentication)) {
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Teachers cannot create other teacher accounts");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(userService.create(user, User.Role.TEACHER));
    }

    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User user, Authentication authentication) {
        return isAdmin(authentication)
                ? userService.update(id, user, User.Role.TEACHER)
                : userService.updateOwn(id, user, User.Role.TEACHER, authentication);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, Authentication authentication) {
        if (isAdmin(authentication)) {
            userService.delete(id, User.Role.TEACHER);
        } else {
            userService.deleteOwn(id, User.Role.TEACHER, authentication);
        }
        return ResponseEntity.noContent().build();
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN"));
    }
}

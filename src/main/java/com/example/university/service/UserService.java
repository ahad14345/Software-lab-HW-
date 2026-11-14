package com.example.university.service;

import com.example.university.model.User;
import com.example.university.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public List<User> findAll(User.Role role) {
        return role == null ? userRepository.findAll() : userRepository.findByRole(role);
    }

    public List<User> findOwn(User.Role role, Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .filter(candidate -> candidate.getRole() == role)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profile not found"));
        return List.of(user);
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }

    public User create(User user, User.Role requiredRole) {
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password is required");
        }
        validateRole(user, requiredRole);
        if (userRepository.existsByUsername(user.getUsername())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        user.setId(null);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public User update(Long id, User replacement, User.Role requiredRole) {
        User existing = findById(id);
        validateRole(replacement, requiredRole);
        if (!existing.getUsername().equals(replacement.getUsername())
                && userRepository.findByUsername(replacement.getUsername()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username already exists");
        }
        existing.setUsername(replacement.getUsername());
        existing.setRole(replacement.getRole());
        existing.setFullName(replacement.getFullName());
        existing.setEmail(replacement.getEmail());
        existing.setPhone(replacement.getPhone());
        existing.setDepartment(replacement.getDepartment());
        existing.setAddress(replacement.getAddress());
        if (replacement.getPassword() != null && !replacement.getPassword().isBlank()) {
            existing.setPassword(passwordEncoder.encode(replacement.getPassword()));
        }
        return userRepository.save(existing);
    }

    public User updateOwn(Long id, User replacement, User.Role requiredRole, Authentication authentication) {
        User existing = findById(id);
        ensureOwner(existing, requiredRole, authentication);
        replacement.setUsername(existing.getUsername());
        replacement.setRole(existing.getRole());
        return update(id, replacement, requiredRole);
    }

    public void deleteOwn(Long id, User.Role requiredRole, Authentication authentication) {
        User existing = findById(id);
        ensureOwner(existing, requiredRole, authentication);
        userRepository.delete(existing);
    }

    public void delete(Long id, User.Role requiredRole) {
        User existing = findById(id);
        validateRole(existing, requiredRole);
        userRepository.delete(existing);
    }

    private void validateRole(User user, User.Role requiredRole) {
        if (user.getUsername() == null || user.getUsername().isBlank()
                || user.getRole() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Username and role are required");
        }
        if (requiredRole != null && user.getRole() != requiredRole) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "User role must be " + requiredRole);
        }
    }

    private void ensureOwner(User user, User.Role requiredRole, Authentication authentication) {
        if (user.getRole() != requiredRole || !user.getUsername().equals(authentication.getName())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You can only manage your own profile");
        }
    }
}

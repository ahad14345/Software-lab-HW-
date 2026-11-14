package com.example.university.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.university.model.User;
import com.example.university.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User student;

    @BeforeEach
    void setUp() {
        student = new User(42L, "student42", "plain-password", "Student 42",
                "student42@example.com", null, "Computer Science", null, User.Role.STUDENT);
    }

    @Test
    void createEncodesPasswordAndSavesStudent() {
        when(userRepository.existsByUsername("student42")).thenReturn(false);
        when(passwordEncoder.encode("plain-password")).thenReturn("encoded-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User created = userService.create(student, User.Role.STUDENT);

        assertThat(created.getId()).isNull();
        assertThat(created.getPassword()).isEqualTo("encoded-password");
        verify(userRepository).save(student);
    }

    @Test
    void createRejectsDuplicateUsername() {
        when(userRepository.existsByUsername("student42")).thenReturn(true);

        assertThatThrownBy(() -> userService.create(student, User.Role.STUDENT))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Username already exists");
    }

    @Test
    void findByIdRejectsMissingUser() {
        when(userRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.findById(42L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("User not found");
    }
}

package com.example.university;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.university.model.User;
import com.example.university.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UniversityIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDatabase() {
        userRepository.deleteAll();
        User admin = new User(null, "test-admin", passwordEncoder.encode("test-admin-password"),
                "Test Admin", null, null, null, null, User.Role.ADMIN);
        userRepository.save(admin);
    }

    @Test
    void adminCanCreateAndReadBackStudent() throws Exception {
        String studentJson = """
                {
                  "username": "integration-student",
                  "password": "student-password",
                  "fullName": "Integration Student",
                  "email": "integration@example.com",
                  "role": "STUDENT"
                }
                """;

        mockMvc.perform(post("/api/students")
                        .with(httpBasic("test-admin", "test-admin-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(studentJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value("integration-student"))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("student-password"))));

        mockMvc.perform(get("/api/students")
                        .with(httpBasic("test-admin", "test-admin-password")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.username == 'integration-student')]").exists());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/students"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void studentCannotCreateAnotherStudent() throws Exception {
        User student = new User(null, "existing-student", passwordEncoder.encode("student-password"),
                "Existing Student", null, null, null, null, User.Role.STUDENT);
        userRepository.save(student);

        mockMvc.perform(post("/api/students")
                        .with(httpBasic("existing-student", "student-password"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"another-student","password":"password","role":"STUDENT"}
                                """))
                .andExpect(status().isForbidden());
    }
}

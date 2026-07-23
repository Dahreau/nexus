package com.example.userservice.controller;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import com.example.userservice.model.User;
import com.example.userservice.repository.UserRepository;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private AuthController authController;

    @Autowired
    private UserRepository userRepository;

    @SuppressWarnings("unused")
    @BeforeEach
    void cleanup() {
        userRepository.deleteAll();
    }

    @Test
    void register_NewClient_ShouldCreateUserAndReturnToken() {
        // Given
        Map<String, String> request = Map.of(
                "name", "Test User",
                "email", "client@test.com",
                "password", "password123",
                "role", "CLIENT"
        );

        // When
        ResponseEntity<?> response = authController.register(request);

        // Then
        assertEquals(200, response.getStatusCode().value());
        Object responseBody = response.getBody();
        assertNotNull(responseBody);
        assertTrue(responseBody instanceof Map);

        Map<?, ?> body = (Map<?, ?>) responseBody;
        assertNotNull(body.get("token"));
        assertNotNull(body.get("userId"));

        // Verify user was saved in database
        User savedUser = userRepository.findByEmail("client@test.com").orElse(null);
        assertNotNull(savedUser);
        assertEquals("Test User", savedUser.getName());
        assertEquals("CLIENT", savedUser.getRole().name());
    }

    @Test
    void register_NewSeller_ShouldCreateSellerUser() {
        // Given
        Map<String, String> request = Map.of(
                "name", "Test Seller",
                "email", "seller@test.com",
                "password", "password123",
                "role", "SELLER"
        );

        // When
        ResponseEntity<?> response = authController.register(request);

        // Then
        assertEquals(200, response.getStatusCode().value());

        User savedUser = userRepository.findByEmail("seller@test.com").orElse(null);
        assertNotNull(savedUser);
        assertEquals("Test Seller", savedUser.getName());
        assertEquals("SELLER", savedUser.getRole().name());
    }

    @Test
    void register_DuplicateEmail_ShouldReturnError() {
        // Given - create first user
        Map<String, String> firstRequest = Map.of(
                "name", "First User",
                "email", "duplicate@test.com",
                "password", "password123",
                "role", "CLIENT"
        );
        authController.register(firstRequest);

        // When - try to create user with same email
        Map<String, String> secondRequest = Map.of(
                "name", "Second User",
                "email", "duplicate@test.com",
                "password", "password123",
                "role", "CLIENT"
        );
        ResponseEntity<?> response = authController.register(secondRequest);

        // Then
        assertEquals(400, response.getStatusCode().value());

        Object responseBody = response.getBody();
        assertNotNull(responseBody);
        assertTrue(responseBody.toString().contains("Email already in use"));
    }

    @Test
    void login_ValidCredentials_ShouldReturnToken() {
        // Given - register a user first
        Map<String, String> registerRequest = Map.of(
                "name", "Login Test User",
                "email", "login@test.com",
                "password", "loginpassword",
                "role", "CLIENT"
        );
        authController.register(registerRequest);

        // When - login with valid credentials
        Map<String, String> loginRequest = Map.of(
                "email", "login@test.com",
                "password", "loginpassword"
        );
        ResponseEntity<?> response = authController.login(loginRequest);

        // Then
        assertEquals(200, response.getStatusCode().value());

        Object responseBody = response.getBody();
        assertNotNull(responseBody);
        assertTrue(responseBody instanceof Map);

        Map<?, ?> body = (Map<?, ?>) responseBody;
        assertNotNull(body.get("token"));
        assertNotNull(body.get("userId"));
    }

    @Test
    void login_InvalidPassword_ShouldReturnError() {
        // Given - register a user
        Map<String, String> registerRequest = Map.of(
                "name", "Login Test User",
                "email", "login2@test.com",
                "password", "correctpassword",
                "role", "CLIENT"
        );
        authController.register(registerRequest);

        // When - login with wrong password
        Map<String, String> loginRequest = Map.of(
                "email", "login2@test.com",
                "password", "wrongpassword"
        );
        ResponseEntity<?> response = authController.login(loginRequest);

        // Then
        assertEquals(401, response.getStatusCode().value());

        Object responseBody = response.getBody();
        assertNotNull(responseBody);
        assertTrue(responseBody.toString().contains("Invalid credentials"));
    }

    @Test
    void login_NonExistentEmail_ShouldReturnError() {
        // Given - no user with this email
        Map<String, String> loginRequest = Map.of(
                "email", "nonexistent@test.com",
                "password", "anypassword"
        );

        // When
        ResponseEntity<?> response = authController.login(loginRequest);

        // Then
        assertEquals(401, response.getStatusCode().value());

        Object responseBody = response.getBody();
        assertNotNull(responseBody);
        assertTrue(responseBody.toString().contains("Invalid credentials"));
    }
}

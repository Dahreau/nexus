package com.example.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class UserServiceApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        // Test that Spring context loads successfully
        assertNotNull(context, "Spring application context should be loaded");
        assertTrue(context.containsBean("userServiceApplication"),
                "UserServiceApplication bean should be present");
    }

    @Test
    void mainMethod_ShouldStartApplication() {
        // Test that the application can start without errors
        assertDoesNotThrow(() -> {
            UserServiceApplication.main(new String[]{});
        }, "Application should start without throwing exceptions");
    }

    @Test
    void essentialBeans_ShouldBeAvailable() {
        // Test that essential beans are properly configured
        assertNotNull(context.getBean(org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.class),
                "BCryptPasswordEncoder bean should be available");

        // Check if MongoDB related beans are available
        assertNotNull(context.getBean(org.springframework.data.mongodb.core.MongoTemplate.class),
                "MongoTemplate bean should be available");
    }

    @Test
    void applicationProperties_ShouldBeLoaded() {
        // Test that application properties are properly loaded
        String jwtSecret = context.getEnvironment().getProperty("jwt.secret");
        assertNotNull(jwtSecret, "JWT secret should be configured");
    }
}

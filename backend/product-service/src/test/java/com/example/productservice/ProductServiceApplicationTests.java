package com.example.productservice;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class ProductServiceApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    void contextLoads() {
        // Test that Spring context loads successfully
        assertNotNull(context, "Spring application context should be loaded");
        assertTrue(context.containsBean("productServiceApplication"),
                "ProductServiceApplication bean should be present");
    }

    @Test
    void mainMethod_ShouldStartApplication() {
        // Test that the application can start without errors
        assertDoesNotThrow(() -> {
            ProductServiceApplication.main(new String[]{});
        }, "Application should start without throwing exceptions");
    }

    @Test
    void securityBeans_ShouldBeConfigured() {
        // Test that security configuration is properly set up
        assertNotNull(context.getBean(org.springframework.security.config.annotation.web.configuration.WebSecurityConfiguration.class),
                "Web security configuration should be available");
    }

    @Test
    void mongoDBConfiguration_ShouldBeAvailable() {
        // Test MongoDB configuration
        assertNotNull(context.getBean(org.springframework.data.mongodb.core.MongoTemplate.class),
                "MongoTemplate bean should be available for product data");
    }

    @Test
    void internalToken_ShouldBeConfigured() {
        // Test that internal token for microservice communication is available
        String internalToken = context.getEnvironment().getProperty("INTERNAL_TOKEN");
        assertNotNull(internalToken, "Internal token for service communication should be configured");
        assertTrue(internalToken != null && !internalToken.trim().isEmpty(), "Internal token should not be empty");
    }
}

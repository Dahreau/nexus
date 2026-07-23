package com.example.orderservice.config;

import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleRuntimeException() {
        RuntimeException ex = new RuntimeException("Test error");
        ResponseEntity<Map<String, String>> response = handler.handleRuntimeException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());

        Map<String, String> body = Objects.requireNonNull(response.getBody());
        assertEquals("Test error", body.get("error"));
    }
}

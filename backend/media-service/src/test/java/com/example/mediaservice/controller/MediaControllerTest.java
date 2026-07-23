package com.example.mediaservice.controller;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import com.example.mediaservice.model.Media;
import com.example.mediaservice.repository.MediaRepository;

@ActiveProfiles("test")
@SpringBootTest
class MediaControllerTest {

    @Autowired
    private MediaController mediaController;

    @Autowired
    private MediaRepository mediaRepository;

    @BeforeEach
    void cleanup() {
        mediaRepository.deleteAll();
    }

    @Test
    void byProduct_ShouldReturnMediaForProduct() {
        // Given
        Media media1 = new Media("http://example.com/image1.jpg", "product-123");
        Media media2 = new Media("http://example.com/image2.jpg", "product-123");
        Media media3 = new Media("http://example.com/image3.jpg", "product-456");

        mediaRepository.save(media1);
        mediaRepository.save(media2);
        mediaRepository.save(media3);

        // When
        List<Media> result = mediaController.byProduct("product-123");

        // Then
        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(m -> m.getProductId().equals("product-123")));
    }

    @Test
    void byProduct_NoMedia_ShouldReturnEmptyList() {
        // When
        List<Media> result = mediaController.byProduct("nonexistent-product");

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    void upload_EmptyFile_ShouldReturnError() {
        // Given
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "test.jpg", "image/jpeg", new byte[0]
        );

        // When
        ResponseEntity<?> response = mediaController.upload(emptyFile, "product-123");

        // Then
        assertEquals(403, response.getStatusCode().value());
        assertNotNull(response.getBody());
        String responseBody = response.getBody().toString();
        assertTrue(responseBody.contains("sellers") || responseBody.contains("seller"),
                "Response should contain seller restriction. Actual: " + responseBody);
    }

    @Test
    void upload_InvalidFileType_ShouldReturnError() {
        // Given
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "test.txt", "text/plain", "invalid content".getBytes()
        );

        // When
        ResponseEntity<?> response = mediaController.upload(invalidFile, "product-123");

        // Then
        assertEquals(403, response.getStatusCode().value());
        assertNotNull(response.getBody());
        String responseBody = response.getBody().toString();
        assertTrue(responseBody.contains("sellers") || responseBody.contains("seller"),
                "Response should contain seller restriction. Actual: " + responseBody);
    }
}

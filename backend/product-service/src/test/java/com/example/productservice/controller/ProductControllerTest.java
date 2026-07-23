package com.example.productservice.controller;

import java.util.List;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import com.example.productservice.dto.ProductDTO;
import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class ProductControllerTest {

    @Autowired
    private ProductController productController;

    @Autowired
    private ProductRepository productRepository;

    @SuppressWarnings("unused")
    @BeforeEach
    void cleanup() {
        productRepository.deleteAll();
    }

    @Test
    void listAll_ShouldReturnAllProducts() {
        // Given - create some test products
        Product p1 = new Product();
        p1.setName("Product 1");
        p1.setPrice(19.99);
        productRepository.save(p1);

        Product p2 = new Product();
        p2.setName("Product 2");
        p2.setPrice(29.99);
        productRepository.save(p2);

        // When
        List<Product> products = productController.listAll();

        // Then
        assertEquals(2, products.size());
        assertTrue(products.stream().anyMatch(p -> p.getName().equals("Product 1")));
        assertTrue(products.stream().anyMatch(p -> p.getName().equals("Product 2")));
    }

    @Test
    void getOne_ExistingProduct_ShouldReturnProduct() {
        // Given
        Product product = new Product();
        product.setName("Test Product");
        product.setPrice(15.99);
        Product saved = productRepository.save(product);

        // When
        ResponseEntity<?> response = productController.getOne(saved.getId());

        // Then
        assertEquals(200, response.getStatusCode().value());
        Product found = (Product) response.getBody();
        assertNotNull(found);
        assertEquals("Test Product", found.getName());
        assertEquals(15.99, found.getPrice());
    }

    @Test
    void getOne_NonExistentProduct_ShouldReturnNotFound() {
        // When
        ResponseEntity<?> response = productController.getOne("nonexistent-id");

        // Then
        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void addImage_WithWrongToken_ShouldReturnForbidden() {
        // Given
        Product product = new Product();
        product.setName("Product");
        Product saved = productRepository.save(product);

        String wrongToken = "wrong-token";
        Map<String, String> request = Map.of("mediaId", "test-media-id");

        // When
        ResponseEntity<?> response = productController.addImage(saved.getId(), request, wrongToken);

        // Then
        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    void addImage_WithoutToken_ShouldReturnForbidden() {
        // Given
        Product product = new Product();
        product.setName("Product");
        Product saved = productRepository.save(product);

        Map<String, String> request = Map.of("mediaId", "test-media-id");

        // When
        ResponseEntity<?> response = productController.addImage(saved.getId(), request, null);

        // Then
        assertEquals(403, response.getStatusCode().value());
    }

    @Test
    @WithMockUser(roles = "SELLER", username = "seller1")
    void createProduct_AsSeller_ShouldSucceed() {
        // Given
        ProductDTO product = new ProductDTO();
        product.setName("Seller's Product");
        product.setPrice(49.99);

        // When
        ResponseEntity<?> response = productController.create(product);

        // Then
        assertEquals(200, response.getStatusCode().value());
        Product created = (Product) response.getBody();
        assertNotNull(created);
        assertEquals("seller1", created.getUserId()); // Vérifie que le userId est bien set
    }

    @Test
    @WithMockUser(roles = "USER")
    void createProduct_AsUser_ShouldReturnForbidden() {
        // Given
        ProductDTO product = new ProductDTO();
        product.setName("User's Product");

        // When
        ProductController.ControllerException exception = org.junit.jupiter.api.Assertions.assertThrows(
                ProductController.ControllerException.class,
                () -> productController.create(product)
        );

        // Then
        assertEquals(403, exception.getResponse().getStatusCode().value());
    }
}

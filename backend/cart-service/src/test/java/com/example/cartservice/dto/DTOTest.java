package com.example.cartservice.dto;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

class DTOTest {

    @Test
    void testCartRequest() {
        CartRequest request = new CartRequest();
        request.setProductId("p1");
        request.setQuantity(2);

        assertEquals("p1", request.getProductId());
        assertEquals(2, request.getQuantity());

        CartRequest request2 = new CartRequest();
        request2.setProductId("p1");
        request2.setQuantity(2);

        assertEquals(request, request2);
        assertEquals(request.hashCode(), request2.hashCode());
        assertNotNull(request.toString());
    }

    @Test
    void testProductDTO() {
        BigDecimal price = new BigDecimal("100.00");
        ProductDTO dto = new ProductDTO();
        dto.setId("p1");
        dto.setName("Product 1");
        dto.setPrice(price);
        dto.setQuantity(10);
        dto.setUserId("user1");

        assertEquals("p1", dto.getId());
        assertEquals("Product 1", dto.getName());
        assertEquals(price, dto.getPrice());
        assertEquals(10, dto.getQuantity());
        assertEquals("user1", dto.getUserId());

        ProductDTO dto2 = new ProductDTO();
        dto2.setId("p1");
        dto2.setName("Product 1");
        dto2.setPrice(price);
        dto2.setQuantity(10);
        dto2.setUserId("user1");

        assertEquals(dto, dto2);
        assertEquals(dto.hashCode(), dto2.hashCode());
        assertNotNull(dto.toString());
    }
}

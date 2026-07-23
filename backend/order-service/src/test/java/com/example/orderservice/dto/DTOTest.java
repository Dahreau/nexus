package com.example.orderservice.dto;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class DTOTest {

    @Test
    void testCartDTO() {
        CartDTO dto = new CartDTO();
        dto.setUserId("u1");
        CartItemDTO item = new CartItemDTO();
        dto.setItems(Collections.singletonList(item));

        assertEquals("u1", dto.getUserId());
        assertEquals(1, dto.getItems().size());
        assertNotNull(dto.toString());
    }

    @Test
    void testCartItemDTO() {
        CartItemDTO dto = new CartItemDTO();
        dto.setProductId("p1");
        dto.setProductName("Prod 1");
        dto.setPrice(BigDecimal.TEN);
        dto.setQuantity(2);
        dto.setSellerId("s1");

        assertEquals("p1", dto.getProductId());
        assertEquals("Prod 1", dto.getProductName());
        assertEquals(BigDecimal.TEN, dto.getPrice());
        assertEquals(2, dto.getQuantity());
        assertEquals("s1", dto.getSellerId());
    }

    @Test
    void testCheckoutRequest() {
        CheckoutRequest dto = new CheckoutRequest();
        dto.setShippingAddress("Addr 1");
        dto.setPaymentMethod("CARD");

        assertEquals("Addr 1", dto.getShippingAddress());
        assertEquals("CARD", dto.getPaymentMethod());
    }

    @Test
    void testProductSummaryDTO() {
        ProductSummaryDTO dto = new ProductSummaryDTO("p1", "Prod 1", 5L);
        assertEquals("p1", dto.getProductId());
        assertEquals("Prod 1", dto.getProductName());
        assertEquals(5L, dto.getCount());

        ProductSummaryDTO dto2 = new ProductSummaryDTO();
        dto2.setProductId("p1");
        dto2.setProductName("Prod 1");
        dto2.setCount(5L);
        assertEquals(dto, dto2);
    }

    @Test
    void testSellerStatsDTO() {
        SellerStatsDTO dto = SellerStatsDTO.builder()
                .totalRevenue(BigDecimal.valueOf(100))
                .completedOrders(10L)
                .bestSellers(Collections.emptyList())
                .build();

        assertEquals(BigDecimal.valueOf(100), dto.getTotalRevenue());
        assertEquals(10L, dto.getCompletedOrders());
        assertTrue(dto.getBestSellers().isEmpty());

        SellerStatsDTO dto2 = new SellerStatsDTO(BigDecimal.valueOf(100), 10L, Collections.emptyList());
        assertEquals(dto, dto2);
    }

    @Test
    void testUserStatsDTO() {
        UserStatsDTO dto = UserStatsDTO.builder()
                .totalSpent(BigDecimal.valueOf(50))
                .totalOrders(5L)
                .topProducts(Collections.emptyList())
                .build();

        assertEquals(BigDecimal.valueOf(50), dto.getTotalSpent());
        assertEquals(5L, dto.getTotalOrders());
        assertTrue(dto.getTopProducts().isEmpty());

        UserStatsDTO dto2 = new UserStatsDTO(BigDecimal.valueOf(50), 5L, Collections.emptyList());
        assertEquals(dto, dto2);
    }

    @Test
    void testStockUpdateEvent() {
        StockUpdateEvent dto = new StockUpdateEvent("p1", 2);
        assertEquals("p1", dto.getProductId());
        assertEquals(2, dto.getQuantity());

        StockUpdateEvent dto2 = new StockUpdateEvent();
        dto2.setProductId("p1");
        dto2.setQuantity(2);
        assertEquals(dto, dto2);
    }
}

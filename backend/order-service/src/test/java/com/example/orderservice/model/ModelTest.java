package com.example.orderservice.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.time.Month;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import org.junit.jupiter.api.Test;

class ModelTest {

    @Test
    void testOrder() {
        LocalDateTime now = LocalDateTime.of(2024, Month.JANUARY, 1, 12, 0);
        OrderItem item = OrderItem.builder()
                .productId("p1")
                .productName("Prod 1")
                .priceAtPurchase(BigDecimal.TEN)
                .quantity(2)
                .sellerId("s1")
                .build();

        Order order = Order.builder()
                .id("o1")
                .userId("u1")
                .items(Collections.singletonList(item))
                .totalAmount(BigDecimal.valueOf(20))
                .status(OrderStatus.PENDING)
                .paymentMethod("CARD")
                .shippingAddress("Addr 1")
                .createdAt(now)
                .updatedAt(now)
                .build();

        assertEquals("o1", order.getId());
        assertEquals("u1", order.getUserId());
        assertEquals(1, order.getItems().size());
        assertEquals(BigDecimal.valueOf(20), order.getTotalAmount());
        assertEquals(OrderStatus.PENDING, order.getStatus());
        assertEquals("CARD", order.getPaymentMethod());
        assertEquals("Addr 1", order.getShippingAddress());
        assertEquals(now, order.getCreatedAt());
        assertEquals(now, order.getUpdatedAt());

        Order order2 = new Order("o1", "u1", Collections.singletonList(item), BigDecimal.valueOf(20), OrderStatus.PENDING, "CARD", "Addr 1", now, now);
        assertEquals(order, order2);
        assertEquals(order.hashCode(), order2.hashCode());
        assertNotNull(order.toString());
    }

    @Test
    void testOrderItem() {
        OrderItem item = new OrderItem();
        item.setProductId("p1");
        item.setProductName("Prod 1");
        item.setPriceAtPurchase(BigDecimal.TEN);
        item.setQuantity(2);
        item.setSellerId("s1");

        assertEquals("p1", item.getProductId());
        assertEquals("Prod 1", item.getProductName());
        assertEquals(BigDecimal.TEN, item.getPriceAtPurchase());
        assertEquals(2, item.getQuantity());
        assertEquals("s1", item.getSellerId());

        OrderItem item2 = new OrderItem("p1", "Prod 1", BigDecimal.TEN, 2, "s1");
        assertEquals(item, item2);
    }

    @Test
    void testOrderStatus() {
        assertEquals(5, OrderStatus.values().length);
        assertEquals(OrderStatus.PENDING, OrderStatus.valueOf("PENDING"));
    }
}

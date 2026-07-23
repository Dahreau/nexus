package com.example.cartservice.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class ModelTest {

    @Test
    void testCartData() {
        List<CartItem> items = new ArrayList<>();
        Cart cart = Cart.builder()
                .id("1")
                .userId("user123")
                .items(items)
                .build();

        assertEquals("1", cart.getId());
        assertEquals("user123", cart.getUserId());
        assertEquals(items, cart.getItems());

        cart.setId("2");
        assertEquals("2", cart.getId());

        Cart cart2 = new Cart("2", "user123", items);
        assertEquals(cart, cart2);
        assertEquals(cart.hashCode(), cart2.hashCode());
        assertNotNull(cart.toString());
    }

    @Test
    void testCartNoArgsConstructor() {
        Cart cart = new Cart();
        assertNotNull(cart);
        assertNotNull(cart.getItems());
        assertTrue(cart.getItems().isEmpty());
    }

    @Test
    void testCartItemData() {
        BigDecimal price = new BigDecimal("10.00");
        CartItem item = CartItem.builder()
                .productId("p1")
                .productName("Product 1")
                .price(price)
                .quantity(2)
                .sellerId("s1")
                .build();

        assertEquals("p1", item.getProductId());
        assertEquals("Product 1", item.getProductName());
        assertEquals(price, item.getPrice());
        assertEquals(2, item.getQuantity());
        assertEquals("s1", item.getSellerId());

        item.setQuantity(5);
        assertEquals(5, item.getQuantity());

        CartItem item2 = new CartItem("p1", "Product 1", price, 5, "s1");
        assertEquals(item, item2);
        assertEquals(item.hashCode(), item2.hashCode());
        assertNotNull(item.toString());
    }

    @Test
    void testCartItemNoArgsConstructor() {
        CartItem item = new CartItem();
        assertNotNull(item);
    }
}

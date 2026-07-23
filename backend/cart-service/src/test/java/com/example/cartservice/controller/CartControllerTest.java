package com.example.cartservice.controller;

import java.security.Key;
import java.util.ArrayList;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.cartservice.dto.CartRequest;
import com.example.cartservice.model.Cart;
import com.example.cartservice.service.CartService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CartService cartService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${jwt.secret}")
    private String jwtSecret;

    private String validToken;
    private final String userId = "user123";

    private String createValidToken(String sub, String role) {
        Key key = Keys.hmacShaKeyFor(jwtSecret.getBytes());
        return "Bearer " + Jwts.builder()
                .claim("sub", sub)
                .claim("role", role)
                .signWith(key)
                .compact();
    }

    @BeforeEach
    void setUp() {
        validToken = createValidToken(userId, "USER");
    }

    @Test
    void getCart_Success() throws Exception {
        Cart cart = Cart.builder().userId(userId).items(new ArrayList<>()).build();
        when(cartService.getCartByUserId(userId)).thenReturn(cart);

        mockMvc.perform(get("/api/carts")
                .header("Authorization", validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId));
    }

    @Test
    void addToCart_Success() throws Exception {
        CartRequest request = new CartRequest();
        request.setProductId("p1");
        request.setQuantity(2);

        Cart cart = Cart.builder().userId(userId).items(new ArrayList<>()).build();
        when(cartService.addToCart(eq(userId), any(CartRequest.class))).thenReturn(cart);

        mockMvc.perform(post("/api/carts")
                .header("Authorization", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId));
    }

    @Test
    void updateQuantity_Success() throws Exception {
        CartRequest request = new CartRequest();
        request.setProductId("p1");
        request.setQuantity(5);

        Cart cart = Cart.builder().userId(userId).items(new ArrayList<>()).build();
        when(cartService.updateQuantity(eq(userId), any(CartRequest.class))).thenReturn(cart);

        mockMvc.perform(put("/api/carts")
                .header("Authorization", validToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId));
    }

    @Test
    void removeFromCart_Success() throws Exception {
        Cart cart = Cart.builder().userId(userId).items(new ArrayList<>()).build();
        when(cartService.removeFromCart(userId, "p1")).thenReturn(cart);

        mockMvc.perform(delete("/api/carts/p1")
                .header("Authorization", validToken))
                .andExpect(status().isOk());
    }

    @Test
    void clearCart_Success() throws Exception {
        mockMvc.perform(delete("/api/carts/clear")
                .header("Authorization", validToken))
                .andExpect(status().isOk());
    }

    @Test
    void testInvalidToken() throws Exception {
        mockMvc.perform(get("/api/carts")
                .header("Authorization", "Bearer token.pourri.123"))
                .andExpect(status().isForbidden());
    }
}

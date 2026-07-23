package com.example.cartservice.controller;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.cartservice.dto.CartRequest;
import com.example.cartservice.model.Cart;
import com.example.cartservice.service.CartService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/carts")
@RequiredArgsConstructor
public class CartController {

    private final CartService cartService;

    private String getAuthenticatedUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    @GetMapping
    public Cart getCart() {
        return cartService.getCartByUserId(getAuthenticatedUserId());
    }

    @PostMapping
    public Cart addToCart(@RequestBody @Valid CartRequest request) {
        return cartService.addToCart(getAuthenticatedUserId(), request);
    }

    @PutMapping
    public Cart updateQuantity(@RequestBody @Valid CartRequest request) {
        return cartService.updateQuantity(getAuthenticatedUserId(), request);
    }

    @DeleteMapping("/{productId}")
    public Cart removeFromCart(@PathVariable String productId) {
        return cartService.removeFromCart(getAuthenticatedUserId(), productId);
    }

    @DeleteMapping("/clear")
    public void clearCart() {
        cartService.clearCart(getAuthenticatedUserId());
    }
}

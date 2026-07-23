package com.example.orderservice.controller;

import java.time.LocalDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.example.orderservice.dto.CheckoutRequest;
import com.example.orderservice.dto.SellerStatsDTO;
import com.example.orderservice.dto.UserStatsDTO;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.service.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor

public class OrderController {

    private final OrderService orderService;
    private static final String ERRORS = "Accès réservé aux vendeurs";

    private String getAuthenticatedUserId() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean isSeller() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_SELLER"));
    }

    @GetMapping("/stats/user")
    public UserStatsDTO getMyStats() {
        return orderService.getUserStats(getAuthenticatedUserId());
    }

    @GetMapping("/stats/seller")
    public SellerStatsDTO getSellerStats() {
        if (!isSeller()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ERRORS);
        }
        return orderService.getSellerStats(getAuthenticatedUserId());
    }

    @GetMapping
    public Page<Order> getMyOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (start != null && end != null && start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de début doit être antérieure à la date de fin");
        }

        return orderService.searchOrders(getAuthenticatedUserId(), null, status, start, end, keyword, PageRequest.of(page, size));
    }

    @GetMapping("/seller")
    public Page<Order> getSellerOrders(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (start != null && end != null && start.isAfter(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La date de début doit être antérieure à la date de fin");
        }

        if (!isSeller()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ERRORS);
        }
        return orderService.searchOrders(null, getAuthenticatedUserId(), status, start, end, keyword, PageRequest.of(page, size));
    }

    @PostMapping("/{id}/cancel")
    public Order cancelOrder(@PathVariable String id) {
        return orderService.cancelOrder(getAuthenticatedUserId(), id);
    }

    @PutMapping("/{id}/status")
    public Order updateOrderStatus(
            @PathVariable String id,
            @RequestParam OrderStatus status) {
        if (!isSeller()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ERRORS);
        }
        return orderService.updateOrderStatus(getAuthenticatedUserId(), id, status);
    }

    @DeleteMapping("/{id}")
    public void deleteOrder(@PathVariable String id) {
        orderService.deleteOrder(getAuthenticatedUserId(), id);
    }

    @PostMapping("/{id}/redo")
    public Order redoOrder(@RequestHeader("Authorization") String token, @PathVariable String id) {
        return orderService.redoOrder(getAuthenticatedUserId(), id, token);
    }

    @PostMapping("/checkout")
    public Order checkout(@RequestHeader("Authorization") String token, @RequestBody @Valid CheckoutRequest request) {
        return orderService.checkout(getAuthenticatedUserId(), request, token);
    }
}

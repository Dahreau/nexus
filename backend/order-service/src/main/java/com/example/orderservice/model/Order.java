package com.example.orderservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "orders")
public class Order {

    @Id
    private String id;
    private String userId;
    private List<OrderItem> items;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private String paymentMethod; // e.g., "PAY_ON_DELIVERY"
    private String shippingAddress;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

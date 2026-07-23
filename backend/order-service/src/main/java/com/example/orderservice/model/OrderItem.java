package com.example.orderservice.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class OrderItem {

    private String productId;
    private String productName;
    private BigDecimal priceAtPurchase;
    private Integer quantity;
    private String sellerId;
}

package com.example.cartservice.model;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CartItem {

    private String productId;
    private String productName;
    private BigDecimal price;
    private Integer quantity;
    private String sellerId;
}

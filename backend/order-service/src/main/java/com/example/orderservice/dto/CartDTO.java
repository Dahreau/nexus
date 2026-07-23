package com.example.orderservice.dto;

import lombok.Data;
import java.util.List;

@Data
public class CartDTO {
    private String userId;
    private List<CartItemDTO> items;
}

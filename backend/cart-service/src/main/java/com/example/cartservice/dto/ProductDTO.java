package com.example.cartservice.dto;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class ProductDTO {

    private String id;
    private String name;
    private BigDecimal price;
    private Integer quantity;

    @JsonProperty("userId")
    private String userId;
}

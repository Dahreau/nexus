package com.example.cartservice.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CartRequest {

    @NotBlank(message = "L'ID du produit est requis")
    private String productId;

    @NotNull(message = "La quantité est requise")
    @Min(value = 1, message = "La quantité doit être au moins de 1")
    private Integer quantity;
}

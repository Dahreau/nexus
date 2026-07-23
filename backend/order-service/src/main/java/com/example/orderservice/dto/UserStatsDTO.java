package com.example.orderservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserStatsDTO {
    private BigDecimal totalSpent;
    private long totalOrders;
    private List<ProductSummaryDTO> topProducts;
}

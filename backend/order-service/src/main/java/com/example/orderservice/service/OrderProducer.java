package com.example.orderservice.service;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.orderservice.dto.StockUpdateEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderProducer {

    private final WebClient.Builder webClientBuilder;

    public void sendStockUpdate(String productId, Integer quantity) {
        log.info("Envoi mise à jour stock pour le produit {} : -{}", productId, quantity);
        String internalToken = System.getenv("INTERNAL_TOKEN");
        webClientBuilder.build()
                .post()
                .uri("http://product-service:8082/api/products/stock-update")
                .header("X-Internal-Token", internalToken)
                .bodyValue(new StockUpdateEvent(productId, quantity))
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}

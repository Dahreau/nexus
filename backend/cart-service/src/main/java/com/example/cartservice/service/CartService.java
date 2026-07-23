package com.example.cartservice.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.cartservice.dto.CartRequest;
import com.example.cartservice.dto.ProductDTO;
import com.example.cartservice.model.Cart;
import com.example.cartservice.model.CartItem;
import com.example.cartservice.repository.CartRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CartService {

    private final CartRepository cartRepository;
    private final WebClient.Builder webClientBuilder;

    public Cart getCartByUserId(String userId) {
        return cartRepository.findByUserId(userId)
                .orElseGet(() -> cartRepository.save(Cart.builder().userId(userId).items(new ArrayList<>()).build()));
    }

    public Cart addToCart(String userId, CartRequest request) {
        log.info("Tentative d'ajout au panier pour l'utilisateur {} : {}", userId, request);

        ProductDTO product = fetchProductDetails(request.getProductId());

        if (product == null) {
            log.error("Produit non trouvé : {}", request.getProductId());
            throw new IllegalArgumentException("Produit non trouvé");
        }

        if (userId.equals(product.getUserId())) {
            log.warn("Le vendeur {} a tenté d'acheter son propre produit {}", userId, product.getId());
            throw new IllegalArgumentException("Vous ne pouvez pas acheter votre propre produit");
        }

        Cart cart = getCartByUserId(userId);

        Optional<CartItem> existingItem = cart.getItems().stream()
                .filter(item -> item.getProductId().equals(request.getProductId()))
                .findFirst();

        int currentQuantity = existingItem.map(CartItem::getQuantity).orElse(0);
        int newTotalQuantity = currentQuantity + request.getQuantity();

        if (product.getQuantity() < newTotalQuantity) {
            log.warn("Stock insuffisant pour le produit {} : {} requis au total, {} disponibles",
                    product.getId(), newTotalQuantity, product.getQuantity());
            throw new IllegalStateException("Stock insuffisant");
        }

        if (existingItem.isPresent()) {
            existingItem.get().setQuantity(newTotalQuantity);
        } else {
            cart.getItems().add(CartItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .price(product.getPrice())
                    .quantity(request.getQuantity())
                    .sellerId(product.getUserId())
                    .build());
        }

        return cartRepository.save(cart);
    }

    public Cart updateQuantity(String userId, CartRequest request) {
        Cart cart = getCartByUserId(userId);

        CartItem item = cart.getItems().stream()
                .filter(i -> i.getProductId().equals(request.getProductId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Produit non trouvé dans le panier"));

        ProductDTO product = fetchProductDetails(request.getProductId());
        if (product.getQuantity() < request.getQuantity()) {
            throw new IllegalStateException("Stock insuffisant");
        }

        item.setQuantity(request.getQuantity());
        return cartRepository.save(cart);
    }

    public Cart removeFromCart(String userId, String productId) {
        Cart cart = getCartByUserId(userId);
        cart.getItems().removeIf(item -> item.getProductId().equals(productId));
        return cartRepository.save(cart);
    }

    public void clearCart(String userId) {
        Cart cart = getCartByUserId(userId);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    private ProductDTO fetchProductDetails(String productId) {
        log.info("Récupération des détails du produit {} depuis product-service", productId);
        try {
            return webClientBuilder.build()
                    .get()
                    .uri("http://product-service:8082/api/products/" + productId)
                    .retrieve()
                    .bodyToMono(ProductDTO.class)
                    .timeout(Duration.ofSeconds(5))
                    .block();
        } catch (Exception e) {
            log.error("Erreur lors de l'appel au product-service : {}", e.getMessage());
            throw new IllegalStateException("Impossible de vérifier le produit : " + e.getMessage(), e);
        }
    }
}

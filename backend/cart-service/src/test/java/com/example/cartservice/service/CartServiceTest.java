package com.example.cartservice.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.cartservice.dto.CartRequest;
import com.example.cartservice.dto.ProductDTO;
import com.example.cartservice.model.Cart;
import com.example.cartservice.model.CartItem;
import com.example.cartservice.repository.CartRepository;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private WebClient.Builder webClientBuilder;

    @Mock
    private WebClient webClient;

    @SuppressWarnings("rawtypes")
    @Mock
    private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

    @SuppressWarnings("rawtypes")
    @Mock
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    @Mock
    private WebClient.ResponseSpec responseSpec;

    @InjectMocks
    private CartService cartService;

    @SuppressWarnings({"unchecked", "unused"})
    @BeforeEach
    void setUp() {
        lenient().when(webClientBuilder.build()).thenReturn(webClient);
        lenient().when(webClient.get()).thenReturn(requestHeadersUriSpec);
        lenient().when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        lenient().when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
    }

    @Test
    void getCartByUserId_ExistingCart() {
        Cart cart = Cart.builder().userId("user1").items(new ArrayList<>()).build();
        when(cartRepository.findByUserId("user1")).thenReturn(Optional.of(cart));

        Cart result = cartService.getCartByUserId("user1");

        assertEquals("user1", result.getUserId());
        verify(cartRepository, times(1)).findByUserId("user1");
    }

    @Test
    void getCartByUserId_NewCart() {
        when(cartRepository.findByUserId("user1")).thenReturn(Optional.empty());
        when(cartRepository.save(any(Cart.class))).thenAnswer(i -> i.getArguments()[0]);

        Cart result = cartService.getCartByUserId("user1");

        assertEquals("user1", result.getUserId());
        verify(cartRepository, times(1)).findByUserId("user1");
        verify(cartRepository, times(1)).save(any(Cart.class));
    }

    @Test
    void addToCart_Success() {
        CartRequest request = new CartRequest();
        request.setProductId("p1");
        request.setQuantity(2);

        ProductDTO product = new ProductDTO();
        product.setId("p1");
        product.setName("Product 1");
        product.setPrice(new BigDecimal("10.00"));
        product.setQuantity(10);
        product.setUserId("seller1");

        Cart cart = Cart.builder().userId("user1").items(new ArrayList<>()).build();

        when(responseSpec.bodyToMono(ProductDTO.class)).thenReturn(Mono.just(product));
        when(cartRepository.findByUserId("user1")).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(i -> i.getArguments()[0]);

        Cart result = cartService.addToCart("user1", request);

        assertEquals(1, result.getItems().size());
        assertEquals("p1", result.getItems().get(0).getProductId());
        assertEquals(2, result.getItems().get(0).getQuantity());
    }

    @Test
    void addToCart_ExistingItem_Success() {
        CartRequest request = new CartRequest();
        request.setProductId("p1");
        request.setQuantity(2);

        ProductDTO product = new ProductDTO();
        product.setId("p1");
        product.setName("Product 1");
        product.setPrice(new BigDecimal("10.00"));
        product.setQuantity(10);
        product.setUserId("seller1");

        CartItem existingItem = CartItem.builder().productId("p1").quantity(3).build();
        ArrayList<CartItem> items = new ArrayList<>();
        items.add(existingItem);
        Cart cart = Cart.builder().userId("user1").items(items).build();

        when(responseSpec.bodyToMono(ProductDTO.class)).thenReturn(Mono.just(product));
        when(cartRepository.findByUserId("user1")).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(i -> i.getArguments()[0]);

        Cart result = cartService.addToCart("user1", request);

        assertEquals(1, result.getItems().size());
        assertEquals(5, result.getItems().get(0).getQuantity());
    }

    @Test
    void addToCart_ProductNotFound() {
        CartRequest request = new CartRequest();
        request.setProductId("p1");
        request.setQuantity(2);

        when(responseSpec.bodyToMono(ProductDTO.class)).thenReturn(Mono.empty());

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> cartService.addToCart("user1", request));
        assertEquals("Produit non trouvé", exception.getMessage());
    }

    @Test
    void addToCart_InsufficientStock() {
        CartRequest request = new CartRequest();
        request.setProductId("p1");
        request.setQuantity(20);

        ProductDTO product = new ProductDTO();
        product.setId("p1");
        product.setQuantity(10);

        Cart cart = Cart.builder().userId("user1").items(new ArrayList<>()).build();

        when(responseSpec.bodyToMono(ProductDTO.class)).thenReturn(Mono.just(product));
        when(cartRepository.findByUserId("user1")).thenReturn(Optional.of(cart));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> cartService.addToCart("user1", request));
        assertEquals("Stock insuffisant", exception.getMessage());
    }

    @Test
    void updateQuantity_Success() {
        CartRequest request = new CartRequest();
        request.setProductId("p1");
        request.setQuantity(5);

        ProductDTO product = new ProductDTO();
        product.setId("p1");
        product.setQuantity(10);

        CartItem existingItem = CartItem.builder().productId("p1").quantity(3).build();
        ArrayList<CartItem> items = new ArrayList<>();
        items.add(existingItem);
        Cart cart = Cart.builder().userId("user1").items(items).build();

        when(responseSpec.bodyToMono(ProductDTO.class)).thenReturn(Mono.just(product));
        when(cartRepository.findByUserId("user1")).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(i -> i.getArguments()[0]);

        Cart result = cartService.updateQuantity("user1", request);

        assertEquals(5, result.getItems().get(0).getQuantity());
    }

    @Test
    void removeFromCart_Success() {
        CartItem item = CartItem.builder().productId("p1").build();
        ArrayList<CartItem> items = new ArrayList<>();
        items.add(item);
        Cart cart = Cart.builder().userId("user1").items(items).build();

        when(cartRepository.findByUserId("user1")).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(i -> i.getArguments()[0]);

        Cart result = cartService.removeFromCart("user1", "p1");

        assertTrue(result.getItems().isEmpty());
    }

    @Test
    void clearCart_Success() {
        CartItem item = CartItem.builder().productId("p1").build();
        ArrayList<CartItem> items = new ArrayList<>();
        items.add(item);
        Cart cart = Cart.builder().userId("user1").items(items).build();

        when(cartRepository.findByUserId("user1")).thenReturn(Optional.of(cart));
        when(cartRepository.save(any(Cart.class))).thenAnswer(i -> i.getArguments()[0]);

        cartService.clearCart("user1");

        verify(cartRepository, times(1)).save(argThat(c -> c.getItems().isEmpty()));
    }
}

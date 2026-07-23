package com.example.orderservice.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import com.example.orderservice.dto.CartDTO;
import com.example.orderservice.dto.CartItemDTO;
import com.example.orderservice.dto.CheckoutRequest;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;

import reactor.core.publisher.Mono;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private WebClient.Builder webClientBuilder;
    @Mock
    private MongoTemplate mongoTemplate;
    @Mock
    private OrderProducer orderProducer;

    @InjectMocks
    private OrderService orderService;

    private WebClient webClient;
    
    @SuppressWarnings("rawtypes")
       private WebClient.RequestHeadersUriSpec requestHeadersUriSpec;

       @SuppressWarnings("rawtypes")
    private WebClient.RequestHeadersSpec requestHeadersSpec;

    private WebClient.ResponseSpec responseSpec;

    @BeforeEach
    @SuppressWarnings("unused") 
    void setUp() {
        webClient = mock(WebClient.class);
        requestHeadersUriSpec = mock(WebClient.RequestHeadersUriSpec.class);
        requestHeadersSpec = mock(WebClient.RequestHeadersSpec.class);
        responseSpec = mock(WebClient.ResponseSpec.class);

        lenient().when(webClientBuilder.build()).thenReturn(webClient);
    }

    @Test
    void searchOrders() {
        when(mongoTemplate.count(any(Query.class), eq(Order.class))).thenReturn(1L);
        when(mongoTemplate.find(any(Query.class), eq(Order.class))).thenReturn(Collections.singletonList(new Order()));

        Page<Order> result = orderService.searchOrders("u1", null, null, null, null, null, Pageable.unpaged());

        assertEquals(1, result.getTotalElements());
        assertEquals(1, result.getContent().size());
    }

    @Test
    void cancelOrderSuccess() {
        Order order = Order.builder().userId("u1").status(OrderStatus.PENDING).build();
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);

        Order result = orderService.cancelOrder("u1", "o1");

        assertEquals(OrderStatus.CANCELLED, result.getStatus());
    }

    @Test
    void cancelOrderNotFound() {
        when(orderRepository.findById("o1")).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> orderService.cancelOrder("u1", "o1"));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void cancelOrderWrongUser() {
        Order order = Order.builder().userId("u2").build();
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> orderService.cancelOrder("u1", "o1"));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }

    @Test
    void cancelOrderNotPending() {
        Order order = Order.builder().userId("u1").status(OrderStatus.PAID).build();
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> orderService.cancelOrder("u1", "o1"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }

    @Test
    void deleteOrderSuccess() {
        Order order = Order.builder().userId("u1").status(OrderStatus.DELIVERED).build();
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));

        orderService.deleteOrder("u1", "o1");

        verify(orderRepository).deleteById("o1");
    }

    @Test
    void updateOrderStatusSuccess() {
        OrderItem item = OrderItem.builder().sellerId("s1").productId("p1").quantity(1).build();
        Order order = Order.builder().items(Collections.singletonList(item)).status(OrderStatus.PENDING).build();
        when(orderRepository.findById("o1")).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);

        Order result = orderService.updateOrderStatus("s1", "o1", OrderStatus.PAID);

        assertEquals(OrderStatus.PAID, result.getStatus());
        verify(orderProducer).sendStockUpdate("p1", 1);
    }

    @Test
    void redoOrder() {
        Order oldOrder = Order.builder()
                .userId("u1")
                .items(new ArrayList<>())
                .totalAmount(BigDecimal.TEN)
                .paymentMethod("CARD")
                .shippingAddress("Addr")
                .build();
        when(orderRepository.findById("o1")).thenReturn(Optional.of(oldOrder));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);

        Order result = orderService.redoOrder("u1", "o1", "token");

        assertEquals(OrderStatus.PENDING, result.getStatus());
        assertEquals(BigDecimal.TEN, result.getTotalAmount());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void checkout() {
        CartDTO cart = new CartDTO();
        cart.setUserId("u1");
        CartItemDTO item = new CartItemDTO();
        item.setProductId("p1");
        item.setPrice(BigDecimal.TEN);
        item.setQuantity(2);
        item.setSellerId("s1");
        cart.setItems(Collections.singletonList(item));

        when(webClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(anyString(), anyString())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.bodyToMono(CartDTO.class)).thenReturn(Mono.just(cart));

        WebClient.RequestHeadersUriSpec deleteSpec = mock(WebClient.RequestHeadersUriSpec.class);
        when(webClient.delete()).thenReturn(deleteSpec);
        when(deleteSpec.uri(anyString())).thenReturn(requestHeadersSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(Mono.empty());

        CheckoutRequest request = new CheckoutRequest();
        request.setPaymentMethod("CARD");
        request.setShippingAddress("Addr");

        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArguments()[0]);

        Order result = orderService.checkout("u1", request, "token");

        assertNotNull(result);
 
    }
}

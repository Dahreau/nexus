package com.example.orderservice.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators;
import org.springframework.data.mongodb.core.aggregation.ConvertOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import com.example.orderservice.dto.CartDTO;
import com.example.orderservice.dto.CheckoutRequest;
import com.example.orderservice.dto.ProductSummaryDTO;
import com.example.orderservice.dto.SellerStatsDTO;
import com.example.orderservice.dto.UserStatsDTO;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final WebClient.Builder webClientBuilder;
    private final MongoTemplate mongoTemplate;
    private final OrderProducer orderProducer;

    private static final String USER_ID_FIELD = "userId";
    private static final String STATUS_FIELD = "status";
    private static final String TOTAL_SPENT_FIELD = "totalSpent";
    private static final String TOTAL_ORDERS_FIELD = "totalOrders";
    private static final String ITEMS_FIELD = "items";
    private static final String PRODUCT_NAME_FIELD = "productName";
    private static final String ITEMS_PRODUCT_NAME_FIELD = "items.productName";
    private static final String ITEMS_QUANTITY_FIELD = "items.quantity";
    private static final String COUNT_FIELD = "count";
    private static final String TOTAL_REVENUE_FIELD = "totalRevenue";
    private static final String COMPLETED_ORDERS_FIELD = "completedOrders";
    private static final String ERROR_NOT_FOUND = "Commande non trouvée";
    private static final String ERROR_DENIED = "Accès refusé";
    private static final String ITEMS_PRODUCT_ID_FIELD = "items.productId";
    private static final String PRODUCT_ID_FIELD = "productId";
    private static final String ITEMS_SELLER_ID_FIELD = "items.sellerId";
    // "Dépensé" ne doit compter que les commandes réellement payées, pas les PENDING (pas encore payées) ni les CANCELLED.
    private static final List<String> PAID_STATUSES = List.of(
            OrderStatus.PAID.name(), OrderStatus.SHIPPED.name(), OrderStatus.DELIVERED.name());

    public UserStatsDTO getUserStats(String userId) {
        try {
            Aggregation baseAgg = Aggregation.newAggregation(
                    Aggregation.match(Criteria.where(USER_ID_FIELD).is(userId).and(STATUS_FIELD).in(PAID_STATUSES)),
                    Aggregation.project()
                            .and(ConvertOperators.ToDouble.toDouble("$totalAmount")).as("numericAmount"),
                    Aggregation.group()
                            .sum("numericAmount").as(TOTAL_SPENT_FIELD)
                            .count().as(TOTAL_ORDERS_FIELD)
            );

            Map<String, Object> baseResults = mongoTemplate.aggregate(baseAgg, Order.class, org.bson.Document.class).getUniqueMappedResult();

            Aggregation productsAgg = Aggregation.newAggregation(
                    Aggregation.match(Criteria.where(USER_ID_FIELD).is(userId).and(STATUS_FIELD).in(PAID_STATUSES)),
                    Aggregation.unwind(ITEMS_FIELD),
                    Aggregation.group(ITEMS_PRODUCT_ID_FIELD)
                            .first(ITEMS_PRODUCT_NAME_FIELD).as(PRODUCT_NAME_FIELD)
                            .sum(ITEMS_QUANTITY_FIELD).as(COUNT_FIELD),
                    Aggregation.project(PRODUCT_NAME_FIELD, COUNT_FIELD).and("_id").as(PRODUCT_ID_FIELD),
                    Aggregation.sort(Sort.Direction.DESC, COUNT_FIELD),
                    Aggregation.limit(5)
            );

            List<ProductSummaryDTO> topProducts = mongoTemplate.aggregate(productsAgg, Order.class, ProductSummaryDTO.class).getMappedResults();

            double spent = 0;
            long orders = 0;

            if (baseResults != null) {
                spent = baseResults.get(TOTAL_SPENT_FIELD) != null ? Double.parseDouble(baseResults.get(TOTAL_SPENT_FIELD).toString()) : 0;
                orders = baseResults.get(TOTAL_ORDERS_FIELD) != null ? Long.parseLong(baseResults.get(TOTAL_ORDERS_FIELD).toString()) : 0;
            }

            return UserStatsDTO.builder()
                    .totalSpent(BigDecimal.valueOf(spent))
                    .totalOrders(orders)
                    .topProducts(topProducts != null ? topProducts : new ArrayList<>())
                    .build();
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("Error user stats: {}", e.getMessage());
            return UserStatsDTO.builder().totalSpent(BigDecimal.ZERO).totalOrders(0).topProducts(new ArrayList<>()).build();
        }
    }

    public SellerStatsDTO getSellerStats(String sellerId) {
        try {
            Aggregation revenueAgg = Aggregation.newAggregation(
                    Aggregation.unwind(ITEMS_FIELD),
                    Aggregation.match(Criteria.where(ITEMS_SELLER_ID_FIELD).is(sellerId).and(STATUS_FIELD).is(OrderStatus.DELIVERED.name())),
                    Aggregation.project(ITEMS_QUANTITY_FIELD)
                            .and(ConvertOperators.ToDouble.toDouble("$items.priceAtPurchase")).as("unitPrice"),
                    Aggregation.project()
                            .and(ArithmeticOperators.Multiply.valueOf("unitPrice").multiplyBy("quantity")).as("lineRevenue"),
                    Aggregation.group()
                            .sum("lineRevenue").as(TOTAL_REVENUE_FIELD)
                            .count().as(COMPLETED_ORDERS_FIELD)
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> revenueResults = mongoTemplate.aggregate(revenueAgg, Order.class, Map.class).getUniqueMappedResult();

            Aggregation bestSellersAgg = Aggregation.newAggregation(
                    Aggregation.unwind(ITEMS_FIELD),
                    Aggregation.match(Criteria.where(ITEMS_SELLER_ID_FIELD).is(sellerId).and(STATUS_FIELD).ne(OrderStatus.CANCELLED.name())),
                    Aggregation.group(ITEMS_PRODUCT_ID_FIELD)
                            .first(ITEMS_PRODUCT_NAME_FIELD).as(PRODUCT_NAME_FIELD)
                            .sum(ITEMS_QUANTITY_FIELD).as(COUNT_FIELD),
                    Aggregation.project(PRODUCT_NAME_FIELD, COUNT_FIELD).and("_id").as(PRODUCT_ID_FIELD),
                    Aggregation.sort(Sort.Direction.DESC, COUNT_FIELD),
                    Aggregation.limit(5)
            );

            List<ProductSummaryDTO> bestSellers = mongoTemplate.aggregate(bestSellersAgg, Order.class, ProductSummaryDTO.class).getMappedResults();

            double rev = 0;
            long sales = 0;

            if (revenueResults != null) {
                rev = revenueResults.get(TOTAL_REVENUE_FIELD) != null ? Double.parseDouble(revenueResults.get(TOTAL_REVENUE_FIELD).toString()) : 0;
                sales = revenueResults.get(COMPLETED_ORDERS_FIELD) != null ? Long.parseLong(revenueResults.get(COMPLETED_ORDERS_FIELD).toString()) : 0;
            }

            return SellerStatsDTO.builder()
                    .totalRevenue(BigDecimal.valueOf(rev))
                    .completedOrders(sales)
                    .bestSellers(bestSellers != null ? bestSellers : new ArrayList<>())
                    .build();
        } catch (org.springframework.dao.DataAccessException e) {
            log.error("Error seller stats: {}", e.getMessage());
            return SellerStatsDTO.builder().totalRevenue(BigDecimal.ZERO).completedOrders(0).bestSellers(new ArrayList<>()).build();
        }
    }

    public Page<Order> searchOrders(String userId, String sellerId, OrderStatus status,
            LocalDateTime start, LocalDateTime end, String keyword, Pageable pageable) {
        Query query = new Query().with(pageable);
        List<Criteria> criteriaList = new ArrayList<>();

        if (userId != null) {
            criteriaList.add(Criteria.where(USER_ID_FIELD).is(userId));
        }
        if (sellerId != null) {
            criteriaList.add(Criteria.where(ITEMS_SELLER_ID_FIELD).is(sellerId));
        }
        if (status != null) {
            criteriaList.add(Criteria.where(STATUS_FIELD).is(status));
        }
        if (start != null && end != null) {
            criteriaList.add(Criteria.where("createdAt").gte(start).lte(end));
        }
        if (keyword != null) {
            criteriaList.add(Criteria.where(ITEMS_PRODUCT_NAME_FIELD).regex(java.util.regex.Pattern.quote(keyword), "i"));
        }

        if (!criteriaList.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criteriaList.toArray(Criteria[]::new)));
        }

        long count = mongoTemplate.count(query, Order.class);
        List<Order> orders = mongoTemplate.find(query, Order.class);

        return new PageImpl<>(orders, pageable, count);
    }

    public Order cancelOrder(String userId, String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ERROR_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ERROR_DENIED);
        }

        if (order.getStatus() != OrderStatus.PENDING) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Seules les commandes en attente peuvent être annulées");
        }

        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    public void deleteOrder(String userId, String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ERROR_NOT_FOUND));

        if (!order.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ERROR_DENIED);
        }

        if (order.getStatus() != OrderStatus.DELIVERED && order.getStatus() != OrderStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Impossible de supprimer une commande active");
        }

        orderRepository.deleteById(orderId);
    }

    public Order updateOrderStatus(String sellerId, String orderId, OrderStatus newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ERROR_NOT_FOUND));

        boolean isSellerOfOrder = order.getItems().stream()
                .anyMatch(item -> item.getSellerId().equals(sellerId));

        if (!isSellerOfOrder) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ERROR_DENIED);
        }

        if (order.getStatus() == OrderStatus.PENDING && newStatus == OrderStatus.PAID) {
            order.getItems().forEach(item -> {
                try {
                    orderProducer.sendStockUpdate(item.getProductId(), item.getQuantity());
                } catch (Exception e) {
                    log.warn("Échec de la mise à jour du stock pour le produit {} (commande {})",
                            item.getProductId(), orderId, e);
                }
            });
        }

        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());
        return orderRepository.save(order);
    }

    @Transactional
    public Order redoOrder(String userId, String orderId, String token) {
        Order oldOrder = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, ERROR_NOT_FOUND));

        if (!oldOrder.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, ERROR_DENIED);
        }

        Order newOrder = Order.builder()
                .userId(userId)
                .items(oldOrder.getItems())
                .totalAmount(oldOrder.getTotalAmount())
                .status(OrderStatus.PENDING)
                .paymentMethod(oldOrder.getPaymentMethod())
                .shippingAddress(oldOrder.getShippingAddress())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return orderRepository.save(newOrder);
    }

    @Transactional
    public Order checkout(String userId, CheckoutRequest request, String token) {
        CartDTO cart = fetchCart(token);

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le panier est vide");
        }

        BigDecimal totalAmount = cart.getItems().stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<OrderItem> orderItems = cart.getItems().stream()
                .map(item -> OrderItem.builder()
                .productId(item.getProductId())
                .productName(item.getProductName())
                .priceAtPurchase(item.getPrice())
                .quantity(item.getQuantity())
                .sellerId(item.getSellerId())
                .build())
                .toList();

        Order order = Order.builder()
                .userId(userId)
                .items(orderItems)
                .totalAmount(totalAmount)
                .status(OrderStatus.PENDING)
                .paymentMethod(request.getPaymentMethod())
                .shippingAddress(request.getShippingAddress())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Order savedOrder = orderRepository.save(order);
        try {
            clearCart(token);
        } catch (org.springframework.web.reactive.function.client.WebClientException e) {
            log.warn("Commande {} créée mais échec du vidage du panier", savedOrder.getId(), e);
        }
        return savedOrder;
    }

    private CartDTO fetchCart(String token) {
        return webClientBuilder.build()
                .get()
                .uri("http://cart-service:8085/api/carts")
                .header("Authorization", token)
                .retrieve()
                .bodyToMono(CartDTO.class)
                .block();
    }

    private void clearCart(String token) {
        webClientBuilder.build()
                .delete()
                .uri("http://cart-service:8085/api/carts/clear")
                .header("Authorization", token)
                .retrieve()
                .toBodilessEntity()
                .block();
    }
}

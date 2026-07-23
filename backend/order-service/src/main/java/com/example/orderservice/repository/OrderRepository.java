package com.example.orderservice.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.example.orderservice.model.Order;

public interface OrderRepository extends MongoRepository<Order, String> {
}

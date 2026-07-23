package com.example.productservice.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import com.example.productservice.model.Product;

public interface ProductRepository extends MongoRepository<Product, String> {

    List<Product> findByUserId(String userId);

    @Query("{ $and: [ "
            + "{ $or: [ { 'name': { $regex: ?0, $options: 'i' } }, { 'description': { $regex: ?0, $options: 'i' } } ] }, "
            + "{ 'price': { $gte: ?1, $lte: ?2 } } "
            + "] }")
    Page<Product> searchAndFilter(String keyword, double minPrice, double maxPrice, Pageable pageable);
}

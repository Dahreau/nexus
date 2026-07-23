package com.example.productservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

@Document(collection = "products")
public class Product {
    @Id
    private String id;
    private String name;
    private String description;
    private Double price;
    private Integer quantity;
    private String userId; // seller id
    private String sellerName; // denormalized for product listings
    private List<String> imageIds; // references to Media documents

    public Product() {}

    // getters/setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getSellerName() { return sellerName; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public List<String> getImageIds() { return imageIds; }
    public void setImageIds(List<String> imageIds) { this.imageIds = imageIds; }
}
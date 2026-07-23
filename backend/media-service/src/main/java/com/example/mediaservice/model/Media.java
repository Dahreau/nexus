package com.example.mediaservice.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "media")
public class Media {
    @Id
    private String id;
    private String imagePath;
    private String productId;

    public Media() {}

    public Media(String imagePath, String productId) {
        this.imagePath = imagePath;
        this.productId = productId;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getImagePath() { return imagePath; }
    public void setImagePath(String imagePath) { this.imagePath = imagePath; }
    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
}
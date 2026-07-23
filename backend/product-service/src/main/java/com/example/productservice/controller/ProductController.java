package com.example.productservice.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.productservice.dto.ProductDTO;
import com.example.productservice.model.Product;
import com.example.productservice.repository.ProductRepository;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository repo;

    private static final String ROLE_SELLER = "ROLE_SELLER";
    private static final String ERROR_KEY = "error";

    public ProductController(ProductRepository repo) {
        this.repo = repo;
    }

    @ExceptionHandler(ControllerException.class)
    public ResponseEntity<Object> handleControllerException(ControllerException ex) {
        return ex.getResponse();
    }

    @GetMapping
    public List<Product> listAll() {
        return repo.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getOne(@PathVariable String id) {
        return repo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    // create product - only seller
    @PostMapping
    public ResponseEntity<Object> create(@RequestBody ProductDTO dto) {
        String userId = validateSeller("Only sellers can create products");
        validatePriceAndQuantity(dto);
        Product p = new Product();
        p.setName(dto.getName());
        p.setDescription(dto.getDescription());
        p.setPrice(dto.getPrice());
        p.setQuantity(dto.getQuantity());
        p.setImageIds(dto.getImageIds());
        p.setUserId(userId);
        p.setSellerName(getAuthenticatedName());
        Product saved = repo.save(p);
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable String id, @RequestBody ProductDTO dto) {
        String userId = validateSeller("Only sellers can update products");
        validatePriceAndQuantity(dto);
        Product existing = validateOwnership(id, userId, "Cannot modify another seller's product");
        existing.setName(dto.getName());
        existing.setDescription(dto.getDescription());
        existing.setPrice(dto.getPrice());
        existing.setQuantity(dto.getQuantity());
        existing.setImageIds(dto.getImageIds());
        if (existing.getSellerName() == null) {
            existing.setSellerName(getAuthenticatedName());
        }
        repo.save(existing);
        return ResponseEntity.ok(existing);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable String id) {
        String userId = validateSeller("Only sellers can delete products");
        validateOwnership(id, userId, "Cannot delete another seller's product");
        repo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // Internal: order-service calls this to decrement stock after payment (same token convention as /images).
    @PostMapping("/stock-update")
    public ResponseEntity<Object> updateStock(@RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        String internalToken = System.getenv("INTERNAL_TOKEN");
        if (internalToken == null || !internalToken.equals(token)) {
            return ResponseEntity.status(403).body(Map.of(ERROR_KEY, "Forbidden"));
        }
        Object productIdObj = body.get("productId");
        Object quantityObj = body.get("quantity");
        if (!(productIdObj instanceof String productId) || !(quantityObj instanceof Number quantityNum)) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "productId et quantity requis"));
        }
        var opt = repo.findById(productId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Product product = opt.get();
        int current = product.getQuantity() != null ? product.getQuantity() : 0;
        product.setQuantity(Math.max(0, current - quantityNum.intValue()));
        repo.save(product);
        return ResponseEntity.ok(product);
    }

    @PostMapping("/{id}/images")
    public ResponseEntity<Object> addImage(@PathVariable String id, @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        String internalToken = System.getenv("INTERNAL_TOKEN");
        if (internalToken == null || !internalToken.equals(token)) {
            return ResponseEntity.status(403).body(Map.of(ERROR_KEY, "Forbidden"));
        }
        var opt = repo.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Product product = opt.get();
        String mediaId = body.get("mediaId");
        if (mediaId == null || mediaId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "mediaId required"));
        }
        List<String> imgs = product.getImageIds();
        if (imgs == null) {
            imgs = new ArrayList<>();
        }
        imgs.add(mediaId);
        product.setImageIds(imgs);
        repo.save(product);
        return ResponseEntity.ok(product);
    }

    private void validatePriceAndQuantity(ProductDTO dto) {
        if (dto.getPrice() <= 0) {
            throw new ControllerException(ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "Le prix doit être supérieur à 0")));
        }
        if (dto.getQuantity() < 0) {
            throw new ControllerException(ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "La quantité ne peut pas être négative")));
        }
    }

    // Falls back to the user id if the token predates the "name" claim.
    private String getAuthenticatedName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return null;
        }
        Object credentials = auth.getCredentials();
        if (credentials instanceof String name && !name.isBlank()) {
            return name;
        }
        return auth.getName();
    }

    private String validateSeller(String errorMsg) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities().stream().noneMatch(a -> a.getAuthority().equalsIgnoreCase(ROLE_SELLER))) {
            throw new ControllerException(ResponseEntity.status(403).body(Map.of(ERROR_KEY, errorMsg)));
        }
        return auth.getName();
    }

    private Product validateOwnership(String id, String userId, String errorMsg) {
        var opt = repo.findById(id);
        if (opt.isEmpty()) {
            throw new ControllerException(ResponseEntity.notFound().build());
        }
        Product existing = opt.get();
        if (!userId.equals(existing.getUserId())) {
            throw new ControllerException(ResponseEntity.status(403).body(Map.of(ERROR_KEY, errorMsg)));
        }
        return existing;
    }

    public static class ControllerException extends RuntimeException {

        private final transient ResponseEntity<Object> response;

        public ControllerException(ResponseEntity<Object> response) {
            this.response = response;
        }

        public ResponseEntity<Object> getResponse() {
            return response;
        }
    }

    @GetMapping("/search")
    public ResponseEntity<Page<Product>> searchProducts(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "0") Double minPrice,
            @RequestParam(required = false, defaultValue = "1000000") Double maxPrice,
            @RequestParam(required = false, defaultValue = "0") Integer page,
            @RequestParam(required = false, defaultValue = "10") Integer size
    ) {

        if (minPrice > maxPrice) {
            throw new ControllerException(ResponseEntity.badRequest().body(Map.of(ERROR_KEY, "minPrice cannot be greater than maxPrice")));
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Product> results = repo.searchAndFilter(keyword, minPrice, maxPrice, pageable);
        return ResponseEntity.ok(results);
    }
}

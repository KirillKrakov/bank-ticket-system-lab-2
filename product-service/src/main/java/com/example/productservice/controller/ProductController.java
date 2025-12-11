package com.example.productservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    @GetMapping
    public ResponseEntity<?> listProducts() {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm Product Service!",
                "endpoint", "GET /api/v1/products",
                "service", "product-service"
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getProduct(@PathVariable String id) {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm Product Service!",
                "endpoint", "GET /api/v1/products/" + id,
                "service", "product-service",
                "productId", id
        ));
    }

    @PostMapping
    public ResponseEntity<?> createProduct() {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm Product Service!",
                "endpoint", "POST /api/v1/products",
                "service", "product-service"
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable String id) {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm Product Service!",
                "endpoint", "PUT /api/v1/products/" + id,
                "service", "product-service",
                "productId", id
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable String id) {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm Product Service!",
                "endpoint", "DELETE /api/v1/products/" + id,
                "service", "product-service",
                "productId", id
        ));
    }
}
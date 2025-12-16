package com.example.applicationservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "product-service",
        fallback = ProductServiceClientFallback.class
)
public interface ProductServiceClient {

    @GetMapping("/api/v1/products/{productId}/exists")
    boolean productExists(@PathVariable("productId") UUID productId);
}
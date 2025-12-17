package com.example.applicationservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@FeignClient(
        name = "product-service",
        fallback = ProductServiceClientFallback.class
)
public interface ProductServiceClient {

    @GetMapping("/api/v1/products/{id}/exists")
    Boolean productExists(@PathVariable("id") UUID id);

    @GetMapping("/api/v1/products/{id}")
    Map<String, Object> getProductById(@PathVariable("id") UUID id);
}
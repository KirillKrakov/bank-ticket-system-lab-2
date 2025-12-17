package com.example.applicationservice.feign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class ProductServiceClientFallbackFactory {

    private static final Logger log = LoggerFactory.getLogger(ProductServiceClientFallbackFactory.class);
    private final ReactiveCircuitBreaker circuitBreaker;

    public ProductServiceClientFallbackFactory(ReactiveCircuitBreakerFactory circuitBreakerFactory) {
        this.circuitBreaker = circuitBreakerFactory.create("product-service");
    }

    public ProductServiceClient create(Throwable throwable) {
        log.warn("Fallback triggered for product-service: {}", throwable.getMessage());
        return new ProductServiceClient() {
            @Override
            public Mono<Boolean> productExists(UUID id) {
                log.warn("Fallback: Returning false for product exists check: {}", id);
                return Mono.just(false);
            }

            @Override
            public Mono<Map<String, Object>> getProductById(UUID id) {
                log.warn("Fallback: Returning empty product data for: {}", id);
                Map<String, Object> emptyProduct = new HashMap<>();
                emptyProduct.put("id", id);
                emptyProduct.put("name", "Unknown Product");
                return Mono.just(emptyProduct);
            }
        };
    }
}
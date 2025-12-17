package com.example.applicationservice.feign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

public class ProductServiceClientFallback implements ProductServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClientFallback.class);

    @Override
    public Mono<Boolean> productExists(UUID id) {
        log.warn("Fallback: Cannot check if product exists: {}", id);
        return Mono.just(false);
    }

    @Override
    public Mono<Map<String, Object>> getProductById(UUID id) {
        log.warn("Fallback: Cannot get user: {}", id);
        return Mono.empty();
    }
}

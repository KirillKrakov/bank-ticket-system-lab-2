package com.example.applicationservice.feign;

import com.example.applicationservice.model.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Component
public class ProductServiceClientFallback implements ProductServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClientFallback.class);

    @Override
    public Mono<Boolean> productExists(UUID id) {
        log.warn("Fallback: Cannot check if product exists: {}", id);
        return Mono.just(false);
    }

    @Override
    public Mono<Map<String, Object>> getProductById(UUID id) {
        log.warn("Fallback: Cannot get product: {}", id);
        return Mono.empty();
    }
}
package com.example.applicationservice.feign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;

public class ProductServiceClientFallback implements ProductServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClientFallback.class);

    @Override
    public Boolean productExists(UUID id) {
        log.warn("Fallback: Cannot check if product exists: {}", id);
        return false;
    }

    @Override
    public Map<String, Object> getProductById(UUID id) {
        log.warn("Fallback: Cannot get user: {}", id);
        return Collections.emptyMap();
    }
}

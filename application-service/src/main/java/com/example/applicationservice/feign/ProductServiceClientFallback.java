package com.example.applicationservice.feign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class ProductServiceClientFallback implements ProductServiceClient {
    private static final Logger logger = LoggerFactory.getLogger(UserServiceClientFallback.class);

    @Override
    public boolean productExists(UUID productId) {
        logger.warn("Fallback: product-service unavailable, assuming product {} exists", productId);
        return true;
    }
}

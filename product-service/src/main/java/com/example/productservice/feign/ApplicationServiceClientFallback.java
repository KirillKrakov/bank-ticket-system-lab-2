package com.example.productservice.feign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ApplicationServiceClientFallback implements ApplicationServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationServiceClientFallback.class);

    @Override
    public void deleteApplicationsByProductId(UUID productId) {
        logger.warn("Fallback: application-service unavailable, cannot delete applications for product {}", productId);
        // Не делаем ничего - eventual consistency
    }
}
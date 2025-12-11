package com.example.productservice.feign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AssignmentServiceClientFallback implements AssignmentServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(AssignmentServiceClientFallback.class);

    @Override
    public boolean existsByUserAndProductAndRole(UUID userId, UUID productId, String role) {
        logger.warn("Fallback: assignment-service unavailable, assuming no assignment exists");
        return false; // Безопаснее предположить, что связи нет
    }
}
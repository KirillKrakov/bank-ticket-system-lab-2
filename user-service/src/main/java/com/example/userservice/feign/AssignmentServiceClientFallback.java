package com.example.userservice.feign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AssignmentServiceClientFallback implements AssignmentServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(AssignmentServiceClientFallback.class);

    @Override
    public Void deleteAssignmentsByUserId(UUID userId) {
        logger.warn("Fallback triggered: Cannot delete applications for user {}. " +
                "Application service might be unavailable.", userId);
        return null;
    }
}
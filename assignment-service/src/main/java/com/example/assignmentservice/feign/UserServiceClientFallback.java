package com.example.assignmentservice.feign;

import com.example.assignmentservice.model.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserServiceClientFallback implements UserServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceClientFallback.class);

    @Override
    public Boolean userExists(UUID userId) {
        logger.warn("Fallback: user-service unavailable, assuming user {} exists", userId);
        return true;
    }

    @Override
    public UserRole getUserRole(UUID userId) {
        logger.warn("Fallback: user-service unavailable, returning default role CLIENT for user {}", userId);
        return UserRole.ROLE_CLIENT;
    }
}
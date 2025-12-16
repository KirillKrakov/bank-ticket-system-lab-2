package com.example.productservice.feign;

import com.example.productservice.model.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class UserServiceClientFallback implements UserServiceClient {

    private static final Logger logger = LoggerFactory.getLogger(UserServiceClientFallback.class);

    @Override
    public boolean userExists(UUID userId) {
        logger.warn("Fallback: user-service unavailable, assuming user {} exists", userId);
        return true; // Безопаснее предположить, что пользователь существует
    }

    @Override
    public String getUserRole(UUID userId) {
        logger.warn("Fallback: user-service unavailable, returning default role CLIENT for user {}", userId);
        return "ROLE_CLIENT";
    }
}
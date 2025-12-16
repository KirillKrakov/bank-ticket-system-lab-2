package com.example.applicationservice.feign;

import com.example.applicationservice.model.enums.UserRole;
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
    public UserRole getUserRole(UUID userId) {
        logger.warn("Fallback: user-service unavailable, returning default role for user {}", userId);
        return UserRole.ROLE_CLIENT; // Возвращаем роль по умолчанию
    }
}
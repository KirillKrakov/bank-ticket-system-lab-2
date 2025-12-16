package com.example.applicationservice.feign;

import com.example.applicationservice.model.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

@Component
public class UserServiceClientFallback implements UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClientFallback.class);

    @Override
    public Mono<Boolean> userExists(UUID id) {
        log.warn("Fallback: Cannot check if user exists: {}", id);
        return Mono.just(false);
    }

    @Override
    public Mono<Map<String, Object>> getUserById(UUID id) {
        log.warn("Fallback: Cannot get user: {}", id);
        return Mono.empty();
    }

    @Override
    public Mono<UserRole> getUserRole(UUID id) {
        log.warn("Fallback: Cannot get user role: {}", id);
        return Mono.just(UserRole.ROLE_CLIENT);
    }
}
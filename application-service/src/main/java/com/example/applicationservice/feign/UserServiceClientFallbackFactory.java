package com.example.applicationservice.feign;

import com.example.applicationservice.model.enums.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class UserServiceClientFallbackFactory {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClientFallbackFactory.class);
    private final ReactiveCircuitBreaker circuitBreaker;

    public UserServiceClientFallbackFactory(ReactiveCircuitBreakerFactory circuitBreakerFactory) {
        this.circuitBreaker = circuitBreakerFactory.create("user-service");
    }

    public UserServiceClient create(Throwable throwable) {
        log.warn("Fallback triggered for user-service: {}", throwable.getMessage());
        return new UserServiceClient() {
            @Override
            public Mono<Boolean> userExists(UUID id) {
                log.warn("Fallback: Returning false for user exists check: {}", id);
                return Mono.just(false);
            }

            @Override
            public Mono<UserRole> getUserRole(UUID id) {
                log.warn("Fallback: Returning default role for user: {}", id);
                return Mono.just(UserRole.ROLE_CLIENT);
            }
        };
    }
}

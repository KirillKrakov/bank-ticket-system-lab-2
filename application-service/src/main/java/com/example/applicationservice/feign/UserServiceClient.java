package com.example.applicationservice.feign;

import com.example.applicationservice.model.enums.UserRole;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(
        name = "user-service",
        fallback = UserServiceClientFallback.class
)
public interface UserServiceClient {

    @GetMapping("/api/v1/users/{userId}/exists")
    boolean userExists(@PathVariable("userId") UUID userId);

    @GetMapping("/api/v1/users/{userId}/role")
    UserRole getUserRole(@PathVariable("userId") UUID userId);
}
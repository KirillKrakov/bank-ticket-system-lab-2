package com.example.userservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(
        name = "assignment-service",
        fallback = AssignmentServiceClientFallback.class
)
public interface AssignmentServiceClient {

    @DeleteMapping("/api/v1/assignments/internal/by-user")
    Void deleteAssignmentsByUserId(@RequestParam("userId") UUID userId);
}
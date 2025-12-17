package com.example.userservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;
import reactor.core.publisher.Mono;

@FeignClient(
        name = "application-service",
        fallback = ApplicationServiceClientFallback.class
)
public interface ApplicationServiceClient {

    @DeleteMapping("/api/v1/applications/internal/by-user")
    Void deleteApplicationsByUserId(@RequestParam("userId") String userId);
}
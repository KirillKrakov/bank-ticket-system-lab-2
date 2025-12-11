package com.example.productservice.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.UUID;

@FeignClient(
        name = "application-service",
        fallback = ApplicationServiceClientFallback.class
)
public interface ApplicationServiceClient {

    @DeleteMapping("/api/internal/applications/by-product")
    void deleteApplicationsByProductId(@RequestParam("productId") UUID productId);
}
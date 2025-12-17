package com.example.userservice.feign;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class ApplicationServiceClientFallback implements ApplicationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ApplicationServiceClientFallback.class);

    @Override
    public Void deleteApplicationsByUserId(String userId) {
        log.warn("Fallback triggered: Cannot delete applications for user {}. " +
                "Application service might be unavailable.", userId);
        return null;
    }
}
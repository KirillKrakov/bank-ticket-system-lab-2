package com.example.tagservice.feign;

import com.example.tagservice.dto.ApplicationInfoDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class ApplicationServiceClientFallback implements ApplicationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ApplicationServiceClientFallback.class);

    @Override
    public List<ApplicationInfoDto> getApplicationsByTag(String tagName) {
        log.warn("Fallback: Cannot get applications for tag {}", tagName);
        return Collections.emptyList();
    }
}
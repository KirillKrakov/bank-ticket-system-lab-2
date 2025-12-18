package com.example.applicationservice.config;

import feign.Response;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomErrorDecoder implements ErrorDecoder {
    private static final Logger logger = LoggerFactory.getLogger(CustomErrorDecoder.class);

    @Override
    public Exception decode(String methodKey, Response response) {
        logger.error("Feign client error: methodKey={}, status={}, reason={}",
                methodKey, response.status(), response.reason());
        return new RuntimeException("Feign client error: " + response.status());
    }
}
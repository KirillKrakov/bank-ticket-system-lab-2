package com.example.applicationservice.feign;

import com.example.applicationservice.dto.TagDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
public class TagServiceClientFallbackFactory {

    private static final Logger log = LoggerFactory.getLogger(TagServiceClientFallbackFactory.class);
    private final ReactiveCircuitBreaker circuitBreaker;

    public TagServiceClientFallbackFactory(ReactiveCircuitBreakerFactory circuitBreakerFactory) {
        this.circuitBreaker = circuitBreakerFactory.create("tag-service");
    }

    public TagServiceClient create(Throwable throwable) {
        log.warn("Fallback triggered for tag-service: {}", throwable.getMessage());
        return new TagServiceClient() {
            @Override
            public Mono<List<TagDto>> createOrGetTagsBatch(List<String> tagNames) {
                log.warn("Fallback: Returning empty tags list");
                List<TagDto> emptyTags = new ArrayList<>();
                for (String name : tagNames) {
                    TagDto tagDto = new TagDto();
                    tagDto.setName(name);
                    tagDto.setId(UUID.randomUUID());
                    emptyTags.add(tagDto);
                }
                return Mono.just(emptyTags);
            }

            @Override
            public Mono<Boolean> tagExists(String name) {
                log.warn("Fallback: Returning false for tag exists check: {}", name);
                return Mono.just(false);
            }

            @Override
            public Mono<TagDto> createTag(String name) {
                log.warn("Fallback: Creating dummy tag: {}", name);
                TagDto tagDto = new TagDto();
                tagDto.setId(UUID.randomUUID());
                tagDto.setName(name);
                return Mono.just(tagDto);
            }
        };
    }
}

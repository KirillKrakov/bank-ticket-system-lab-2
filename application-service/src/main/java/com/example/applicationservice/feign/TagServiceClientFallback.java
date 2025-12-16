package com.example.applicationservice.feign;

import com.example.applicationservice.dto.TagDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class TagServiceClientFallback implements TagServiceClient {

    private static final Logger log = LoggerFactory.getLogger(TagServiceClientFallback.class);

    @Override
    public Mono<List<TagDto>> createOrGetTagsBatch(List<String> tagNames) {
        log.warn("Fallback: Cannot create/get tags batch: {}", tagNames);
        // Возвращаем пустой список, приложение будет работать без тегов
        return Mono.just(Collections.emptyList());
    }

    @Override
    public Mono<Boolean> tagExists(String name) {
        log.warn("Fallback: Cannot check if tag exists: {}", name);
        return Mono.just(false);
    }

    @Override
    public Mono<TagDto> createTag(String name) {
        log.warn("Fallback: Cannot create tag: {}", name);
        // Создаём заглушку
        TagDto dto = new TagDto();
        dto.setId(UUID.randomUUID());
        dto.setName(name);
        return Mono.just(dto);
    }
}
package com.example.applicationservice.feign;

import com.example.applicationservice.dto.TagDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@FeignClient(
        name = "tag-service",
        configuration = ReactiveFeignConfiguration.class,
        fallbackFactory = TagServiceClientFallbackFactory.class
)
public interface TagServiceClient {

    @PostMapping("/api/v1/tags/batch")
    Mono<List<TagDto>> createOrGetTagsBatch(@RequestBody List<String> tagNames);

    @GetMapping("/api/v1/tags/{name}/exists")
    Mono<Boolean> tagExists(@PathVariable String name);

    @PostMapping("/api/v1/tags")
    Mono<TagDto> createTag(@RequestBody String name);
}
package com.example.tagservice.controller;

import com.example.tagservice.dto.TagDto;
import com.example.tagservice.service.TagService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/tags")
public class TagController {

    private static final Logger log = LoggerFactory.getLogger(TagController.class);
    private static final int MAX_PAGE_SIZE = 50;
    private final TagService tagService;

    public TagController(TagService tagService) {
        this.tagService = tagService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<TagDto> createTag(
            @Valid @RequestBody String name,
            UriComponentsBuilder uriBuilder) {

        name = name.trim();
        var tag = tagService.createIfNotExists(name);

        TagDto dto = new TagDto();
        dto.setId(tag.getId());
        dto.setName(tag.getName());

        URI location = uriBuilder.path("/api/v1/tags/{name}")
                .buildAndExpand(dto.getName())
                .toUri();

        log.info("Tag created or retrieved: {}", name);
        return ResponseEntity.created(location).body(dto);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<List<TagDto>> createOrGetTagsBatch(
            @Valid @RequestBody List<String> tagNames) {

        if (tagNames == null || tagNames.isEmpty()) {
            return ResponseEntity.ok(List.of());
        }

        List<com.example.tagservice.model.entity.Tag> tags =
                tagService.createOrGetTags(tagNames);

        List<TagDto> dtos = tags.stream()
                .map(tag -> {
                    TagDto dto = new TagDto();
                    dto.setId(tag.getId());
                    dto.setName(tag.getName());
                    return dto;
                })
                .collect(Collectors.toList());

        log.info("Processed batch of {} tags", dtos.size());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping
    public ResponseEntity<List<TagDto>> listTags(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException(
                    String.format("Page size cannot be greater than %d", MAX_PAGE_SIZE));
        }

        Page<TagDto> tagPage = tagService.listAll(page, size);

        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Total-Count", String.valueOf(tagPage.getTotalElements()));

        return ResponseEntity.ok()
                .headers(headers)
                .body(tagPage.getContent());
    }

    @GetMapping("/{name}")
    public ResponseEntity<TagDto> getTagByName(@PathVariable String name) {
        TagDto tag = tagService.getTagByName(name);
        return ResponseEntity.ok(tag);
    }

    @GetMapping("/{name}/exists")
    public ResponseEntity<Boolean> tagExists(@PathVariable String name) {
        boolean exists = tagService.tagExists(name);
        return ResponseEntity.ok(exists);
    }

    // Внутренний endpoint для других сервисов
    @PostMapping("/internal/batch")
    public ResponseEntity<List<TagDto>> getTagsBatch(@RequestBody List<String> names) {
        List<TagDto> tags = tagService.getTagsByNames(names);
        return ResponseEntity.ok(tags);
    }
}
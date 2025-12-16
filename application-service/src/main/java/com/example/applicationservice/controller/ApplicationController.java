package com.example.applicationservice.controller;

import com.example.applicationservice.dto.*;
import com.example.applicationservice.exception.BadRequestException;
import com.example.applicationservice.service.ApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;

@Tag(name = "Applications", description = "API for managing applications")
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private static final Logger log = LoggerFactory.getLogger(ApplicationController.class);
    private static final int MAX_PAGE_SIZE = 50;
    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    // Create: POST "/api/v1/applications" + ApplicationRequest(applicantId, productId, documents, tags)
    @Operation(summary = "Create a new application", description = "Registers a new application: applicantId, productId, documents, tags")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Application created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "404", description = "Applicant or product not found"),
            @ApiResponse(responseCode = "409", description = "Failed to process tags")
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<ApplicationDto>> createApplication(
            @Valid @RequestBody ApplicationRequest request) {

        log.info("Creating new application for applicant: {}, product: {}",
                request.getApplicantId(), request.getProductId());

        return applicationService.createApplication(request)
                .map(dto -> {
                    log.info("Application created successfully: {}", dto.getId());
                    return ResponseEntity.status(HttpStatus.CREATED)
                            .body(dto);
                })
                .onErrorResume(e -> {
                    log.error("Failed to create application: {}", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    // ReadAll: GET "/api/v1/applications?page=0&size=20"
    @Operation(summary = "Read all applications with pagination", description = "Returns a paginated list of applications")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of applications"),
            @ApiResponse(responseCode = "400", description = "Page size too large")
    })
    @GetMapping
    public Mono<ResponseEntity<Flux<ApplicationDto>>> listApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        if (size > MAX_PAGE_SIZE) {
            return Mono.error(new BadRequestException(
                    String.format("Page size cannot be greater than %d", MAX_PAGE_SIZE)));
        }

        log.debug("Listing applications - page: {}, size: {}", page, size);

        return applicationService.count()
                .map(totalCount -> {
                    HttpHeaders headers = new HttpHeaders();
                    headers.add("X-Total-Count", String.valueOf(totalCount));

                    Flux<ApplicationDto> applications = applicationService.findAll(page, size);
                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(applications);
                })
                .onErrorResume(e -> {
                    log.error("Failed to list applications: {}", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    // Read: GET "/api/v1/applications/{id}"
    @Operation(summary = "Read certain application by its ID", description = "Returns data about a single application")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Application data"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    @GetMapping("/{id}")
    public Mono<ResponseEntity<ApplicationDto>> getApplication(@PathVariable UUID id) {
        log.debug("Getting application: {}", id);

        return applicationService.findById(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build())
                .onErrorResume(e -> {
                    log.error("Failed to get application {}: {}", id, e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    // ReadAllByStream: GET "/api/v1/applications/stream?cursor=<base64>&limit=20"
    @Operation(summary = "Read all applications with endless scrolling", description = "Returns endless scrolling of the list of applications by cursor")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of applications"),
            @ApiResponse(responseCode = "400", description = "Invalid cursor or limit too large")
    })
    @GetMapping("/stream")
    public Mono<ResponseEntity<List<ApplicationDto>>> streamApplications(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") int limit) {

        if (limit > MAX_PAGE_SIZE) {
            return Mono.error(new BadRequestException(
                    String.format("Limit cannot be greater than %d", MAX_PAGE_SIZE)));
        }

        log.debug("Streaming applications - cursor: {}, limit: {}", cursor, limit);

        return applicationService.streamWithNextCursor(cursor, limit)
                .map(page -> {
                    HttpHeaders headers = new HttpHeaders();
                    if (page.nextCursor() != null) {
                        headers.add("X-Next-Cursor", page.nextCursor());
                    }
                    return ResponseEntity.ok()
                            .headers(headers)
                            .body(page.items());
                })
                .onErrorResume(e -> {
                    log.error("Failed to stream applications: {}", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    // Update(addTags): PUT "/api/v1/applications/{id}/tags?actorId={actorId}"
    @Operation(summary = "Add tags to application", description = "Add tags to application if actor has sufficient rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Tags added successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "Application not found"),
            @ApiResponse(responseCode = "409", description = "Failed to process tags")
    })
    @PutMapping("/{id}/tags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<ResponseEntity<Void>> addTags(
            @PathVariable UUID id,
            @RequestBody List<String> tags,
            @RequestParam("actorId") UUID actorId) {

        log.info("Adding tags to application {} by actor {}", id, actorId);

        return applicationService.attachTags(id, tags, actorId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(e -> {
                    log.error("Failed to add tags to application {}: {}", id, e.getMessage());

                    if (e instanceof com.example.applicationservice.exception.ForbiddenException) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    } else if (e instanceof com.example.applicationservice.exception.NotFoundException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    } else if (e instanceof com.example.applicationservice.exception.ConflictException) {
                        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build());
                    }

                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    // Delete(deleteTags): DELETE "/api/v1/applications/{id}/tags?actorId={actorId}"
    @Operation(summary = "Remove tags from application", description = "Remove tags from application if actor has sufficient rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Tags removed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    @DeleteMapping("/{id}/tags")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<ResponseEntity<Void>> removeTags(
            @PathVariable UUID id,
            @RequestBody List<String> tags,
            @RequestParam("actorId") UUID actorId) {

        log.info("Removing tags from application {} by actor {}", id, actorId);

        return applicationService.removeTags(id, tags, actorId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(e -> {
                    log.error("Failed to remove tags from application {}: {}", id, e.getMessage());

                    if (e instanceof com.example.applicationservice.exception.ForbiddenException) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    } else if (e instanceof com.example.applicationservice.exception.NotFoundException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }

                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    // Update(changeStatus): PUT "/api/v1/applications/{id}/status?actorId={actorId}"
    @Operation(summary = "Change application status", description = "Change application status if actor has sufficient rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Status changed successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid status"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "Application not found"),
            @ApiResponse(responseCode = "409", description = "Conflict (e.g., manager changing own application)")
    })
    @PutMapping("/{id}/status")
    public Mono<ResponseEntity<ApplicationDto>> changeStatus(
            @PathVariable UUID id,
            @RequestBody String status,
            @RequestParam("actorId") UUID actorId) {

        log.info("Changing status of application {} to '{}' by actor {}", id, status, actorId);

        return applicationService.changeStatus(id, status, actorId)
                .map(ResponseEntity::ok)
                .onErrorResume(e -> {
                    log.error("Failed to change status of application {}: {}", id, e.getMessage());

                    if (e instanceof com.example.applicationservice.exception.ForbiddenException) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    } else if (e instanceof com.example.applicationservice.exception.NotFoundException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    } else if (e instanceof com.example.applicationservice.exception.ConflictException) {
                        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).build());
                    }

                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    // Delete: DELETE "/api/v1/applications/{id}?actorId={actorId}"
    @Operation(summary = "Delete application", description = "Delete application if actor has sufficient rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Application deleted successfully"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<ResponseEntity<Void>> deleteApplication(
            @PathVariable UUID id,
            @RequestParam("actorId") UUID actorId) {

        log.info("Deleting application {} by actor {}", id, actorId);

        return applicationService.deleteApplication(id, actorId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(e -> {
                    log.error("Failed to delete application {}: {}", id, e.getMessage());

                    if (e instanceof com.example.applicationservice.exception.ForbiddenException) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    } else if (e instanceof com.example.applicationservice.exception.NotFoundException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }

                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    // ReadHistory: GET "/api/v1/applications/{id}/history?actorId={actorId}"
    @Operation(summary = "Get application history", description = "Get application change history if actor has sufficient rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Application history"),
            @ApiResponse(responseCode = "403", description = "Insufficient permissions"),
            @ApiResponse(responseCode = "404", description = "Application not found")
    })
    @GetMapping("/{id}/history")
    public Mono<ResponseEntity<Flux<ApplicationHistoryDto>>> getApplicationHistory(
            @PathVariable UUID id,
            @RequestParam("actorId") UUID actorId) {

        log.debug("Getting history for application {} by actor {}", id, actorId);

        return applicationService.listHistory(id, actorId)
                .collectList()
                .map(history -> ResponseEntity.ok(Flux.fromIterable(history)))
                .onErrorResume(e -> {
                    log.error("Failed to get history for application {}: {}", id, e.getMessage());

                    if (e instanceof com.example.applicationservice.exception.ForbiddenException) {
                        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                    } else if (e instanceof com.example.applicationservice.exception.NotFoundException) {
                        return Mono.just(ResponseEntity.notFound().build());
                    }

                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    // Internal endpoint для user-service
    @Operation(summary = "Delete applications by user ID (internal)",
            description = "Delete all applications for a user (internal use only)")
    @DeleteMapping("/internal/by-user")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<ResponseEntity<Void>> deleteApplicationsByUserId(
            @RequestParam("userId") UUID userId) {

        log.info("Deleting all applications for user {} (internal call)", userId);

        return applicationService.deleteApplicationsByUserId(userId)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(e -> {
                    log.error("Failed to delete applications for user {}: {}", userId, e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().build());
                });
    }

    // Health check endpoint
    @GetMapping("/health")
    @ResponseStatus(HttpStatus.OK)
    public Mono<ResponseEntity<String>> health() {
        return Mono.just(ResponseEntity.ok("Application service is healthy"));
    }
}
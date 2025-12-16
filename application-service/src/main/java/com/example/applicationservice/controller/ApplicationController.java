package com.example.applicationservice.controller;

import com.example.applicationservice.dto.ApplicationDto;
import com.example.applicationservice.dto.ApplicationHistoryDto;
import com.example.applicationservice.dto.ApplicationRequest;
import com.example.applicationservice.exception.BadRequestException;
import com.example.applicationservice.service.ApplicationService;
import com.example.applicationservice.util.ApplicationPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@Tag(name = "Applications", description = "API for managing applications")
@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    private static final int MAX_PAGE_SIZE = 50;
    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    // Create: POST "/api/v1/applications" + ApplicationRequest(applicantId,productId,documents(fileName,contentType,storagePath)) (Body)
    @Operation(summary = "Create a new application", description = "Registers a new application: applicantId, productId, documents " +
            "(fileName, contentType, storagePath), tags ([name,...])")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Application created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body"),
            @ApiResponse(responseCode = "404", description = "Applicant or product with their ID are not found")
    })
    @PostMapping
    public ResponseEntity<ApplicationDto> create(@Valid @RequestBody ApplicationRequest req, UriComponentsBuilder uriBuilder) {
        ApplicationDto dto = applicationService.createApplication(req);
        URI location = uriBuilder.path("/api/v1/applications/{id}").buildAndExpand(dto.getId()).toUri();
        return ResponseEntity.created(location).body(dto);
    }

    // ReadAll: GET "/api/v1/applications?page=0&size=20"
    @Operation(summary = "Read all applications with pagination", description = "Returns a paginated list of applications")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of applications"),
            @ApiResponse(responseCode = "400", description = "Page size too large")
    })
    @GetMapping
    public ResponseEntity<List<ApplicationDto>> list(@RequestParam(defaultValue = "0") int page,
                                                     @RequestParam(defaultValue = "20") int size,
                                                     HttpServletResponse response) {
        if (size > MAX_PAGE_SIZE) {
            throw new BadRequestException("size cannot be greater than " + MAX_PAGE_SIZE);
        }
        Page<ApplicationDto> p = applicationService.list(page, size);
        response.setHeader("X-Total-Count", String.valueOf(p.getTotalElements()));
        return ResponseEntity.ok(p.getContent());
    }

    // Read: GET “/api/v1/applications/{id}”
    @Operation(summary = "Read certain application by its ID", description = "Returns data about a single application: " +
            "ID, applicantId, productId, status, createdAt, documents (fileName, contentType, storagePath), tags ([name,...])")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Data about a single user"),
            @ApiResponse(responseCode = "404", description = "Product with this ID is not found")
    })
    @GetMapping("/{id}")
    public ResponseEntity<ApplicationDto> get(@PathVariable UUID id) {
        ApplicationDto dto = applicationService.get(id);
        return dto == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(dto);
    }

    // ReadAllByStream: GET “/api/v1/applications/stream?cursor=<base64>&limit=20
    @Operation(summary = "Read all applications with endless scrolling", description = "Returns endless scrolling of the list of applications by cursor")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of applications"),
            @ApiResponse(responseCode = "400", description = "Page size too large")
    })
    @GetMapping("/stream")
    public ResponseEntity<List<ApplicationDto>> stream(@RequestParam(required = false) String cursor,
                                                       @RequestParam(required = false, defaultValue = "20") int limit) {
        if (limit > 50) {
            throw new BadRequestException("limit cannot be greater than 50");
        }

        ApplicationPage page = applicationService.streamWithNextCursor(cursor, limit);

        HttpHeaders headers = new HttpHeaders();
        if (page.nextCursor() != null) {
            headers.add("X-Next-Cursor", page.nextCursor());
        }
        return ResponseEntity.ok().headers(headers).body(page.items());
    }

    // Update(addTags): PUT “/api/v1/applications/{id}/tags?actorId={applicantOrManagerId}” + List<String> tags (Body)
    @Operation(summary = "Update the list of tags for a specific application found by ID", description = "Add some tags of single application " +
            "if the actor has sufficient rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Tags added successfully"),
            @ApiResponse(responseCode = "401", description = "Actor is unauthorized (actorId is null"),
            @ApiResponse(responseCode = "403", description = "Insufficient level of actor's rights (not APPLICANT, ADMIN or MANAGER)"),
            @ApiResponse(responseCode = "404", description = "Application or actor with their ID are not found")
    })
    @PutMapping("/{id}/tags")
    public ResponseEntity<Void> addTags(@PathVariable UUID id, @RequestBody List<String> tags, @RequestParam("actorId") UUID actorId) {
        applicationService.attachTags(id, tags, actorId);
        return ResponseEntity.noContent().build();
    }

    // Delete(deleteTags): DELETE “/api/v1/applications/{id}/tags?actorId={applicantOrManagerId}” + List<String> tags (Body)
    @Operation(summary = "Delete something from the list of tags for a specific application", description = "Remove some tags of single application " +
            "if the actor has sufficient rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Tags removed successfully"),
            @ApiResponse(responseCode = "401", description = "Actor is unauthorized (actorId is null"),
            @ApiResponse(responseCode = "403", description = "Insufficient level of actor's rights (not APPLICANT, ADMIN or MANAGER)"),
            @ApiResponse(responseCode = "404", description = "Application or actor with their ID are not found")
    })
    @DeleteMapping("/{id}/tags")
    public ResponseEntity<Void> removeTags(@PathVariable UUID id, @RequestBody List<String> tags, @RequestParam("actorId") UUID actorId) {
        applicationService.removeTags(id, tags, actorId);
        return ResponseEntity.noContent().build();
    }

    // Update(changeStatus): PUT “/api/v1/applications/{id}/status?actorId={actorId}” + ApplicationStatus
    @Operation(summary = "Update status for a specific application found by ID", description = "Update status of single application and " +
            "return all its data if the actor has sufficient rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Application status updated successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request body: status must be not empty"),
            @ApiResponse(responseCode = "401", description = "Actor is unauthorized (actorId is null"),
            @ApiResponse(responseCode = "403", description = "Insufficient level of actor's rights (not ADMIN or MANAGER)"),
            @ApiResponse(responseCode = "404", description = "Application or actor with their ID are not found"),
            @ApiResponse(responseCode = "409", description = "Status must be correct (DRAFT, SUBMITTED, IN_REVIEW, APPROVED, or REJECTED) " +
                    "and managers cannot update the status of their own application")
    })
    @PutMapping("/{id}/status")
    public ResponseEntity<ApplicationDto> changeStatus(
            @PathVariable("id") UUID id, @RequestBody String status, @RequestParam("actorId") UUID actorId) {
        ApplicationDto updated = applicationService.changeStatus(id, status, actorId);
        return ResponseEntity.ok(updated);
    }

    // Delete: DELETE “/api/v1/applications/{id}?actorId={actorId}”
    @Operation(summary = "Delete a specific application found by ID", description = "Deletes one specific application from the database " +
            "if the actor has sufficient rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Application deleted successfully"),
            @ApiResponse(responseCode = "401", description = "Actor is unauthorized (actorId is null"),
            @ApiResponse(responseCode = "403", description = "Insufficient level of actor's rights to delete user (not ADMIN)"),
            @ApiResponse(responseCode = "404", description = "Application or actor with their ID are not found"),
            @ApiResponse(responseCode = "409", description = "An error occurred while deletion of application")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteApplication(@PathVariable("id") UUID id,
                                                  @RequestParam("actorId") UUID actorId) {
        applicationService.deleteApplication(id, actorId);
        return ResponseEntity.noContent().build();
    }

    // ReadHistory: GET “/api/v1/applications/{id}/history?actorId={actorId}”
    @Operation(summary = "Read list of specific application history", description = "Returns list of history of specific application changes " +
            "if the actor has sufficient rights")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "List of application changes history"),
            @ApiResponse(responseCode = "401", description = "Actor is unauthorized (actorId is null"),
            @ApiResponse(responseCode = "403", description = "Insufficient level of actor's rights to delete user (not APPLICANT, ADMIN or MANAGER)"),
            @ApiResponse(responseCode = "404", description = "Application or actor with their ID are not found"),
    })
    @GetMapping("/{id}/history")
    public ResponseEntity<List<ApplicationHistoryDto>> getHistory(@PathVariable("id") UUID id,
                                                                  @RequestParam("actorId") UUID actorId) {
        List<ApplicationHistoryDto> list = applicationService.listHistory(id, actorId);
        return ResponseEntity.ok(list);
    }
}

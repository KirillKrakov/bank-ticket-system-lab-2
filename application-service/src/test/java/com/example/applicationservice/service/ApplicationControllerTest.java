package com.example.applicationservice.service;

import com.example.applicationservice.controller.ApplicationController;
import com.example.applicationservice.dto.*;
import com.example.applicationservice.exception.*;
import com.example.applicationservice.service.ApplicationService;
import com.example.applicationservice.util.ApplicationPage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ApplicationControllerTest {

    @Mock
    private ApplicationService applicationService;

    @InjectMocks
    private ApplicationController applicationController;

    private ApplicationDto createSampleApplicationDto() {
        ApplicationDto dto = new ApplicationDto();
        dto.setId(UUID.randomUUID());
        dto.setApplicantId(UUID.randomUUID());
        dto.setProductId(UUID.randomUUID());
        dto.setStatus(com.example.applicationservice.model.enums.ApplicationStatus.SUBMITTED);
        dto.setCreatedAt(Instant.now());
        return dto;
    }

    private ApplicationRequest createSampleApplicationRequest() {
        ApplicationRequest request = new ApplicationRequest();
        request.setApplicantId(UUID.randomUUID());
        request.setProductId(UUID.randomUUID());
        return request;
    }

    // -----------------------
    // createApplication tests
    // -----------------------
    @Test
    public void createApplication_success_returnsCreated() {
        ApplicationRequest request = createSampleApplicationRequest();
        ApplicationDto responseDto = createSampleApplicationDto();

        when(applicationService.createApplication(request))
                .thenReturn(Mono.just(responseDto));

        StepVerifier.create(applicationController.createApplication(request))
                .assertNext(response -> {
                    assertEquals(HttpStatus.CREATED, response.getStatusCode());
                    assertNotNull(response.getBody());
                    assertEquals(responseDto.getId(), response.getBody().getId());
                })
                .verifyComplete();
    }

    @Test
    public void createApplication_serviceThrowsBadRequest_returnsBadRequest() {
        ApplicationRequest request = createSampleApplicationRequest();

        when(applicationService.createApplication(request))
                .thenReturn(Mono.error(new BadRequestException("Invalid request")));

        StepVerifier.create(applicationController.createApplication(request))
                .assertNext(response -> assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void createApplication_serviceThrowsNotFound_returnsBadRequest() {
        ApplicationRequest request = createSampleApplicationRequest();

        when(applicationService.createApplication(request))
                .thenReturn(Mono.error(new NotFoundException("Not found")));

        StepVerifier.create(applicationController.createApplication(request))
                .assertNext(response -> assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void createApplication_serviceThrowsGenericException_returnsBadRequest() {
        ApplicationRequest request = createSampleApplicationRequest();

        when(applicationService.createApplication(request))
                .thenReturn(Mono.error(new RuntimeException("Unexpected error")));

        StepVerifier.create(applicationController.createApplication(request))
                .assertNext(response -> assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode()))
                .verifyComplete();
    }

    // -----------------------
    // listApplications tests
    // -----------------------
    @Test
    public void listApplications_validParameters_returnsOkWithHeaders() {
        ApplicationDto dto1 = createSampleApplicationDto();
        ApplicationDto dto2 = createSampleApplicationDto();

        when(applicationService.count()).thenReturn(Mono.just(100L));
        when(applicationService.findAll(0, 20))
                .thenReturn(Flux.just(dto1, dto2));

        StepVerifier.create(applicationController.listApplications(0, 20))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertNotNull(response.getHeaders().get("X-Total-Count"));
                    assertEquals("100", response.getHeaders().getFirst("X-Total-Count"));
                    assertNotNull(response.getBody());
                })
                .verifyComplete();
    }

    @Test
    public void listApplications_countThrowsException_returnsInternalServerError() {
        when(applicationService.count())
                .thenReturn(Mono.error(new RuntimeException("DB error")));

        StepVerifier.create(applicationController.listApplications(0, 20))
                .assertNext(response -> assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode()))
                .verifyComplete();
    }

    // -----------------------
    // getApplication tests
    // -----------------------
    @Test
    public void getApplication_found_returnsOk() {
        UUID appId = UUID.randomUUID();
        ApplicationDto dto = createSampleApplicationDto();
        dto.setId(appId);

        when(applicationService.findById(appId))
                .thenReturn(Mono.just(dto));

        StepVerifier.create(applicationController.getApplication(appId))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertEquals(appId, response.getBody().getId());
                })
                .verifyComplete();
    }

    @Test
    public void getApplication_notFound_returnsNotFound() {
        UUID appId = UUID.randomUUID();

        when(applicationService.findById(appId))
                .thenReturn(Mono.empty());

        StepVerifier.create(applicationController.getApplication(appId))
                .assertNext(response -> assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void getApplication_serviceThrowsException_returnsInternalServerError() {
        UUID appId = UUID.randomUUID();

        when(applicationService.findById(appId))
                .thenReturn(Mono.error(new RuntimeException("DB error")));

        StepVerifier.create(applicationController.getApplication(appId))
                .assertNext(response -> assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode()))
                .verifyComplete();
    }

    // -----------------------
    // streamApplications tests
    // -----------------------
    @Test
    public void streamApplications_success_returnsOkWithCursorHeader() {
        ApplicationDto dto = createSampleApplicationDto();
        ApplicationPage page = new ApplicationPage(List.of(dto), "next-cursor-123");

        when(applicationService.streamWithNextCursor(null, 20))
                .thenReturn(Mono.just(page));

        StepVerifier.create(applicationController.streamApplications(null, 20))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertEquals("next-cursor-123", response.getHeaders().getFirst("X-Next-Cursor"));
                    assertEquals(1, response.getBody().size());
                })
                .verifyComplete();
    }

    @Test
    public void streamApplications_emptyPage_returnsOkWithoutCursor() {
        ApplicationPage page = new ApplicationPage(List.of(), null);

        when(applicationService.streamWithNextCursor(null, 20))
                .thenReturn(Mono.just(page));

        StepVerifier.create(applicationController.streamApplications(null, 20))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertNull(response.getHeaders().getFirst("X-Next-Cursor"));
                    assertTrue(response.getBody().isEmpty());
                })
                .verifyComplete();
    }

    @Test
    public void streamApplications_serviceThrowsBadRequest_returnsBadRequest() {
        when(applicationService.streamWithNextCursor(null, 20))
                .thenReturn(Mono.error(new BadRequestException("Invalid cursor")));

        StepVerifier.create(applicationController.streamApplications(null, 20))
                .assertNext(response -> assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode()))
                .verifyComplete();
    }

    // -----------------------
    // addTags tests
    // -----------------------
    @Test
    public void addTags_success_returnsNoContent() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1", "tag2");

        when(applicationService.attachTags(appId, tags, actorId))
                .thenReturn(Mono.empty());

        StepVerifier.create(applicationController.addTags(appId, tags, actorId))
                .assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void addTags_forbidden_returnsForbidden() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationService.attachTags(appId, tags, actorId))
                .thenReturn(Mono.error(new ForbiddenException("No permission")));

        StepVerifier.create(applicationController.addTags(appId, tags, actorId))
                .assertNext(response -> assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void addTags_notFound_returnsNotFound() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationService.attachTags(appId, tags, actorId))
                .thenReturn(Mono.error(new NotFoundException("Application not found")));

        StepVerifier.create(applicationController.addTags(appId, tags, actorId))
                .assertNext(response -> assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void addTags_conflict_returnsConflict() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationService.attachTags(appId, tags, actorId))
                .thenReturn(Mono.error(new ConflictException("Tag conflict")));

        StepVerifier.create(applicationController.addTags(appId, tags, actorId))
                .assertNext(response -> assertEquals(HttpStatus.CONFLICT, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void addTags_genericError_returnsBadRequest() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationService.attachTags(appId, tags, actorId))
                .thenReturn(Mono.error(new RuntimeException("Unexpected")));

        StepVerifier.create(applicationController.addTags(appId, tags, actorId))
                .assertNext(response -> assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode()))
                .verifyComplete();
    }

    // -----------------------
    // removeTags tests
    // -----------------------
    @Test
    public void removeTags_success_returnsNoContent() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationService.removeTags(appId, tags, actorId))
                .thenReturn(Mono.empty());

        StepVerifier.create(applicationController.removeTags(appId, tags, actorId))
                .assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void removeTags_forbidden_returnsForbidden() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationService.removeTags(appId, tags, actorId))
                .thenReturn(Mono.error(new ForbiddenException("No permission")));

        StepVerifier.create(applicationController.removeTags(appId, tags, actorId))
                .assertNext(response -> assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void removeTags_notFound_returnsNotFound() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of("tag1");

        when(applicationService.removeTags(appId, tags, actorId))
                .thenReturn(Mono.error(new NotFoundException("Not found")));

        StepVerifier.create(applicationController.removeTags(appId, tags, actorId))
                .assertNext(response -> assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode()))
                .verifyComplete();
    }

    // -----------------------
    // changeStatus tests
    // -----------------------
    @Test
    public void changeStatus_success_returnsOk() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String status = "APPROVED";
        ApplicationDto dto = createSampleApplicationDto();

        when(applicationService.changeStatus(appId, status, actorId))
                .thenReturn(Mono.just(dto));

        StepVerifier.create(applicationController.changeStatus(appId, status, actorId))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertEquals(dto, response.getBody());
                })
                .verifyComplete();
    }

    @Test
    public void changeStatus_forbidden_returnsForbidden() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String status = "APPROVED";

        when(applicationService.changeStatus(appId, status, actorId))
                .thenReturn(Mono.error(new ForbiddenException("No permission")));

        StepVerifier.create(applicationController.changeStatus(appId, status, actorId))
                .assertNext(response -> assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void changeStatus_notFound_returnsNotFound() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String status = "APPROVED";

        when(applicationService.changeStatus(appId, status, actorId))
                .thenReturn(Mono.error(new NotFoundException("Not found")));

        StepVerifier.create(applicationController.changeStatus(appId, status, actorId))
                .assertNext(response -> assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void changeStatus_conflict_returnsConflict() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String status = "APPROVED";

        when(applicationService.changeStatus(appId, status, actorId))
                .thenReturn(Mono.error(new ConflictException("Status conflict")));

        StepVerifier.create(applicationController.changeStatus(appId, status, actorId))
                .assertNext(response -> assertEquals(HttpStatus.CONFLICT, response.getStatusCode()))
                .verifyComplete();
    }

    // -----------------------
    // deleteApplication tests
    // -----------------------
    @Test
    public void deleteApplication_success_returnsNoContent() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(applicationService.deleteApplication(appId, actorId))
                .thenReturn(Mono.empty());

        StepVerifier.create(applicationController.deleteApplication(appId, actorId))
                .assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void deleteApplication_forbidden_returnsForbidden() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(applicationService.deleteApplication(appId, actorId))
                .thenReturn(Mono.error(new ForbiddenException("No permission")));

        StepVerifier.create(applicationController.deleteApplication(appId, actorId))
                .assertNext(response -> assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void deleteApplication_notFound_returnsNotFound() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(applicationService.deleteApplication(appId, actorId))
                .thenReturn(Mono.error(new NotFoundException("Not found")));

        StepVerifier.create(applicationController.deleteApplication(appId, actorId))
                .assertNext(response -> assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void deleteApplication_genericError_returnsInternalServerError() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(applicationService.deleteApplication(appId, actorId))
                .thenReturn(Mono.error(new RuntimeException("DB error")));

        StepVerifier.create(applicationController.deleteApplication(appId, actorId))
                .assertNext(response -> assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode()))
                .verifyComplete();
    }

    // -----------------------
    // getApplicationHistory tests
    // -----------------------
    @Test
    public void getApplicationHistory_success_returnsOk() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        ApplicationHistoryDto historyDto = new ApplicationHistoryDto();
        historyDto.setId(UUID.randomUUID());

        when(applicationService.listHistory(appId, actorId))
                .thenReturn(Flux.just(historyDto));

        StepVerifier.create(applicationController.getApplicationHistory(appId, actorId))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertNotNull(response.getBody());
                })
                .verifyComplete();
    }

    @Test
    public void getApplicationHistory_forbidden_returnsForbidden() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(applicationService.listHistory(appId, actorId))
                .thenReturn(Flux.error(new ForbiddenException("No permission")));

        StepVerifier.create(applicationController.getApplicationHistory(appId, actorId))
                .assertNext(response -> assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void getApplicationHistory_notFound_returnsNotFound() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        when(applicationService.listHistory(appId, actorId))
                .thenReturn(Flux.error(new NotFoundException("Not found")));

        StepVerifier.create(applicationController.getApplicationHistory(appId, actorId))
                .assertNext(response -> assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode()))
                .verifyComplete();
    }

    // -----------------------
    // internal endpoints tests
    // -----------------------
    @Test
    public void deleteApplicationsByUserId_success_returnsNoContent() {
        UUID userId = UUID.randomUUID();

        when(applicationService.deleteApplicationsByUserId(userId))
                .thenReturn(Mono.empty());

        StepVerifier.create(applicationController.deleteApplicationsByUserId(userId))
                .assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void deleteApplicationsByUserId_error_returnsInternalServerError() {
        UUID userId = UUID.randomUUID();

        when(applicationService.deleteApplicationsByUserId(userId))
                .thenReturn(Mono.error(new RuntimeException("Error")));

        StepVerifier.create(applicationController.deleteApplicationsByUserId(userId))
                .assertNext(response -> assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void deleteApplicationsByProductId_success_returnsNoContent() {
        UUID productId = UUID.randomUUID();

        when(applicationService.deleteApplicationsByProductId(productId))
                .thenReturn(Mono.empty());

        StepVerifier.create(applicationController.deleteApplicationsByProductId(productId))
                .assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode()))
                .verifyComplete();
    }

    // -----------------------
    // getApplicationsByTag tests
    // -----------------------
    @Test
    public void getApplicationsByTag_success_returnsOk() {
        String tagName = "important";
        ApplicationInfoDto infoDto = new ApplicationInfoDto();
        infoDto.setId(UUID.randomUUID());

        when(applicationService.findApplicationsByTag(tagName))
                .thenReturn(Mono.just(List.of(infoDto)));

        StepVerifier.create(applicationController.getApplicationsByTag(tagName))
                .assertNext(response -> {
                    assertEquals(HttpStatus.OK, response.getStatusCode());
                    assertEquals(1, response.getBody().size());
                })
                .verifyComplete();
    }

    @Test
    public void getApplicationsByTag_badRequest_returnsBadRequest() {
        String tagName = "invalid";

        when(applicationService.findApplicationsByTag(tagName))
                .thenReturn(Mono.error(new BadRequestException("Invalid tag")));

        StepVerifier.create(applicationController.getApplicationsByTag(tagName))
                .assertNext(response -> assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void getApplicationsByTag_genericError_returnsInternalServerError() {
        String tagName = "test";

        when(applicationService.findApplicationsByTag(tagName))
                .thenReturn(Mono.error(new RuntimeException("DB error")));

        StepVerifier.create(applicationController.getApplicationsByTag(tagName))
                .assertNext(response -> assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode()))
                .verifyComplete();
    }

    // -----------------------
    // edge cases tests
    // -----------------------
    @Test
    public void listApplications_negativePage_handlesCorrectly() {
        when(applicationService.count()).thenReturn(Mono.just(0L));
        when(applicationService.findAll(-1, 10))
                .thenReturn(Flux.empty());

        StepVerifier.create(applicationController.listApplications(-1, 10))
                .assertNext(response -> assertEquals(HttpStatus.OK, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void streamApplications_negativeLimit_handledByService() {
        when(applicationService.streamWithNextCursor(null, -1))
                .thenReturn(Mono.error(new BadRequestException("Invalid limit")));

        StepVerifier.create(applicationController.streamApplications(null, -1))
                .assertNext(response -> assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void addTags_emptyTagsList_success() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        List<String> tags = List.of();

        when(applicationService.attachTags(appId, tags, actorId))
                .thenReturn(Mono.empty());

        StepVerifier.create(applicationController.addTags(appId, tags, actorId))
                .assertNext(response -> assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void changeStatus_emptyStatus_handledByService() {
        UUID appId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        String status = "";

        when(applicationService.changeStatus(appId, status, actorId))
                .thenReturn(Mono.error(new ConflictException("Invalid status")));

        StepVerifier.create(applicationController.changeStatus(appId, status, actorId))
                .assertNext(response -> assertEquals(HttpStatus.CONFLICT, response.getStatusCode()))
                .verifyComplete();
    }

    @Test
    public void getApplicationsByTag_emptyTag_handledByService() {
        String tagName = "";

        when(applicationService.findApplicationsByTag(tagName))
                .thenReturn(Mono.error(new BadRequestException("Empty tag")));

        StepVerifier.create(applicationController.getApplicationsByTag(tagName))
                .assertNext(response -> assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode()))
                .verifyComplete();
    }
}

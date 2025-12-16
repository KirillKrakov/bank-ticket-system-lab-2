package com.example.applicationservice.service;


import com.example.applicationservice.dto.*;
import com.example.applicationservice.exception.*;
import com.example.applicationservice.feign.ProductServiceClient;
import com.example.applicationservice.feign.UserServiceClient;
import com.example.applicationservice.model.entity.Application;
import com.example.applicationservice.model.entity.ApplicationHistory;
import com.example.applicationservice.model.entity.Document;
import com.example.applicationservice.model.enums.ApplicationStatus;
import com.example.applicationservice.model.enums.UserRole;
import com.example.applicationservice.repository.ApplicationHistoryRepository;
import com.example.applicationservice.repository.ApplicationRepository;
import com.example.applicationservice.util.ApplicationPage;
import com.example.applicationservice.util.CursorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private static final Logger logger = LoggerFactory.getLogger(ApplicationService.class);

    private final ApplicationRepository applicationRepository;
    private final ApplicationHistoryRepository applicationHistoryRepository;
    private final UserServiceClient userServiceClient;
    private final ProductServiceClient productServiceClient;
    //private final TagService tagService;

    public ApplicationService(ApplicationRepository applicationRepository, ApplicationHistoryRepository applicationHistoryRepository, UserServiceClient userServiceClient, ProductServiceClient productServiceClient
            //, TagService tagService
    ) {
        this.applicationRepository = applicationRepository;
        this.applicationHistoryRepository = applicationHistoryRepository;
        this.userServiceClient = userServiceClient;
        this.productServiceClient = productServiceClient;
        //this.tagService = tagService;
    }

    private UserRole convertToUserRole(String roleString) {
        if (roleString == null) {
            return UserRole.ROLE_CLIENT;
        }

        try {
            return UserRole.valueOf(roleString.toUpperCase());
        } catch (IllegalArgumentException e) {
            logger.warn("Unknown role received: {}, defaulting to ROLE_CLIENT", roleString);
            return UserRole.ROLE_CLIENT;
        }
    }


    @Transactional
    public ApplicationDto createApplication(ApplicationRequest req) {
        if (req == null) throw new BadRequestException("Request is required");
        if ((req.getApplicantId() == null) || (req.getProductId() == null)) {
            throw new BadRequestException("Applicant ID and Product ID must be in request body");
        }

        if (!userServiceClient.userExists(req.getApplicantId())) {
            throw new NotFoundException("Actor not found");
        }

        String roleString = userServiceClient.getUserRole(req.getApplicantId());
        UserRole applicantRole = convertToUserRole(roleString);

        if (!productServiceClient.productExists(req.getProductId())) {
            throw new NotFoundException("Actor not found");
        }

        Application app = new Application();
        UUID applicationId = UUID.randomUUID();
        app.setId(applicationId);
        app.setApplicantId(req.getApplicantId());
        app.setProductId(req.getProductId());
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(Instant.now());

        List<DocumentRequest> docsReq = req.getDocuments() == null ? List.of() : req.getDocuments();
        List<Document> docs = new ArrayList<>();
        for (DocumentRequest dreq : docsReq) {
            Document d = new Document();
            d.setId(UUID.randomUUID());
            d.setFileName(dreq.getFileName());
            d.setContentType(dreq.getContentType());
            d.setStoragePath(dreq.getStoragePath());
            d.setApplication(app);
            docs.add(d);
        }
        app.setDocuments(docs);

        applicationRepository.save(app);

        ApplicationHistory hist = new ApplicationHistory();
        hist.setId(UUID.randomUUID());
        hist.setApplication(app);
        hist.setOldStatus(null);
        hist.setNewStatus(app.getStatus());
        hist.setChangedBy(applicantRole);
        hist.setChangedAt(Instant.now());
        applicationHistoryRepository.save(hist);

        List<String> tagsReq = req.getTags() == null ? List.of() : req.getTags();
        attachTags(applicationId, tagsReq, req.getApplicantId());

        return toDto(app);
    }

    @Transactional(readOnly = true)
    public Page<ApplicationDto> list(int page, int size) {
        Pageable p = PageRequest.of(page, size);
        Page<Application> applications = applicationRepository.findAll(p);
        return applications.map(this::toDto);
    }

    public ApplicationDto get(UUID id) {
        return applicationRepository.findById(id).map(this::toDto).orElse(null);
    }

    private ApplicationDto toDto(Application app) {
        ApplicationDto dto = new ApplicationDto();
        dto.setId(app.getId());
        dto.setApplicantId(app.getApplicantId());
        dto.setProductId(app.getProductId());
        dto.setStatus(app.getStatus());
        dto.setCreatedAt(app.getCreatedAt());
        List<DocumentDto> docs = app.getDocuments().stream().map(d -> {
            DocumentDto dd = new DocumentDto();
            dd.setId(d.getId());
            dd.setFileName(d.getFileName());
            dd.setContentType(d.getContentType());
            dd.setStoragePath(d.getStoragePath());
            return dd;
        }).collect(Collectors.toList());
       // List<String> tagNames = app.getTags() == null ? List.of() :
                //app.getTags().stream().map(Tag::getName).toList();
        dto.setDocuments(docs);
        //dto.setTags(tagNames);
        return dto;
    }

    @Transactional(readOnly = true)
    public ApplicationPage streamWithNextCursor(String cursor, int limit) {
        if (limit <= 0) throw new BadRequestException("limit must be greater than 0");
        int capped = Math.min(limit, 50);

        CursorUtil.Decoded dec = CursorUtil.decodeOrThrow(cursor);
        Instant ts = dec == null ? null : dec.timestamp;
        UUID id = dec == null ? null : dec.id;

        List<Application> apps;
        if (ts == null) {
            // первая страница
            apps = applicationRepository.findFirstPage(capped);
        } else {
            // последующие страницы
            apps = applicationRepository.findByKeyset(ts, id, capped);
        }

        List<ApplicationDto> dtos = apps.stream().map(this::toDto).collect(Collectors.toList());

        String nextCursor = null;
        if (!apps.isEmpty()) {
            Application last = apps.get(apps.size() - 1);
            nextCursor = CursorUtil.encode(last.getCreatedAt(), last.getId());
        }

        return new ApplicationPage(dtos, nextCursor);
    }

    @Transactional
    public void attachTags(UUID applicationId, List<String> tagNames, UUID actorId) {
        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }

        if (!userServiceClient.userExists(actorId)) {
            throw new NotFoundException("Actor not found");
        }

        String roleString = userServiceClient.getUserRole(actorId);
        UserRole currentRole = convertToUserRole(roleString);

        Application app = applicationRepository.findById(applicationId).orElseThrow(() -> new NotFoundException("Application not found"));
        if (!(app.getApplicantId().equals(actorId) || currentRole == UserRole.ROLE_ADMIN
                || currentRole == UserRole.ROLE_MANAGER)) {
            throw new ForbiddenException("You must have the rights of an applicant, manager, or administrator for this request");
        }

//        for (String name : tagNames) {
//            Tag t = tagService.createTag(name);
//            app.getTags().add(t);
//        }
        applicationRepository.save(app);
    }

    public void removeTags(UUID applicationId, List<String> tagNames, UUID actorId) {
        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        if (!userServiceClient.userExists(actorId)) {
            throw new NotFoundException("Actor not found");
        }

        String roleString = userServiceClient.getUserRole(actorId);
        UserRole currentRole = convertToUserRole(roleString);

        Application app = applicationRepository.findById(applicationId).orElseThrow(() -> new NotFoundException("Application not found"));
        if (!(app.getApplicantId().equals(actorId) || currentRole == UserRole.ROLE_ADMIN
                || currentRole == UserRole.ROLE_MANAGER)) {
            throw new ForbiddenException("You must have the rights of an applicant, manager, or administrator for this request");
        }

        //app.getTags().removeIf(tag -> tagNames.contains(tag.getName()));
        applicationRepository.save(app);
    }

    @Transactional
    public ApplicationDto changeStatus(UUID applicationId, String status, UUID actorId) {
        if (status == null) throw new BadRequestException("Status must be not empty");

        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }

        if (!userServiceClient.userExists(actorId)) {
            throw new NotFoundException("Actor not found");
        }
        String roleString = userServiceClient.getUserRole(actorId);
        UserRole actorRole = convertToUserRole(roleString);

        Application app = applicationRepository.findById(applicationId).
                orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));

        if (actorRole != UserRole.ROLE_MANAGER && actorRole != UserRole.ROLE_ADMIN) {
            throw new ForbiddenException("Only admin or manager can change application status");
        }
        if (actorRole == UserRole.ROLE_MANAGER) {
            if (app.getApplicantId() != null && app.getApplicantId().equals(actorId)) {
                throw new ConflictException("Managers cannot change status of their own applications");
            }
        }

        try {
            ApplicationStatus newStatus = ApplicationStatus.valueOf(status.trim().toUpperCase());
            ApplicationStatus oldStatus = app.getStatus();
            if (oldStatus == newStatus) {
                return toDto(app);
            }
            try {
                app.setStatus(newStatus);
                app.setUpdatedAt(Instant.now());
                applicationRepository.save(app);

                ApplicationHistory hist = new ApplicationHistory();
                hist.setId(UUID.randomUUID());
                hist.setApplication(app);
                hist.setOldStatus(oldStatus);
                hist.setNewStatus(newStatus);
                hist.setChangedBy(actorRole);
                hist.setChangedAt(Instant.now());

                applicationHistoryRepository.save(hist);
            } catch (DataIntegrityViolationException ex) {
                Throwable root = ex.getRootCause() != null ? ex.getRootCause() : ex;
                throw new ConflictException("DB constraint violated: " + root.getMessage());
            }

            return toDto(app);
        } catch (IllegalArgumentException e) {
            throw new ConflictException("This status is incorrect. List of statuses: " +
                    "DRAFT, SUBMITTED, IN_REVIEW, APPROVED, REJECTED");
        }
    }

    // вот здесь дискуссионно - если считать, что applicationRepository.delete(app); сразу выполняются полностью, то можно убрать транзакцию,
    // но если мы считаем, что рекурсивно будут удаляться все связанные с заявкой документы, её история, то как будто стоит реализовать одной транзакцией
    @Transactional
    public void deleteApplication(UUID applicationId, UUID actorId) {
        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        if (!userServiceClient.userExists(actorId)) {
            throw new NotFoundException("Actor not found");
        }

        String roleString = userServiceClient.getUserRole(actorId);
        UserRole actorRole = convertToUserRole(roleString);
        if (actorRole != UserRole.ROLE_ADMIN) {
            throw new ForbiddenException("Only admin can delete applications");
        }

        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));

        try {
            applicationRepository.delete(app);
        } catch (Exception ex) {
            throw new ConflictException("Failed to delete application (DB constraint): " + ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<ApplicationHistoryDto> listHistory(UUID applicationId, UUID actorId) {
        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        if (!userServiceClient.userExists(actorId)) {
            throw new NotFoundException("Actor not found");
        }
        String roleString = userServiceClient.getUserRole(actorId);
        UserRole actorRole = convertToUserRole(roleString);

        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));

        boolean isApplicant = app.getApplicantId() != null && app.getApplicantId().equals(actorId);
        if (!(isApplicant || actorRole == UserRole.ROLE_ADMIN || actorRole == UserRole.ROLE_MANAGER)) {
            throw new ForbiddenException("Only applicant, manager or admin can see the history of application changes");
        }

        List<ApplicationHistory> history = applicationHistoryRepository.findByApplicationIdOrderByChangedAtDesc(applicationId);
        return history.stream().map(h -> {
            ApplicationHistoryDto dto = new ApplicationHistoryDto();
            dto.setId(h.getId());
            dto.setApplicationId(h.getApplication() != null ? h.getApplication().getId() : null);
            dto.setOldStatus(h.getOldStatus());
            dto.setNewStatus(h.getNewStatus());
            dto.setChangedByRole(h.getChangedBy());
            dto.setChangedAt(h.getChangedAt());
            return dto;
        }).collect(Collectors.toList());
    }

    public List<Application> findByApplicantId(UUID applicantId) {
        return applicationRepository.findByApplicantId(applicantId);
    }

    public List<Application> findByProductId(UUID productId) {
        return applicationRepository.findByProductId(productId);
    }

    public void delete(Application a) {
        applicationRepository.delete(a);
    }
}

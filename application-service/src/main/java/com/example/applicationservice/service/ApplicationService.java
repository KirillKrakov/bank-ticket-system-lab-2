package com.example.applicationservice.service;

import com.example.bankticketsystem.dto.*;
import com.example.bankticketsystem.exception.*;
import com.example.bankticketsystem.model.entity.*;
import com.example.bankticketsystem.model.enums.ApplicationStatus;
import com.example.bankticketsystem.model.enums.UserRole;
import com.example.bankticketsystem.repository.*;
import com.example.bankticketsystem.util.ApplicationPage;
import com.example.bankticketsystem.util.CursorUtil;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private final ApplicationRepository applicationRepository;
    private final ApplicationHistoryRepository applicationHistoryRepository;
    private final UserService userService;
    private final ProductService productService;
    private final TagService tagService;

    public ApplicationService(ApplicationRepository applicationRepository,
                              ApplicationHistoryRepository applicationHistoryRepository,
                              @Lazy UserService userService,
                              @Lazy ProductService productService,
                              TagService tagService) {
        this.applicationRepository = applicationRepository;
        this.applicationHistoryRepository = applicationHistoryRepository;
        this.userService = userService;
        this.productService = productService;
        this.tagService =tagService;
    }

    @Transactional
    public ApplicationDto createApplication(ApplicationRequest req) {
        if (req == null) throw new BadRequestException("Request is required");
        if ((req.getApplicantId() == null) || (req.getProductId() == null)) {
            throw new BadRequestException("Applicant ID and Product ID must be in request body");
        }

        User applicant = userService.findById(req.getApplicantId())
                .orElseThrow(() -> new NotFoundException("Applicant not found"));

        Product product = productService.findById(req.getProductId())
                .orElseThrow(() -> new NotFoundException("Product not found"));

        Application app = new Application();
        UUID applicationId = UUID.randomUUID();
        app.setId(applicationId);
        app.setApplicant(applicant);
        app.setProduct(product);
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
        hist.setChangedBy(applicant.getRole());
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
        dto.setApplicantId(app.getApplicant() != null ? app.getApplicant().getId() : null);
        dto.setProductId(app.getProduct() != null ? app.getProduct().getId() : null);
        dto.setStatus(app.getStatus());
        dto.setCreatedAt(app.getCreatedAt());
        List<com.example.bankticketsystem.dto.DocumentDto> docs = app.getDocuments().stream().map(d -> {
            com.example.bankticketsystem.dto.DocumentDto dd = new com.example.bankticketsystem.dto.DocumentDto();
            dd.setId(d.getId());
            dd.setFileName(d.getFileName());
            dd.setContentType(d.getContentType());
            dd.setStoragePath(d.getStoragePath());
            return dd;
        }).collect(Collectors.toList());
        List<String> tagNames = app.getTags() == null ? List.of() :
                app.getTags().stream().map(Tag::getName).toList();
        dto.setDocuments(docs);
        dto.setTags(tagNames);
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
        User current = userService.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor not found: " + actorId));

        Application app = applicationRepository.findById(applicationId).orElseThrow(() -> new NotFoundException("Application not found"));
        if (!(app.getApplicant().getId().equals(current.getId()) || current.getRole() == UserRole.ROLE_ADMIN
                || current.getRole() == UserRole.ROLE_MANAGER)) {
            throw new ForbiddenException("You must have the rights of an applicant, manager, or administrator for this request");
        }

        for (String name : tagNames) {
            Tag t = tagService.createTag(name);
            app.getTags().add(t);
        }
        applicationRepository.save(app);
    }

    public void removeTags(UUID applicationId, List<String> tagNames, UUID actorId) {
        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        User current = userService.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor not found: " + actorId));

        Application app = applicationRepository.findById(applicationId).orElseThrow(() -> new NotFoundException("Application not found"));
        if (!(app.getApplicant().getId().equals(current.getId()) || current.getRole() == UserRole.ROLE_ADMIN
                || current.getRole() == UserRole.ROLE_MANAGER)) {
            throw new ForbiddenException("You must have the rights of an applicant, manager, or administrator for this request");
        }

        app.getTags().removeIf(tag -> tagNames.contains(tag.getName()));
        applicationRepository.save(app);
    }

    @Transactional
    public ApplicationDto changeStatus(UUID applicationId, String status, UUID actorId) {
        if (status == null) throw new BadRequestException("Status must be not empty");

        if (actorId == null) {
            throw new UnauthorizedException("You must specify the actorId to authorize in this request");
        }
        User actor = userService.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor not found: " + actorId));

        Application app = applicationRepository.findById(applicationId).
                orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));

        if (actor.getRole() != UserRole.ROLE_MANAGER && actor.getRole() != UserRole.ROLE_ADMIN) {
            throw new ForbiddenException("Only admin or manager can change application status");
        }
        if (actor.getRole() == UserRole.ROLE_MANAGER) {
            if (app.getApplicant() != null && app.getApplicant().getId().equals(actor.getId())) {
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
                hist.setChangedBy(actor.getRole());
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
        User actor = userService.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor not found: " + actorId));

        if (actor.getRole() != UserRole.ROLE_ADMIN) {
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
        User actor = userService.findById(actorId)
                .orElseThrow(() -> new NotFoundException("Actor not found: " + actorId));

        Application app = applicationRepository.findById(applicationId)
                .orElseThrow(() -> new NotFoundException("Application not found: " + applicationId));

        boolean isApplicant = app.getApplicant() != null && app.getApplicant().getId().equals(actor.getId());
        if (!(isApplicant || actor.getRole() == UserRole.ROLE_ADMIN || actor.getRole() == UserRole.ROLE_MANAGER)) {
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

package com.example.applicationservice.service;

import com.example.applicationservice.dto.*;
import com.example.applicationservice.exception.*;
import com.example.applicationservice.feign.*;
import com.example.applicationservice.model.entity.*;
import com.example.applicationservice.model.enums.ApplicationStatus;
import com.example.applicationservice.model.enums.UserRole;
import com.example.applicationservice.repository.*;
import com.example.applicationservice.util.ApplicationPage;
import com.example.applicationservice.util.CursorUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ApplicationService {

    private static final Logger log = LoggerFactory.getLogger(ApplicationService.class);

    private final ApplicationRepository applicationRepository;
    private final ApplicationHistoryRepository applicationHistoryRepository;
    private final DocumentRepository documentRepository;
    private final UserServiceClient userServiceClient;
    private final ProductServiceClient productServiceClient;
    private final TagServiceClient tagServiceClient;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            ApplicationHistoryRepository applicationHistoryRepository,
            DocumentRepository documentRepository,
            UserServiceClient userServiceClient,
            ProductServiceClient productServiceClient,
            TagServiceClient tagServiceClient) {
        this.applicationRepository = applicationRepository;
        this.applicationHistoryRepository = applicationHistoryRepository;
        this.documentRepository = documentRepository;
        this.userServiceClient = userServiceClient;
        this.productServiceClient = productServiceClient;
        this.tagServiceClient = tagServiceClient;
    }

    @Transactional
    public Mono<ApplicationDto> createApplication(ApplicationRequest req) {
        if (req == null) {
            return Mono.error(new BadRequestException("Request is required"));
        }

        UUID applicantId = req.getApplicantId();
        UUID productId = req.getProductId();

        if (applicantId == null || productId == null) {
            return Mono.error(new BadRequestException("Applicant ID and Product ID are required"));
        }

        // 1. Валидация пользователя через Feign
        return userServiceClient.userExists(applicantId)
                .flatMap(userExists -> {
                    if (!userExists) {
                        return Mono.error(new NotFoundException("Applicant not found"));
                    }
                    // 2. Валидация продукта через Feign
                    return productServiceClient.productExists(productId);
                })
                .flatMap(productExists -> {
                    if (!productExists) {
                        return Mono.error(new NotFoundException("Product not found"));
                    }

                    // 3. Создание заявки в блокирующем коде
                    return Mono.fromCallable(() -> {
                        Application app = new Application();
                        app.setId(UUID.randomUUID());
                        app.setApplicantId(applicantId);
                        app.setProductId(productId);
                        app.setStatus(ApplicationStatus.SUBMITTED);
                        app.setCreatedAt(Instant.now());

                        // Документы
                        if (req.getDocuments() != null) {
                            List<Document> docs = req.getDocuments().stream()
                                    .map(dreq -> {
                                        Document d = new Document();
                                        d.setId(UUID.randomUUID());
                                        d.setFileName(dreq.getFileName());
                                        d.setContentType(dreq.getContentType());
                                        d.setStoragePath(dreq.getStoragePath());
                                        d.setApplication(app);
                                        return d;
                                    })
                                    .collect(Collectors.toList());
                            app.setDocuments(docs);
                        }

                        // Сохранение заявки
                        applicationRepository.save(app);

                        // История
                        ApplicationHistory hist = new ApplicationHistory();
                        hist.setId(UUID.randomUUID());
                        hist.setApplication(app);
                        hist.setOldStatus(null);
                        hist.setNewStatus(app.getStatus());
                        hist.setChangedBy(UserRole.ROLE_CLIENT);
                        hist.setChangedAt(Instant.now());
                        applicationHistoryRepository.save(hist);

                        log.info("Application created: {}", app.getId());
                        return app;
                    }).subscribeOn(Schedulers.boundedElastic());
                })
                .flatMap(app -> {
                    // 4. Обработка тегов через tag-service (асинхронно)
                    List<String> tagNames = req.getTags() != null ? req.getTags() : List.of();
                    if (!tagNames.isEmpty()) {
                        return tagServiceClient.createOrGetTagsBatch(tagNames)
                                .flatMap(tagDtos -> Mono.fromCallable(() -> {
                                    Set<String> tagNamesSet = tagDtos.stream()
                                            .map(TagDto::getName)
                                            .collect(Collectors.toSet());
                                    app.setTags(tagNamesSet);
                                    applicationRepository.save(app);
                                    log.info("Added {} tags to application {}", tagNamesSet.size(), app.getId());
                                    return app;
                                }).subscribeOn(Schedulers.boundedElastic()))
                                .onErrorResume(e -> {
                                    log.warn("Failed to process tags for application {}: {}",
                                            app.getId(), e.getMessage());
                                    // Продолжаем без тегов
                                    return Mono.just(app);
                                });
                    }
                    return Mono.just(app);
                })
                .map(this::toDto);
    }

    public Flux<ApplicationDto> findAll(int page, int size) {
        if (size > 50) {
            return Flux.error(new BadRequestException("Page size cannot exceed 50"));
        }

        return Mono.fromCallable(() -> {
                    Pageable pageable = PageRequest.of(page, size);
                    Page<Application> applications = applicationRepository.findAll(pageable);
                    return applications.stream()
                            .map(this::toDto)
                            .collect(Collectors.toList());
                }).subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<ApplicationDto> findById(UUID id) {
        return Mono.fromCallable(() ->
                applicationRepository.findById(id)
                        .map(this::toDto)
                        .orElse(null)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<ApplicationPage> streamWithNextCursor(String cursor, int limit) {
        if (limit <= 0) {
            return Mono.error(new BadRequestException("limit must be greater than 0"));
        }
        int capped = Math.min(limit, 50);

        CursorUtil.Decoded decoded = CursorUtil.decodeOrThrow(cursor);
        Instant ts = decoded != null ? decoded.timestamp : null;
        UUID id = decoded != null ? decoded.id : null;

        return Mono.fromCallable(() -> {
            List<Application> apps;
            if (ts == null) {
                apps = applicationRepository.findFirstPage(capped);
            } else {
                apps = applicationRepository.findByKeyset(ts, id, capped);
            }

            List<ApplicationDto> dtos = apps.stream()
                    .map(this::toDto)
                    .collect(Collectors.toList());

            String nextCursor = null;
            if (!apps.isEmpty()) {
                Application last = apps.get(apps.size() - 1);
                nextCursor = CursorUtil.encode(last.getCreatedAt(), last.getId());
            }

            return new ApplicationPage(dtos, nextCursor);
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<Void> attachTags(UUID applicationId, List<String> tagNames, UUID actorId) {
        return validateActor(applicationId, actorId)
                .flatMap(valid -> {
                    if (!valid) {
                        return Mono.error(new ForbiddenException("Insufficient permissions"));
                    }

                    return Mono.fromCallable(() ->
                                    applicationRepository.findById(applicationId)
                                            .orElseThrow(() -> new NotFoundException("Application not found"))
                            ).subscribeOn(Schedulers.boundedElastic())
                            .flatMap(app -> tagServiceClient.createOrGetTagsBatch(tagNames)
                                    .flatMap(tagDtos -> Mono.fromCallable(() -> {
                                        Set<String> newTags = tagDtos.stream()
                                                .map(TagDto::getName)
                                                .collect(Collectors.toSet());
                                        app.getTags().addAll(newTags);
                                        applicationRepository.save(app);
                                        log.info("Added {} tags to application {}", newTags.size(), applicationId);
                                        return (Void) null;
                                    }).subscribeOn(Schedulers.boundedElastic()))
                                    .onErrorResume(e -> {
                                        log.error("Failed to attach tags: {}", e.getMessage());
                                        return Mono.error(new ConflictException(
                                                "Failed to attach tags: " + e.getMessage()));
                                    }));
                });
    }

    @Transactional
    public Mono<Void> removeTags(UUID applicationId, List<String> tagNames, UUID actorId) {
        return validateActor(applicationId, actorId)
                .flatMap(valid -> {
                    if (!valid) {
                        return Mono.error(new ForbiddenException("Insufficient permissions"));
                    }

                    return Mono.fromCallable(() -> {
                        Application app = applicationRepository.findById(applicationId)
                                .orElseThrow(() -> new NotFoundException("Application not found"));
                        tagNames.forEach(app.getTags()::remove);
                        applicationRepository.save(app);
                        return (Void) null;
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }

    @Transactional
    public Mono<ApplicationDto> changeStatus(UUID applicationId, String status, UUID actorId) {
        return validateActorIsManagerOrAdmin(actorId)
                .flatMap(isManagerOrAdmin -> {
                    if (!isManagerOrAdmin) {
                        return Mono.error(new ForbiddenException("Only admin or manager can change application status"));
                    }

                    return findById(applicationId)
                            .flatMap(appDto -> {
                                // Проверка, что менеджер не меняет свою собственную заявку
                                if (appDto.getApplicantId().equals(actorId)) {
                                    return userServiceClient.getUserRole(actorId)
                                            .flatMap(role -> {
                                                if ("ROLE_MANAGER".equals(role.name())) {
                                                    return Mono.error(new ConflictException("Managers cannot change status of their own applications"));
                                                }
                                                return Mono.just(true);
                                            });
                                }
                                return Mono.just(true);
                            })
                            .then(Mono.fromCallable(() -> {
                                ApplicationStatus newStatus;
                                try {
                                    newStatus = ApplicationStatus.valueOf(status.trim().toUpperCase());
                                } catch (IllegalArgumentException e) {
                                    throw new ConflictException("Invalid status. Valid values: DRAFT, SUBMITTED, IN_REVIEW, APPROVED, REJECTED");
                                }

                                Application app = applicationRepository.findById(applicationId)
                                        .orElseThrow(() -> new NotFoundException("Application not found"));

                                ApplicationStatus oldStatus = app.getStatus();
                                if (oldStatus == newStatus) {
                                    return toDto(app);
                                }

                                app.setStatus(newStatus);
                                app.setUpdatedAt(Instant.now());
                                applicationRepository.save(app);

                                ApplicationHistory hist = new ApplicationHistory();
                                hist.setId(UUID.randomUUID());
                                hist.setApplication(app);
                                hist.setOldStatus(oldStatus);
                                hist.setNewStatus(newStatus);
                                hist.setChangedBy(UserRole.ROLE_MANAGER);
                                hist.setChangedAt(Instant.now());
                                applicationHistoryRepository.save(hist);

                                log.info("Application {} status changed from {} to {}",
                                        applicationId, oldStatus, newStatus);
                                return toDto(app);
                            }).subscribeOn(Schedulers.boundedElastic()));
                });
    }

    @Transactional
    public Mono<Void> deleteApplication(UUID applicationId, UUID actorId) {
        return validateActorIsAdmin(actorId)
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        return Mono.error(new ForbiddenException("Only admin can delete applications"));
                    }

                    return Mono.fromCallable(() -> {
                        Application app = applicationRepository.findById(applicationId)
                                .orElseThrow(() -> new NotFoundException("Application not found"));

                        // Удаляем связанные документы и историю
                        documentRepository.deleteAll(app.getDocuments());
                        applicationHistoryRepository.deleteAll(app.getHistory());
                        applicationRepository.delete(app);

                        log.info("Application deleted: {}", applicationId);
                        return (Void) null;
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }

    public Flux<ApplicationHistoryDto> listHistory(UUID applicationId, UUID actorId) {
        return validateActorCanViewHistory(applicationId, actorId)
                .flatMapMany(canView -> {
                    if (!canView) {
                        return Flux.error(new ForbiddenException("Insufficient permissions to view history"));
                    }

                    return Mono.fromCallable(() ->
                                    applicationHistoryRepository.findByApplicationIdOrderByChangedAtDesc(applicationId)
                                            .stream()
                                            .map(this::toHistoryDto)
                                            .collect(Collectors.toList())
                            ).subscribeOn(Schedulers.boundedElastic())
                            .flatMapMany(Flux::fromIterable);
                });
    }

    // Внутренний endpoint для user-service
    @Transactional
    public Mono<Void> deleteApplicationsByUserId(UUID userId) {
        return Mono.fromCallable(() -> {
            List<Application> applications = applicationRepository.findByApplicantId(userId);
            for (Application app : applications) {
                documentRepository.deleteAll(app.getDocuments());
                applicationHistoryRepository.deleteAll(app.getHistory());
                applicationRepository.delete(app);
                log.info("Deleted application {} for user {}", app.getId(), userId);
            }
            return (Void) null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // Внутренний endpoint для product-service
    @Transactional
    public Mono<Void> deleteApplicationsByProductId(UUID productId) {
        return Mono.fromCallable(() -> {
            List<Application> applications = applicationRepository.findByProductId(productId);
            for (Application app : applications) {
                documentRepository.deleteAll(app.getDocuments());
                applicationHistoryRepository.deleteAll(app.getHistory());
                applicationRepository.delete(app);
                log.info("Deleted application {} for product {}", app.getId(), productId);
            }
            return (Void) null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // Вспомогательные методы
    private ApplicationDto toDto(Application app) {
        ApplicationDto dto = new ApplicationDto();
        dto.setId(app.getId());
        dto.setApplicantId(app.getApplicantId());
        dto.setProductId(app.getProductId());
        dto.setStatus(app.getStatus());
        dto.setCreatedAt(app.getCreatedAt());

        if (app.getDocuments() != null) {
            List<DocumentDto> docDtos = app.getDocuments().stream()
                    .map(doc -> {
                        DocumentDto docDto = new DocumentDto();
                        docDto.setId(doc.getId());
                        docDto.setFileName(doc.getFileName());
                        docDto.setContentType(doc.getContentType());
                        docDto.setStoragePath(doc.getStoragePath());
                        return docDto;
                    })
                    .collect(Collectors.toList());
            dto.setDocuments(docDtos);
        }

        if (app.getTags() != null) {
            dto.setTags(new ArrayList<>(app.getTags()));
        }

        return dto;
    }

    private ApplicationHistoryDto toHistoryDto(ApplicationHistory history) {
        ApplicationHistoryDto dto = new ApplicationHistoryDto();
        dto.setId(history.getId());
        dto.setApplicationId(history.getApplication().getId());
        dto.setOldStatus(history.getOldStatus());
        dto.setNewStatus(history.getNewStatus());
        // Преобразуем строку в UserRole (упрощённо)
        // dto.setChangedByRole(UserRole.valueOf(history.getChangedBy()));
        dto.setChangedAt(history.getChangedAt());
        return dto;
    }

    private Mono<Boolean> validateActor(UUID applicationId, UUID actorId) {
        return findById(applicationId)
                .flatMap(app ->
                        userServiceClient.getUserRole(actorId)
                                .flatMap(role -> {
                                    if (app.getApplicantId().equals(actorId)) {
                                        return Mono.just(true);
                                    }
                                    return Mono.just("ROLE_ADMIN".equals(role.name()) || "ROLE_MANAGER".equals(role.name()));
                                })
                                .defaultIfEmpty(false)
                )
                .defaultIfEmpty(false);
    }

    private Mono<Boolean> validateActorIsManagerOrAdmin(UUID actorId) {
        return userServiceClient.getUserRole(actorId)
                .map(role -> "ROLE_ADMIN".equals(role.name()) || "ROLE_MANAGER".equals(role.name()))
                .defaultIfEmpty(false);
    }

    private Mono<Boolean> validateActorIsAdmin(UUID actorId) {
        return userServiceClient.getUserRole(actorId)
                .map(role -> "ROLE_ADMIN".equals(role.name()))
                .defaultIfEmpty(false);
    }

    private Mono<Boolean> validateActorCanViewHistory(UUID applicationId, UUID actorId) {
        return findById(applicationId)
                .flatMap(app ->
                        userServiceClient.getUserRole(actorId)
                                .flatMap(role -> {
                                    if (app.getApplicantId().equals(actorId)) {
                                        return Mono.just(true);
                                    }
                                    return Mono.just("ROLE_ADMIN".equals(role.name()) || "ROLE_MANAGER".equals(role.name()));
                                })
                                .defaultIfEmpty(false)
                )
                .defaultIfEmpty(false);
    }

    public Mono<Long> count() {
        return Mono.fromCallable(applicationRepository::count
        ).subscribeOn(Schedulers.boundedElastic());
    }
}
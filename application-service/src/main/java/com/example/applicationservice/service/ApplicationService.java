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
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.ReactiveCircuitBreakerFactory;
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
    private final ReactiveCircuitBreaker circuitBreaker;

    public ApplicationService(
            ApplicationRepository applicationRepository,
            ApplicationHistoryRepository applicationHistoryRepository,
            DocumentRepository documentRepository,
            UserServiceClient userServiceClient,
            ProductServiceClient productServiceClient,
            TagServiceClient tagServiceClient,
            ReactiveCircuitBreakerFactory circuitBreakerFactory) {
        this.applicationRepository = applicationRepository;
        this.applicationHistoryRepository = applicationHistoryRepository;
        this.documentRepository = documentRepository;
        this.userServiceClient = userServiceClient;
        this.productServiceClient = productServiceClient;
        this.tagServiceClient = tagServiceClient;
        this.circuitBreaker = circuitBreakerFactory.create("application-service");
    }

    @Transactional
    public Mono<ApplicationDto> createApplication(ApplicationRequest req) {
        return Mono.defer(() -> {
            // Валидация входных данных
            if (req == null) {
                return Mono.error(new BadRequestException("Request is required"));
            }

            UUID applicantId = req.getApplicantId();
            UUID productId = req.getProductId();

            if (applicantId == null || productId == null) {
                return Mono.error(new BadRequestException("Applicant ID and Product ID are required"));
            }

            // Параллельная валидация пользователя и продукта
            Mono<Boolean> userValidation = circuitBreaker.run(
                    userServiceClient.userExists(applicantId)
                            .filter(Boolean::booleanValue)
                            .switchIfEmpty(Mono.error(new NotFoundException("Applicant not found"))),
                    throwable -> Mono.error(new BadRequestException("User service unavailable"))
            );

            Mono<Boolean> productValidation = circuitBreaker.run(
                    productServiceClient.productExists(productId)
                            .filter(Boolean::booleanValue)
                            .switchIfEmpty(Mono.error(new NotFoundException("Product not found"))),
                    throwable -> Mono.error(new BadRequestException("Product service unavailable"))
            );

            return Mono.zip(userValidation, productValidation)
                    .flatMap(tuple -> {
                        // Создание заявки в блокирующем контексте
                        return Mono.fromCallable(() -> createApplicationEntity(req))
                                .subscribeOn(Schedulers.boundedElastic());
                    })
                    .flatMap(app -> {
                        // Обработка тегов если есть
                        List<String> tagNames = req.getTags() != null ? req.getTags() : List.of();
                        if (!tagNames.isEmpty()) {
                            return circuitBreaker.run(
                                    tagServiceClient.createOrGetTagsBatch(tagNames)
                                            .flatMap(tagDtos -> Mono.fromCallable(() -> {
                                                Set<String> tagNamesSet = tagDtos.stream()
                                                        .map(TagDto::getName)
                                                        .collect(Collectors.toSet());
                                                app.setTags(tagNamesSet);
                                                applicationRepository.save(app);
                                                log.info("Added {} tags to application {}", tagNamesSet.size(), app.getId());
                                                return app;
                                            }).subscribeOn(Schedulers.boundedElastic())),
                                    throwable -> {
                                        log.warn("Tag service unavailable, continuing without tags: {}", throwable.getMessage());
                                        return Mono.just(app);
                                    }
                            );
                        }
                        return Mono.just(app);
                    })
                    .map(this::toDto);
        }).onErrorResume(e -> {
            log.error("Failed to create application", e);
            if (e instanceof BadRequestException || e instanceof NotFoundException) {
                return Mono.error(e);
            }
            return Mono.error(new BadRequestException("Failed to create application"));
        });
    }

    private Application createApplicationEntity(ApplicationRequest req) {
        Application app = new Application();
        app.setId(UUID.randomUUID());
        app.setApplicantId(req.getApplicantId());
        app.setProductId(req.getProductId());
        app.setStatus(ApplicationStatus.SUBMITTED);
        app.setCreatedAt(Instant.now());

        // Документы
        if (req.getDocuments() != null && !req.getDocuments().isEmpty()) {
            List<Document> docs = req.getDocuments().stream()
                    .map(dreq -> {
                        Document doc = new Document();
                        doc.setId(UUID.randomUUID());
                        doc.setFileName(dreq.getFileName());
                        doc.setContentType(dreq.getContentType());
                        doc.setStoragePath(dreq.getStoragePath());
                        doc.setApplication(app);
                        return doc;
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
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    public Mono<ApplicationDto> findById(UUID id) {
        return Mono.fromCallable(() ->
                        applicationRepository.findById(id)
                                .map(this::toDto)
                                .orElseThrow(() -> new NotFoundException("Application not found"))
                )
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(e -> {
                    if (e instanceof NotFoundException) return e;
                    return new BadRequestException("Failed to retrieve application");
                });
    }

    public Mono<ApplicationPage> streamWithNextCursor(String cursor, int limit) {
        if (limit <= 0) {
            return Mono.error(new BadRequestException("limit must be greater than 0"));
        }
        int capped = Math.min(limit, 50);

        return Mono.fromCallable(() -> {
                    CursorUtil.Decoded decoded = CursorUtil.decodeOrThrow(cursor);
                    Instant ts = decoded != null ? decoded.timestamp : null;
                    UUID id = decoded != null ? decoded.id : null;

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
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<Void> attachTags(UUID applicationId, List<String> tagNames, UUID actorId) {
        return validateActor(applicationId, actorId)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new ForbiddenException("Insufficient permissions")))
                .then(Mono.fromCallable(() ->
                        applicationRepository.findById(applicationId)
                                .orElseThrow(() -> new NotFoundException("Application not found"))
                ).subscribeOn(Schedulers.boundedElastic()))
                .flatMap(app -> {
                    if (tagNames == null || tagNames.isEmpty()) {
                        return Mono.empty();
                    }

                    return circuitBreaker.run(
                            tagServiceClient.createOrGetTagsBatch(tagNames)
                                    .flatMap(tagDtos -> Mono.fromCallable(() -> {
                                        Set<String> newTags = tagDtos.stream()
                                                .map(TagDto::getName)
                                                .collect(Collectors.toSet());
                                        app.getTags().addAll(newTags);
                                        applicationRepository.save(app);
                                        log.info("Added {} tags to application {}", newTags.size(), applicationId);
                                        return (Void) null;
                                    }).subscribeOn(Schedulers.boundedElastic())),
                            throwable -> {
                                log.error("Failed to attach tags: {}", throwable.getMessage());
                                return Mono.error(new ConflictException("Failed to attach tags: Service unavailable"));
                            }
                    );
                });
    }

    @Transactional
    public Mono<Void> removeTags(UUID applicationId, List<String> tagNames, UUID actorId) {
        return validateActor(applicationId, actorId)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new ForbiddenException("Insufficient permissions")))
                .then(Mono.fromCallable(() -> {
                            Application app = applicationRepository.findById(applicationId)
                                    .orElseThrow(() -> new NotFoundException("Application not found"));

                            if (tagNames != null) {
                                tagNames.forEach(app.getTags()::remove);
                            }
                            applicationRepository.save(app);
                            return (Void) null;
                        })
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    @Transactional
    public Mono<ApplicationDto> changeStatus(UUID applicationId, String status, UUID actorId) {
        return validateActorIsManagerOrAdmin(actorId)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new ForbiddenException("Only admin or manager can change application status")))
                .then(Mono.defer(() -> {
                    ApplicationStatus newStatus;
                    try {
                        newStatus = ApplicationStatus.valueOf(status.trim().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        return Mono.error(new BadRequestException(
                                "Invalid status. Valid values: " + Arrays.toString(ApplicationStatus.values())));
                    }

                    return Mono.fromCallable(() ->
                                    applicationRepository.findById(applicationId)
                                            .orElseThrow(() -> new NotFoundException("Application not found"))
                            )
                            .subscribeOn(Schedulers.boundedElastic())
                            .flatMap(app -> validateManagerSelfChange(app, actorId)
                                    .then(Mono.fromCallable(() -> updateApplicationStatus(app, newStatus, actorId))
                                            .subscribeOn(Schedulers.boundedElastic()))
                            )
                            .map(this::toDto);
                }));
    }

    private Mono<Void> validateManagerSelfChange(Application app, UUID actorId) {
        if (app.getApplicantId().equals(actorId)) {
            return userServiceClient.getUserRole(actorId)
                    .flatMap(role -> {
                        if (UserRole.ROLE_MANAGER.equals(role)) {
                            return Mono.error(new ConflictException("Managers cannot change status of their own applications"));
                        }
                        return Mono.empty();
                    });
        }
        return Mono.empty();
    }

    private Application updateApplicationStatus(Application app, ApplicationStatus newStatus, UUID actorId) {
        ApplicationStatus oldStatus = app.getStatus();
        if (oldStatus == newStatus) {
            return app;
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

        log.info("Application {} status changed from {} to {} by {}",
                app.getId(), oldStatus, newStatus, actorId);
        return app;
    }

    @Transactional
    public Mono<Void> deleteApplication(UUID applicationId, UUID actorId) {
        return validateActorIsAdmin(actorId)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new ForbiddenException("Only admin can delete applications")))
                .then(Mono.fromCallable(() -> {
                            Application app = applicationRepository.findById(applicationId)
                                    .orElseThrow(() -> new NotFoundException("Application not found"));

                            // Удаляем связанные документы и историю
                            documentRepository.deleteAll(app.getDocuments());
                            applicationHistoryRepository.deleteAll(app.getHistory());
                            applicationRepository.delete(app);

                            log.info("Application deleted: {}", applicationId);
                            return (Void) null;
                        })
                        .subscribeOn(Schedulers.boundedElastic()));
    }

    public Flux<ApplicationHistoryDto> listHistory(UUID applicationId, UUID actorId) {
        return validateActorCanViewHistory(applicationId, actorId)
                .filter(Boolean::booleanValue)
                .switchIfEmpty(Mono.error(new ForbiddenException("Insufficient permissions to view history")))
                .thenMany(Mono.fromCallable(() ->
                                applicationHistoryRepository.findByApplicationIdOrderByChangedAtDesc(applicationId)
                                        .stream()
                                        .map(this::toHistoryDto)
                                        .collect(Collectors.toList())
                        )
                        .subscribeOn(Schedulers.boundedElastic()))
                .flatMap(Flux::fromIterable);
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
                })
                .subscribeOn(Schedulers.boundedElastic());
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
                })
                .subscribeOn(Schedulers.boundedElastic());
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
        dto.setChangedAt(history.getChangedAt());
        return dto;
    }

    private Mono<Boolean> validateActor(UUID applicationId, UUID actorId) {
        return Mono.zip(
                findById(applicationId).onErrorReturn(null),
                circuitBreaker.run(
                        userServiceClient.getUserRole(actorId),
                        throwable -> {
                            log.warn("Failed to get user role: {}", throwable.getMessage());
                            return Mono.just(UserRole.ROLE_CLIENT);
                        }
                ).defaultIfEmpty(UserRole.ROLE_CLIENT)
        ).map(tuple -> {
            ApplicationDto app = tuple.getT1();
            UserRole role = tuple.getT2();

            if (app == null) return false;

            // Владелец заявки всегда имеет доступ
            if (app.getApplicantId().equals(actorId)) return true;

            // Admin и Manager имеют доступ к любым заявкам
            return role == UserRole.ROLE_ADMIN || role == UserRole.ROLE_MANAGER;
        }).defaultIfEmpty(false);
    }

    private Mono<Boolean> validateActorIsManagerOrAdmin(UUID actorId) {
        return circuitBreaker.run(
                        userServiceClient.getUserRole(actorId),
                        throwable -> {
                            log.warn("Failed to get user role: {}", throwable.getMessage());
                            return Mono.just(UserRole.ROLE_CLIENT);
                        }
                ).map(role -> role == UserRole.ROLE_ADMIN || role == UserRole.ROLE_MANAGER)
                .defaultIfEmpty(false);
    }

    private Mono<Boolean> validateActorIsAdmin(UUID actorId) {
        return circuitBreaker.run(
                        userServiceClient.getUserRole(actorId),
                        throwable -> {
                            log.warn("Failed to get user role: {}", throwable.getMessage());
                            return Mono.just(UserRole.ROLE_CLIENT);
                        }
                ).map(role -> role == UserRole.ROLE_ADMIN)
                .defaultIfEmpty(false);
    }

    private Mono<Boolean> validateActorCanViewHistory(UUID applicationId, UUID actorId) {
        return validateActor(applicationId, actorId);
    }

    public Mono<Long> count() {
        return Mono.fromCallable(applicationRepository::count)
                .subscribeOn(Schedulers.boundedElastic());
    }
}
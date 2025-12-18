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

        return Mono.fromCallable(() -> userServiceClient.userExists(applicantId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(userExists -> {
                    if (!userExists) {
                        return Mono.error(new NotFoundException("Applicant not found"));
                    }
                    return Mono.fromCallable(() -> productServiceClient.productExists(productId))
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .flatMap(productExists -> {
                    if (!productExists) {
                        return Mono.error(new NotFoundException("Product not found"));
                    }

                    return Mono.fromCallable(() -> {
                        Application app = new Application();
                        app.setId(UUID.randomUUID());
                        app.setApplicantId(applicantId);
                        app.setProductId(productId);
                        app.setStatus(ApplicationStatus.SUBMITTED);
                        app.setCreatedAt(Instant.now());

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

                        applicationRepository.save(app);

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
                    List<String> tagNames = req.getTags() != null ? req.getTags() : List.of();
                    if (!tagNames.isEmpty()) {
                        // Обертываем tagServiceClient вызов
                        return Mono.fromCallable(() -> tagServiceClient.createOrGetTagsBatch(tagNames))
                                .subscribeOn(Schedulers.boundedElastic())
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
                                    return Mono.just(app);
                                });
                    }
                    return Mono.just(app);
                })
                .map(this::toDto);
    }

    @Transactional(readOnly = true)
    public Flux<ApplicationDto> findAll(int page, int size) {
        if (size > 50) {
            return Flux.error(new BadRequestException("Page size cannot exceed 50"));
        }

        return Mono.fromCallable(() -> {
                    Pageable pageable = PageRequest.of(page, size);
                    // Используем обновленный метод
                    Page<Application> applications = applicationRepository.findAllWithDocumentsAndTags(pageable);
                    return applications.stream()
                            .map(this::toDto)
                            .collect(Collectors.toList());
                }).subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Transactional(readOnly = true)
    public Mono<ApplicationDto> findById(UUID id) {
        return Mono.fromCallable(() ->
                applicationRepository.findByIdWithDocumentsAndTags(id)
                        .map(this::toDto)
                        .orElse(null)
        ).subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional(readOnly = true)
    public Mono<ApplicationPage> streamWithNextCursor(String cursor, int limit) {
        if (limit <= 0) {
            return Mono.error(new BadRequestException("limit must be greater than 0"));
        }
        int capped = Math.min(limit, 50);

        // Создаем final переменные или используем final reference
        final Instant[] tsHolder = new Instant[1];
        final UUID[] idHolder = new UUID[1];

        if (cursor != null && !cursor.trim().isEmpty()) {
            try {
                CursorUtil.Decoded decoded = CursorUtil.decode(cursor);
                if (decoded != null) {
                    tsHolder[0] = decoded.timestamp;
                    idHolder[0] = decoded.id;
                }
            } catch (Exception e) {
                return Mono.error(new BadRequestException("Invalid cursor format: " + e.getMessage()));
            }
        }

        return Mono.fromCallable(() -> {
            Instant ts = tsHolder[0];
            UUID id = idHolder[0];

            // Получаем ID заявок с пагинацией
            List<UUID> appIds;
            if (ts == null) {
                appIds = applicationRepository.findIdsFirstPage(capped);
            } else {
                appIds = applicationRepository.findIdsByKeyset(ts, id, capped);
            }

            // Загружаем полные данные для этих ID
            List<Application> apps = appIds.isEmpty()
                    ? List.of()
                    : applicationRepository.findAllByIdWithDocumentsAndTags(appIds);

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

                    // Используем один блок fromCallable для всей логики
                    return Mono.fromCallable(() -> {
                                // 1. Получаем заявку с тегами
                                Application app = applicationRepository.findByIdWithTags(applicationId)
                                        .orElseThrow(() -> new NotFoundException("Application not found"));

                                // 2. Получаем теги из tag-service
                                List<TagDto> tagDtos = tagServiceClient.createOrGetTagsBatch(tagNames);

                                // 3. Добавляем теги к заявке
                                Set<String> newTags = tagDtos.stream()
                                        .map(TagDto::getName)
                                        .collect(Collectors.toSet());

                                app.getTags().addAll(newTags);
                                applicationRepository.save(app);

                                log.info("Added {} tags to application {}", newTags.size(), applicationId);
                                return (Void) null;

                            }).subscribeOn(Schedulers.boundedElastic())
                            .onErrorResume(e -> {
                                log.error("Failed to attach tags: {}", e.getMessage());
                                return Mono.error(new ConflictException("Failed to attach tags: " + e.getMessage()));
                            });
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
                        // Получаем заявку с тегами
                        Application app = applicationRepository.findByIdWithTags(applicationId)
                                .orElseThrow(() -> new NotFoundException("Application not found"));

                        // Удаляем теги
                        tagNames.forEach(app.getTags()::remove);
                        applicationRepository.save(app);

                        log.info("Removed {} tags from application {}", tagNames.size(), applicationId);
                        return (Void) null;
                    }).subscribeOn(Schedulers.boundedElastic());
                });
    }

    @Transactional
    public Mono<ApplicationDto> changeStatus(UUID applicationId, String status, UUID actorId) {
        return Mono.fromCallable(() -> {
            // Вся логика в одном блоке

            // 1. Проверка прав
            UserRole role = userServiceClient.getUserRole(actorId);
            boolean isManagerOrAdmin = "ROLE_ADMIN".equals(role.name()) || "ROLE_MANAGER".equals(role.name());
            if (!isManagerOrAdmin) {
                throw new ForbiddenException("Only admin or manager can change application status");
            }

            // 2. Получаем заявку с JOIN FETCH
            Application app = applicationRepository.findByIdWithDocumentsAndTags(applicationId)
                    .orElseThrow(() -> new NotFoundException("Application not found"));

            // 3. Проверка на менеджера
            if (app.getApplicantId().equals(actorId) && "ROLE_MANAGER".equals(role.name())) {
                throw new ConflictException("Managers cannot change status of their own applications");
            }

            // 4. Меняем статус
            ApplicationStatus newStatus = ApplicationStatus.valueOf(status.trim().toUpperCase());
            ApplicationStatus oldStatus = app.getStatus();

            if (oldStatus != newStatus) {
                app.setStatus(newStatus);
                app.setUpdatedAt(Instant.now());
                applicationRepository.save(app);

                // 5. Сохраняем историю
                ApplicationHistory hist = new ApplicationHistory();
                hist.setId(UUID.randomUUID());
                hist.setApplication(app);
                hist.setOldStatus(oldStatus);
                hist.setNewStatus(newStatus);
                hist.setChangedBy(role);
                hist.setChangedAt(Instant.now());
                applicationHistoryRepository.save(hist);

                log.info("Application {} status changed from {} to {}",
                        applicationId, oldStatus, newStatus);
            }

            // 6. Возвращаем DTO
            return toDto(app);

        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Transactional
    public Mono<Void> deleteApplication(UUID applicationId, UUID actorId) {
        return validateActorIsAdmin(actorId)
                .flatMap(isAdmin -> {
                    if (!isAdmin) {
                        return Mono.error(new ForbiddenException("Only admin can delete applications"));
                    }
                    return Mono.fromCallable(() -> {
                        // Удаляем в правильном порядке
                        documentRepository.deleteByApplicationId(applicationId);
                        applicationHistoryRepository.deleteByApplicationId(applicationId);
                        applicationRepository.deleteTagsByApplicationId(applicationId);
                        applicationRepository.deleteById(applicationId);

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
            // Сначала получаем ID заявок пользователя
            List<UUID> applicationIds = applicationRepository.findIdsByApplicantId(userId);
            // Удаляем документы, историю и заявки по отдельности
            for (UUID appId : applicationIds) {
                // Удаляем документы
                documentRepository.deleteByApplicationId(appId);
                // Удаляем историю
                applicationHistoryRepository.deleteByApplicationId(appId);
                // Удаляем теги
                applicationRepository.deleteTagsByApplicationId(appId);
                // Удаляем заявку
                applicationRepository.deleteById(appId);

                log.info("Deleted application {} for user {}", appId, userId);
            }
            return (Void) null;
        }).subscribeOn(Schedulers.boundedElastic());
    }

    // Внутренний endpoint для product-service
    @Transactional
    public Mono<Void> deleteApplicationsByProductId(UUID productId) {
        return Mono.fromCallable(() -> {
            // Сначала получаем ID заявок пользователя
            List<UUID> productIds = applicationRepository.findIdsByProductId(productId);
            // Удаляем документы, историю и заявки по отдельности
            for (UUID appId : productIds) {
                // Удаляем документы
                documentRepository.deleteByApplicationId(appId);
                // Удаляем историю
                applicationHistoryRepository.deleteByApplicationId(appId);
                // Удаляем теги
                applicationRepository.deleteTagsByApplicationId(appId);
                // Удаляем заявку
                applicationRepository.deleteById(appId);

                log.info("Deleted application {} for product {}", appId, productId);
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

    // Обновляем validateActor метод
    private Mono<Boolean> validateActor(UUID applicationId, UUID actorId) {
        return findById(applicationId)
                .flatMap(app ->
                        Mono.fromCallable(() -> userServiceClient.getUserRole(actorId))
                                .subscribeOn(Schedulers.boundedElastic())
                                .map(role -> {
                                    if (app.getApplicantId().equals(actorId)) {
                                        return true;
                                    }
                                    return "ROLE_ADMIN".equals(role.name()) || "ROLE_MANAGER".equals(role.name());
                                })
                                .defaultIfEmpty(false)
                )
                .defaultIfEmpty(false);
    }

    private Mono<Boolean> validateActorIsManagerOrAdmin(UUID actorId) {
        return Mono.fromCallable(() -> userServiceClient.getUserRole(actorId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(role -> "ROLE_ADMIN".equals(role.name()) || "ROLE_MANAGER".equals(role.name()))
                .defaultIfEmpty(false);
    }

    private Mono<Boolean> validateActorIsAdmin(UUID actorId) {
        return Mono.fromCallable(() -> userServiceClient.getUserRole(actorId))
                .subscribeOn(Schedulers.boundedElastic())
                .map(role -> "ROLE_ADMIN".equals(role.name()))
                .defaultIfEmpty(false);
    }

    private Mono<Boolean> validateActorCanViewHistory(UUID applicationId, UUID actorId) {
        return findById(applicationId)
                .flatMap(app ->
                        Mono.fromCallable(() -> userServiceClient.getUserRole(actorId))
                                .subscribeOn(Schedulers.boundedElastic())
                                .map(role -> {
                                    if (app.getApplicantId().equals(actorId)) {
                                        return true;
                                    }
                                    return "ROLE_ADMIN".equals(role.name()) || "ROLE_MANAGER".equals(role.name());
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
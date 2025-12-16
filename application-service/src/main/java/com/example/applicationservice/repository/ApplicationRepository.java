package com.example.applicationservice.repository;

import com.example.applicationservice.model.entity.Application;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID>, ApplicationRepositoryCustom {
    Page<Application> findAll(Pageable pageable);
    Page<Application> findByTags_Name(String tagName, Pageable pageable);
    long countByApplicantId(UUID applicantId);
    long countByProductId(UUID productId);
    List<Application> findByProductId(UUID productId);
    List<Application> findByApplicantId(UUID applicantId);
    // Первый запрос — первая страница, без WHERE по курсору
    @Query(value = "SELECT * FROM application " +
            "ORDER BY created_at DESC, id DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<Application> findFirstPage(@Param("limit") int limit);

    // Второй запрос — keyset для последующих страниц
    @Query(value = "SELECT * FROM application " +
            "WHERE (created_at, id) < (:ts, :id) " +
            "ORDER BY created_at DESC, id DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<Application> findByKeyset(@Param("ts") Instant ts,
                                   @Param("id") UUID id,
                                   @Param("limit") int limit);
}

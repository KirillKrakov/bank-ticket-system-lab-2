package com.example.applicationservice.repository;

import com.example.applicationservice.model.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    List<Application> findByApplicantId(UUID applicantId);
    List<Application> findByProductId(UUID productId);

    @Query(value = "SELECT * FROM application " +
            "ORDER BY created_at DESC, id DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<Application> findFirstPage(@Param("limit") int limit);

    @Query(value = "SELECT * FROM application " +
            "WHERE (created_at, id) < (:ts, :id) " +
            "ORDER BY created_at DESC, id DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<Application> findByKeyset(@Param("ts") Instant ts,
                                   @Param("id") UUID id,
                                   @Param("limit") int limit);
}
package com.example.applicationservice.repository;

import com.example.applicationservice.model.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DocumentRepository extends JpaRepository<Document, UUID> {
    long countByApplicationId(UUID applicationId);
}

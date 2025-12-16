package com.example.applicationservice.repository;

import com.example.applicationservice.model.entity.Application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ApplicationRepositoryCustom {
    List<Application> findByKeyset(Instant ts, UUID id, int limit);
}

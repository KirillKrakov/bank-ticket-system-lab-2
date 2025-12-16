package com.example.applicationservice.repository;

import com.example.applicationservice.model.entity.Application;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class ApplicationRepositoryCustomImpl implements ApplicationRepositoryCustom {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<Application> findByKeyset(Instant ts, UUID id, int limit) {
        if (ts == null || id == null) {
            String sql = "SELECT * FROM application ORDER BY created_at DESC, id DESC LIMIT :limit";
            Query q = em.createNativeQuery(sql, Application.class);
            q.setParameter("limit", limit);
            return q.getResultList();
        } else {
            String sql = "SELECT * FROM application " +
                    "WHERE (created_at, id) < (CAST(:ts AS timestamp with time zone), CAST(:id AS uuid)) " +
                    "ORDER BY created_at DESC, id DESC LIMIT :limit";
            Query q = em.createNativeQuery(sql, Application.class);
            q.setParameter("ts", ts);           // Instant -> JDBC timestamp (should work)
            q.setParameter("id", id);           // pass UUID directly
            q.setParameter("limit", limit);
            return q.getResultList();
        }
    }
}

package com.example.applicationservice.model.entity;

import com.example.applicationservice.model.enums.ApplicationStatus;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "application")
public class Application {

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private ApplicationStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Version
    private Long version;

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Document> documents = new ArrayList<>();

    @OneToMany(mappedBy = "application", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ApplicationHistory> history = new ArrayList<>();

//    @ManyToMany
//    @JoinTable(
//            name = "application_tag",
//            joinColumns = @JoinColumn(name = "application_id"),
//            inverseJoinColumns = @JoinColumn(name = "tag_id")
//    )
//    private Set<Tag> tags = new HashSet<>();

    public Application() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getApplicantId() {return applicantId;}
    public void setApplicantId(UUID applicantId) {this.applicantId = applicantId;}

    public UUID getProductId() {return productId;}
    public void setProductId(UUID productId) {this.productId = productId;}

    public ApplicationStatus getStatus() { return status; }
    public void setStatus(ApplicationStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public List<Document> getDocuments() { return documents; }
    public void setDocuments(List<Document> documents) { this.documents = documents; }

    public List<ApplicationHistory> getHistory() { return history; }
    public void setHistory(List<ApplicationHistory> history) { this.history = history; }

//    public Set<Tag> getTags() { return tags; }
//    public void setTags(Set<Tag> tags) { this.tags = tags; }
}

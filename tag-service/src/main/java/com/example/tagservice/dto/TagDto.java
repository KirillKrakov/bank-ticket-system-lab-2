package com.example.tagservice.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public class TagDto {
    private UUID id;

    @NotBlank(message = "Tag name is required")
    private String name;

    private List<UUID> applicationIds;

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public List<UUID> getApplicationIds() { return applicationIds; }
    public void setApplicationIds(List<UUID> applicationIds) {
        this.applicationIds = applicationIds;
    }
}
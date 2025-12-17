package com.example.applicationservice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;

import java.util.List;
import java.util.UUID;

public class TagDto {
    private UUID id;

    @NotBlank(message = "Tag name is required")
    private String name;


    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<ApplicationDto> applications;

    public UUID getId() { return id; }
    public String getName() { return name; }

    public void setId(UUID id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public List<ApplicationDto> getApplications() { return applications; }
    public void setApplications(List<ApplicationDto> applications) {
        this.applications = applications;
    }
}
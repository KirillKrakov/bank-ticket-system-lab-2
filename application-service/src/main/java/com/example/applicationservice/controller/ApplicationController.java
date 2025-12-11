package com.example.applicationservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/applications")
public class ApplicationController {

    @GetMapping
    public ResponseEntity<?> listApplications() {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm Application Service!",
                "endpoint", "GET /api/v1/applications",
                "service", "application-service"
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getApplication(@PathVariable String id) {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm Application Service!",
                "endpoint", "GET /api/v1/applications/" + id,
                "service", "application-service",
                "applicationId", id
        ));
    }

    @PostMapping
    public ResponseEntity<?> createApplication() {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm Application Service!",
                "endpoint", "POST /api/v1/applications",
                "service", "application-service"
        ));
    }

    @GetMapping("/stream")
    public ResponseEntity<?> streamApplications() {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm Application Service! (Stream endpoint)",
                "endpoint", "GET /api/v1/applications/stream",
                "service", "application-service"
        ));
    }
}
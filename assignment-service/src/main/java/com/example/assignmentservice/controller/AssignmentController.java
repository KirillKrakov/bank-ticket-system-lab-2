package com.example.assignmentservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/assignments")
public class AssignmentController {

    @GetMapping
    public ResponseEntity<?> listAssignments() {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm Assignment Service!",
                "endpoint", "GET /api/v1/assignments",
                "service", "assignment-service"
        ));
    }

    @PostMapping
    public ResponseEntity<?> createAssignment() {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm Assignment Service!",
                "endpoint", "POST /api/v1/assignments",
                "service", "assignment-service"
        ));
    }

    @DeleteMapping
    public ResponseEntity<?> deleteAssignment() {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm Assignment Service!",
                "endpoint", "DELETE /api/v1/assignments",
                "service", "assignment-service"
        ));
    }
}
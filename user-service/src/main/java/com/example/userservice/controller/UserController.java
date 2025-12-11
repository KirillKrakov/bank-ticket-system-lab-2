package com.example.userservice.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    @GetMapping
    public ResponseEntity<?> listUsers() {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm User Service!",
                "endpoint", "GET /api/v1/users",
                "service", "user-service"
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getUser(@PathVariable String id) {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm User Service!",
                "endpoint", "GET /api/v1/users/" + id,
                "service", "user-service",
                "userId", id
        ));
    }

    @PostMapping
    public ResponseEntity<?> createUser() {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm User Service!",
                "endpoint", "POST /api/v1/users",
                "service", "user-service"
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable String id) {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm User Service!",
                "endpoint", "PUT /api/v1/users/" + id,
                "service", "user-service",
                "userId", id
        ));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable String id) {
        return ResponseEntity.ok(Map.of(
                "message", "Hi! I'm User Service!",
                "endpoint", "DELETE /api/v1/users/" + id,
                "service", "user-service",
                "userId", id
        ));
    }
}
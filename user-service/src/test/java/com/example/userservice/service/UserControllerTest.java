package com.example.userservice.service;

import com.example.userservice.controller.UserController;
import com.example.userservice.dto.UserDto;
import com.example.userservice.dto.UserRequest;
import com.example.userservice.exception.ForbiddenException;
import com.example.userservice.model.enums.UserRole;
import com.example.userservice.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    @InjectMocks
    private UserController userController;

    private final UUID testUserId = UUID.randomUUID();
    private final UUID actorAdminId = UUID.randomUUID();
    private UserDto testUserDto = new UserDto();

    @BeforeEach
    void setUp() {
        testUserDto.setId(testUserId);
        testUserDto.setUsername("testuser");
        testUserDto.setEmail("test@example.com");
        testUserDto.setRole(UserRole.ROLE_CLIENT);
        testUserDto.setId(testUserId);
        testUserDto.setCreatedAt(java.time.Instant.now());
    }

    // Основные успешные сценарии
    @Test
    void createUser_Success() {
        UserRequest request = new UserRequest();
        request.setUsername("newuser");
        request.setEmail("new@example.com");
        request.setPassword("Password123");

        when(userService.create(any())).thenReturn(Mono.just(testUserDto));

        StepVerifier.create(userController.createUser(request))
                .expectNext(testUserDto)
                .verifyComplete();
    }

    @Test
    void getAllUsers_Success() {
        when(userService.findAll(0, 20)).thenReturn(Flux.just(testUserDto));

        StepVerifier.create(userController.getAllUsers(0, 20))
                .expectNext(testUserDto)
                .verifyComplete();
    }

    @Test
    void getAllUsers_SizeExceedsMax_ThrowsError() {
        StepVerifier.create(userController.getAllUsers(0, 51))
                .expectError(IllegalArgumentException.class)
                .verify();
    }

    @Test
    void getUserById_Exists_ReturnsOk() {
        when(userService.findById(testUserId)).thenReturn(Mono.just(testUserDto));

        StepVerifier.create(userController.getUserById(testUserId))
                .expectNextMatches(response ->
                        response.getStatusCode() == HttpStatus.OK &&
                                response.getBody() != null)
                .verifyComplete();
    }

    @Test
    void getUserById_NotFound_ReturnsNotFound() {
        when(userService.findById(testUserId)).thenReturn(Mono.empty());

        StepVerifier.create(userController.getUserById(testUserId))
                .expectNextMatches(response ->
                        response.getStatusCode() == HttpStatus.NOT_FOUND)
                .verifyComplete();
    }

    @Test
    void updateUser_Success_ReturnsOk() {
        UserRequest request = new UserRequest();
        request.setUsername("updated");

        when(userService.update(any(), any(), any()))
                .thenReturn(Mono.just(testUserDto));

        StepVerifier.create(userController.updateUser(testUserId, actorAdminId, request))
                .expectNextMatches(response ->
                        response.getStatusCode() == HttpStatus.OK)
                .verifyComplete();
    }

    @Test
    void updateUser_Forbidden_ReturnsForbidden() {
        UserRequest request = new UserRequest();

        when(userService.update(any(), any(), any()))
                .thenReturn(Mono.error(new ForbiddenException("Access denied")));

        StepVerifier.create(userController.updateUser(testUserId, actorAdminId, request))
                .expectNextMatches(response ->
                        response.getStatusCode() == HttpStatus.FORBIDDEN)
                .verifyComplete();
    }

    @Test
    void deleteUser_Success() {
        when(userService.delete(testUserId, actorAdminId)).thenReturn(Mono.empty());

        StepVerifier.create(userController.deleteUser(testUserId, actorAdminId))
                .verifyComplete();
    }

    @Test
    void promoteToManager_Success() {
        when(userService.promoteToManager(testUserId, actorAdminId)).thenReturn(Mono.empty());

        StepVerifier.create(userController.promoteToManager(testUserId, actorAdminId))
                .verifyComplete();
    }

    @Test
    void demoteToClient_Success() {
        when(userService.demoteToClient(testUserId, actorAdminId)).thenReturn(Mono.empty());

        StepVerifier.create(userController.demoteToClient(testUserId, actorAdminId))
                .verifyComplete();
    }

    @Test
    void userExists_UserFound_ReturnsTrue() {
        when(userService.findById(testUserId)).thenReturn(Mono.just(testUserDto));

        StepVerifier.create(userController.userExists(testUserId))
                .expectNextMatches(response ->
                        response.getStatusCode() == HttpStatus.OK &&
                                Boolean.TRUE.equals(response.getBody()))
                .verifyComplete();
    }

    @Test
    void getUserRole_UserFound_ReturnsRole() {
        when(userService.findById(testUserId)).thenReturn(Mono.just(testUserDto));

        StepVerifier.create(userController.getUserRole(testUserId))
                .expectNextMatches(response ->
                        response.getStatusCode() == HttpStatus.OK &&
                                response.getBody() == UserRole.ROLE_CLIENT)
                .verifyComplete();
    }
}
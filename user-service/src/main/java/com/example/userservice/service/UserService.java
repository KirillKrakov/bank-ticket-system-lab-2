package com.example.userservice.service;

import com.example.userservice.dto.UserDto;
import com.example.userservice.dto.UserRequest;
import com.example.userservice.exception.*;
import com.example.userservice.model.entity.User;
import com.example.userservice.model.enums.UserRole;
import com.example.userservice.repository.UserRepository;
import com.password4j.Password;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    @Autowired
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public Mono<UserDto> create(UserRequest req) {
        if (req == null) {
            return Mono.error(new BadRequestException("Request is required"));
        }
        if ((req.getUsername() == null) || (req.getEmail() == null) || (req.getPassword() == null)) {
            return Mono.error(new BadRequestException("Username, email and password must be in request body"));
        }

        String username = req.getUsername().trim();
        String email = req.getEmail().trim().toLowerCase();

        return userRepository.existsByUsername(username)
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new ConflictException("Username already in use"));
                    }
                    return userRepository.existsByEmail(email);
                })
                .flatMap(exists -> {
                    if (exists) {
                        return Mono.error(new ConflictException("Email already in use"));
                    }

                    User u = new User();
                    u.setId(UUID.randomUUID());
                    u.setUsername(username);
                    u.setEmail(email);
                    u.setPasswordHash(Password.hash(req.getPassword()).withBcrypt().getResult());
                    u.setRole(UserRole.ROLE_CLIENT);
                    u.setCreatedAt(Instant.now());
                    return userRepository.save(u);
                })
                .map(this::toDto);
    }

    public Flux<UserDto> list(int page, int size) {
        // Для R2DBC пока используем простую пагинацию
        // В реальном проекте можно добавить R2dbcEntityTemplate
        return userRepository.findAll()
                .skip((long) page * size)
                .take(size)
                .map(this::toDto);
    }

    public Mono<UserDto> get(UUID id) {
        return userRepository.findById(id)
                .map(this::toDto)
                .switchIfEmpty(Mono.error(new NotFoundException("User not found: " + id)));
    }

    public Mono<Long> count() {
        return userRepository.count();
    }

    private UserDto toDto(User u) {
        UserDto dto = new UserDto();
        dto.setId(u.getId());
        dto.setUsername(u.getUsername());
        dto.setEmail(u.getEmail());
        dto.setPassword("<Hidden>");
        dto.setRole(u.getRole());
        dto.setCreatedAt(u.getCreatedAt());
        return dto;
    }

    @Transactional
    public Mono<UserDto> updateUser(UUID userId, UUID actorId, UserRequest req) {
        return validateAdmin(actorId)
                .flatMap(admin -> userRepository.findById(userId))
                .switchIfEmpty(Mono.error(new NotFoundException("User not found: " + userId)))
                .flatMap(existing -> {
                    if (req.getUsername() != null) existing.setUsername(req.getUsername());
                    if (req.getEmail() != null) existing.setEmail(req.getEmail());
                    if (req.getPassword() != null) {
                        existing.setPasswordHash(Password.hash(req.getPassword()).withBcrypt().getResult());
                    }
                    existing.setUpdatedAt(Instant.now());
                    return userRepository.save(existing);
                })
                .map(this::toDto);
    }

    @Transactional
    public Mono<Void> deleteUser(UUID userId, UUID actorId) {
        return validateAdmin(actorId)
                .flatMap(admin -> userRepository.findById(userId))
                .switchIfEmpty(Mono.error(new NotFoundException("User not found: " + userId)))
                .flatMap(userRepository::delete);
    }

    @Transactional
    public Mono<Void> promoteToManager(UUID id, UUID actorId) {
        return validateAdmin(actorId)
                .flatMap(admin -> userRepository.findById(id))
                .switchIfEmpty(Mono.error(new NotFoundException("User not found: " + id)))
                .flatMap(u -> {
                    if (u.getRole() != UserRole.ROLE_MANAGER) {
                        u.setRole(UserRole.ROLE_MANAGER);
                        return userRepository.save(u).then();
                    }
                    return Mono.empty();
                });
    }

    @Transactional
    public Mono<Void> demoteToUser(UUID id, UUID actorId) {
        return validateAdmin(actorId)
                .flatMap(admin -> userRepository.findById(id))
                .switchIfEmpty(Mono.error(new NotFoundException("User not found: " + id)))
                .flatMap(u -> {
                    if (u.getRole() != UserRole.ROLE_CLIENT) {
                        u.setRole(UserRole.ROLE_CLIENT);
                        return userRepository.save(u).then();
                    }
                    return Mono.empty();
                });
    }

    private Mono<User> validateAdmin(UUID actorId) {
        if (actorId == null) {
            return Mono.error(new UnauthorizedException("You must specify the actorId to authorize in this request"));
        }
        return userRepository.findById(actorId)
                .switchIfEmpty(Mono.error(new NotFoundException("Actor not found: " + actorId)))
                .flatMap(actor -> {
                    if (actor.getRole() != UserRole.ROLE_ADMIN) {
                        return Mono.error(new ForbiddenException("Only ADMIN can perform this action"));
                    }
                    return Mono.just(actor);
                });
    }

    public Mono<User> findById(UUID id) {
        return userRepository.findById(id);
    }
}
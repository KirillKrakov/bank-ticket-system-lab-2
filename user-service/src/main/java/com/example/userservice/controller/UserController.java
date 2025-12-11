package com.example.userservice.controller;

import com.example.userservice.dto.UserDto;
import com.example.userservice.dto.UserRequest;
import com.example.userservice.exception.BadRequestException;
import com.example.userservice.service.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {

    private static final int MAX_PAGE_SIZE = 50;
    private final UserService userService;

    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserDto> register(@Valid @RequestBody UserRequest req) {
        return userService.create(req);
    }

    @GetMapping
    public Flux<UserDto> list(@RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size) {
        if (size > MAX_PAGE_SIZE) {
            return Flux.error(new BadRequestException("size cannot be greater than " + MAX_PAGE_SIZE));
        }
        return userService.list(page, size);
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<UserDto>> showUser(@PathVariable("id") UUID id) {
        return userService.get(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<UserDto>> updateUser(@PathVariable("id") UUID id,
                                                    @RequestParam("actorId") UUID actorId,
                                                    @Valid @RequestBody UserRequest req) {
        return userService.updateUser(id, actorId, req)
                .map(ResponseEntity::ok);
    }

    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteUser(@PathVariable("id") UUID id,
                                                 @RequestParam("actorId") UUID actorId) {
        return userService.deleteUser(id, actorId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @PutMapping("/{id}/promote-manager")
    public Mono<ResponseEntity<Void>> promoteManager(@PathVariable("id") UUID id,
                                                     @RequestParam("actorId") UUID actorId) {
        return userService.promoteToManager(id, actorId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }

    @PutMapping("/{id}/demote-manager")
    public Mono<ResponseEntity<Void>> demoteManager(@PathVariable("id") UUID id,
                                                    @RequestParam("actorId") UUID actorId) {
        return userService.demoteToUser(id, actorId)
                .then(Mono.just(ResponseEntity.noContent().build()));
    }
}
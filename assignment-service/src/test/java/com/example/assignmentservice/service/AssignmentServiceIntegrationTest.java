package com.example.assignmentservice.service;

import com.example.assignmentservice.AssignmentServiceApplication;
import com.example.assignmentservice.dto.UserProductAssignmentDto;
import com.example.assignmentservice.model.entity.UserProductAssignment;
import com.example.assignmentservice.model.enums.AssignmentRole;
import com.example.assignmentservice.model.enums.UserRole;
import com.example.assignmentservice.repository.UserProductAssignmentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.http.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
@SpringBootTest(
        classes = AssignmentServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureWireMock(port = 0)
public class AssignmentServiceIntegrationTest {

    @Container
    public static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("assignmentdb")
            .withUsername("postgres")
            .withPassword("postgres");

    private static int wireMockPort;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", postgres::getJdbcUrl);
        r.add("spring.datasource.username", postgres::getUsername);
        r.add("spring.datasource.password", postgres::getPassword);

        // Указываем явно порт WireMock
        r.add("wiremock.server.port", () -> wireMockPort);
    }

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private UserProductAssignmentRepository assignmentRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void cleanDb() {
        assignmentRepository.deleteAll();
        wireMockPort = Integer.parseInt(System.getProperty("wiremock.server.port", "8080"));
    }

    // Основной тест полного жизненного цикла
    @Test
    void fullAssignmentLifecycle_withAdminActor() {
        // Arrange
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        // Настраиваем WireMock для user-service
        stubFor(get(urlPathEqualTo("/api/users/" + actorId + "/role"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("\"" + UserRole.ROLE_ADMIN + "\"")));

        stubFor(get(urlPathEqualTo("/api/users/" + userId + "/exists"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("true")));

        // Настраиваем WireMock для product-service
        stubFor(get(urlPathEqualTo("/api/products/" + productId + "/exists"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("true")));

        // Создаем запрос на назначение
        String requestBody = String.format(
                "{\"userId\":\"%s\",\"productId\":\"%s\",\"role\":\"PRODUCT_OWNER\"}",
                userId, productId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        // Act 1: Создаем назначение
        ResponseEntity<UserProductAssignmentDto> createResponse = restTemplate.postForEntity(
                "/api/assignments?actorId=" + actorId,
                request,
                UserProductAssignmentDto.class
        );

        // Assert 1: Проверяем создание
        assertEquals(HttpStatus.CREATED, createResponse.getStatusCode());
        assertNotNull(createResponse.getBody());
        UUID assignmentId = createResponse.getBody().getId();
        assertEquals(AssignmentRole.PRODUCT_OWNER, createResponse.getBody().getRole());

        // Проверяем, что назначение сохранилось в БД
        List<UserProductAssignment> assignments = assignmentRepository.findAll();
        assertEquals(1, assignments.size());
        assertEquals(assignmentId, assignments.get(0).getId());

        // Act 2: Получаем список назначений по пользователю
        ResponseEntity<UserProductAssignmentDto[]> listByUserResponse = restTemplate.getForEntity(
                "/api/assignments?userId=" + userId,
                UserProductAssignmentDto[].class
        );

        // Assert 2
        assertEquals(HttpStatus.OK, listByUserResponse.getStatusCode());
        assertNotNull(listByUserResponse.getBody());
        assertEquals(1, listByUserResponse.getBody().length);
        assertEquals(assignmentId, listByUserResponse.getBody()[0].getId());

        // Act 3: Получаем список назначений по продукту
        ResponseEntity<UserProductAssignmentDto[]> listByProductResponse = restTemplate.getForEntity(
                "/api/assignments?productId=" + productId,
                UserProductAssignmentDto[].class
        );

        // Assert 3
        assertEquals(HttpStatus.OK, listByProductResponse.getStatusCode());
        assertNotNull(listByProductResponse.getBody());
        assertEquals(1, listByProductResponse.getBody().length);

        // Act 4: Получаем все назначения
        ResponseEntity<UserProductAssignmentDto[]> listAllResponse = restTemplate.getForEntity(
                "/api/assignments",
                UserProductAssignmentDto[].class
        );

        // Assert 4
        assertEquals(HttpStatus.OK, listAllResponse.getStatusCode());
        assertNotNull(listAllResponse.getBody());
        assertEquals(1, listAllResponse.getBody().length);

        // Act 5: Обновляем назначение
        String updateRequestBody = String.format(
                "{\"userId\":\"%s\",\"productId\":\"%s\",\"role\":\"RESELLER\"}",
                userId, productId
        );

        HttpEntity<String> updateRequest = new HttpEntity<>(updateRequestBody, headers);
        ResponseEntity<UserProductAssignmentDto> updateResponse = restTemplate.postForEntity(
                "/api/assignments?actorId=" + actorId,
                updateRequest,
                UserProductAssignmentDto.class
        );

        // Assert 5
        assertEquals(HttpStatus.CREATED, updateResponse.getStatusCode());
        assertEquals(assignmentId, updateResponse.getBody().getId());
        assertEquals(AssignmentRole.RESELLER, updateResponse.getBody().getRole());

        // Act 6: Удаляем назначение
        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                "/api/assignments?actorId=" + actorId + "&userId=" + userId + "&productId=" + productId,
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class
        );

        // Assert 6
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());

        // Проверяем, что назначение удалилось из БД
        List<UserProductAssignment> assignmentsAfterDelete = assignmentRepository.findAll();
        assertEquals(0, assignmentsAfterDelete.size());
    }

    // Тест для проверки существования назначения по роли
    @Test
    void existsByUserAndProductAndRole_returnsTrue_whenAssignmentExists() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        AssignmentRole role = AssignmentRole.PRODUCT_OWNER;

        // Создаем назначение напрямую в БД
        UserProductAssignment assignment = new UserProductAssignment();
        assignment.setId(UUID.randomUUID());
        assignment.setUserId(userId);
        assignment.setProductId(productId);
        assignment.setRoleOnProduct(role);
        assignment.setAssignedAt(Instant.now());
        assignmentRepository.save(assignment);

        // Act
        ResponseEntity<Boolean> response = restTemplate.getForEntity(
                "/api/assignments/exists?userId=" + userId +
                        "&productId=" + productId +
                        "&role=" + role,
                Boolean.class
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody());
    }

    @Test
    void existsByUserAndProductAndRole_returnsFalse_whenAssignmentDoesNotExist() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        AssignmentRole role = AssignmentRole.PRODUCT_OWNER;

        // Act
        ResponseEntity<Boolean> response = restTemplate.getForEntity(
                "/api/assignments/exists?userId=" + userId +
                        "&productId=" + productId +
                        "&role=" + role,
                Boolean.class
        );

        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertFalse(response.getBody());
    }

    // Тест на недостаточные права
    @Test
    void assign_returnsForbidden_whenActorNotAuthorized() {
        // Arrange
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        // Настраиваем WireMock - актор не админ и не владелец
        stubFor(get(urlPathEqualTo("/api/users/" + actorId + "/role"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("\"" + UserRole.ROLE_CLIENT + "\"")));

        // Актор существует
        stubFor(get(urlPathEqualTo("/api/users/" + actorId + "/exists"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("true")));

        // Целевой пользователь существует
        stubFor(get(urlPathEqualTo("/api/users/" + userId + "/exists"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("true")));

        // Продукт существует
        stubFor(get(urlPathEqualTo("/api/products/" + productId + "/exists"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("true")));

        // Создаем запрос на назначение
        String requestBody = String.format(
                "{\"userId\":\"%s\",\"productId\":\"%s\",\"role\":\"PRODUCT_OWNER\"}",
                userId, productId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/assignments?actorId=" + actorId,
                request,
                String.class
        );

        // Assert
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // Тест на несуществующего пользователя
    @Test
    void assign_returnsNotFound_whenUserDoesNotExist() {
        // Arrange
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        // Актор - админ
        stubFor(get(urlPathEqualTo("/api/users/" + actorId + "/role"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("\"" + UserRole.ROLE_ADMIN + "\"")));

        // Пользователь не существует
        stubFor(get(urlPathEqualTo("/api/users/" + userId + "/exists"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("false")));

        // Создаем запрос на назначение
        String requestBody = String.format(
                "{\"userId\":\"%s\",\"productId\":\"%s\",\"role\":\"PRODUCT_OWNER\"}",
                userId, productId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/assignments?actorId=" + actorId,
                request,
                String.class
        );

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // Тест на несуществующий продукт
    @Test
    void assign_returnsNotFound_whenProductDoesNotExist() {
        // Arrange
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        // Актор - админ
        stubFor(get(urlPathEqualTo("/api/users/" + actorId + "/role"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("\"" + UserRole.ROLE_ADMIN + "\"")));

        // Пользователь существует
        stubFor(get(urlPathEqualTo("/api/users/" + userId + "/exists"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("true")));

        // Продукт не существует
        stubFor(get(urlPathEqualTo("/api/products/" + productId + "/exists"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("false")));

        // Создаем запрос на назначение
        String requestBody = String.format(
                "{\"userId\":\"%s\",\"productId\":\"%s\",\"role\":\"PRODUCT_OWNER\"}",
                userId, productId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/assignments?actorId=" + actorId,
                request,
                String.class
        );

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // Тест на удаление всех назначений пользователя
    @Test
    void deleteAssignments_byUser_removesAllUserAssignments() {
        // Arrange
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();

        // Актор - админ
        stubFor(get(urlPathEqualTo("/api/users/" + actorId + "/role"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("\"" + UserRole.ROLE_ADMIN + "\"")));

        // Пользователь существует
        stubFor(get(urlPathEqualTo("/api/users/" + userId + "/exists"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("true")));

        // Создаем два назначения для пользователя
        UserProductAssignment assignment1 = new UserProductAssignment();
        assignment1.setId(UUID.randomUUID());
        assignment1.setUserId(userId);
        assignment1.setProductId(productId1);
        assignment1.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        assignment1.setAssignedAt(Instant.now());

        UserProductAssignment assignment2 = new UserProductAssignment();
        assignment2.setId(UUID.randomUUID());
        assignment2.setUserId(userId);
        assignment2.setProductId(productId2);
        assignment2.setRoleOnProduct(AssignmentRole.RESELLER);
        assignment2.setAssignedAt(Instant.now());

        assignmentRepository.saveAll(Arrays.asList(assignment1, assignment2));

        // Act
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/assignments?actorId=" + actorId + "&userId=" + userId,
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class
        );

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Проверяем, что все назначения пользователя удалены
        List<UserProductAssignment> remainingAssignments = assignmentRepository.findByUserId(userId);
        assertEquals(0, remainingAssignments.size());
    }

    // Тест на удаление всех назначений продукта
    @Test
    void deleteAssignments_byProduct_removesAllProductAssignments() {
        // Arrange
        UUID actorId = UUID.randomUUID();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        // Актор - админ
        stubFor(get(urlPathEqualTo("/api/users/" + actorId + "/role"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("\"" + UserRole.ROLE_ADMIN + "\"")));

        // Продукт существует
        stubFor(get(urlPathEqualTo("/api/products/" + productId + "/exists"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("true")));

        // Создаем два назначения для продукта
        UserProductAssignment assignment1 = new UserProductAssignment();
        assignment1.setId(UUID.randomUUID());
        assignment1.setUserId(userId1);
        assignment1.setProductId(productId);
        assignment1.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        assignment1.setAssignedAt(Instant.now());

        UserProductAssignment assignment2 = new UserProductAssignment();
        assignment2.setId(UUID.randomUUID());
        assignment2.setUserId(userId2);
        assignment2.setProductId(productId);
        assignment2.setRoleOnProduct(AssignmentRole.RESELLER);
        assignment2.setAssignedAt(Instant.now());

        assignmentRepository.saveAll(Arrays.asList(assignment1, assignment2));

        // Act
        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/assignments?actorId=" + actorId + "&productId=" + productId,
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                Void.class
        );

        // Assert
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        // Проверяем, что все назначения продукта удалены
        List<UserProductAssignment> remainingAssignments = assignmentRepository.findByProductId(productId);
        assertEquals(0, remainingAssignments.size());
    }

    // Тест на работу актора-владельца продукта (не админ)
    @Test
    void assign_successful_whenActorIsProductOwner() {
        // Arrange
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        // Актор - обычный пользователь
        stubFor(get(urlPathEqualTo("/api/users/" + actorId + "/role"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("\"" + UserRole.ROLE_CLIENT + "\"")));

        // Целевой пользователь существует
        stubFor(get(urlPathEqualTo("/api/users/" + userId + "/exists"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("true")));

        // Продукт существует
        stubFor(get(urlPathEqualTo("/api/products/" + productId + "/exists"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("true")));

        // Предварительно создаем назначение, где актор - владелец продукта
        UserProductAssignment actorAssignment = new UserProductAssignment();
        actorAssignment.setId(UUID.randomUUID());
        actorAssignment.setUserId(actorId);
        actorAssignment.setProductId(productId);
        actorAssignment.setRoleOnProduct(AssignmentRole.PRODUCT_OWNER);
        actorAssignment.setAssignedAt(Instant.now());
        assignmentRepository.save(actorAssignment);

        // Создаем запрос на назначение
        String requestBody = String.format(
                "{\"userId\":\"%s\",\"productId\":\"%s\",\"role\":\"RESELLER\"}",
                userId, productId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        // Act
        ResponseEntity<UserProductAssignmentDto> response = restTemplate.postForEntity(
                "/api/assignments?actorId=" + actorId,
                request,
                UserProductAssignmentDto.class
        );

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(AssignmentRole.RESELLER, response.getBody().getRole());
        assertEquals(userId, response.getBody().getUserId());
        assertEquals(productId, response.getBody().getProductId());
    }

    // Тест на проверку Circuit Breaker (Service Unavailable)
    @Test
    void assign_returnsServiceUnavailable_whenUserServiceIsDown() {
        // Arrange
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        // User service возвращает ошибку 503
        stubFor(get(urlPathEqualTo("/api/users/" + actorId + "/role"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withFixedDelay(100)));

        // Создаем запрос на назначение
        String requestBody = String.format(
                "{\"userId\":\"%s\",\"productId\":\"%s\",\"role\":\"PRODUCT_OWNER\"}",
                userId, productId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        // Act
        ResponseEntity<String> response = restTemplate.postForEntity(
                "/api/assignments?actorId=" + actorId,
                request,
                String.class
        );

        // Assert
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
    }

    // Тест на отсутствие actorId при удалении
    @Test
    void deleteAssignments_returnsUnauthorized_whenActorIdIsMissing() {
        // Arrange
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        // Act
        ResponseEntity<String> response = restTemplate.exchange(
                "/api/assignments?userId=" + userId + "&productId=" + productId,
                HttpMethod.DELETE,
                HttpEntity.EMPTY,
                String.class
        );

        // Assert
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
    }

    // Дополнительный тест на проверку, что владелец может обновлять назначение
    @Test
    void assign_updatesExistingAssignment_whenAssignmentExists() {
        // Arrange
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        // Актор - админ
        stubFor(get(urlPathEqualTo("/api/users/" + actorId + "/role"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("\"" + UserRole.ROLE_ADMIN + "\"")));

        stubFor(get(urlPathEqualTo("/api/users/" + userId + "/exists"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("true")));

        stubFor(get(urlPathEqualTo("/api/products/" + productId + "/exists"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody("true")));

        // Создаем существующее назначение
        UserProductAssignment existingAssignment = new UserProductAssignment();
        existingAssignment.setId(UUID.randomUUID());
        existingAssignment.setUserId(userId);
        existingAssignment.setProductId(productId);
        existingAssignment.setRoleOnProduct(AssignmentRole.RESELLER);
        existingAssignment.setAssignedAt(Instant.now().minusSeconds(3600));
        assignmentRepository.save(existingAssignment);

        // Создаем запрос на обновление роли
        String requestBody = String.format(
                "{\"userId\":\"%s\",\"productId\":\"%s\",\"role\":\"PRODUCT_OWNER\"}",
                userId, productId
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(requestBody, headers);

        // Act
        ResponseEntity<UserProductAssignmentDto> response = restTemplate.postForEntity(
                "/api/assignments?actorId=" + actorId,
                request,
                UserProductAssignmentDto.class
        );

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(existingAssignment.getId(), response.getBody().getId()); // Тот же ID
        assertEquals(AssignmentRole.PRODUCT_OWNER, response.getBody().getRole()); // Новая роль

        // Проверяем, что в БД только одна запись
        List<UserProductAssignment> assignments = assignmentRepository.findAll();
        assertEquals(1, assignments.size());
        assertEquals(existingAssignment.getId(), assignments.get(0).getId());
        assertEquals(AssignmentRole.PRODUCT_OWNER, assignments.get(0).getRoleOnProduct());
    }
}
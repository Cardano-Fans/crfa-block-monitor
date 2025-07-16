package com.cardano.monitor.resource;

import com.cardano.monitor.dto.HealthResponse;
import com.cardano.monitor.model.*;
import com.cardano.monitor.service.BlockProducerMonitorServiceIF;
import com.cardano.monitor.service.DnsServiceIF;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@DisplayName("MonitorResource Health Endpoint Tests")
class MonitorResourceTest {

    @InjectMock
    BlockProducerMonitorServiceIF monitorService;

    @InjectMock
    DnsServiceIF dnsService;

    @BeforeEach
    void setUp() {
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        reset(monitorService, dnsService);
        
        // Mock DNS service to return PRIMARY by default
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.PRIMARY);
    }

    @Test
    @DisplayName("Should return 200 and HEALTHY when daemon is running")
    // SCENARIO: Health check when monitoring service is operational
    // Tests that health endpoint returns 200 status code and HEALTHY status when daemon is running
    // Validates proper health reporting for operational system state
    void shouldReturn200AndHealthyWhenDaemonIsRunning() {
        // Given - Service is running
        ServerStatus runningStatus = createServerStatus(DaemonStatus.RUNNING);
        when(monitorService.getStatus()).thenReturn(runningStatus);

        // When & Then
        given()
            .when()
                .get("/api/health")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("status", equalTo("HEALTHY"))
                .body("timestamp", notNullValue());

        verify(monitorService).getStatus();
    }

    @Test
    @DisplayName("Should return 500 and UNHEALTHY when daemon is stopped")
    // SCENARIO: Health check when monitoring service is not operational
    // Tests that health endpoint returns 500 status code and UNHEALTHY status when daemon is stopped
    // Validates proper error reporting for non-operational system state
    void shouldReturn500AndUnhealthyWhenDaemonIsStopped() {
        // Given - Service is stopped
        ServerStatus stoppedStatus = createServerStatus(DaemonStatus.STOPPED);
        when(monitorService.getStatus()).thenReturn(stoppedStatus);

        // When & Then
        given()
            .when()
                .get("/api/health")
            .then()
                .statusCode(500)
                .contentType("application/json")
                .body("status", equalTo("UNHEALTHY"))
                .body("timestamp", notNullValue());

        verify(monitorService).getStatus();
    }

    @Test
    @DisplayName("Should handle service exceptions gracefully during health check")
    // SCENARIO: Health endpoint resilience when underlying service throws exceptions
    // Tests error handling when the monitoring service encounters unexpected errors
    // Validates that health endpoint doesn't crash and provides appropriate error response
    void shouldHandleServiceExceptionsGracefullyDuringHealthCheck() {
        // Given - Service throws exception
        when(monitorService.getStatus()).thenThrow(new RuntimeException("Service error"));

        // When & Then - Should return 500 due to exception
        given()
            .when()
                .get("/api/health")
            .then()
                .statusCode(500);

        verify(monitorService).getStatus();
    }

    @Test
    @DisplayName("Should return valid JSON structure for healthy response")
    // SCENARIO: Health endpoint JSON structure validation for healthy state
    // Tests that the response JSON contains all required fields with correct types
    // Validates API contract compliance for healthy responses
    void shouldReturnValidJsonStructureForHealthyResponse() {
        // Given - Service is running
        ServerStatus runningStatus = createServerStatus(DaemonStatus.RUNNING);
        when(monitorService.getStatus()).thenReturn(runningStatus);

        // When & Then
        given()
            .when()
                .get("/api/health")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("status", is("HEALTHY"))
                .body("timestamp", notNullValue())
                .body("$", hasKey("status"))
                .body("$", hasKey("timestamp"));

        verify(monitorService).getStatus();
    }

    @Test
    @DisplayName("Should return valid JSON structure for unhealthy response")
    // SCENARIO: Health endpoint JSON structure validation for unhealthy state
    // Tests that the response JSON contains all required fields with correct types for error case
    // Validates API contract compliance for unhealthy responses
    void shouldReturnValidJsonStructureForUnhealthyResponse() {
        // Given - Service is stopped
        ServerStatus stoppedStatus = createServerStatus(DaemonStatus.STOPPED);
        when(monitorService.getStatus()).thenReturn(stoppedStatus);

        // When & Then
        given()
            .when()
                .get("/api/health")
            .then()
                .statusCode(500)
                .contentType("application/json")
                .body("status", is("UNHEALTHY"))
                .body("timestamp", notNullValue())
                .body("$", hasKey("status"))
                .body("$", hasKey("timestamp"));

        verify(monitorService).getStatus();
    }

    @Test
    @DisplayName("Should handle rapid health check requests consistently")
    // SCENARIO: Health endpoint under load - multiple rapid requests
    // Tests that the endpoint can handle multiple concurrent/rapid requests
    // Validates performance and consistency under basic load conditions
    void shouldHandleRapidHealthCheckRequestsConsistently() {
        // Given - Service is running
        ServerStatus runningStatus = createServerStatus(DaemonStatus.RUNNING);
        when(monitorService.getStatus()).thenReturn(runningStatus);

        // When & Then - Multiple rapid requests
        for (int i = 0; i < 10; i++) {
            given()
                .when()
                    .get("/api/health")
                .then()
                    .statusCode(200)
                    .body("status", equalTo("HEALTHY"));
        }

        verify(monitorService, times(10)).getStatus();
    }

    @Test
    @DisplayName("Should provide correct HTTP status codes for health states")
    // SCENARIO: HTTP status code validation for different health states
    // Tests that the endpoint follows HTTP conventions: 200 for healthy, 500 for unhealthy
    // Validates proper status code semantics for monitoring and alerting systems
    void shouldProvideCorrectHttpStatusCodesForHealthStates() {
        // Test RUNNING -> 200
        ServerStatus runningStatus = createServerStatus(DaemonStatus.RUNNING);
        when(monitorService.getStatus()).thenReturn(runningStatus);

        given()
            .when()
                .get("/api/health")
            .then()
                .statusCode(200);

        // Test STOPPED -> 500
        ServerStatus stoppedStatus = createServerStatus(DaemonStatus.STOPPED);
        when(monitorService.getStatus()).thenReturn(stoppedStatus);

        given()
            .when()
                .get("/api/health")
            .then()
                .statusCode(500);

        verify(monitorService, times(2)).getStatus();
    }

    /**
     * Helper method to create a ServerStatus with the specified daemon status
     */
    private ServerStatus createServerStatus(DaemonStatus daemonStatus) {
        return new ServerStatus(
            daemonStatus,
            ServerType.PRIMARY,
            ServerHealthStatus.UP,
            ServerHealthStatus.UP,
            Instant.now(),
            null,
            null,
            NextAction.NONE.withoutContext(),
            new ServerStatus.ConfigInfo(
                new ServerStatus.ServerInfo("test-primary", "127.0.0.1", 9001),
                new ServerStatus.ServerInfo("test-secondary", "127.0.0.2", 9002)
            )
        );
    }
}
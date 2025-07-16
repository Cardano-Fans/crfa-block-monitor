package com.cardano.monitor.resource;

import com.cardano.monitor.dto.StatusResponse;
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
@DisplayName("MonitorResource Status Endpoint Tests")
class MonitorResourceStatusTest {

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
    @DisplayName("Should return status using getStatus() method (idempotent)")
    void shouldReturnStatusUsingGetStatusMethod() {
        // Given - Service status
        ServerStatus status = createServerStatus(DaemonStatus.RUNNING);
        when(monitorService.getStatus()).thenReturn(status);

        // When & Then
        given()
            .when()
                .get("/api/status")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("timestamp", notNullValue())
                .body("monitor", notNullValue())
                .body("monitor.daemon_status", equalTo("RUNNING"))
                .body("monitor.current_active", equalTo("PRIMARY"));

        // Verify getStatus() was called, not checkServers()
        verify(monitorService).getStatus();
        verify(monitorService, never()).checkServers();
    }

    @Test
    @DisplayName("Should handle multiple rapid status requests idempotently")
    void shouldHandleMultipleRapidStatusRequestsIdempotently() {
        // Given - Service status
        ServerStatus status = createServerStatus(DaemonStatus.RUNNING);
        when(monitorService.getStatus()).thenReturn(status);

        // When & Then - Multiple rapid requests should not cause side effects
        for (int i = 0; i < 10; i++) {
            given()
                .when()
                    .get("/api/status")
                .then()
                    .statusCode(200)
                    .body("monitor.daemon_status", equalTo("RUNNING"));
        }

        // Verify getStatus() was called multiple times, but never checkServers()
        verify(monitorService, times(10)).getStatus();
        verify(monitorService, never()).checkServers();
    }

    @Test
    @DisplayName("Should return cached status without triggering server checks")
    void shouldReturnCachedStatusWithoutTriggeringServerChecks() {
        // Given - Service status with UNKNOWN health (cached status)
        ServerStatus cachedStatus = new ServerStatus(
            DaemonStatus.RUNNING,
            ServerType.PRIMARY,
            ServerHealthStatus.UNKNOWN, // Cached status shows UNKNOWN
            ServerHealthStatus.UNKNOWN,
            Instant.now().minusSeconds(30),
            null,
            null,
            NextAction.NONE.withoutContext(),
            new ServerStatus.ConfigInfo(
                new ServerStatus.ServerInfo("test-primary", "127.0.0.1", 9001),
                new ServerStatus.ServerInfo("test-secondary", "127.0.0.2", 9002)
            )
        );
        when(monitorService.getStatus()).thenReturn(cachedStatus);

        // When & Then
        given()
            .when()
                .get("/api/status")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("monitor.primary_status", equalTo("UNKNOWN"))
                .body("monitor.secondary_status", equalTo("UNKNOWN"));

        // Verify only getStatus() was called (idempotent)
        verify(monitorService).getStatus();
        verify(monitorService, never()).checkServers();
    }

    @Test
    @DisplayName("Should handle service exceptions gracefully during status check")
    void shouldHandleServiceExceptionsGracefullyDuringStatusCheck() {
        // Given - Service throws exception
        when(monitorService.getStatus()).thenThrow(new RuntimeException("Service error"));

        // When & Then - Should return 500 due to exception
        given()
            .when()
                .get("/api/status")
            .then()
                .statusCode(500);

        verify(monitorService).getStatus();
        verify(monitorService, never()).checkServers();
    }

    @Test
    @DisplayName("Should return valid JSON structure for status response")
    void shouldReturnValidJsonStructureForStatusResponse() {
        // Given - Service status
        ServerStatus status = createServerStatus(DaemonStatus.RUNNING);
        when(monitorService.getStatus()).thenReturn(status);

        // When & Then
        given()
            .when()
                .get("/api/status")
            .then()
                .statusCode(200)
                .contentType("application/json")
                .body("timestamp", notNullValue())
                .body("monitor", notNullValue())
                .body("monitor.daemon_status", notNullValue())
                .body("monitor.current_active", notNullValue())
                .body("monitor.primary_status", notNullValue())
                .body("monitor.secondary_status", notNullValue())
                .body("monitor.last_check", notNullValue())
                .body("monitor.next_action", notNullValue())
                .body("monitor.config", notNullValue())
                .body("monitor.config.primary", notNullValue())
                .body("monitor.config.secondary", notNullValue());

        verify(monitorService).getStatus();
        verify(monitorService, never()).checkServers();
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
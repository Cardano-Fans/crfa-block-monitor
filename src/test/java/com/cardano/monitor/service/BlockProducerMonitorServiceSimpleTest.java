package com.cardano.monitor.service;

import com.cardano.monitor.config.MonitorConfig;
import com.cardano.monitor.model.*;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@DisplayName("BlockProducerMonitorService Simple Tests")
class BlockProducerMonitorServiceSimpleTest {

    @Inject
    BlockProducerMonitorService monitorService;

    @Inject
    MonitorConfig config;

    @InjectMock
    NetworkService networkService;

    @InjectMock
    DnsService dnsService;

    @BeforeEach
    void setUp() {
        reset(networkService, dnsService);
        // Reset service state before each test
        monitorService.stop(); // Ensure stopped state
        monitorService.resetState(); // Reset all internal state
    }

    @Test
    @DisplayName("Should start and stop service successfully")
    // SCENARIO: Basic service lifecycle - verifies the daemon can be started and stopped correctly
    // Tests the fundamental start/stop operations and validates service state transitions
    void shouldStartAndStopService() {
        // Given
        when(networkService.checkHostPort(anyString(), anyInt(), any(Duration.class))).thenReturn(true);

        // Service should already be running by default
        assertEquals(DaemonStatus.RUNNING, monitorService.getStatus().daemonStatus());

        // When - Try to start already running service
        ApiResponse startResponse = monitorService.start();

        // Then - Should return error for already running
        assertFalse(startResponse.success());
        assertEquals("Monitor is already running", startResponse.message());
        assertEquals(DaemonStatus.RUNNING, monitorService.getStatus().daemonStatus());

        // When - Stop service
        ApiResponse stopResponse = monitorService.stop();

        // Then - Service should stop successfully
        assertTrue(stopResponse.success());
        assertEquals("Monitor stopped successfully", stopResponse.message());
        assertEquals(DaemonStatus.STOPPED, monitorService.getStatus().daemonStatus());

        // When - Start service again
        ApiResponse restartResponse = monitorService.start();

        // Then - Service should start successfully
        assertTrue(restartResponse.success());
        assertEquals("Monitor started successfully", restartResponse.message());
        assertEquals(DaemonStatus.RUNNING, monitorService.getStatus().daemonStatus());
    }

    @Test
    @DisplayName("Should return correct status when both servers are healthy")
    // SCENARIO: Happy path monitoring - both servers are up and running normally
    // Verifies the system correctly identifies healthy servers and maintains primary operation
    void shouldReturnCorrectStatusWhenBothServersHealthy() {
        // Given
        when(networkService.checkHostPort(eq(config.primary().host()), eq(config.primary().port()), any(Duration.class))).thenReturn(true);
        when(networkService.checkHostPort(eq(config.secondary().host()), eq(config.secondary().port()), any(Duration.class))).thenReturn(true);

        // When
        ServerStatus status = monitorService.checkServers();

        // Then
        assertEquals(ServerHealthStatus.UP, status.primaryStatus());
        assertEquals(ServerHealthStatus.UP, status.secondaryStatus());
        assertEquals(ServerType.PRIMARY, status.currentActive());
        assertEquals(NextAction.NONE, status.nextAction().getAction());
        assertNotNull(status.lastCheck());
        assertNotNull(status.config());
        assertEquals(config.primary().name(), status.config().primary().name());
        assertEquals(config.secondary().name(), status.config().secondary().name());
    }

    @Test
    @DisplayName("Should detect primary server down")
    // SCENARIO: Primary server failure detection - primary goes down, secondary remains up
    // Tests the system's ability to detect when the primary server becomes unavailable
    void shouldDetectPrimaryServerDown() {
        // Given
        when(networkService.checkHostPort(eq(config.primary().host()), eq(config.primary().port()), any(Duration.class))).thenReturn(false);
        when(networkService.checkHostPort(eq(config.secondary().host()), eq(config.secondary().port()), any(Duration.class))).thenReturn(true);

        // When
        ServerStatus status = monitorService.checkServers();

        // Then
        assertEquals(ServerHealthStatus.DOWN, status.primaryStatus());
        assertEquals(ServerHealthStatus.UP, status.secondaryStatus());
        assertEquals(ServerType.PRIMARY, status.currentActive());
        assertEquals(NextAction.WAITING_FOR_FAILOVER, status.nextAction().getAction());
        assertNotNull(status.primaryDownSince());
        assertNull(status.primaryUpSince());
    }

    @Test
    @DisplayName("Should detect secondary server down")
    // SCENARIO: Secondary server failure while primary is healthy - secondary becomes unavailable
    // Tests monitoring of backup server when primary server is functioning normally
    void shouldDetectSecondaryServerDown() {
        // Given
        when(networkService.checkHostPort(eq(config.primary().host()), eq(config.primary().port()), any(Duration.class))).thenReturn(true);
        when(networkService.checkHostPort(eq(config.secondary().host()), eq(config.secondary().port()), any(Duration.class))).thenReturn(false);

        // When
        ServerStatus status = monitorService.checkServers();

        // Then
        assertEquals(ServerHealthStatus.UP, status.primaryStatus());
        assertEquals(ServerHealthStatus.DOWN, status.secondaryStatus());
        assertEquals(ServerType.PRIMARY, status.currentActive());
        assertEquals(NextAction.NONE, status.nextAction().getAction());
        assertNull(status.primaryDownSince());
        assertNotNull(status.primaryUpSince()); // Primary should have up timestamp
    }

    @Test
    @DisplayName("Should detect both servers down")
    // SCENARIO: Complete network outage - both primary and secondary servers are unreachable
    // Tests graceful handling when no servers are available (should not attempt failover)
    void shouldDetectBothServersDown() {
        // Given
        when(networkService.checkHostPort(anyString(), anyInt(), any(Duration.class))).thenReturn(false);

        // When
        ServerStatus status = monitorService.checkServers();

        // Then
        assertEquals(ServerHealthStatus.DOWN, status.primaryStatus());
        assertEquals(ServerHealthStatus.DOWN, status.secondaryStatus());
        assertEquals(ServerType.PRIMARY, status.currentActive());
        // Initially, it should be waiting for failover, not immediately showing both servers down
        assertEquals(NextAction.WAITING_FOR_FAILOVER, status.nextAction().getAction());
        assertNotNull(status.primaryDownSince());
    }

    @Test
    @DisplayName("Should handle manual switch to secondary")
    // SCENARIO: Manual failover operation - administrator manually switches to secondary
    // Tests manual override functionality when automatic failover is not desired
    void shouldHandleManualSwitchToSecondary() {
        // Given
        when(networkService.checkHostPort(eq(config.secondary().host()), eq(config.secondary().port()), any(Duration.class))).thenReturn(true);
        when(dnsService.switchDnsToServer(ServerType.SECONDARY)).thenReturn(true);

        // When
        ApiResponse response = monitorService.manualSwitch(ServerType.SECONDARY);

        // Then
        assertTrue(response.success());
        assertEquals("Successfully switched to secondary server", response.message());
        
        ServerStatus status = monitorService.getStatus();
        assertEquals(ServerType.SECONDARY, status.currentActive());
        assertTrue(status.manualOverride());
        verify(dnsService).switchDnsToServer(ServerType.SECONDARY);
    }

    @Test
    @DisplayName("Should handle manual switch to primary")
    // SCENARIO: Manual failback operation - switching back to primary after manual secondary operation
    // Tests ability to manually return to primary server after previous manual failover
    void shouldHandleManualSwitchToPrimary() {
        // Given - First manually switch to secondary
        when(networkService.checkHostPort(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(dnsService.switchDnsToServer(any())).thenReturn(true);
        monitorService.manualSwitch(ServerType.SECONDARY);

        // When - Switch back to primary
        ApiResponse response = monitorService.manualSwitch(ServerType.PRIMARY);

        // Then
        assertTrue(response.success());
        assertEquals("Successfully switched to primary server", response.message());
        
        ServerStatus status = monitorService.getStatus();
        assertEquals(ServerType.PRIMARY, status.currentActive());
        assertTrue(status.manualOverride());
        verify(dnsService).switchDnsToServer(ServerType.PRIMARY);
    }

    @Test
    @DisplayName("Should not switch if already using target server")
    // SCENARIO: Invalid manual switch attempt - trying to switch to currently active server
    // Tests error handling when administrator attempts redundant switch operation
    void shouldNotSwitchIfAlreadyUsingTargetServer() {
        // Given - Mock primary as reachable since it's the current active server
        when(networkService.checkHostPort(eq(config.primary().host()), eq(config.primary().port()), any(Duration.class))).thenReturn(true);
        
        // When - Try to switch to primary (already active)
        ApiResponse response = monitorService.manualSwitch(ServerType.PRIMARY);

        // Then
        assertFalse(response.success());
        assertEquals("Already using primary server", response.message());
        verify(dnsService, never()).switchDnsToServer(any());
    }

    @Test
    @DisplayName("Should not switch if target server is unreachable")
    // SCENARIO: Manual switch to failed server - attempting to switch to an unreachable target
    // Tests safety checks that prevent switching to a server that's known to be down
    void shouldNotSwitchIfTargetServerUnreachable() {
        // Given
        when(networkService.checkHostPort(eq(config.secondary().host()), eq(config.secondary().port()), any(Duration.class))).thenReturn(false);

        // When
        ApiResponse response = monitorService.manualSwitch(ServerType.SECONDARY);

        // Then
        assertFalse(response.success());
        assertEquals("Target secondary server (test-secondary) is not reachable", response.message());
        verify(dnsService, never()).switchDnsToServer(any());
    }

    @Test
    @DisplayName("Should clear manual override")
    // SCENARIO: Clearing manual override - returning to automatic monitoring after manual intervention
    // Tests the ability to resume automatic failover/failback after manual control
    void shouldClearManualOverride() {
        // Given - Set manual override first
        when(networkService.checkHostPort(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(dnsService.switchDnsToServer(any())).thenReturn(true);
        monitorService.manualSwitch(ServerType.SECONDARY);

        // When
        ApiResponse response = monitorService.clearManualOverride();

        // Then
        assertTrue(response.success());
        assertEquals("Manual override cleared", response.message());
        
        ServerStatus status = monitorService.getStatus();
        assertFalse(status.manualOverride());
    }

    @Test
    @DisplayName("Should show manual override active in status")
    // SCENARIO: Manual override status reporting - verifying that manual mode is correctly indicated
    // Tests that the system correctly reports manual override state in status responses
    void shouldShowManualOverrideActiveInStatus() {
        // Given - Set manual override
        when(networkService.checkHostPort(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(dnsService.switchDnsToServer(any())).thenReturn(true);
        monitorService.manualSwitch(ServerType.SECONDARY);

        // When
        ServerStatus status = monitorService.checkServers();

        // Then
        assertEquals(NextAction.MANUAL_OVERRIDE_ACTIVE, status.nextAction().getAction());
        assertTrue(status.manualOverride());
    }

    @Test
    @DisplayName("Should handle network service exceptions gracefully")
    // SCENARIO: Network service failure - underlying network checks throw exceptions
    // Tests resilience when network connectivity checks encounter unexpected errors
    void shouldHandleNetworkServiceExceptionsGracefully() {
        // Given
        when(networkService.checkHostPort(eq(config.primary().host()), eq(config.primary().port()), any(Duration.class)))
            .thenThrow(new RuntimeException("Network error"));
        when(networkService.checkHostPort(eq(config.secondary().host()), eq(config.secondary().port()), any(Duration.class)))
            .thenReturn(true);

        // When & Then - Should not throw exception
        ServerStatus status = assertDoesNotThrow(() -> monitorService.checkServers());
        
        // Network exception should be treated as server down
        assertEquals(ServerHealthStatus.DOWN, status.primaryStatus());
        assertEquals(ServerHealthStatus.UP, status.secondaryStatus());
        assertEquals(NextAction.WAITING_FOR_FAILOVER, status.nextAction().getAction());
        assertNotNull(status.primaryDownSince());
    }

    @Test
    @DisplayName("Should validate configuration values")
    // SCENARIO: Configuration validation - verifying test configuration is properly loaded
    // Tests that the service correctly reads and exposes configuration values from test profile
    void shouldValidateConfigurationValues() {
        // Given
        when(networkService.checkHostPort(anyString(), anyInt(), any(Duration.class))).thenReturn(true);

        // When
        ServerStatus status = monitorService.checkServers();

        // Then - Verify configuration from test application.yml
        assertEquals("test-primary", status.config().primary().name());
        assertEquals("127.0.0.1", status.config().primary().host());
        assertEquals(9001, status.config().primary().port());
        
        assertEquals("test-secondary", status.config().secondary().name());
        assertEquals("127.0.0.2", status.config().secondary().host());
        assertEquals(9002, status.config().secondary().port());
    }

    @Test
    @DisplayName("Should maintain state consistency across multiple checks")
    // SCENARIO: State consistency validation - multiple rapid status checks should be consistent
    // Tests that repeated server checks maintain consistent state while updating timestamps
    void shouldMaintainStateConsistencyAcrossMultipleChecks() {
        // Given
        when(networkService.checkHostPort(anyString(), anyInt(), any(Duration.class))).thenReturn(true);

        // When
        ServerStatus status1 = monitorService.checkServers();
        ServerStatus status2 = monitorService.checkServers();
        ServerStatus status3 = monitorService.checkServers();

        // Then
        assertEquals(status1.currentActive(), status2.currentActive());
        assertEquals(status2.currentActive(), status3.currentActive());
        assertEquals(status1.manualOverride(), status2.manualOverride());
        assertEquals(status2.manualOverride(), status3.manualOverride());
        
        // Last check timestamps should be different
        assertTrue(status2.lastCheck().isAfter(status1.lastCheck()));
        assertTrue(status3.lastCheck().isAfter(status2.lastCheck()));
    }
}
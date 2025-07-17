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
    BlockProducerMonitorServiceIF monitorService;

    @Inject
    MonitorConfig config;

    @InjectMock
    NetworkServiceIF networkService;

    @InjectMock
    DnsServiceIF dnsService;

    @BeforeEach
    void setUp() {
        reset(networkService, dnsService);
        // Reset service state before each test
        monitorService.stop(); // Ensure stopped state
        monitorService.resetState(); // Reset all internal state
        
        // Mock DNS service to return PRIMARY by default
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.PRIMARY);
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
        when(networkService.getServerHealthStatus(ServerType.PRIMARY)).thenReturn(ServerHealthStatus.UP);
        when(networkService.getServerHealthStatus(ServerType.SECONDARY)).thenReturn(ServerHealthStatus.UP);

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
        when(networkService.getServerHealthStatus(ServerType.PRIMARY)).thenReturn(ServerHealthStatus.DOWN);
        when(networkService.getServerHealthStatus(ServerType.SECONDARY)).thenReturn(ServerHealthStatus.UP);

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
        when(networkService.getServerHealthStatus(ServerType.PRIMARY)).thenReturn(ServerHealthStatus.UP);
        when(networkService.getServerHealthStatus(ServerType.SECONDARY)).thenReturn(ServerHealthStatus.DOWN);

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
        when(networkService.getServerHealthStatus(ServerType.PRIMARY)).thenReturn(ServerHealthStatus.DOWN);
        when(networkService.getServerHealthStatus(ServerType.SECONDARY)).thenReturn(ServerHealthStatus.DOWN);
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.NONE);

        // When
        ServerStatus status = monitorService.checkServers();

        // Then
        assertEquals(ServerHealthStatus.DOWN, status.primaryStatus());
        assertEquals(ServerHealthStatus.DOWN, status.secondaryStatus());
        assertEquals(ServerType.NONE, status.currentActive());
        // When both servers are down, should show both servers down status
        assertEquals(NextAction.BOTH_SERVERS_DOWN, status.nextAction().getAction());
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
        // Mock DNS service to return SECONDARY after switch
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.PRIMARY)
            .thenReturn(ServerType.SECONDARY); // Return SECONDARY after switch

        // When
        ApiResponse response = monitorService.manualSwitch(ServerType.SECONDARY);

        // Then
        assertTrue(response.success());
        assertEquals("Successfully switched to secondary server", response.message());
        
        ServerStatus status = monitorService.getStatus();
        assertEquals(ServerType.SECONDARY, status.currentActive());
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
        // Mock DNS service to return PRIMARY -> SECONDARY -> PRIMARY sequence
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.PRIMARY)
            .thenReturn(ServerType.SECONDARY) // After first switch
            .thenReturn(ServerType.PRIMARY); // After second switch
        
        monitorService.manualSwitch(ServerType.SECONDARY);

        // When - Switch back to primary
        ApiResponse response = monitorService.manualSwitch(ServerType.PRIMARY);

        // Then
        assertTrue(response.success());
        assertEquals("Successfully switched to primary server", response.message());
        
        ServerStatus status = monitorService.getStatus();
        assertEquals(ServerType.PRIMARY, status.currentActive());
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
    @DisplayName("Should handle network service exceptions gracefully")
    // SCENARIO: Network service failure - underlying network checks throw exceptions
    // Tests resilience when network connectivity checks encounter unexpected errors
    void shouldHandleNetworkServiceExceptionsGracefully() {
        // Given
        when(networkService.getServerHealthStatus(ServerType.PRIMARY))
            .thenThrow(new RuntimeException("Network error"));
        when(networkService.getServerHealthStatus(ServerType.SECONDARY))
            .thenReturn(ServerHealthStatus.UP);

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
        when(networkService.getServerHealthStatus(ServerType.PRIMARY)).thenReturn(ServerHealthStatus.UP);
        when(networkService.getServerHealthStatus(ServerType.SECONDARY)).thenReturn(ServerHealthStatus.UP);

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
        when(networkService.getServerHealthStatus(ServerType.PRIMARY)).thenReturn(ServerHealthStatus.UP);
        when(networkService.getServerHealthStatus(ServerType.SECONDARY)).thenReturn(ServerHealthStatus.UP);

        // When
        ServerStatus status1 = monitorService.checkServers();
        ServerStatus status2 = monitorService.checkServers();
        ServerStatus status3 = monitorService.checkServers();

        // Then
        assertEquals(status1.currentActive(), status2.currentActive());
        assertEquals(status2.currentActive(), status3.currentActive());
        
        // Last check timestamps should be different
        assertTrue(status2.lastCheck().isAfter(status1.lastCheck()));
        assertTrue(status3.lastCheck().isAfter(status2.lastCheck()));
    }

    @Test
    @DisplayName("Should favor primary over secondary")
    // SCENARIO: Primary-first policy - when both servers are available, primary should always be active
    // Tests that secondary is only used when primary is down
    void shouldFavorPrimaryOverSecondary() {
        // Given - Both servers are up
        when(networkService.getServerHealthStatus(ServerType.PRIMARY)).thenReturn(ServerHealthStatus.UP);
        when(networkService.getServerHealthStatus(ServerType.SECONDARY)).thenReturn(ServerHealthStatus.UP);
        when(dnsService.switchDnsToServer(any())).thenReturn(true);

        // When - Starting from NONE state (both servers down scenario)
        monitorService.resetState(); // This sets currentActive to PRIMARY
        ServerStatus status = monitorService.checkServers();

        // Then - Should be using PRIMARY
        assertEquals(ServerType.PRIMARY, status.currentActive());
        assertEquals(NextAction.NONE, status.nextAction().getAction());
    }

    @Test
    @DisplayName("Should switch from NONE to primary when primary comes up")
    // SCENARIO: Recovery from total outage - primary server comes back online first
    // Tests that system recovers to primary when it becomes available
    void shouldSwitchFromNoneToPrimaryWhenPrimaryComesUp() {
        // Given - DNS service returns NONE (no active server)
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.NONE);
        // Primary comes up, secondary stays down
        when(networkService.getServerHealthStatus(ServerType.PRIMARY)).thenReturn(ServerHealthStatus.UP);
        when(networkService.getServerHealthStatus(ServerType.SECONDARY)).thenReturn(ServerHealthStatus.DOWN);
        when(dnsService.switchDnsToServer(ServerType.PRIMARY)).thenReturn(true);
        
        // When
        ServerStatus status = monitorService.checkServers();

        // Then - Should switch to PRIMARY
        assertEquals(ServerType.PRIMARY, status.currentActive());
        assertEquals(NextAction.SWITCHED_TO_PRIMARY, status.nextAction().getAction());
        verify(dnsService).switchDnsToServer(ServerType.PRIMARY);
    }

    @Test  
    @DisplayName("Should prefer primary when both come up from NONE state")
    // SCENARIO: Recovery from total outage - both servers come back online simultaneously
    // Tests that primary is chosen when both are available after NONE state
    void shouldPreferPrimaryWhenBothComeUpFromNone() {
        // Given - DNS service returns NONE (no active server)
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.NONE);
        // Both servers come up
        when(networkService.getServerHealthStatus(ServerType.PRIMARY)).thenReturn(ServerHealthStatus.UP);
        when(networkService.getServerHealthStatus(ServerType.SECONDARY)).thenReturn(ServerHealthStatus.UP);
        when(dnsService.switchDnsToServer(ServerType.PRIMARY)).thenReturn(true);
        
        // When
        ServerStatus status = monitorService.checkServers();

        // Then - Should choose PRIMARY (not SECONDARY)
        assertEquals(ServerType.PRIMARY, status.currentActive());
        assertEquals(NextAction.SWITCHED_TO_PRIMARY, status.nextAction().getAction());
        verify(dnsService).switchDnsToServer(ServerType.PRIMARY);
        verify(dnsService, never()).switchDnsToServer(ServerType.SECONDARY);
    }
}
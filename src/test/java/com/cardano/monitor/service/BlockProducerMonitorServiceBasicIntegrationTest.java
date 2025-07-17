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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@DisplayName("BlockProducerMonitorService Basic Integration Tests")
class BlockProducerMonitorServiceBasicIntegrationTest {

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
        // Reset all mocks before each test
        reset(networkService, dnsService);
        // Reset service state before each test
        monitorService.stop(); // Ensure stopped state
        monitorService.resetState(); // Reset all internal state
        
        // Mock DNS service to return PRIMARY by default
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.PRIMARY);
    }

    @Test
    @DisplayName("Should integrate with all components for basic status check")
    // SCENARIO: Basic integration validation - ensures all injected components work together
    // Tests fundamental integration between monitor service, network service, and configuration
    // Validates happy path component interaction and proper dependency injection
    void shouldIntegrateWithAllComponentsForBasicStatusCheck() {
        // Given
        when(networkService.getServerHealthStatus(ServerType.PRIMARY)).thenReturn(ServerHealthStatus.UP);
        when(networkService.getServerHealthStatus(ServerType.SECONDARY)).thenReturn(ServerHealthStatus.UP);

        // When
        ServerStatus status = monitorService.checkServers();

        // Then
        assertNotNull(status);
        assertEquals(ServerType.PRIMARY, status.currentActive());
        assertEquals(ServerHealthStatus.UP, status.primaryStatus());
        assertEquals(ServerHealthStatus.UP, status.secondaryStatus());
        assertEquals(NextAction.NONE, status.nextAction().getAction());
        assertNotNull(status.config());
        assertNotNull(status.lastCheck());
        
        // Verify network service was called with correct config
        verify(networkService).getServerHealthStatus(ServerType.PRIMARY);
        verify(networkService).getServerHealthStatus(ServerType.SECONDARY);
    }

    @Test
    @DisplayName("Should handle service lifecycle integration")
    // SCENARIO: Service state management integration - testing daemon control integration
    // Tests integration between service lifecycle (start/stop) and monitoring state
    // Validates proper state transitions and daemon status reporting
    void shouldHandleServiceLifecycleIntegration() {
        // Given
        when(networkService.getServerHealthStatus(ServerType.PRIMARY)).thenReturn(ServerHealthStatus.UP);
        when(networkService.getServerHealthStatus(ServerType.SECONDARY)).thenReturn(ServerHealthStatus.UP);

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
        ServerStatus statusAfterStop = monitorService.getStatus();

        // Then - Service should be stopped
        assertTrue(stopResponse.success());
        assertEquals(DaemonStatus.STOPPED, statusAfterStop.daemonStatus());

        // When - Start service again
        ApiResponse restartResponse = monitorService.start();
        ServerStatus statusAfterRestart = monitorService.getStatus();

        // Then - Service should start successfully
        assertTrue(restartResponse.success());
        assertEquals(DaemonStatus.RUNNING, statusAfterRestart.daemonStatus());
    }

    @Test
    @DisplayName("Should handle manual switch integration")
    // SCENARIO: Manual switch integration - testing manual failover with DNS service integration
    // Tests end-to-end manual switch operation including DNS update and state management
    // Validates integration between manual operations and external DNS service
    void shouldHandleManualSwitchIntegration() {
        // Given
        when(networkService.checkHostPort(eq(config.secondary().host()), eq(config.secondary().port()), any(Duration.class)))
            .thenReturn(true);
        when(dnsService.switchDnsToServer(ServerType.SECONDARY)).thenReturn(true);
        // Mock DNS service to return SECONDARY after switch
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.PRIMARY)
            .thenReturn(ServerType.SECONDARY); // After switch

        // When
        ApiResponse response = monitorService.manualSwitch(ServerType.SECONDARY);
        ServerStatus status = monitorService.getStatus();

        // Then
        assertTrue(response.success());
        assertEquals(ServerType.SECONDARY, status.currentActive());
        
        // Verify DNS service was called
        verify(dnsService).switchDnsToServer(ServerType.SECONDARY);
    }

    @Test
    @DisplayName("Should handle DNS service failure integration")
    // SCENARIO: DNS service failure integration - testing error handling when DNS updates fail
    // Tests proper rollback and error reporting when external DNS service is unavailable
    // Validates integration error handling and state consistency during failures
    void shouldHandleDnsServiceFailureIntegration() {
        // Given
        when(networkService.checkHostPort(eq(config.secondary().host()), eq(config.secondary().port()), any(Duration.class)))
            .thenReturn(true);
        when(dnsService.switchDnsToServer(ServerType.SECONDARY)).thenReturn(false);

        // When
        ApiResponse response = monitorService.manualSwitch(ServerType.SECONDARY);
        ServerStatus status = monitorService.getStatus();

        // Then
        assertFalse(response.success());
        assertEquals("Failed to switch to secondary server", response.message());
        assertEquals(ServerType.PRIMARY, status.currentActive());
    }

    @Test
    @DisplayName("Should handle network service failure integration")
    // SCENARIO: Network service failure integration - testing server health check integration
    // Tests integration when network connectivity checks detect server failures
    // Validates proper failure detection and state tracking integration
    void shouldHandleNetworkServiceFailureIntegration() {
        // Given
        when(networkService.getServerHealthStatus(ServerType.PRIMARY)).thenReturn(ServerHealthStatus.DOWN);
        when(networkService.getServerHealthStatus(ServerType.SECONDARY)).thenReturn(ServerHealthStatus.UP);

        // When
        ServerStatus status = monitorService.checkServers();

        // Then
        assertEquals(ServerHealthStatus.DOWN, status.primaryStatus());
        assertEquals(ServerHealthStatus.UP, status.secondaryStatus());
        assertEquals(NextAction.WAITING_FOR_FAILOVER, status.nextAction().getAction());
        assertNotNull(status.primaryDownSince());
    }

    @Test
    @DisplayName("Should handle concurrent operations integration")
    // SCENARIO: Basic concurrent operations integration - testing thread safety at integration level
    // Tests simple multi-threading scenarios with 2 threads performing basic operations
    // Validates thread safety and state consistency for basic concurrent access
    void shouldHandleConcurrentOperationsIntegration() throws InterruptedException {
        // Given
        when(networkService.getServerHealthStatus(ServerType.PRIMARY)).thenReturn(ServerHealthStatus.UP);
        when(networkService.getServerHealthStatus(ServerType.SECONDARY)).thenReturn(ServerHealthStatus.UP);
        when(dnsService.switchDnsToServer(any())).thenReturn(true);
        // Mock DNS service to handle multiple calls (default to PRIMARY, then SECONDARY after switch)
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.PRIMARY, ServerType.PRIMARY, ServerType.SECONDARY);

        CountDownLatch latch = new CountDownLatch(2);
        
        // Thread 1: Check servers
        Thread checkThread = new Thread(() -> {
            try {
                for (int i = 0; i < 3; i++) {
                    monitorService.checkServers();
                    Thread.sleep(100);
                }
                latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Thread 2: Manual operations
        Thread manualThread = new Thread(() -> {
            try {
                Thread.sleep(150);
                monitorService.manualSwitch(ServerType.SECONDARY);
                Thread.sleep(150);
                latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // When
        checkThread.start();
        manualThread.start();

        // Then
        assertTrue(latch.await(5, TimeUnit.SECONDS));
        
        // Final state should be consistent
        ServerStatus finalStatus = monitorService.getStatus();
        assertNotNull(finalStatus.currentActive());
        assertNotNull(finalStatus.nextAction());
        assertNotNull(finalStatus.daemonStatus());
    }

    @Test
    @DisplayName("Should validate configuration integration")
    // SCENARIO: Configuration integration validation - ensuring test configuration is properly loaded
    // Tests integration between configuration service and monitoring service
    // Validates that test profile configuration values are correctly injected and accessible
    void shouldValidateConfigurationIntegration() {
        // Given
        when(networkService.getServerHealthStatus(ServerType.PRIMARY)).thenReturn(ServerHealthStatus.UP);
        when(networkService.getServerHealthStatus(ServerType.SECONDARY)).thenReturn(ServerHealthStatus.UP);

        // When
        ServerStatus status = monitorService.checkServers();

        // Then - Should use test profile configuration
        assertEquals("test-primary", status.config().primary().name());
        assertEquals("127.0.0.1", status.config().primary().host());
        assertEquals(9001, status.config().primary().port());
        
        assertEquals("test-secondary", status.config().secondary().name());
        assertEquals("127.0.0.2", status.config().secondary().host());
        assertEquals(9002, status.config().secondary().port());
    }

    @Test
    @DisplayName("Should handle multiple status checks consistently")
    // SCENARIO: State consistency across multiple operations - testing integration stability
    // Tests that repeated status checks maintain consistent state while updating timestamps
    // Validates integration stability and proper state management across multiple calls
    void shouldHandleMultipleStatusChecksConsistently() {
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
}
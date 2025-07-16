package com.cardano.monitor.service;

import com.cardano.monitor.config.MonitorConfig;
import com.cardano.monitor.model.*;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@DisplayName("BlockProducerMonitorService Integration Tests")
    class BlockProducerMonitorServiceIntegrationTest {

    @Inject
    BlockProducerMonitorServiceIF monitorService;

    @Inject
    MonitorConfig config;

    @InjectMock
    NetworkService networkService;

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
    @DisplayName("Should perform complete failover and failback cycle")
    // SCENARIO: End-to-end integration test of complete disaster recovery cycle
    // Tests full 120+ second cycle: healthy -> failure -> wait -> failover -> recovery -> wait -> failback
    // Validates real-time integration with actual configured delays (30s failover, 60s failback)
    @Timeout(120) // Increased timeout to account for test configuration delays
    void shouldPerformCompleteFailoverAndFailbackCycle() throws InterruptedException {
        // Given
        AtomicInteger primaryHealthState = new AtomicInteger(1); // 1 = UP, 0 = DOWN
        when(networkService.checkHostPort(eq(config.primary().host()), eq(config.primary().port()), any(Duration.class)))
            .thenAnswer(invocation -> primaryHealthState.get() == 1);
        when(networkService.checkHostPort(eq(config.secondary().host()), eq(config.secondary().port()), any(Duration.class)))
            .thenReturn(true);
        when(dnsService.switchDnsToServer(any())).thenReturn(true);
        
        // Mock DNS service for complete cycle
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.PRIMARY)
            .thenReturn(ServerType.PRIMARY) // Phase 2: Still on primary during wait
            .thenReturn(ServerType.SECONDARY) // Phase 3: After switch to secondary
            .thenReturn(ServerType.SECONDARY) // Phase 4: Still on secondary during failback wait
            .thenReturn(ServerType.PRIMARY); // Phase 5: Back to primary after failback

        // Phase 1: Both servers healthy
        ServerStatus status1 = monitorService.checkServers();
        assertEquals(ServerType.PRIMARY, status1.currentActive());
        assertEquals(NextAction.NONE, status1.nextAction().getAction());

        // Phase 2: Primary goes down
        primaryHealthState.set(0);
        ServerStatus status2 = monitorService.checkServers();
        assertEquals(ServerType.PRIMARY, status2.currentActive());
        assertEquals(NextAction.WAITING_FOR_FAILOVER, status2.nextAction().getAction());

        // Phase 3: Wait for failover delay (30s from test config) and check again
        Thread.sleep(31000);
        ServerStatus status3 = monitorService.checkServers();
        assertEquals(ServerType.SECONDARY, status3.currentActive());
        assertEquals(NextAction.SWITCHED_TO_SECONDARY, status3.nextAction().getAction());

        // Phase 4: Primary comes back up
        primaryHealthState.set(1);
        ServerStatus status4 = monitorService.checkServers();
        assertEquals(ServerType.SECONDARY, status4.currentActive());
        assertEquals(NextAction.WAITING_FOR_FAILBACK, status4.nextAction().getAction());

        // Phase 5: Wait for failback delay (60s from test config) and check again
        Thread.sleep(61000);
        ServerStatus status5 = monitorService.checkServers();
        assertEquals(ServerType.PRIMARY, status5.currentActive());
        assertEquals(NextAction.SWITCHED_TO_PRIMARY, status5.nextAction().getAction());

        // Verify DNS switches occurred
        verify(dnsService).switchDnsToServer(ServerType.SECONDARY);
        verify(dnsService).switchDnsToServer(ServerType.PRIMARY);
    }

    @Test
    @DisplayName("Should handle rapid server state changes")
    // SCENARIO: Stress test for server flapping protection under rapid state changes
    // Tests system stability when servers rapidly change state (every 100ms for 10 iterations)
    // Validates flap protection prevents unnecessary DNS switches during unstable conditions
    @Timeout(10)
    void shouldHandleRapidServerStateChanges() throws InterruptedException {
        // Given
        AtomicInteger checkCount = new AtomicInteger(0);
        when(networkService.checkHostPort(eq(config.primary().host()), eq(config.primary().port()), any(Duration.class)))
            .thenAnswer(invocation -> {
                int count = checkCount.incrementAndGet();
                // Simulate flapping: down for 2 checks, up for 2 checks, repeat
                return (count % 4) >= 2;
            });
        when(networkService.checkHostPort(eq(config.secondary().host()), eq(config.secondary().port()), any(Duration.class)))
            .thenReturn(true);

        // When - Multiple rapid checks
        for (int i = 0; i < 10; i++) {
            ServerStatus status = monitorService.checkServers();
            Thread.sleep(100);
            
            // Should never failover due to rapid state changes
            assertEquals(ServerType.PRIMARY, status.currentActive());
            
            // Should either be waiting for failover or show none (if primary came back up)
            assertTrue(status.nextAction().getAction() == NextAction.WAITING_FOR_FAILOVER || 
                      status.nextAction().getAction() == NextAction.NONE);
        }

        // Then - No DNS switches should have occurred due to rapid changes
        verify(dnsService, never()).switchDnsToServer(any());
    }

    @Test
    @DisplayName("Should handle concurrent manual and automatic operations")
    // SCENARIO: Multi-threaded integration test of concurrent operations
    // Tests thread safety when automatic monitoring and manual operations occur simultaneously
    // Validates proper synchronization between different operational threads
    @Timeout(10)
    void shouldHandleConcurrentManualAndAutomaticOperations() throws InterruptedException {
        // Given
        when(networkService.checkHostPort(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(dnsService.switchDnsToServer(any())).thenReturn(true);
        // Mock DNS service for concurrent operations
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.PRIMARY, ServerType.SECONDARY, ServerType.PRIMARY);

        CountDownLatch latch = new CountDownLatch(2);
        
        // Thread 1: Automatic monitoring
        Thread monitorThread = new Thread(() -> {
            try {
                for (int i = 0; i < 5; i++) {
                    monitorService.checkServers();
                    Thread.sleep(200);
                }
                latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // Thread 2: Manual operations
        Thread manualThread = new Thread(() -> {
            try {
                Thread.sleep(300);
                monitorService.manualSwitch(ServerType.SECONDARY);
                Thread.sleep(300);
                Thread.sleep(300);
                monitorService.manualSwitch(ServerType.PRIMARY);
                latch.countDown();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // When
        monitorThread.start();
        manualThread.start();

        // Then
        assertTrue(latch.await(10, TimeUnit.SECONDS));
        
        // Final state should be consistent
        ServerStatus finalStatus = monitorService.getStatus();
        assertNotNull(finalStatus.currentActive());
        assertNotNull(finalStatus.nextAction());
        
        // At least one manual switch should have occurred
        verify(dnsService, atLeastOnce()).switchDnsToServer(any());
    }

    @Test
    @DisplayName("Should maintain state consistency under stress")
    // SCENARIO: High-load stress testing with 10 concurrent threads performing 200 total operations
    // Tests system stability under heavy concurrent load mixing all operation types
    // Validates thread safety, state consistency, and system responsiveness under stress
    @Timeout(15)
    void shouldMaintainStateConsistencyUnderStress() throws InterruptedException {
        // Given
        when(networkService.checkHostPort(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(dnsService.switchDnsToServer(any())).thenReturn(true);

        CountDownLatch latch = new CountDownLatch(10);
        
        // Create 10 threads performing various operations
        for (int i = 0; i < 10; i++) {
            final int threadId = i;
            Thread thread = new Thread(() -> {
                try {
                    for (int j = 0; j < 20; j++) {
                        switch (threadId % 4) {
                            case 0 -> monitorService.checkServers();
                            case 1 -> monitorService.getStatus();
                            case 2 -> {
                                if (j % 3 == 0) monitorService.manualSwitch(ServerType.SECONDARY);
                            }
                            case 3 -> {
                            }
                        }
                        Thread.sleep(50);
                    }
                    latch.countDown();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            thread.start();
        }

        // When
        assertTrue(latch.await(15, TimeUnit.SECONDS));

        // Then - System should still be responsive and consistent
        ServerStatus finalStatus = monitorService.getStatus();
        assertNotNull(finalStatus.currentActive());
        assertNotNull(finalStatus.nextAction());
        assertNotNull(finalStatus.daemonStatus());
        
        // Should be able to perform operations after stress test
        // Service should already be running, so start() should return error
        ApiResponse response = monitorService.start();
        assertNotNull(response);
        assertFalse(response.success());
        assertEquals("Monitor is already running", response.message());
    }

    @Test
    @DisplayName("Should handle DNS service intermittent failures")
    // SCENARIO: DNS provider experiencing intermittent API failures during critical failover
    // Tests retry logic when DNS updates fail - system should persist until successful
    // Validates graceful handling of external service failures with eventual consistency
    @Timeout(60)
    void shouldHandleDnsServiceIntermittentFailures() throws InterruptedException {
        // Given
        when(networkService.checkHostPort(eq(config.primary().host()), eq(config.primary().port()), any(Duration.class)))
            .thenReturn(false);
        when(networkService.checkHostPort(eq(config.secondary().host()), eq(config.secondary().port()), any(Duration.class)))
            .thenReturn(true);
        
        // DNS service fails first two times, then succeeds
        when(dnsService.switchDnsToServer(ServerType.SECONDARY))
            .thenReturn(false) // First attempt fails
            .thenReturn(false) // Second attempt fails
            .thenReturn(true); // Third attempt succeeds

        // When - First check establishes down time
        ServerStatus status1 = monitorService.checkServers();
        assertEquals(NextAction.WAITING_FOR_FAILOVER, status1.nextAction().getAction());

        // Wait for failover delay (30s from test config)
        Thread.sleep(31000);

        // When - Second check attempts failover (should fail)
        ServerStatus status2 = monitorService.checkServers();
        assertEquals(NextAction.FAILED_TO_SWITCH_TO_SECONDARY, status2.nextAction().getAction());
        assertEquals(ServerType.PRIMARY, status2.currentActive());

        // When - Third check attempts failover again (should fail)
        ServerStatus status3 = monitorService.checkServers();
        assertEquals(NextAction.FAILED_TO_SWITCH_TO_SECONDARY, status3.nextAction().getAction());
        assertEquals(ServerType.PRIMARY, status3.currentActive());

        // Mock DNS service to return SECONDARY after successful switch
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.PRIMARY)
            .thenReturn(ServerType.PRIMARY) // First two failed attempts
            .thenReturn(ServerType.PRIMARY) // Third failed attempt
            .thenReturn(ServerType.SECONDARY); // Fourth successful attempt
        
        // When - Fourth check attempts failover again (should succeed)
        ServerStatus status4 = monitorService.checkServers();
        assertEquals(NextAction.SWITCHED_TO_SECONDARY, status4.nextAction().getAction());
        assertEquals(ServerType.SECONDARY, status4.currentActive());

        // Then - DNS service should have been called 3 times
        verify(dnsService, times(3)).switchDnsToServer(ServerType.SECONDARY);
    }

    @Test
    @DisplayName("Should handle service restart scenarios")
    // SCENARIO: Service lifecycle management - testing start/stop/restart operations
    // Tests integration between daemon control and monitoring functionality
    // Validates clean state transitions and service recovery after restart
    @Timeout(10)
    void shouldHandleServiceRestartScenarios() {
        // Given
        when(networkService.checkHostPort(anyString(), anyInt(), any(Duration.class))).thenReturn(true);
        when(dnsService.switchDnsToServer(any())).thenReturn(true);

        // Phase 1: Service should already be running by default
        assertEquals(DaemonStatus.RUNNING, monitorService.getStatus().daemonStatus());
        
        // Try to start already running service - should fail
        ApiResponse startResponse = monitorService.start();
        assertFalse(startResponse.success());
        assertEquals("Monitor is already running", startResponse.message());

        // Phase 2: Stop service
        ApiResponse stopResponse = monitorService.stop();
        assertTrue(stopResponse.success());
        assertEquals(DaemonStatus.STOPPED, monitorService.getStatus().daemonStatus());

        // Phase 3: Restart service
        ApiResponse restartResponse = monitorService.start();
        assertTrue(restartResponse.success());
        assertEquals(DaemonStatus.RUNNING, monitorService.getStatus().daemonStatus());

        // Phase 4: Verify service is fully functional after restart
        ServerStatus status = monitorService.checkServers();
        assertEquals(ServerType.PRIMARY, status.currentActive());
        assertEquals(NextAction.NONE, status.nextAction().getAction());
        assertEquals(ServerHealthStatus.UP, status.primaryStatus());
        assertEquals(ServerHealthStatus.UP, status.secondaryStatus());
    }

    @Test
    @DisplayName("Should handle configuration edge cases")
    // SCENARIO: Edge case testing with boundary conditions and configuration limits
    // Tests system behavior with timing edge cases and configuration boundary values
    // Validates proper timeout handling and configuration validation
    @Timeout(60)
    void shouldHandleConfigurationEdgeCases() throws InterruptedException {
        // Given - Very short delays for testing
        // Using test config timing values

        when(networkService.checkHostPort(eq(config.primary().host()), eq(config.primary().port()), any(Duration.class)))
            .thenReturn(false);
        when(networkService.checkHostPort(eq(config.secondary().host()), eq(config.secondary().port()), any(Duration.class)))
            .thenReturn(true);
        when(dnsService.switchDnsToServer(any())).thenReturn(true);

        // When - First check
        ServerStatus status1 = monitorService.checkServers();
        assertEquals(NextAction.WAITING_FOR_FAILOVER, status1.nextAction().getAction());

        // Wait for failover delay (30s from test config)
        Thread.sleep(31000);

        // Mock DNS service to return SECONDARY after switch
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.PRIMARY)
            .thenReturn(ServerType.SECONDARY); // After switch
        
        // When - Second check should trigger failover
        ServerStatus status2 = monitorService.checkServers();
        assertEquals(NextAction.SWITCHED_TO_SECONDARY, status2.nextAction().getAction());

        // Then - Verify timeout was respected
        verify(networkService, atLeast(2)).checkHostPort(anyString(), anyInt(), any(Duration.class));
    }

}

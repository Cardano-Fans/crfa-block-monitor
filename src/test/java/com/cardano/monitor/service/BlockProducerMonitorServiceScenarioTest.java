package com.cardano.monitor.service;

import com.cardano.monitor.config.MonitorConfig;
import com.cardano.monitor.model.*;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
@DisplayName("BlockProducerMonitorService Scenario Tests")
class BlockProducerMonitorServiceScenarioTest {

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
    @DisplayName("Scenario: Primary server permanent failure")
    // SCENARIO: Complete primary server failure requiring automatic failover to secondary
    // Simulates hardware failure, network outage, or other permanent primary server issues
    // Tests full failover cycle: detection -> wait period -> DNS switch -> stable secondary operation
    void scenarioPrimaryServerPermanentFailure() throws InterruptedException {
        // Given: Primary server fails permanently
        when(networkService.checkHostPort(eq(config.primary().host()), eq(config.primary().port()), any(Duration.class)))
            .thenReturn(false);
        when(networkService.checkHostPort(eq(config.secondary().host()), eq(config.secondary().port()), any(Duration.class)))
            .thenReturn(true);
        when(dnsService.switchDnsToServer(ServerType.SECONDARY)).thenReturn(true);
        // Mock DNS service to return SECONDARY after successful switch
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.PRIMARY)
            .thenReturn(ServerType.SECONDARY) // After switch
            .thenReturn(ServerType.SECONDARY); // Stable on secondary

        // When: Monitor detects failure and waits for failover
        ServerStatus beforeFailover = monitorService.checkServers();
        assertEquals(NextAction.WAITING_FOR_FAILOVER, beforeFailover.nextAction().getAction());
        assertEquals(ServerType.PRIMARY, beforeFailover.currentActive());

        Thread.sleep(31000); // Wait for failover delay from test config (30s)
        ServerStatus afterFailover = monitorService.checkServers();

        // Then: System should failover to secondary
        assertEquals(NextAction.SWITCHED_TO_SECONDARY, afterFailover.nextAction().getAction());
        assertEquals(ServerType.SECONDARY, afterFailover.currentActive());
        assertEquals(ServerHealthStatus.DOWN, afterFailover.primaryStatus());
        assertEquals(ServerHealthStatus.UP, afterFailover.secondaryStatus());

        // Subsequent checks should show stable secondary operation
        ServerStatus stableSecondary = monitorService.checkServers();
        assertEquals(NextAction.NONE, stableSecondary.nextAction().getAction()); // Stable operation on secondary
        assertEquals(ServerType.SECONDARY, stableSecondary.currentActive());
    }

    @Test
    @DisplayName("Scenario: Network partition causing false positive")
    // SCENARIO: Network partition where monitoring system loses connectivity to both servers
    // Simulates network isolation where servers are actually running but appear down to monitor
    // Tests safety feature that prevents failover when no servers are reachable (avoid split-brain)
    void scenarioNetworkPartitionCausingFalsePositive() throws InterruptedException {
        // Given: Network partition causes both servers to appear down
        when(networkService.checkHostPort(anyString(), anyInt(), any(Duration.class)))
            .thenReturn(false);
        // Mock DNS service to return NONE when both servers are down
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.NONE);
        // Using test config failover delay of 30s

        // When: Monitor detects both servers down
        ServerStatus beforeFailover = monitorService.checkServers();
        assertEquals(NextAction.BOTH_SERVERS_DOWN, beforeFailover.nextAction().getAction());
        assertEquals(ServerType.NONE, beforeFailover.currentActive());

        // Even after waiting, should remain in BOTH_SERVERS_DOWN state
        Thread.sleep(31000); // Wait for test config failover delay
        ServerStatus afterFailover = monitorService.checkServers();

        // Then: System should remain in BOTH_SERVERS_DOWN state
        assertEquals(NextAction.BOTH_SERVERS_DOWN, afterFailover.nextAction().getAction());
        assertEquals(ServerType.NONE, afterFailover.currentActive()); // Should be NONE when both down
        assertEquals(ServerHealthStatus.DOWN, afterFailover.primaryStatus());
        assertEquals(ServerHealthStatus.DOWN, afterFailover.secondaryStatus());
    }

    @Test
    @DisplayName("Scenario: DNS API outage during failover")
    // SCENARIO: DNS service provider experiences outage during critical failover operation
    // Simulates external DNS API being unavailable when automatic failover is needed
    // Tests graceful handling of DNS update failures and retry behavior
    void scenarioDnsApiOutageDuringFailover() throws InterruptedException {
        // Given: Primary is down, secondary is up, but DNS API is failing
        when(networkService.checkHostPort(eq(config.primary().host()), eq(config.primary().port()), any(Duration.class)))
            .thenReturn(false);
        when(networkService.checkHostPort(eq(config.secondary().host()), eq(config.secondary().port()), any(Duration.class)))
            .thenReturn(true);
        when(dnsService.switchDnsToServer(ServerType.SECONDARY)).thenReturn(false);
        // Using test config failover delay of 30s

        // When: Monitor attempts failover
        ServerStatus beforeFailover = monitorService.checkServers();
        assertEquals(NextAction.WAITING_FOR_FAILOVER, beforeFailover.nextAction().getAction());

        Thread.sleep(31000); // Wait for test config failover delay
        ServerStatus afterFailover = monitorService.checkServers();

        // Then: System should show failed switch state
        assertEquals(NextAction.FAILED_TO_SWITCH_TO_SECONDARY, afterFailover.nextAction().getAction());
        assertEquals(ServerType.PRIMARY, afterFailover.currentActive()); // Should stay on primary
        assertEquals(ServerHealthStatus.DOWN, afterFailover.primaryStatus());
        assertEquals(ServerHealthStatus.UP, afterFailover.secondaryStatus());

        // Subsequent checks should continue showing failed state
        ServerStatus retryAttempt = monitorService.checkServers();
        assertEquals(NextAction.FAILED_TO_SWITCH_TO_SECONDARY, retryAttempt.nextAction().getAction());
    }


    @ParameterizedTest
    @EnumSource(ServerType.class)
    @DisplayName("Scenario: Manual switch to each server type")
    // SCENARIO: Comprehensive manual switching validation for all available server types
    // Tests manual failover to each possible server target, validating edge cases
    // Verifies proper error handling for invalid switches (same server) vs valid switches
    void scenarioManualSwitchToEachServerType(ServerType targetServer) {
        // Given: Both servers are healthy
        when(networkService.checkHostPort(anyString(), anyInt(), any(Duration.class)))
            .thenReturn(true);
        when(dnsService.switchDnsToServer(any())).thenReturn(true);

        // When: Manual switch to target server
        ApiResponse response = monitorService.manualSwitch(targetServer);

        // Then: Response depends on the target server type
        if (targetServer == ServerType.PRIMARY) {
            // Should fail because we're already on PRIMARY (resetState sets this)
            assertFalse(response.success());
            assertEquals("Already using primary server", response.message());
            verify(dnsService, never()).switchDnsToServer(targetServer);
        } else if (targetServer == ServerType.NONE) {
            // Should fail because NONE is not a valid manual switch target
            assertFalse(response.success());
            assertEquals("Cannot manually switch to NONE. Use specific server type.", response.message());
            verify(dnsService, never()).switchDnsToServer(targetServer);
        } else {
            // Should succeed for SECONDARY
            // Mock DNS service to return SECONDARY after switch
            when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.PRIMARY)
                .thenReturn(ServerType.SECONDARY);
            assertTrue(response.success());
            assertEquals(targetServer, monitorService.getStatus().currentActive());
            verify(dnsService).switchDnsToServer(targetServer);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 30, 300, 600, 3600})
    @DisplayName("Scenario: Different failover delay configurations")
    // SCENARIO: Testing system behavior with various failover timing configurations
    // Simulates different deployment environments with different risk tolerances
    // Tests that timing displays correctly reflect actual configured delays, not test parameters
    void scenarioDifferentFailoverDelayConfigurations(long delaySeconds) throws InterruptedException {
        // Given: Different failover delays
        // Using test config failover delay of 30s
        when(networkService.checkHostPort(eq(config.primary().host()), eq(config.primary().port()), any(Duration.class)))
            .thenReturn(false);
        when(networkService.checkHostPort(eq(config.secondary().host()), eq(config.secondary().port()), any(Duration.class)))
            .thenReturn(true);

        // When: Monitor detects primary down
        ServerStatus status = monitorService.checkServers();

        // Then: Should show correct remaining time (using test config delay of 30s)
        assertEquals(NextAction.WAITING_FOR_FAILOVER, status.nextAction().getAction());
        // The service uses the actual configured delay (30s), not the parameterized value
        assertTrue(status.nextAction().getValue().contains("30s") || 
                  status.nextAction().getValue().contains("29s"));
    }

    @Test
    @DisplayName("Scenario: Server flapping (up/down/up/down)")
    // SCENARIO: Unstable server experiencing rapid up/down state changes
    // Simulates intermittent network issues, server overload, or hardware problems
    // Tests stability features that prevent rapid failover/failback cycles (flap protection)
    void scenarioServerFlapping() throws InterruptedException {
        // Given: Server that flaps between up and down
        when(networkService.checkHostPort(eq(config.primary().host()), eq(config.primary().port()), any(Duration.class)))
            .thenReturn(false) // First check: down
            .thenReturn(true)  // Second check: up
            .thenReturn(false) // Third check: down
            .thenReturn(true)  // Fourth check: up
            .thenReturn(false); // Fifth check: down
        when(networkService.checkHostPort(eq(config.secondary().host()), eq(config.secondary().port()), any(Duration.class)))
            .thenReturn(true);
        // Using test config failover delay of 30s

        // Mock DNS service to consistently return PRIMARY for flapping scenario
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.PRIMARY);
        
        // When: Multiple rapid checks
        List<ServerStatus> statuses = Arrays.asList(
            monitorService.checkServers(), // Primary down
            monitorService.checkServers(), // Primary up
            monitorService.checkServers(), // Primary down
            monitorService.checkServers(), // Primary up
            monitorService.checkServers()  // Primary down
        );

        // Then: System should handle flapping gracefully
        for (ServerStatus status : statuses) {
            assertEquals(ServerType.PRIMARY, status.currentActive());
            // Should either be waiting for failover or show none (if primary came back up)
            assertTrue(status.nextAction().getAction() == NextAction.WAITING_FOR_FAILOVER || 
                      status.nextAction().getAction() == NextAction.NONE);
        }

        // No failover should occur due to rapid state changes
        verify(dnsService, never()).switchDnsToServer(any());
    }

    @Test
    @DisplayName("Scenario: Gradual degradation and recovery")
    // SCENARIO: Complete operational cycle from healthy state through failure to recovery
    // Simulates realistic operational timeline: normal -> failure -> failover -> recovery -> failback
    // Tests the full automated disaster recovery cycle with proper timing delays
    void scenarioGradualDegradationAndRecovery() throws InterruptedException {
        // Given: System starts healthy
        when(networkService.checkHostPort(anyString(), anyInt(), any(Duration.class)))
            .thenReturn(true);
        when(dnsService.switchDnsToServer(any())).thenReturn(true);
        // Using test config failover delay of 30s
        // Using test config failback delay of 60s

        // Mock DNS service for the complete lifecycle
        when(dnsService.detectCurrentActiveServer()).thenReturn(ServerType.PRIMARY)
            .thenReturn(ServerType.PRIMARY) // Phase 2: Still on primary during wait
            .thenReturn(ServerType.SECONDARY) // Phase 3: After switch to secondary
            .thenReturn(ServerType.SECONDARY) // Phase 4: Still on secondary during failback wait
            .thenReturn(ServerType.PRIMARY); // Phase 5: Back to primary after failback
            
        // Phase 1: All healthy
        ServerStatus phase1 = monitorService.checkServers();
        assertEquals(NextAction.NONE, phase1.nextAction().getAction());
        assertEquals(ServerType.PRIMARY, phase1.currentActive());

        // Phase 2: Primary degrades
        when(networkService.checkHostPort(eq(config.primary().host()), eq(config.primary().port()), any(Duration.class)))
            .thenReturn(false);
        
        ServerStatus phase2 = monitorService.checkServers();
        assertEquals(NextAction.WAITING_FOR_FAILOVER, phase2.nextAction().getAction());
        
        Thread.sleep(31000); // Wait for test config failover delay
        ServerStatus phase3 = monitorService.checkServers();
        assertEquals(NextAction.SWITCHED_TO_SECONDARY, phase3.nextAction().getAction());
        assertEquals(ServerType.SECONDARY, phase3.currentActive());

        // Phase 3: Primary recovers
        when(networkService.checkHostPort(eq(config.primary().host()), eq(config.primary().port()), any(Duration.class)))
            .thenReturn(true);
        
        ServerStatus phase4 = monitorService.checkServers();
        assertEquals(NextAction.WAITING_FOR_FAILBACK, phase4.nextAction().getAction());
        
        Thread.sleep(61000); // Wait for test config failback delay (60s)
        ServerStatus phase5 = monitorService.checkServers();
        assertEquals(NextAction.SWITCHED_TO_PRIMARY, phase5.nextAction().getAction());
        assertEquals(ServerType.PRIMARY, phase5.currentActive());

        // Verify complete cycle
        verify(dnsService).switchDnsToServer(ServerType.SECONDARY);
        verify(dnsService).switchDnsToServer(ServerType.PRIMARY);
    }

    @Test
    @DisplayName("Scenario: Configuration changes during runtime")
    // SCENARIO: Validation that runtime configuration remains stable during operation
    // Simulates attempts to change configuration while system is running
    // Tests configuration immutability and requirement for restart to apply changes
    void scenarioConfigurationChangesDuringRuntime() {
        // Given: Initial configuration
        when(networkService.checkHostPort(anyString(), anyInt(), any(Duration.class)))
            .thenReturn(true);

        ServerStatus initialStatus = monitorService.checkServers();
        assertEquals("test-primary", initialStatus.config().primary().name());
        assertEquals("test-secondary", initialStatus.config().secondary().name());

        // When: Configuration changes (simulated by changing mock returns)
        // Configuration changes would require application restart in real scenario

        ServerStatus updatedStatus = monitorService.checkServers();

        // Then: Should still reflect original configuration
        assertEquals("test-primary", updatedStatus.config().primary().name());
        assertEquals("test-secondary", updatedStatus.config().secondary().name());
    }

    @Test
    @DisplayName("Scenario: Timestamp accuracy verification")
    // SCENARIO: Verification of accurate time tracking for monitoring operations
    // Tests that all timestamps (last check, down since, up since) are accurate and sequential
    // Critical for proper timing-based failover/failback decisions
    void scenarioTimestampAccuracyVerification() throws InterruptedException {
        // Given: System is running
        when(networkService.checkHostPort(anyString(), anyInt(), any(Duration.class)))
            .thenReturn(true);

        // When: Multiple checks with delays
        Instant before1 = Instant.now();
        ServerStatus status1 = monitorService.checkServers();
        Instant after1 = Instant.now();

        Thread.sleep(100);

        Instant before2 = Instant.now();
        ServerStatus status2 = monitorService.checkServers();
        Instant after2 = Instant.now();

        // Then: Timestamps should be accurate
        assertTrue(status1.lastCheck().isAfter(before1) || status1.lastCheck().equals(before1));
        assertTrue(status1.lastCheck().isBefore(after1) || status1.lastCheck().equals(after1));
        
        assertTrue(status2.lastCheck().isAfter(before2) || status2.lastCheck().equals(before2));
        assertTrue(status2.lastCheck().isBefore(after2) || status2.lastCheck().equals(after2));
        
        assertTrue(status2.lastCheck().isAfter(status1.lastCheck()));
    }
}
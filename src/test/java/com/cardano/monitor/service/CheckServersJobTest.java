package com.cardano.monitor.service;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.InjectMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.*;

@QuarkusTest
@DisplayName("CheckServersJob Tests")
class CheckServersJobTest {

    @InjectMock
    BlockProducerMonitorServiceIF monitorService;

    private CheckServersJob checkServersJob;

    @BeforeEach
    void setUp() {
        reset(monitorService);
        checkServersJob = new CheckServersJob();
        checkServersJob.monitorService = monitorService;
    }

    @Test
    @DisplayName("Should invoke checkServers when monitor service is running")
    void shouldInvokeCheckServersWhenMonitorServiceIsRunning() {
        // Given - Monitor service is running
        when(monitorService.isRunning()).thenReturn(true);

        // When - Scheduled job runs
        checkServersJob.checkServers();

        // Then - checkServers should be called
        verify(monitorService).isRunning();
        verify(monitorService).checkServers();
    }

    @Test
    @DisplayName("Should not invoke checkServers when monitor service is stopped")
    void shouldNotInvokeCheckServersWhenMonitorServiceIsStopped() {
        // Given - Monitor service is stopped
        when(monitorService.isRunning()).thenReturn(false);

        // When - Scheduled job runs
        checkServersJob.checkServers();

        // Then - checkServers should not be called
        verify(monitorService).isRunning();
        verify(monitorService, never()).checkServers();
    }

    @Test
    @DisplayName("Should handle exceptions from isRunning gracefully")
    void shouldHandleExceptionsFromIsRunningGracefully() {
        // Given - isRunning throws exception
        when(monitorService.isRunning()).thenThrow(new RuntimeException("Service error"));

        // When - Scheduled job runs
        try {
            checkServersJob.checkServers();
        } catch (Exception e) {
            // Exception should not be swallowed by the job
        }

        // Then - checkServers should not be called
        verify(monitorService).isRunning();
        verify(monitorService, never()).checkServers();
    }

    @Test
    @DisplayName("Should handle exceptions from checkServers gracefully")
    void shouldHandleExceptionsFromCheckServersGracefully() {
        // Given - Monitor service is running but checkServers throws exception
        when(monitorService.isRunning()).thenReturn(true);
        doThrow(new RuntimeException("Check servers error")).when(monitorService).checkServers();

        // When - Scheduled job runs
        try {
            checkServersJob.checkServers();
        } catch (Exception e) {
            // Exception should not be swallowed by the job
        }

        // Then - Both methods should have been called
        verify(monitorService).isRunning();
        verify(monitorService).checkServers();
    }

    @Test
    @DisplayName("Should call methods in correct order")
    void shouldCallMethodsInCorrectOrder() {
        // Given - Monitor service is running
        when(monitorService.isRunning()).thenReturn(true);

        // When - Scheduled job runs
        checkServersJob.checkServers();

        // Then - Verify order of calls
        var inOrder = inOrder(monitorService);
        inOrder.verify(monitorService).isRunning();
        inOrder.verify(monitorService).checkServers();
    }

    @Test
    @DisplayName("Should handle multiple consecutive runs correctly")
    void shouldHandleMultipleConsecutiveRunsCorrectly() {
        // Given - Monitor service is running
        when(monitorService.isRunning()).thenReturn(true);

        // When - Multiple scheduled runs
        checkServersJob.checkServers();
        checkServersJob.checkServers();
        checkServersJob.checkServers();

        // Then - All calls should be made
        verify(monitorService, times(3)).isRunning();
        verify(monitorService, times(3)).checkServers();
    }
}
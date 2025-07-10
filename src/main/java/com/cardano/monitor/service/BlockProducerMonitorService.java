package com.cardano.monitor.service;

import com.cardano.monitor.config.MonitorConfig;
import com.cardano.monitor.model.*;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import lombok.val;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
@Slf4j
public class BlockProducerMonitorService {
    
    @Inject
    MonitorConfig config;
    
    @Inject
    NetworkService networkService;
    
    @Inject
    DnsService dnsService;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicReference<ServerType> currentActive = new AtomicReference<>(ServerType.PRIMARY);
    private final AtomicReference<Instant> primaryDownSince = new AtomicReference<>();
    private final AtomicReference<Instant> primaryUpSince = new AtomicReference<>();
    private final AtomicReference<Instant> lastCheck = new AtomicReference<>(Instant.now());
    private final AtomicBoolean manualOverride = new AtomicBoolean(false);
    private final AtomicReference<NextAction.WithContext> lastNextAction = new AtomicReference<>(NextAction.NONE.withoutContext());
    
    @Scheduled(every = "60s")
    public void scheduledHealthCheck() {
        if (running.get()) {
            checkServers();
        }
    }
    
    public ServerStatus checkServers() {
        log.info("Checking servers..., currentActive: {}", currentActive.get());

        Instant currentTime = Instant.now();
        lastCheck.set(currentTime);
        
        boolean primaryUp;
        try {
            primaryUp = networkService.checkHostPort(
                config.primary().host(),
                config.primary().port(),
                config.timing().connectionTimeout()
            );
        } catch (Exception e) {
            log.warn("Error checking primary server: {}", e.getMessage());
            primaryUp = false;
        }
        
        boolean secondaryUp;
        try {
            secondaryUp = networkService.checkHostPort(
                config.secondary().host(),
                config.secondary().port(),
                config.timing().connectionTimeout()
            );
        } catch (Exception e) {
            log.warn("Error checking secondary server: {}", e.getMessage());
            secondaryUp = false;
        }
        
        // Track primary downtime
        if (!primaryUp) {
            primaryDownSince.compareAndSet(null, currentTime);
            primaryUpSince.set(null);
            if (primaryDownSince.get() == currentTime) {
                log.info("Primary server ({}) went down at {}", config.primary().name(), currentTime);
            }
        } else {
            if (primaryDownSince.get() != null) {
                log.info("Primary server ({}) back up at {}", config.primary().name(), currentTime);
                primaryDownSince.set(null);
            }
            primaryUpSince.compareAndSet(null, currentTime);
        }
        
        // Decision logic (skip if manual override is active)
        NextAction.WithContext nextAction = checkNextAction(primaryUp, currentTime, secondaryUp);

        lastNextAction.set(nextAction);
        
        return new ServerStatus(
            running.get() ? DaemonStatus.RUNNING : DaemonStatus.STOPPED,
            currentActive.get(),
            primaryUp ? ServerHealthStatus.UP : ServerHealthStatus.DOWN,
            secondaryUp ? ServerHealthStatus.UP : ServerHealthStatus.DOWN,
            currentTime,
            primaryDownSince.get(),
            primaryUpSince.get(),
            manualOverride.get(),
            nextAction,
            new ServerStatus.ConfigInfo(
                new ServerStatus.ServerInfo(
                    config.primary().name(),
                    config.primary().host(),
                    config.primary().port()
                ),
                new ServerStatus.ServerInfo(
                    config.secondary().name(),
                    config.secondary().host(),
                    config.secondary().port()
                )
            )
        );
    }

    private NextAction.WithContext checkNextAction(boolean primaryUp, Instant currentTime, boolean secondaryUp) {
        var nextAction = NextAction.NONE.withoutContext();

        if (!manualOverride.get()) {
            ServerType current = currentActive.get();

            if (current == ServerType.PRIMARY) {
                // Currently using primary
                if (!primaryUp && primaryDownSince.get() != null) {
                    Duration downDuration = Duration.between(primaryDownSince.get(), currentTime);
                    if (downDuration.compareTo(config.timing().failoverDelay()) >= 0) {
                        if (secondaryUp) {
                            log.info("Primary down for {}, switching to secondary", downDuration);
                            if (dnsService.switchDnsToServer(ServerType.SECONDARY)) {
                                currentActive.set(ServerType.SECONDARY);
                                nextAction = NextAction.SWITCHED_TO_SECONDARY.withoutContext();
                            } else {
                                nextAction = NextAction.FAILED_TO_SWITCH_TO_SECONDARY.withoutContext();
                            }
                        } else {
                            log.error("Primary down but secondary also unavailable!");
                            nextAction = NextAction.BOTH_SERVERS_DOWN.withoutContext();
                        }
                    } else {
                        long remaining = config.timing().failoverDelay().minus(downDuration).getSeconds();
                        nextAction = NextAction.WAITING_FOR_FAILOVER.withRemainingTime(remaining);
                    }
                }
            } else if (current == ServerType.SECONDARY) {
                // Currently using secondary
                if (primaryUp && primaryUpSince.get() != null) {
                    Duration upDuration = Duration.between(primaryUpSince.get(), currentTime);
                    if (upDuration.compareTo(config.timing().failbackDelay()) >= 0) {
                        log.info("Primary up for {}, switching back to primary", upDuration);
                        if (dnsService.switchDnsToServer(ServerType.PRIMARY)) {
                            currentActive.set(ServerType.PRIMARY);
                            nextAction = NextAction.SWITCHED_TO_PRIMARY.withoutContext();
                        } else {
                            nextAction = NextAction.FAILED_TO_SWITCH_TO_PRIMARY.withoutContext();
                        }
                    } else {
                        long remaining = config.timing().failbackDelay().minus(upDuration).getSeconds();
                        nextAction = NextAction.WAITING_FOR_FAILBACK.withRemainingTime(remaining);
                    }
                } else if (!secondaryUp) {
                    log.error("Secondary server is down!");
                    nextAction = NextAction.SECONDARY_SERVER_DOWN.withoutContext();
                }
            }
        } else {
            nextAction = NextAction.MANUAL_OVERRIDE_ACTIVE.withoutContext();
        }

        return nextAction;
    }

    public ApiResponse start() {
        if (running.get()) {
            return ApiResponse.error("Monitor is already running");
        }
        
        running.set(true);
        log.info("Starting block producer monitoring daemon");
        
        // Initial check
        checkServers();
        
        return ApiResponse.success("Monitor started successfully");
    }
    
    public ApiResponse stop() {
        if (!running.get()) {
            return ApiResponse.error("Monitor is not running");
        }
        
        running.set(false);
        log.info("Stopping block producer monitoring daemon");
        
        return ApiResponse.success("Monitor stopped successfully");
    }
    
    public ApiResponse manualSwitch(ServerType targetServer) {
        if (targetServer == currentActive.get()) {
            return ApiResponse.error(String.format("Already using %s server", targetServer.name().toLowerCase()));
        }
        
        // Check if target server is available
        MonitorConfig.ServerConfig targetConfig = targetServer == ServerType.PRIMARY 
            ? config.primary() : config.secondary();
        
        boolean isReachable = networkService.checkHostPort(
            targetConfig.host(),
            targetConfig.port(),
            config.timing().connectionTimeout()
        );
        
        if (!isReachable) {
            return ApiResponse.error(String.format(
                "Target %s server (%s) is not reachable",
                targetServer.name().toLowerCase(),
                targetConfig.name()
            ));
        }
        
        // Perform the switch
        if (dnsService.switchDnsToServer(targetServer)) {
            currentActive.set(targetServer);
            manualOverride.set(true);
            // Reset timing tracking when manual switch occurs
            primaryDownSince.set(null);
            primaryUpSince.set(null);
            return ApiResponse.success(String.format("Successfully switched to %s server", targetServer.name().toLowerCase()));
        } else {
            return ApiResponse.error(String.format("Failed to switch to %s server", targetServer.name().toLowerCase()));
        }
    }
    
    public ApiResponse clearManualOverride() {
        manualOverride.set(false);
        log.info("Manual override cleared, resuming automatic monitoring");

        return ApiResponse.success("Manual override cleared");
    }
    
    public void resetState() {
        running.set(true);
        currentActive.set(ServerType.PRIMARY);
        primaryDownSince.set(null);
        primaryUpSince.set(null);
        manualOverride.set(false);
        lastNextAction.set(NextAction.NONE.withoutContext());
        lastCheck.set(Instant.now());
    }
    
    public ServerStatus getStatus() {
        return new ServerStatus(
            running.get() ? DaemonStatus.RUNNING : DaemonStatus.STOPPED,
            currentActive.get(),
            ServerHealthStatus.UNKNOWN, // Will be updated by next check
            ServerHealthStatus.UNKNOWN, // Will be updated by next check
            lastCheck.get(),
            primaryDownSince.get(),
            primaryUpSince.get(),
            manualOverride.get(),
            lastNextAction.get(),
            new ServerStatus.ConfigInfo(
                new ServerStatus.ServerInfo(
                    config.primary().name(),
                    config.primary().host(),
                    config.primary().port()
                ),
                new ServerStatus.ServerInfo(
                    config.secondary().name(),
                    config.secondary().host(),
                    config.secondary().port()
                )
            )
        );
    }
}
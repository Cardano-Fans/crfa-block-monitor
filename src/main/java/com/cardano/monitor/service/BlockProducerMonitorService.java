package com.cardano.monitor.service;

import com.cardano.monitor.config.MonitorConfig;
import com.cardano.monitor.model.*;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@ApplicationScoped
@Slf4j
public class BlockProducerMonitorService implements BlockProducerMonitorServiceIF {
    
    @Inject
    MonitorConfig config;
    
    @Inject
    NetworkServiceIF networkService;
    
    @Inject
    DnsServiceIF dnsService;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicReference<Instant> primaryDownSince = new AtomicReference<>();
    private final AtomicReference<Instant> primaryUpSince = new AtomicReference<>();
    private final AtomicReference<Instant> lastCheck = new AtomicReference<>(Instant.now());
    private final AtomicReference<NextAction.WithContext> lastNextAction = new AtomicReference<>(NextAction.NONE.withoutContext());
    

    public ServerStatus checkServers() {
        Instant currentTime = Instant.now();
        lastCheck.set(currentTime);
        
        // Get current active server from DNS (single source of truth)
        ServerType currentActive = dnsService.detectCurrentActiveServer();
        log.info("Checking servers..., currentActive: {}", currentActive);

        boolean primaryUp = false;
        boolean secondaryUp = false;
        
        try {
            primaryUp = networkService.getServerHealthStatus(ServerType.PRIMARY) == ServerHealthStatus.UP;
        } catch (Exception e) {
            log.error("Error checking primary server health: {}", e.getMessage());
            primaryUp = false;
        }
        
        try {
            secondaryUp = networkService.getServerHealthStatus(ServerType.SECONDARY) == ServerHealthStatus.UP;
        } catch (Exception e) {
            log.error("Error checking secondary server health: {}", e.getMessage());
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
        NextAction.WithContext nextAction = checkNextAction(primaryUp, currentTime, secondaryUp, currentActive);

        lastNextAction.set(nextAction);
        
        // Update currentActive if a switch occurred
        ServerType finalCurrentActive = currentActive;
        if (nextAction.getAction() == NextAction.SWITCHED_TO_PRIMARY) {
            finalCurrentActive = ServerType.PRIMARY;
        } else if (nextAction.getAction() == NextAction.SWITCHED_TO_SECONDARY) {
            finalCurrentActive = ServerType.SECONDARY;
        }
        
        return new ServerStatus(
            running.get() ? DaemonStatus.RUNNING : DaemonStatus.STOPPED,
            finalCurrentActive,
            primaryUp ? ServerHealthStatus.UP : ServerHealthStatus.DOWN,
            secondaryUp ? ServerHealthStatus.UP : ServerHealthStatus.DOWN,
            currentTime,
            primaryDownSince.get(),
            primaryUpSince.get(),
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

    private NextAction.WithContext checkNextAction(boolean primaryUp, Instant currentTime, boolean secondaryUp, ServerType currentActive) {
        var nextAction = NextAction.NONE.withoutContext();

        if (currentActive == ServerType.PRIMARY) {
            // Currently using primary
            if (!primaryUp) {
                if (!secondaryUp) {
                    // Both servers are down
                    log.error("Both primary and secondary servers are down!");
                    nextAction = NextAction.BOTH_SERVERS_DOWN.withoutContext();
                } else if (primaryDownSince.get() != null) {
                    Duration downDuration = Duration.between(primaryDownSince.get(), currentTime);
                    if (downDuration.compareTo(config.timing().failoverDelay()) >= 0) {
                        if (secondaryUp) {
                            log.info("Primary down for {}, switching to secondary", downDuration);
                            if (dnsService.switchDnsToServer(ServerType.SECONDARY)) {
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
            }
        } else if (currentActive == ServerType.SECONDARY) {
            // Currently using secondary
            if (!secondaryUp) {
                if (!primaryUp) {
                    // Both servers are down
                    log.error("Both primary and secondary servers are down!");
                    nextAction = NextAction.BOTH_SERVERS_DOWN.withoutContext();
                } else {
                    log.error("Secondary server is down!");
                    nextAction = NextAction.SECONDARY_SERVER_DOWN.withoutContext();
                }
            } else if (primaryUp && primaryUpSince.get() != null) {
                Duration upDuration = Duration.between(primaryUpSince.get(), currentTime);
                if (upDuration.compareTo(config.timing().failbackDelay()) >= 0) {
                    log.info("Primary up for {}, switching back to primary", upDuration);
                    if (dnsService.switchDnsToServer(ServerType.PRIMARY)) {
                        nextAction = NextAction.SWITCHED_TO_PRIMARY.withoutContext();
                    } else {
                        nextAction = NextAction.FAILED_TO_SWITCH_TO_PRIMARY.withoutContext();
                    }
                } else {
                    long remaining = config.timing().failbackDelay().minus(upDuration).getSeconds();
                    nextAction = NextAction.WAITING_FOR_FAILBACK.withRemainingTime(remaining);
                }
            }
        } else if (currentActive == ServerType.NONE) {
            // Currently no active server - check if any server is available
            if (primaryUp) {
                log.info("Primary server available, switching from NONE to primary");
                if (dnsService.switchDnsToServer(ServerType.PRIMARY)) {
                    nextAction = NextAction.SWITCHED_TO_PRIMARY.withoutContext();
                } else {
                    nextAction = NextAction.FAILED_TO_SWITCH_TO_PRIMARY.withoutContext();
                }
            } else if (secondaryUp) {
                log.info("Secondary server available, switching from NONE to secondary");
                if (dnsService.switchDnsToServer(ServerType.SECONDARY)) {
                    nextAction = NextAction.SWITCHED_TO_SECONDARY.withoutContext();
                } else {
                    nextAction = NextAction.FAILED_TO_SWITCH_TO_SECONDARY.withoutContext();
                }
            } else {
                nextAction = NextAction.BOTH_SERVERS_DOWN.withoutContext();
            }
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
        if (targetServer == ServerType.NONE) {
            return ApiResponse.error("Cannot manually switch to NONE. Use specific server type.");
        }
        
        ServerType currentActive = dnsService.detectCurrentActiveServer();
        if (targetServer == currentActive) {
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
            // Reset timing tracking when manual switch occurs
            primaryDownSince.set(null);
            primaryUpSince.set(null);
            return ApiResponse.success(String.format("Successfully switched to %s server", targetServer.name().toLowerCase()));
        } else {
            return ApiResponse.error(String.format("Failed to switch to %s server", targetServer.name().toLowerCase()));
        }
    }

    public void resetState() {
        running.set(true);
        primaryDownSince.set(null);
        primaryUpSince.set(null);
        lastNextAction.set(NextAction.NONE.withoutContext());
        lastCheck.set(Instant.now());
    }
    
    public boolean isRunning() {
        return running.get();
    }
    
    public ServerStatus getStatus() {
        return new ServerStatus(
            running.get() ? DaemonStatus.RUNNING : DaemonStatus.STOPPED,
            dnsService.detectCurrentActiveServer(),
            networkService.getServerHealthStatus(ServerType.PRIMARY),
            networkService.getServerHealthStatus(ServerType.SECONDARY),
            lastCheck.get(),
            primaryDownSince.get(),
            primaryUpSince.get(),
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
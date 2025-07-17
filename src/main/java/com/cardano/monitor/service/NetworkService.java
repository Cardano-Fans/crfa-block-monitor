package com.cardano.monitor.service;

import com.cardano.monitor.config.MonitorConfig;
import com.cardano.monitor.model.ServerHealthStatus;
import com.cardano.monitor.model.ServerType;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;

@ApplicationScoped
@Slf4j
public class NetworkService implements NetworkServiceIF {

    @Inject
    MonitorConfig config;

    public boolean checkHostPort(String host, int port, Duration timeout) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), (int) timeout.toMillis());
            log.debug("Successfully connected to {}:{}", host, port);
            return true;
        } catch (SocketTimeoutException e) {
            log.debug("Connection timeout to {}:{}", host, port);
            return false;
        } catch (IOException e) {
            log.debug("Connection failed to {}:{}: {}", host, port, e.getMessage());
            return false;
        }
    }

    public ServerHealthStatus getServerHealthStatus(ServerType serverType) {
        if (serverType == null) {
            throw new NullPointerException("ServerType cannot be null");
        }
        
        try {
            final Duration timeout = config.timing().connectionTimeout();

            return switch (serverType) {
                case ServerType.PRIMARY -> checkHostPort(config.primary().host(), config.primary().port(), timeout) ? ServerHealthStatus.UP : ServerHealthStatus.DOWN;
                case ServerType.SECONDARY -> checkHostPort(config.secondary().host(), config.secondary().port(), timeout) ? ServerHealthStatus.UP : ServerHealthStatus.DOWN;
                case ServerType.NONE ->  ServerHealthStatus.UNKNOWN;
            };
        } catch (Exception e) {
            log.error("Error checking server health for {}: {}", serverType, e.getMessage());
            return ServerHealthStatus.DOWN;
        }
    }

}

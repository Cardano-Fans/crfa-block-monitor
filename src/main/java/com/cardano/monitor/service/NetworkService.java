package com.cardano.monitor.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;

@ApplicationScoped
@Slf4j
public class NetworkService {
    
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
}
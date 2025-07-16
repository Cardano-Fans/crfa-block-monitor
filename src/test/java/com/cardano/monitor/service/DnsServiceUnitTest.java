package com.cardano.monitor.service;

import com.cardano.monitor.config.MonitorConfig;
import com.cardano.monitor.model.ServerType;
import com.cardano.monitor.service.DnsService.DnsUpdateRequest;
import com.cardano.monitor.service.DnsService.NameComApiClient;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Nested;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("DnsService Unit Tests")
class DnsServiceUnitTest {

    private DnsService dnsService;

    @Mock
    private MonitorConfig mockConfig;

    @Mock
    private NameComApiClient mockNameComClient;

    @Mock
    private Response mockResponse;

    private MonitorConfig.DnsConfig mockDnsConfig;
    private MonitorConfig.ServerConfig mockPrimaryConfig;
    private MonitorConfig.ServerConfig mockSecondaryConfig;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Create DnsService instance with mocked dependencies
        dnsService = new DnsService();
        dnsService.config = mockConfig;
        dnsService.nameComClient = mockNameComClient;

        // Setup DNS config
        mockDnsConfig = mock(MonitorConfig.DnsConfig.class);
        when(mockDnsConfig.domain()).thenReturn("example.com");
        when(mockDnsConfig.recordId()).thenReturn("123456");
        when(mockDnsConfig.recordHost()).thenReturn("test");
        when(mockDnsConfig.recordFqdn()).thenReturn("test.example.com");
        when(mockDnsConfig.recordType()).thenReturn("A");
        when(mockDnsConfig.recordTtl()).thenReturn(300);
        when(mockDnsConfig.username()).thenReturn("testuser");
        when(mockDnsConfig.password()).thenReturn("testpass");

        // Setup server configs
        mockPrimaryConfig = mock(MonitorConfig.ServerConfig.class);
        when(mockPrimaryConfig.name()).thenReturn("Primary Server");
        when(mockPrimaryConfig.host()).thenReturn("192.168.1.100");
        when(mockPrimaryConfig.port()).thenReturn(8080);

        mockSecondaryConfig = mock(MonitorConfig.ServerConfig.class);
        when(mockSecondaryConfig.name()).thenReturn("Secondary Server");
        when(mockSecondaryConfig.host()).thenReturn("192.168.1.101");
        when(mockSecondaryConfig.port()).thenReturn(8080);

        // Setup main config
        when(mockConfig.dns()).thenReturn(mockDnsConfig);
        when(mockConfig.primary()).thenReturn(mockPrimaryConfig);
        when(mockConfig.secondary()).thenReturn(mockSecondaryConfig);
    }

    @Nested
    @DisplayName("switchDnsToServer Tests")
    class SwitchDnsToServerTests {

        @Test
        @DisplayName("Should successfully switch DNS to PRIMARY server")
        void shouldSuccessfullySwitchDnsToPrimaryServer() {
            // Given
            when(mockResponse.getStatus()).thenReturn(200);
            when(mockNameComClient.updateDnsRecord(anyString(), anyString(), anyString(), any(DnsUpdateRequest.class)))
                    .thenReturn(mockResponse);

            // When
            boolean result = dnsService.switchDnsToServer(ServerType.PRIMARY);

            // Then
            assertTrue(result);
            
            ArgumentCaptor<DnsUpdateRequest> requestCaptor = ArgumentCaptor.forClass(DnsUpdateRequest.class);
            verify(mockNameComClient).updateDnsRecord(
                    eq("Basic dGVzdHVzZXI6dGVzdHBhc3M="), // Base64 encoded "testuser:testpass"
                    eq("example.com"),
                    eq("123456"),
                    requestCaptor.capture()
            );

            DnsUpdateRequest capturedRequest = requestCaptor.getValue();
            assertEquals("test", capturedRequest.host());
            assertEquals("test.example.com", capturedRequest.fqdn());
            assertEquals("A", capturedRequest.type());
            assertEquals("192.168.1.100", capturedRequest.answer());
            assertEquals(300, capturedRequest.ttl());
        }

        @Test
        @DisplayName("Should successfully switch DNS to SECONDARY server")
        void shouldSuccessfullySwitchDnsToSecondaryServer() {
            // Given
            when(mockResponse.getStatus()).thenReturn(200);
            when(mockNameComClient.updateDnsRecord(anyString(), anyString(), anyString(), any(DnsUpdateRequest.class)))
                    .thenReturn(mockResponse);

            // When
            boolean result = dnsService.switchDnsToServer(ServerType.SECONDARY);

            // Then
            assertTrue(result);
            
            ArgumentCaptor<DnsUpdateRequest> requestCaptor = ArgumentCaptor.forClass(DnsUpdateRequest.class);
            verify(mockNameComClient).updateDnsRecord(
                    eq("Basic dGVzdHVzZXI6dGVzdHBhc3M="),
                    eq("example.com"),
                    eq("123456"),
                    requestCaptor.capture()
            );

            DnsUpdateRequest capturedRequest = requestCaptor.getValue();
            assertEquals("192.168.1.101", capturedRequest.answer());
        }

        @Test
        @DisplayName("Should return false when switching to NONE server")
        void shouldReturnFalseWhenSwitchingToNoneServer() {
            // When
            boolean result = dnsService.switchDnsToServer(ServerType.NONE);

            // Then
            assertFalse(result);
            verifyNoInteractions(mockNameComClient);
        }

        @Test
        @DisplayName("Should return false when API returns non-200 status")
        void shouldReturnFalseWhenApiReturnsNon200Status() {
            // Given
            when(mockResponse.getStatus()).thenReturn(400);
            when(mockNameComClient.updateDnsRecord(anyString(), anyString(), anyString(), any(DnsUpdateRequest.class)))
                    .thenReturn(mockResponse);

            // When
            boolean result = dnsService.switchDnsToServer(ServerType.PRIMARY);

            // Then
            assertFalse(result);
        }

        @Test
        @DisplayName("Should return false when API throws exception")
        void shouldReturnFalseWhenApiThrowsException() {
            // Given
            when(mockNameComClient.updateDnsRecord(anyString(), anyString(), anyString(), any(DnsUpdateRequest.class)))
                    .thenThrow(new RuntimeException("API Error"));

            // When
            boolean result = dnsService.switchDnsToServer(ServerType.PRIMARY);

            // Then
            assertFalse(result);
        }
    }

    @Nested
    @DisplayName("getCurrentDnsRecordIp Tests")
    class GetCurrentDnsRecordIpTests {

        @Test
        @DisplayName("Should return IP when DNS record is successfully retrieved")
        void shouldReturnIpWhenDnsRecordIsSuccessfullyRetrieved() {
            // Given
            String jsonResponse = "{\"id\":\"123456\",\"host\":\"test\",\"fqdn\":\"test.example.com\",\"type\":\"A\",\"answer\":\"192.168.1.100\",\"ttl\":300}";
            when(mockResponse.getStatus()).thenReturn(200);
            when(mockResponse.readEntity(String.class)).thenReturn(jsonResponse);
            when(mockNameComClient.getDnsRecord(anyString(), anyString(), anyString()))
                    .thenReturn(mockResponse);

            // When
            String result = dnsService.getCurrentDnsRecordIp();

            // Then
            assertEquals("192.168.1.100", result);
            verify(mockNameComClient).getDnsRecord(
                    eq("Basic dGVzdHVzZXI6dGVzdHBhc3M="),
                    eq("example.com"),
                    eq("123456")
            );
        }

        @Test
        @DisplayName("Should return null when API returns non-200 status")
        void shouldReturnNullWhenApiReturnsNon200Status() {
            // Given
            when(mockResponse.getStatus()).thenReturn(404);
            when(mockNameComClient.getDnsRecord(anyString(), anyString(), anyString()))
                    .thenReturn(mockResponse);

            // When
            String result = dnsService.getCurrentDnsRecordIp();

            // Then
            assertNull(result);
        }

        @Test
        @DisplayName("Should return null when response has no answer field")
        void shouldReturnNullWhenResponseHasNoAnswerField() {
            // Given
            String jsonResponse = "{\"id\":\"123456\",\"host\":\"test\",\"fqdn\":\"test.example.com\",\"type\":\"A\",\"ttl\":300}";
            when(mockResponse.getStatus()).thenReturn(200);
            when(mockResponse.readEntity(String.class)).thenReturn(jsonResponse);
            when(mockNameComClient.getDnsRecord(anyString(), anyString(), anyString()))
                    .thenReturn(mockResponse);

            // When
            String result = dnsService.getCurrentDnsRecordIp();

            // Then
            assertNull(result);
        }

        @Test
        @DisplayName("Should return null when API throws exception")
        void shouldReturnNullWhenApiThrowsException() {
            // Given
            when(mockNameComClient.getDnsRecord(anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("API Error"));

            // When
            String result = dnsService.getCurrentDnsRecordIp();

            // Then
            assertNull(result);
        }
    }

    @Nested
    @DisplayName("detectCurrentActiveServer Tests")
    class DetectCurrentActiveServerTests {

        @Test
        @DisplayName("Should return PRIMARY when DNS points to primary server IP")
        void shouldReturnPrimaryWhenDnsPointsToPrimaryServerIp() {
            // Given
            String jsonResponse = "{\"answer\":\"192.168.1.100\"}";
            when(mockResponse.getStatus()).thenReturn(200);
            when(mockResponse.readEntity(String.class)).thenReturn(jsonResponse);
            when(mockNameComClient.getDnsRecord(anyString(), anyString(), anyString()))
                    .thenReturn(mockResponse);

            // When
            ServerType result = dnsService.detectCurrentActiveServer();

            // Then
            assertEquals(ServerType.PRIMARY, result);
        }

        @Test
        @DisplayName("Should return SECONDARY when DNS points to secondary server IP")
        void shouldReturnSecondaryWhenDnsPointsToSecondaryServerIp() {
            // Given
            String jsonResponse = "{\"answer\":\"192.168.1.101\"}";
            when(mockResponse.getStatus()).thenReturn(200);
            when(mockResponse.readEntity(String.class)).thenReturn(jsonResponse);
            when(mockNameComClient.getDnsRecord(anyString(), anyString(), anyString()))
                    .thenReturn(mockResponse);

            // When
            ServerType result = dnsService.detectCurrentActiveServer();

            // Then
            assertEquals(ServerType.SECONDARY, result);
        }

        @Test
        @DisplayName("Should return NONE when DNS points to unknown IP")
        void shouldReturnNoneWhenDnsPointsToUnknownIp() {
            // Given
            String jsonResponse = "{\"answer\":\"192.168.1.999\"}";
            when(mockResponse.getStatus()).thenReturn(200);
            when(mockResponse.readEntity(String.class)).thenReturn(jsonResponse);
            when(mockNameComClient.getDnsRecord(anyString(), anyString(), anyString()))
                    .thenReturn(mockResponse);

            // When
            ServerType result = dnsService.detectCurrentActiveServer();

            // Then
            assertEquals(ServerType.NONE, result);
        }

        @Test
        @DisplayName("Should return NONE when DNS record cannot be retrieved")
        void shouldReturnNoneWhenDnsRecordCannotBeRetrieved() {
            // Given
            when(mockResponse.getStatus()).thenReturn(500);
            when(mockNameComClient.getDnsRecord(anyString(), anyString(), anyString()))
                    .thenReturn(mockResponse);

            // When
            ServerType result = dnsService.detectCurrentActiveServer();

            // Then
            assertEquals(ServerType.NONE, result);
        }

        @Test
        @DisplayName("Should return NONE when API throws exception")
        void shouldReturnNoneWhenApiThrowsException() {
            // Given
            when(mockNameComClient.getDnsRecord(anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("API Error"));

            // When
            ServerType result = dnsService.detectCurrentActiveServer();

            // Then
            assertEquals(ServerType.NONE, result);
        }
    }

    @Nested
    @DisplayName("DnsUpdateRequest Tests")
    class DnsUpdateRequestTests {

        @Test
        @DisplayName("Should create DnsUpdateRequest with correct fields")
        void shouldCreateDnsUpdateRequestWithCorrectFields() {
            // When
            DnsUpdateRequest request = new DnsUpdateRequest(
                    "test",
                    "test.example.com",
                    "A",
                    "192.168.1.100",
                    300
            );

            // Then
            assertEquals("test", request.host());
            assertEquals("test.example.com", request.fqdn());
            assertEquals("A", request.type());
            assertEquals("192.168.1.100", request.answer());
            assertEquals(300, request.ttl());
        }
    }
}
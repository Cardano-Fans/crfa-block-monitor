# API Examples - Strongly Typed Endpoints

## Control Endpoint

### Start Monitor
```bash
curl -X POST http://localhost:8080/api/control \
  -H "Content-Type: application/json" \
  -d '{"action": "START"}'
```

### Stop Monitor
```bash
curl -X POST http://localhost:8080/api/control \
  -H "Content-Type: application/json" \
  -d '{"action": "STOP"}'
```

Response:
```json
{
  "success": true,
  "message": "Monitor started successfully"
}
```

## Active Endpoint

### Switch to Primary Server
```bash
curl -X POST http://localhost:8080/api/active \
  -H "Content-Type: application/json" \
  -d '{"active": "PRIMARY"}'
```

### Switch to Secondary Server
```bash
curl -X POST http://localhost:8080/api/active \
  -H "Content-Type: application/json" \
  -d '{"active": "SECONDARY"}'
```

Response:
```json
{
  "success": true,
  "message": "Successfully switched to PRIMARY server"
}
```

## Status Endpoints

### Health Check
```bash
curl -X GET http://localhost:8080/api/health
```

Response (Healthy):
```json
{
  "status": "HEALTHY",
  "timestamp": "2024-01-01T12:00:00Z"
}
```

Response (Unhealthy):
```json
{
  "status": "UNHEALTHY",
  "timestamp": "2024-01-01T12:00:00Z"
}
```

### Full Status
```bash
curl -X GET http://localhost:8080/api/status
```

Response:
```json
{
  "timestamp": "2024-01-01T12:00:00Z",
  "monitor": {
    "daemon_status": "RUNNING",
    "current_active": "PRIMARY",
    "primary_status": "UP",
    "secondary_status": "UP",
    "last_check": "2024-01-01T12:00:00Z",
    "primary_down_since": null,
    "primary_up_since": "2024-01-01T11:00:00Z",
    "manual_override": false,
    "next_action": "none",
    "config": {
      "primary": {
        "name": "dudi",
        "host": "80.1.2.3",
        "port": 8090
      },
      "secondary": {
        "name": "tygrys",
        "host": "70.1.2.3",
        "port": 8090
      }
    }
  }
}
```

## DNS Endpoints

### Get Current DNS Record
```bash
curl -X GET http://localhost:8080/api/dns/current
```

Response:
```json
{
  "currentIp": "207.38.87.213",
  "activeServer": "PRIMARY"
}
```

Error Response:
```json
{
  "success": false,
  "message": "Failed to read DNS record"
}
```
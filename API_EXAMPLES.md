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

### Case Insensitive (also works with lowercase)
```bash
curl -X POST http://localhost:8080/api/control \
  -H "Content-Type: application/json" \
  -d '{"action": "START"}'
```

## Active Endpoint

### Switch to Primary Server
```bash
curl -X POST http://localhost:8080/api/active \
  -H "Content-Type: application/json" \
  -d '{"active": "PRIMARY"}'
```

### Switch to Secondary Server (case insensitive)
```bash
curl -X POST http://localhost:8080/api/active \
  -H "Content-Type: application/json" \
  -d '{"active": "SECONDARY"}'
```

### Clear Manual Override
```bash
curl -X POST http://localhost:8080/api/active \
  -H "Content-Type: application/json" \
  -d '{"action": "CLEAR_OVERRIDE"}'
```

## Status Endpoints

### Health Check
```bash
curl -X GET http://localhost:8080/api/health
```

Response:
```json
{
  "status": "HEALTHY",
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
        "name": "daisy",
        "host": "207.38.87.213",
        "port": 8000
      },
      "secondary": {
        "name": "borostwory",
        "host": "90.187.182.13",
        "port": 8000
      }
    }
  }
}
```
# NextAction Enum Guide

## Overview

The `NextAction` enum represents the current state and next planned action of the automatic monitoring system. It provides detailed insight into the monitor's decision-making process and helps operators understand what the system is doing or will do next.

## Enum Values

### Normal Operation States

#### `NONE`
- **Description**: Everything is stable, no action needed
- **When it occurs**: Both servers are healthy and system is operating normally
- **JSON Output**: `"none"`
- **Action Required**: None

#### `MANUAL_OVERRIDE_ACTIVE`
- **Description**: Manual override is enabled, automatic switching disabled
- **When it occurs**: Administrator has manually switched servers or disabled automatic failover
- **JSON Output**: `"manual_override_active"`
- **Action Required**: Clear manual override to resume automatic monitoring

### Waiting States (with countdown)

#### `WAITING_FOR_FAILOVER`
- **Description**: Primary is down, waiting for failover delay before switching to secondary
- **When it occurs**: Primary server is unresponsive, secondary is available, but waiting for configured delay
- **JSON Output**: `"waiting_for_failover (240s remaining)"`
- **Action Required**: Monitor the countdown, manual switch available if urgent

#### `WAITING_FOR_FAILBACK`
- **Description**: Primary is back up, waiting for failback delay before switching back
- **When it occurs**: Primary server is healthy again, currently using secondary, waiting for stability period
- **JSON Output**: `"waiting_for_failback (480s remaining)"`
- **Action Required**: Monitor the countdown, system will automatically switch back

### Action Completed States

#### `SWITCHED_TO_SECONDARY`
- **Description**: Successfully switched DNS to secondary server
- **When it occurs**: Automatic failover completed successfully
- **JSON Output**: `"switched_to_secondary"`
- **Action Required**: Monitor secondary server health

#### `SWITCHED_TO_PRIMARY`
- **Description**: Successfully switched DNS back to primary server
- **When it occurs**: Automatic failback completed successfully
- **JSON Output**: `"switched_to_primary"`
- **Action Required**: Monitor primary server health

### Error States (require attention)

#### `FAILED_TO_SWITCH_TO_SECONDARY`
- **Description**: Attempted failover failed (DNS update failed)
- **When it occurs**: Primary is down, secondary is available, but DNS switch failed
- **JSON Output**: `"failed_to_switch_to_secondary"`
- **Action Required**: **URGENT** - Check DNS API credentials and network connectivity

#### `FAILED_TO_SWITCH_TO_PRIMARY`
- **Description**: Attempted failback failed (DNS update failed)
- **When it occurs**: Primary is up, currently using secondary, but DNS switch failed
- **JSON Output**: `"failed_to_switch_to_primary"`
- **Action Required**: Check DNS API credentials, manual switch may be needed

#### `BOTH_SERVERS_DOWN`
- **Description**: Critical - both primary and secondary servers are down
- **When it occurs**: Primary is down and secondary is also unresponsive
- **JSON Output**: `"both_servers_down"`
- **Action Required**: **CRITICAL** - Immediate investigation required, service is down

#### `SECONDARY_SERVER_DOWN`
- **Description**: Currently using secondary server but it's down
- **When it occurs**: Switched to secondary but it's now unresponsive
- **JSON Output**: `"secondary_server_down"`
- **Action Required**: **CRITICAL** - Check secondary server, consider manual switch to primary

## Usage Examples

### Normal Operation
```json
{
  "current_active": "PRIMARY",
  "primary_status": "UP",
  "secondary_status": "UP",
  "next_action": "none"
}
```

### Waiting for Failover
```json
{
  "current_active": "PRIMARY",
  "primary_status": "DOWN",
  "secondary_status": "UP", 
  "next_action": "waiting_for_failover (180s remaining)"
}
```

### Manual Override Active
```json
{
  "current_active": "SECONDARY",
  "primary_status": "UP",
  "secondary_status": "UP",
  "manual_override": true,
  "next_action": "manual_override_active"
}
```

### Critical Error
```json
{
  "current_active": "PRIMARY",
  "primary_status": "DOWN",
  "secondary_status": "DOWN",
  "next_action": "both_servers_down"
}
```

## Implementation Details

### WithContext Class
The `NextAction.WithContext` class allows actions to include additional information:

```java
// Without context
NextAction.NONE.withoutContext()

// With time context
NextAction.WAITING_FOR_FAILOVER.withRemainingTime(240)
```

### JSON Serialization
The enum uses `@JsonValue` to serialize properly:
- Simple actions: `"none"`, `"switched_to_primary"`
- Actions with context: `"waiting_for_failover (240s remaining)"`

## Monitoring Best Practices

1. **Watch for Error States**: Set up alerts for `FAILED_*` and `*_DOWN` states
2. **Monitor Countdown Timers**: Track waiting states to predict when actions will occur
3. **Check Manual Override**: Ensure manual overrides are cleared when no longer needed
4. **Log State Changes**: Track transitions between states for troubleshooting

## Configuration Impact

The timing values in `application.yml` directly affect the countdown timers:

```yaml
monitor:
  timing:
    failover-delay: 300s    # Affects WAITING_FOR_FAILOVER countdown
    failback-delay: 600s    # Affects WAITING_FOR_FAILBACK countdown
```
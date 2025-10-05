# Miele@home Hubitat Integration - Developer Documentation

## 🏗️ Architecture Overview

This integration uses a **parent-app-centric architecture** where the main app (`Miele_Connect_Manager`) handles all API communication, authentication, and common functionality, while device drivers are minimal and delegate operations to the parent.

### Design Principles

1. **Single Source of Truth** - All API logic in the parent app
2. **Minimal Drivers** - Drivers contain only device-specific data and UI
3. **Code Reuse** - 95% code reduction through delegation pattern
4. **Robust Error Handling** - Comprehensive retry logic and fallbacks
5. **API Compliance** - Follows Miele API specifications exactly

## 📁 Project Structure

```
Miele/
├── app/
│   └── Miele_Connect_Manager.groovy     # Main parent app
├── drivers/
│   ├── Miele_Washer_Driver.groovy       # Washer device driver
│   ├── Miele_Dryer_Driver.groovy        # Dryer device driver  
│   ├── Miele_Dishwasher_Driver.groovy   # Dishwasher device driver
│   └── Miele_Oven_Driver.groovy         # Oven device driver
├── README.md                            # User documentation
└── DEVELOPER-README.md                  # This file
```

## 🔐 Authentication & API

### OAuth 2.0 Flow

The integration supports both Miele API versions:

**New OpenID Connect API** (Recommended):
- Authorization: `https://auth.domestic.miele-iot.com/partner/realms/mcs/protocol/openid-connect/auth`
- Token: `https://auth.domestic.miele-iot.com/partner/realms/mcs/protocol/openid-connect/token`

**Legacy API** (Fallback):
- Authorization: `https://api.mcs3.miele.com/thirdparty/login`
- Token: `https://api.mcs3.miele.com/thirdparty/token`

### Token Management

- **Expiry**: 24-hour tokens with automatic refresh
- **Refresh Logic**: 90% of token lifetime with exponential backoff
- **Error Recovery**: Up to 10 retry attempts with circuit breaker
- **Validation**: Comprehensive expiry time validation with fallbacks

### API Endpoints

| Endpoint | Method | Purpose |
|----------|--------|---------|
| `/v1/devices` | GET | Device discovery |
| `/v1/devices/{id}` | GET | Device status |
| `/v1/devices/{id}/actions` | PUT | Send commands |
| `/v1/devices/{id}/programs` | GET | List programs |
| `/v1/devices/{id}/programs` | PUT | Set program |

## 🏛️ Core Components

### Miele_Connect_Manager.groovy

**Key Responsibilities**:
- OAuth authentication and token management
- Device discovery and state polling
- API communication with error handling
- Child device management
- State processing and distribution

**Critical Methods**:
- `authenticatedHttpGet/Put()` - Handles all API calls with auth
- `buildStateMap()` - Processes raw API data into device states
- `driverProcessState()` - Universal state handler for all devices
- `tokenRefreshJob()` - Automatic token refresh with retry logic

### Device Drivers

**Minimal Implementation Pattern**:
```groovy
// Device-specific data only
@Field static final Map DEVICE_POWER_STATES = [...]

// Delegate all operations to parent
def startProgram() {
    Map commandBody = [processAction: 1]
    parent.driverExecuteCommand(device.deviceNetworkId, "startProgram", commandBody)
}

// Device-specific configuration
private Map getDeviceConfig() {
    return [
        deviceType: "Washer",
        powerStates: WASHER_POWER_STATES
        // ... other device-specific settings
    ]
}
```

## 🔧 Command System

### Process Actions

Miele appliances support these standard actions:

| Action | Value | Support | Notes |
|--------|-------|---------|-------|
| START | 1 | ✅ All | Also works for resume |
| STOP | 2 | ✅ All | Stops and resets |
| PAUSE | 3 | ✅ All | Pauses execution |
| RESUME | 4 | ❌ None | Not supported - use START instead |

### Command Flow

```
1. Driver receives command
2. Driver calls parent.driverExecuteCommand()
3. Parent validates and sends to Miele API
4. Parent processes response and updates device
5. Device receives state update via receiveMieleState()
```

## 📊 State Management

### State Processing Pipeline

1. **Raw API Data** - Direct from Miele API
2. **buildStateMap()** - Converts to normalized format
3. **driverProcessState()** - Universal processing with device config
4. **Device Updates** - Specific attribute updates per device type

### Key State Mappings

```groovy
// Operation state determines switch state
"running" → switch: "on"
"finished" → switch: "off"

// Time handling - convert [hours, minutes] to total minutes
[1, 30] → 90 minutes

// Program handling - extract ID and name
ProgramID: {value_raw: "1", value_localized: "Cottons"}
→ selectedProgramId: "1", selectedProgram: "Cottons"
```

## 🚨 Error Handling

### Retry Strategy

- **Exponential Backoff**: 30s, 60s, 120s, 240s...
- **Circuit Breaker**: Stop after 10 consecutive failures
- **Rate Limiting**: Minimum 30s between token refresh attempts
- **Graceful Degradation**: Continue with cached data when possible

### Common Error Scenarios

**503 Service Unavailable**:
- Miele API temporarily down
- Automatic retry with exponential backoff
- User notification after multiple failures

**401 Unauthorized**:
- Token expired or invalid
- Automatic token refresh
- Re-authorization prompt if refresh fails

**400 Bad Request**:
- Invalid command or device state
- User feedback with suggested actions
- Fallback to basic program list for unsupported endpoints

## 🔄 Polling & Real-time Updates

### Adaptive Polling

- **Active Devices**: 1-minute intervals
- **Idle Devices**: 5-minute intervals  
- **Error Conditions**: Exponential backoff up to 1 hour

### State Detection

```groovy
// Determine if device is active
boolean anyDeviceActive = false
getChildDevices().each { device ->
    String operationState = device.currentValue("operationState")?.toLowerCase() ?: ""
    if (operationState.contains("running") || operationState.contains("in use")) {
        anyDeviceActive = true
    }
}
```

## 🎯 Device-Specific Features

### Washer Driver

**Unique Attributes**:
- `spinSpeed` - RPM monitoring
- `waterConsumption` - Liters used
- Power estimation based on operation phase

**State Handling**:
- "In use" state for both running and paused
- Resume via START action (not RESUME)

### Oven Driver

**Unique Features**:
- Thermostat capability with temperature control
- Light control with on/off commands
- Core temperature monitoring

**Temperature Handling**:
- Celsius/Fahrenheit conversion
- Safety validation (30°C - 300°C range)
- Separate current/target/core temperatures

### Dryer Driver

**Unique Attributes**:
- `dryingLevel` - Dryness monitoring
- Enhanced program phase detection

### Dishwasher Driver

**Standard Implementation**:
- Follows base pattern with minimal customization
- Standard program control and monitoring

## ⚠️ Known Limitations

### API Limitations

1. **Next-Gen Appliances**: Limited API support until Fall 2025
   - Monitoring works, control commands may fail
   - Affects newer washers, dryers, vacuum cleaners

2. **Program Listing**: Many devices don't support `/programs` endpoint
   - Returns 400 Bad Request
   - Fallback to basic program list provided

3. **Resume Command**: Action 4 not supported
   - All drivers use Action 1 for both start and resume
   - State validation ensures correct usage

### Hubitat Platform Limitations

1. **Dynamic Command Options**: Cannot reliably update command dropdowns
2. **SSE Support**: No Server-Sent Events, polling required
3. **Async Limitations**: Limited concurrent HTTP requests

## 🔮 Future Development

### Fall 2025 API Update

Miele is releasing updated API support for next-generation appliances:

**Preparation Tasks**:
- [ ] Monitor Miele developer announcements
- [ ] Test new API endpoints when available
- [ ] Update authentication if required
- [ ] Enhance command support for newer devices

**Potential Improvements**:
- Real-time push notifications via webhooks
- Enhanced program customization
- Additional appliance types (coffee machines, etc.)
- Improved energy monitoring

### Code Improvements

**Performance Optimizations**:
- [ ] Implement request caching for device programs
- [ ] Add device-specific polling intervals
- [ ] Optimize state processing for large device counts

**Feature Enhancements**:
- [ ] Add support for program parameters (temperature, duration)
- [ ] Implement maintenance reminders
- [ ] Add energy cost calculations
- [ ] Support for Miele scenes/recipes

## 🧪 Testing & Debugging

### Debug Logging

Enable comprehensive logging:
```groovy
// In app settings
logLevel: "Debug"

// In device preferences  
logEnable: true
```

### API Testing

Use built-in test functions:
- "Test API Connection" - Validates token and endpoint
- "Manual Device Discovery" - Forces device refresh
- Enhanced error logging shows full request/response details

### Common Debug Scenarios

**Token Issues**:
```groovy
log.info "Token expires: ${new Date(state.tokenExpiresAt)}"
log.info "Current time: ${new Date(now())}"
log.info "Valid: ${now() < state.tokenExpiresAt}"
```

**Command Failures**:
```groovy
log.info "Device state: ${device.currentValue('operationState')}"
log.info "Mobile start: ${device.currentValue('mobileStart')}"
log.info "Command body: ${commandBody}"
```

## 📚 Code Style & Standards

### Naming Conventions

- **Methods**: camelCase (`driverProcessState`)
- **Constants**: UPPER_SNAKE_CASE (`MIELE_API_BASE`)
- **Variables**: camelCase (`deviceId`)
- **Files**: PascalCase (`Miele_Connect_Manager`)

### Error Handling Pattern

```groovy
try {
    // Operation
    if (success) {
        log.info "Success message"
        return true
    } else {
        log.warn "Warning message"
        return false
    }
} catch (Exception e) {
    log.error "Error message: ${e.message}"
    return false
}
```

### Logging Standards

- **Error**: System failures, API errors
- **Warn**: Recoverable issues, user guidance needed
- **Info**: Important state changes, successful operations
- **Debug**: Detailed flow information, API requests/responses

## 🤝 Contributing

### Development Setup

1. **Hubitat Development Environment**
   - Use Hubitat Package Manager for easy updates
   - Enable debug logging during development
   - Test with multiple device types

2. **Code Review Checklist**
   - [ ] Follows parent-app delegation pattern
   - [ ] Includes comprehensive error handling
   - [ ] Has appropriate logging levels
   - [ ] Validates user inputs
   - [ ] Updates documentation

3. **Testing Requirements**
   - [ ] Test with both API versions
   - [ ] Verify token refresh scenarios
   - [ ] Test error recovery paths
   - [ ] Validate with multiple device types

### Release Process

1. **Version Bump**: Update version in app definition
2. **Documentation**: Update README and DEVELOPER-README
3. **Testing**: Full integration test with real devices
4. **GitHub Release**: Tag with semantic versioning
5. **HPM Update**: Update Hubitat Package Manager manifest

---

**Happy coding!** 🚀

For questions or contributions, please open an issue on GitHub.
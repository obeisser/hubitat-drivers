# Miele API Reference & Compliance Guide

## 📋 Overview

This document provides comprehensive information about Miele API compatibility, implementation details, and known limitations for the Hubitat integration.

## 🚨 Critical API Compatibility Notice

### Next-Generation Device Limitations

> ⚠️ **IMPORTANT**: API endpoints for next-generation **washers**, **dryers**, and **vacuum cleaners** are currently under development and scheduled for release in **Fall 2025**.

**Official Miele Statement**: Based on [developer.miele.com](https://developer.miele.com/docs/get-started)

**Impact for Users**:
- **Newer appliances** (2023-2025) may have limited API functionality
- **Monitoring works** but control commands may be restricted  
- **Full functionality** available after Fall 2025 API update

### Device Generation Identification

| Generation | Purchase Period | API Support | Control Commands |
|------------|----------------|-------------|------------------|
| **Current** | Pre-2023 | ✅ Full | ✅ Complete |
| **Next-Gen** | 2023-2025 | ⚠️ Limited | ⚠️ Restricted |

## 🔧 API Endpoints & Implementation

### Base API Configuration

```groovy
// API Base URLs
MIELE_API_BASE = "https://api.mcs3.miele.com"

// Authentication Endpoints
NEW_API = "https://auth.domestic.miele-iot.com/partner/realms/mcs/protocol/openid-connect"
LEGACY_API = "https://api.mcs3.miele.com/thirdparty"

// Device Endpoints  
DEVICES_PATH = "/v1/devices"
ACTIONS_PATH = "/v1/devices/{deviceId}/actions"
PROGRAMS_PATH = "/v1/devices/{deviceId}/programs"
```

### Supported HTTP Methods

| Endpoint | Method | Purpose | Payload |
|----------|--------|---------|---------|
| `/v1/devices` | GET | Device discovery | None |
| `/v1/devices/{id}` | GET | Device status | None |
| `/v1/devices/{id}/actions` | PUT | Send commands | `{"processAction": 1-3}` |
| `/v1/devices/{id}/programs` | GET | List programs | None |
| `/v1/devices/{id}/programs` | PUT | Set program | Device-specific JSON |

## 🎯 Device-Specific API Implementation

### Process Actions (Universal)

| Action | Value | Support | Notes |
|--------|-------|---------|-------|
| **START** | 1 | ✅ All | Also works for resume |
| **STOP** | 2 | ✅ All | Stops and resets |
| **PAUSE** | 3 | ✅ All | Pauses execution |
| **RESUME** | 4 | ❌ None | Not supported - use START instead |

### Program Management Payloads

#### Washing Machines
```json
// Simple program selection
PUT /v1/devices/{deviceId}/programs
{
  "programId": 1
}
```

#### Ovens  
```json
// Advanced program with parameters
PUT /v1/devices/{deviceId}/programs
{
  "programId": 24,
  "temperature": 220,
  "duration": [1, 15]
}
```

#### Dryers & Dishwashers
```json
// Standard program selection
PUT /v1/devices/{deviceId}/programs
{
  "programId": 3
}
```

## 🏗️ Implementation Details

### Device-Specific Program Methods

```groovy
// Washer - Simple payload
def setWasherProgram(String deviceId, String programId) {
    Map programBody = [programId: programId as Integer]
    return setDeviceProgramWithBody(deviceId, programBody)
}

// Oven - Enhanced payload with parameters
def setOvenProgram(String deviceId, String programId, Integer temperature = null, List duration = null) {
    Map programBody = [programId: programId as Integer]
    
    if (temperature != null) {
        programBody.temperature = temperature
    }
    
    if (duration != null && duration.size() == 2) {
        programBody.duration = duration  // [hours, minutes]
    }
    
    return setDeviceProgramWithBody(deviceId, programBody)
}
```

### Universal Command Handler

```groovy
def driverExecuteCommand(String deviceId, String commandName, Map commandBody) {
    // API compatibility warning for program control
    if (commandName in ["startProgram", "pauseProgram", "stopProgram"]) {
        log.info "⚠️ API Compatibility: If this command fails, your appliance may be a next-generation model with limited API support until Fall 2025"
    }
    
    authenticatedHttpPut("${MIELE_ACTIONS_PATH.replace('{deviceId}', deviceId)}", commandBody, "commandResponseHandler", [deviceId: deviceId, commandName: commandName])
}
```

## 🚨 Error Handling & Compatibility

### Enhanced Error Detection

```groovy
// Specific error guidance based on HTTP status
if (errorMsg.contains("404") || errorMsg.contains("Not Found")) {
    log.warn "💡 This may indicate your appliance is a next-generation model with limited API endpoints"
    log.warn "📅 Full API support for next-generation washers/dryers is planned for Fall 2025"
} else if (errorMsg.contains("405") || errorMsg.contains("Method Not Allowed")) {
    log.warn "💡 This command may not be supported by your appliance model or current state"
} else if (errorMsg.contains("400") || errorMsg.contains("Bad Request")) {
    log.warn "💡 Command format may be incorrect or appliance not in correct state for this operation"
}
```

### Program Endpoint Limitations

Many devices don't support the programs listing endpoint:

```groovy
// Fallback for unsupported program listing
if (errorMsg.contains("400") || errorMsg.contains("Bad Request")) {
    log.warn "💡 Programs endpoint may not be available for your appliance model"
    
    // Provide basic program map
    Map basicPrograms = [
        "1": "Cottons",
        "2": "Delicates", 
        "3": "Quick Wash",
        "4": "Eco 40-60"
    ]
    child.updateAvailablePrograms(basicPrograms)
}
```

## 📊 Expected Behavior by Device Generation

### Current Generation Devices (Pre-2023)

**Full API Support**:
- ✅ Complete monitoring (state, time, temperature, etc.)
- ✅ Program control (start/pause/stop/set program)
- ✅ Energy tracking (power/water consumption)
- ✅ Real-time updates via polling
- ✅ Program listing (where supported)

**Example Log Output**:
```
INFO: Command 'startProgram' sent successfully to device 000187642328
INFO: [000187642328] Program started
INFO: [000187642328] Operation state changed: Running
```

### Next-Generation Devices (2023-2025)

**Limited API Support**:
- ✅ Basic monitoring (state information)
- ⚠️ Limited control (commands may fail with 404/405)
- ⚠️ Restricted features (some endpoints unavailable)
- ✅ Real-time updates (monitoring should work)

**Example Log Output**:
```
WARN: ⚠️ API Compatibility: If this command fails, your appliance may be a next-generation model
ERROR: Error sending command 'startProgram' to device 000187642328: Not Found
WARN: 💡 This may indicate your appliance is a next-generation model with limited API endpoints
```

## 🔍 Connection Requirements

### WiFi Conn@ct Appliances
- **Direct WiFi connection** to your router
- **No additional hardware** required
- **Registration** via official Miele mobile app

### ZigBee Appliances  
- **Miele@home Gateway (XGW3000)** required
- **Gateway connects** to WiFi network
- **Appliances connect** to gateway via ZigBee
- **Registration** via official Miele mobile app

## 🎯 Command Compatibility Matrix

### Universal Commands (All Devices)
| Command | Current Gen | Next Gen | Notes |
|---------|-------------|----------|-------|
| Device Discovery | ✅ | ✅ | Always works |
| State Monitoring | ✅ | ✅ | Core functionality |
| Basic Information | ✅ | ✅ | Device details |

### Control Commands (Device Dependent)
| Command | Current Gen | Next Gen | Notes |
|---------|-------------|----------|-------|
| Start Program | ✅ | ⚠️ | May fail on next-gen |
| Pause Program | ✅ | ⚠️ | May fail on next-gen |
| Stop Program | ✅ | ⚠️ | May fail on next-gen |
| Set Program | ✅ | ⚠️ | May fail on next-gen |
| Power On/Off | ❌ | ❌ | Not supported by API |

### Advanced Features
| Feature | Current Gen | Next Gen | Notes |
|---------|-------------|----------|-------|
| Program Listing | ⚠️ | ❌ | Device dependent |
| Temperature Control | ✅ | ⚠️ | Oven-specific |
| Energy Monitoring | ✅ | ✅ | Usually available |

## 📅 Future Roadmap

### Fall 2025 API Update

**Planned Improvements**:
- ✅ **Full next-generation device support**
- ✅ **Enhanced control capabilities**  
- ✅ **New API endpoints** for restricted devices
- ✅ **Improved functionality** for newer appliances

**Integration Preparation**:
- **Automatic compatibility** when new endpoints are available
- **Backward compatibility** for current generation devices
- **Enhanced features** as they become available

## 💡 Best Practices

### For Next-Generation Device Users

1. **Use for Monitoring**: Excellent for status tracking and notifications
2. **Test Commands Carefully**: Try basic commands but expect some failures
3. **Check Logs**: Monitor for API compatibility warnings
4. **Plan for Updates**: Full functionality coming Fall 2025
5. **Hybrid Approach**: Use integration + Miele mobile app

### For Current Generation Device Users

1. **Full Integration**: Use all available features
2. **Automation Ready**: Safe for Rule Machine and other automations
3. **Energy Monitoring**: Leverage power consumption data
4. **Complete Control**: Full smart home integration possible

### For Developers

1. **Error Handling**: Always check for API compatibility errors
2. **Graceful Degradation**: Provide fallbacks for unsupported features
3. **User Feedback**: Clear messaging about device limitations
4. **Future Preparation**: Code for upcoming API changes

## 🔧 Troubleshooting Guide

### Command Failures

**404 Not Found**:
- Likely next-generation device with limited endpoints
- Use monitoring features only until Fall 2025

**405 Method Not Allowed**:
- Command not supported by device or current state
- Check device state and try again

**400 Bad Request**:
- Incorrect command format or invalid device state
- Verify JSON payload and device status

### Setup Issues

**"Client not found"**:
- Verify Client ID accuracy (no extra spaces)
- Try switching API versions (New vs Legacy)
- Ensure Miele developer account is approved

**No devices discovered**:
- Verify devices are registered in Miele mobile app
- Check WiFi/ZigBee connectivity
- Ensure devices are connected to Miele cloud

## 📋 Summary

This API reference provides:
- **Complete endpoint documentation** for current implementation
- **Device generation compatibility** information
- **Error handling strategies** for different scenarios
- **Future roadmap** for API improvements
- **Best practices** for users and developers

The integration is designed to work optimally with current generation devices while providing clear feedback and graceful degradation for next-generation devices with API limitations.
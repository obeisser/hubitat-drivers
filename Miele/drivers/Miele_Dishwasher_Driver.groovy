/**
 *  Miele Dishwasher Driver
 *
 *  Copyright 2025, Oliver Beisser
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
import groovy.transform.Field

// Dishwasher-specific Constants
@Field static final Integer LOG_DISABLE_TIMEOUT_SEC = 1800 // 30 minutes
@Field static final Integer STATE_CHANGE_THRESHOLD_MIN = 5 // Minutes for significant time changes
@Field static final Integer TEMP_PLACEHOLDER_VALUE = -32768

// Miele Process Actions
@Field static final Map MIELE_PROCESS_ACTIONS = [
    START: 1,
    STOP: 2,
    PAUSE: 3,
    RESUME: 4
]

// Power Estimation Constants (Watts) - Standardized approach
@Field static final Map DISHWASHER_POWER_STATES = [
    "running": 1800,        // Typical dishwasher power consumption
    "in use": 1800,
    "heating": 2200,        // Higher power during heating phase
    "washing": 1800,
    "rinsing": 1500,
    "drying": 800,          // Lower power during drying phase
    "pause": 50,
    "programmed waiting to start": 50,
    "standby": 10,
    "off": 0,
    "not connected": 0,
    "failure": 0
]

metadata {
    definition (name: "Miele Dishwasher", namespace: "obeisser", author: "obeisser") {
        capability "Switch"
        capability "Refresh"
        capability "PowerMeter"
        capability "EnergyMeter"

        // Enhanced attributes matching other drivers
        attribute "operationState", "string"
        attribute "remainingTime", "number"
        attribute "elapsedTime", "number"
        attribute "programPhase", "string"
        attribute "targetTemperature", "number"
        attribute "currentTemperature", "number"
        attribute "waterConsumption", "number"
        attribute "doorState", "string"
        attribute "signalInfo", "string"
        attribute "signalFailure", "string"
        attribute "signalDoor", "string"
        attribute "remoteEnable", "string"
        attribute "fullRemoteControl", "string"
        attribute "smartGrid", "string"
        attribute "mobileStart", "string"
        attribute "powerOn", "string"
        attribute "powerOff", "string"
        attribute "lightEnable", "string"
        attribute "lightSwitch", "string"
        attribute "startTime", "string"
        attribute "lastUpdate", "string"
        attribute "connectionState", "string"

        command "startProgram"
        command "pauseProgram"
        command "stopProgram"
        command "resumeProgram"
    }
    
    preferences {
        input name: "logEnable", type: "bool", title: "Enable Debug Logging", defaultValue: false
        input name: "txtEnable", type: "bool", title: "Enable Description Text Logging", defaultValue: true
        input name: "powerEstimation", type: "bool", title: "Enable Power Estimation", defaultValue: true, description: "Estimate power consumption based on operation state"
        input name: "energyTracking", type: "bool", title: "Enable Energy Tracking", defaultValue: true, description: "Track cumulative energy consumption"
    }
}

def installed() {
    parent.driverLogInfo(device.displayName, "Installed Miele Dishwasher Driver")
    initialize()
}

def updated() {
    parent.driverLogInfo(device.displayName, "Updated Miele Dishwasher Driver")
    initialize()
}

def initialize() {
    // Set initial states with error handling
    safeSetInitialStates()
    
    // Initialize energy tracking
    if (energyTracking && !device.currentValue("energy")) {
        parent.driverSendEventSafe(device, "energy", 0, [unit: "kWh"])
    }
    
    // Schedule log disable
    if (logEnable) {
        runIn(LOG_DISABLE_TIMEOUT_SEC, "logsOff")
    }
}

def logsOff() {
    parent.driverLogWarn(device.displayName, "Debug logging disabled")
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

// Switch capability commands with enhanced error handling
def on() {
    parent.driverLogDebug(device.displayName, "Turning on dishwasher", logEnable)
    Map commandBody = [powerOn: true]
    if (parent.driverExecuteCommand(device.deviceNetworkId, "powerOn", commandBody)) {
        parent.driverSendEventSafe(device, "switch", "on")
        if (txtEnable) parent.driverLogInfo(device.displayName, "Dishwasher powered on")
    }
}

def off() {
    parent.driverLogDebug(device.displayName, "Turning off dishwasher", logEnable)
    Map commandBody = [powerOff: true]
    if (parent.driverExecuteCommand(device.deviceNetworkId, "powerOff", commandBody)) {
        parent.driverSendEventSafe(device, "switch", "off")
        if (txtEnable) parent.driverLogInfo(device.displayName, "Dishwasher powered off")
    }
}

// Program control commands with robust error handling
def startProgram() {
    parent.driverLogDebug(device.displayName, "Starting dishwasher program", logEnable)
    Map commandBody = [processAction: MIELE_PROCESS_ACTIONS.START]
    if (parent.driverExecuteCommand(device.deviceNetworkId, "startProgram", commandBody)) {
        if (txtEnable) parent.driverLogInfo(device.displayName, "Program started")
    }
}

def pauseProgram() {
    parent.driverLogDebug(device.displayName, "Pausing dishwasher program", logEnable)
    Map commandBody = [processAction: MIELE_PROCESS_ACTIONS.PAUSE]
    if (parent.driverExecuteCommand(device.deviceNetworkId, "pauseProgram", commandBody)) {
        if (txtEnable) parent.driverLogInfo(device.displayName, "Program paused")
    }
}

def stopProgram() {
    parent.driverLogDebug(device.displayName, "Stopping dishwasher program", logEnable)
    Map commandBody = [processAction: MIELE_PROCESS_ACTIONS.STOP]
    if (parent.driverExecuteCommand(device.deviceNetworkId, "stopProgram", commandBody)) {
        if (txtEnable) parent.driverLogInfo(device.displayName, "Program stopped")
    }
}

def resumeProgram() {
    // Resume is handled by startProgram() - action 1 works for both start and resume
    parent.driverLogInfo(device.displayName, "💡 Use 'Start Program' to resume - it works for both starting and resuming")
    startProgram()
}

def refresh() {
    parent.driverLogDebug(device.displayName, "Refreshing dishwasher state", logEnable)
    parent.refreshDevices()
}

// Enhanced state ingress method with robust error handling
def receiveMieleState(Map stateData) {
    if (!stateData) {
        parent.driverLogWarn(device.displayName, "Received null or empty state data")
        return
    }
    
    parent.driverLogDebug(device.displayName, "Receiving state update: ${stateData}", logEnable)
    
    try {
        String previousOperationState = device.currentValue("operationState")
        
        // Process each state update with individual error handling
        stateData.each { String key, value ->
            processDishwasherStateUpdate(key, value, previousOperationState)
        }
        
        // Update last update timestamp
        parent.driverSendEventSafe(device, "lastUpdate", new Date().format("yyyy-MM-dd HH:mm:ss"))
        
    } catch (Exception e) {
        parent.driverLogError(device.displayName, "Error processing state update: ${e.message}")
    }
}

private void processDishwasherStateUpdate(String key, value, String previousOperationState) {
    if (value == null) return
    
    try {
        switch (key) {
            case "remainingTime":
                handleTimeAttribute(key, value, "min", STATE_CHANGE_THRESHOLD_MIN)
                break
                
            case "elapsedTime":
                handleTimeAttribute(key, value, "min")
                break
                
            case "operationState":
                handleOperationStateChange(value, previousOperationState)
                break
                
            case "programName":
            case "programPhase":
                handleStringAttributeWithLogging(key, value)
                break
                
            case "doorState":
            case "signalInfo":
            case "signalFailure":
            case "signalDoor":
            case "remoteEnable":
            case "fullRemoteControl":
            case "smartGrid":
            case "mobileStart":
            case "powerOn":
            case "powerOff":
            case "lightEnable":
            case "lightSwitch":
            case "startTime":
                handleStringAttribute(key, value)
                break
                
            case "targetTemperature":
            case "currentTemperature":
                handleTemperatureAttribute(key, value)
                break
                
            case "energyConsumption":
                handleEnergyAttribute(value)
                break
                
            case "waterConsumption":
                handleNumericAttribute(key, value, "L")
                break
                
            default:
                handleGenericAttribute(key, value)
                break
        }
    } catch (Exception e) {
        parent.driverLogError(device.displayName, "Error processing ${key}: ${e.message}")
    }
}

private void handleOperationStateChange(String newState, String previousState) {
    if (previousState != newState) {
        parent.driverSendEventSafe(device, "operationState", newState)
        updateSwitchFromOperationState(newState)
        
        // Use standardized power estimation from parent
        parent.driverUpdatePowerEstimation(device, newState, DISHWASHER_POWER_STATES, powerEstimation)
        
        if (txtEnable) {
            parent.driverLogInfo(device.displayName, "Operation state: ${previousState} → ${newState}")
        }
    }
}

private void updateSwitchFromOperationState(String operationState) {
    String switchState = determineSwitchState(operationState)
    String currentSwitch = device.currentValue("switch")
    
    if (currentSwitch != switchState) {
        parent.driverSendEventSafe(device, "switch", switchState)
    }
}

private String determineSwitchState(String operationState) {
    if (!operationState) return "unknown"
    
    String lowerState = operationState.toLowerCase()
    
    if (lowerState.contains("running") || lowerState.contains("in use") || lowerState.contains("heating") || 
        lowerState.contains("washing") || lowerState.contains("rinsing") || lowerState.contains("drying") ||
        lowerState.contains("pause") || lowerState.contains("programmed")) {
        return "on"
    } else if (lowerState.contains("off") || lowerState.contains("not connected") || lowerState.contains("failure")) {
        return "off"
    } else {
        return "unknown"
    }
}

// Helper methods for state processing
private void handleTimeAttribute(String key, Integer value, String unit, Integer threshold = 0) {
    Integer currentValue = device.currentValue(key) as Integer
    if (currentValue != value) {
        parent.driverSendEventSafe(device, key, value, [unit: unit])
        
        if (threshold > 0 && txtEnable && Math.abs((currentValue ?: 0) - value) > threshold) {
            parent.driverLogInfo(device.displayName, "${key}: ${value} ${unit}")
        }
    }
}

private void handleNumericAttribute(String key, value, String unit) {
    def currentValue = device.currentValue(key)
    if (currentValue != value) {
        parent.driverSendEventSafe(device, key, value, [unit: unit])
    }
}

private void handleStringAttribute(String key, String value) {
    String currentValue = device.currentValue(key)
    if (currentValue != value) {
        parent.driverSendEventSafe(device, key, value)
    }
}

private void handleStringAttributeWithLogging(String key, String value) {
    String currentValue = device.currentValue(key)
    if (currentValue != value) {
        parent.driverSendEventSafe(device, key, value)
        if (txtEnable) {
            parent.driverLogInfo(device.displayName, "${key}: ${value}")
        }
    }
}

private void handleTemperatureAttribute(String key, Integer value) {
    Integer currentTemp = device.currentValue(key) as Integer
    if (currentTemp != value && value != TEMP_PLACEHOLDER_VALUE) {
        parent.driverSendEventSafe(device, key, value, [unit: "°C"])
    }
}

private void handleEnergyAttribute(Double value) {
    if (energyTracking) {
        Double currentEnergy = device.currentValue("energy") as Double
        if (currentEnergy != value) {
            parent.driverSendEventSafe(device, "energy", value, [unit: "kWh"])
        }
    }
}

private void handleGenericAttribute(String key, value) {
    if (parent.driverHasAttributeSafe(device, key)) {
        String currentValue = device.currentValue(key)
        if (currentValue != value?.toString()) {
            parent.driverSendEventSafe(device, key, value)
        }
    } else {
        parent.driverLogDebug(device.displayName, "Unknown attribute: ${key} = ${value}", logEnable)
    }
}

private void safeSetInitialStates() {
    try {
        parent.driverSendEventSafe(device, "switch", "unknown")
        parent.driverSendEventSafe(device, "operationState", "Unknown")
        parent.driverSendEventSafe(device, "power", 0, [unit: "W"])
        parent.driverSendEventSafe(device, "connectionState", "Unknown")
        parent.driverSendEventSafe(device, "remainingTime", 0, [unit: "min"])
        parent.driverSendEventSafe(device, "elapsedTime", 0, [unit: "min"])
        parent.driverSendEventSafe(device, "targetTemperature", TEMP_PLACEHOLDER_VALUE, [unit: "°C"])
        parent.driverSendEventSafe(device, "currentTemperature", TEMP_PLACEHOLDER_VALUE, [unit: "°C"])
        parent.driverSendEventSafe(device, "waterConsumption", 0, [unit: "L"])
    } catch (Exception e) {
        parent.driverLogError(device.displayName, "Error setting initial states: ${e.message}")
    }
}

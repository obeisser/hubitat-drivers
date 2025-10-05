/**
 *  Miele Oven Driver
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

// Oven-specific Constants
@Field static final Integer LOG_DISABLE_TIMEOUT_SEC = 1800 // 30 minutes
@Field static final Integer STATE_CHANGE_THRESHOLD_MIN = 5 // Minutes for significant time changes
@Field static final Integer MAX_RETRY_ATTEMPTS = 3
@Field static final Integer TEMP_PLACEHOLDER_VALUE = -32768

// Miele Process Actions
@Field static final Map MIELE_PROCESS_ACTIONS = [
    START: 1,
    STOP: 2,
    PAUSE: 3,
    RESUME: 4
]

// Miele Light Commands
@Field static final Map MIELE_LIGHT_COMMANDS = [
    ON: 1,
    OFF: 2
]

// Temperature Constants
@Field static final Integer MIN_OVEN_TEMP_C = 30
@Field static final Integer MAX_OVEN_TEMP_C = 300
@Field static final Integer DEFAULT_OVEN_TEMP_C = 180

// Thermostat Mode Mappings
@Field static final Map OPERATION_TO_THERMOSTAT_MODE = [
    "heating": "heat",
    "running": "heat",
    "in use": "heat",
    "cooling": "cool",
    "off": "off",
    "not connected": "off",
    "failure": "off"
]

metadata {
    definition (name: "Miele Oven", namespace: "obeisser", author: "obeisser") {
        capability "Switch"
        capability "Light"
        capability "Thermostat"
        capability "Refresh"
        capability "TemperatureMeasurement"

        // Required attributes per specification
        attribute "targetTemperature", "number"
        attribute "currentTemperature", "number"
        attribute "coreTemperature", "number"
        attribute "programPhase", "string"
        attribute "operationState", "string"
        attribute "remainingTime", "number"
        attribute "elapsedTime", "number"
        attribute "startTime", "string"
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
        attribute "lastUpdate", "string"
        attribute "connectionState", "string"
        
        // Commands per specification
        command "startProgram"
        command "pauseProgram"
        command "stopProgram"
        command "resumeProgram"
        command "setLight", [[name: "Light State", type: "ENUM", constraints: ["on", "off"]]]
        command "setThermostatSetpoint", [[name: "Temperature", type: "NUMBER", description: "Target temperature"]]
        command "refreshPrograms"
        command "setProgram", [[name: "Program", type: "STRING", description: "Program name (e.g. 'Bake') or ID (e.g. '1') - requires Mobile Start enabled on device"]]
        command "setProgramWithOptions", [
            [name: "Program ID", type: "STRING", description: "Program ID"],
            [name: "Temperature", type: "NUMBER", description: "Temperature in Celsius (optional)"],
            [name: "Duration Hours", type: "NUMBER", description: "Duration hours (optional)"],
            [name: "Duration Minutes", type: "NUMBER", description: "Duration minutes (optional)"]
        ]
    }
    
    preferences {
        input name: "logEnable", type: "bool", title: "Enable Debug Logging", defaultValue: false
        input name: "txtEnable", type: "bool", title: "Enable Description Text Logging", defaultValue: true
        input name: "tempUnit", type: "enum", title: "Temperature Unit", options: ["C", "F"], defaultValue: "C"
        input name: "tempValidation", type: "bool", title: "Enable Temperature Validation", defaultValue: true, description: "Validate temperature ranges for safety"
    }
}

def installed() {
    logInfo("Installed Miele Oven Driver for ${device.displayName}")
    initialize()
}

def updated() {
    logInfo("Updated Miele Oven Driver for ${device.displayName}")
    initialize()
}

def initialize() {
    // Set initial states with error handling
    safeSetInitialStates()
    
    // Schedule log disable
    if (logEnable) {
        runIn(LOG_DISABLE_TIMEOUT_SEC, "logsOff")
    }
}

def logsOff() {
    logWarn("Debug logging disabled")
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

protected void safeSetInitialStates() {
    try {
        String unit = tempUnit ?: "C"
        
        sendEventSafe("switch", "unknown")
        sendEventSafe("operationState", "Unknown")
        sendEventSafe("light", "unknown")
        sendEventSafe("connectionState", "Unknown")
        sendEventSafe("temperature", TEMP_PLACEHOLDER_VALUE, [unit: "°${unit}"])
        sendEventSafe("heatingSetpoint", DEFAULT_OVEN_TEMP_C, [unit: "°${unit}"])
        sendEventSafe("coolingSetpoint", 0, [unit: "°${unit}"])
        sendEventSafe("thermostatSetpoint", DEFAULT_OVEN_TEMP_C, [unit: "°${unit}"])
        sendEventSafe("thermostatMode", "off")
        sendEventSafe("targetTemperature", TEMP_PLACEHOLDER_VALUE, [unit: "°${unit}"])
        sendEventSafe("currentTemperature", TEMP_PLACEHOLDER_VALUE, [unit: "°${unit}"])
        sendEventSafe("coreTemperature", TEMP_PLACEHOLDER_VALUE, [unit: "°${unit}"])
        sendEventSafe("remainingTime", 0, [unit: "min"])
        sendEventSafe("elapsedTime", 0, [unit: "min"])
    } catch (Exception e) {
        logError("Error setting initial states: ${e.message}")
    }
}

// Switch capability commands with enhanced error handling
def on() {
    logDebug("Turning on oven")
    Map commandBody = [powerOn: true]
    if (executeCommand("powerOn", commandBody)) {
        sendEventSafe("switch", "on")
        if (txtEnable) logInfo("Oven powered on")
    }
}

def off() {
    logDebug("Turning off oven")
    Map commandBody = [powerOff: true]
    if (executeCommand("powerOff", commandBody)) {
        sendEventSafe("switch", "off")
        sendEventSafe("thermostatMode", "off")
        if (txtEnable) logInfo("Oven powered off")
    }
}

// Program control commands with robust error handling
def startProgram() {
    logDebug("Starting oven program")
    Map commandBody = [processAction: MIELE_PROCESS_ACTIONS.START]
    if (executeCommand("startProgram", commandBody)) {
        if (txtEnable) logInfo("Program started")
    }
}

def pauseProgram() {
    logDebug("Pausing oven program")
    Map commandBody = [processAction: MIELE_PROCESS_ACTIONS.PAUSE]
    if (executeCommand("pauseProgram", commandBody)) {
        if (txtEnable) logInfo("Program paused")
    }
}

def stopProgram() {
    logDebug("Stopping oven program")
    Map commandBody = [processAction: MIELE_PROCESS_ACTIONS.STOP]
    if (executeCommand("stopProgram", commandBody)) {
        if (txtEnable) logInfo("Program stopped")
    }
}

def resumeProgram() {
    // Resume is handled by startProgram() - action 1 works for both start and resume
    logInfo("💡 Use 'Start Program' to resume - it works for both starting and resuming")
    startProgram()
}

// Light capability commands with validation
def setLight(value) {
    logDebug("Setting oven light to ${value}")
    
    try {
        // Convert various input types to boolean
        boolean lightOn = parseBoolean(value)
        Integer lightCommand = lightOn ? MIELE_LIGHT_COMMANDS.ON : MIELE_LIGHT_COMMANDS.OFF
        
        Map commandBody = [light: lightCommand]
        if (executeCommand("setLight", commandBody)) {
            String lightState = lightOn ? "on" : "off"
            sendEventSafe("light", lightState)
            if (txtEnable) logInfo("Light ${lightState}")
        }
    } catch (Exception e) {
        logError("Error setting light: ${e.message}")
    }
}

// Thermostat capability commands with validation
def setThermostatSetpoint(BigDecimal temp) {
    try {
        Integer validatedTemp = validateTemperature(temp as Integer)
        String unit = tempUnit ?: "C"
        
        logDebug("Setting thermostat setpoint to ${validatedTemp}°${unit}")
        
        Map commandBody = [targetTemperature: [[value: validatedTemp, unit: "Celsius"]]]
        if (executeCommand("setThermostatSetpoint", commandBody)) {
            Integer displayTemp = (tempUnit == "F") ? celsiusToFahrenheit(validatedTemp) : validatedTemp
            
            sendEventSafe("thermostatSetpoint", displayTemp, [unit: "°${unit}"])
            sendEventSafe("heatingSetpoint", displayTemp, [unit: "°${unit}"])
            
            if (txtEnable) logInfo("Target temperature set to ${displayTemp}°${unit}")
        }
    } catch (Exception e) {
        logError("Error setting thermostat setpoint: ${e.message}")
    }
}

def setHeatingSetpoint(BigDecimal temp) {
    setThermostatSetpoint(temp)
}

def setCoolingSetpoint(BigDecimal temp) {
    // Ovens don't typically cool, but implement for compatibility
    try {
        String unit = tempUnit ?: "C"
        sendEventSafe("coolingSetpoint", temp, [unit: "°${unit}"])
    } catch (Exception e) {
        logError("Error setting cooling setpoint: ${e.message}")
    }
}



def refresh() {
    logDebug("Refreshing oven state")
    safeParentCall("refreshDevices")
}

// Enhanced state ingress method with robust error handling
def receiveMieleState(Map stateData) {
    if (!stateData) {
        logWarn("Received null or empty state data")
        return
    }
    
    logDebug("Receiving state update: ${stateData}")
    
    try {
        String previousOperationState = device.currentValue("operationState")
        
        // Process each state update with individual error handling
        stateData.each { String key, value ->
            processOvenStateUpdate(key, value, previousOperationState)
        }
        
        // Update last update timestamp
        sendEventSafe("lastUpdate", new Date().format("yyyy-MM-dd HH:mm:ss"))
        
    } catch (Exception e) {
        logError("Error processing state update: ${e.message}")
    }
}

private void processOvenStateUpdate(String key, value, String previousOperationState) {
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
                handleOvenOperationStateChange(value, previousOperationState)
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
                handleTargetTemperature(value)
                break
                
            case "currentTemperature":
                handleCurrentTemperature(value)
                break
                
            case "coreTemperature":
                handleCoreTemperature(value)
                break
                
            default:
                handleGenericAttribute(key, value)
                break
        }
    } catch (Exception e) {
        logError("Error processing ${key}: ${e.message}")
    }
}

private void handleOvenOperationStateChange(String newState, String previousState) {
    if (previousState != newState) {
        sendEventSafe("operationState", newState)
        updateSwitchFromOperationState(newState)
        updateThermostatModeFromOperationState(newState)
        
        if (txtEnable) {
            logInfo("Operation state: ${previousState} → ${newState}")
        }
    }
}

private void handleTargetTemperature(Integer value) {
    if (value == TEMP_PLACEHOLDER_VALUE) return
    
    try {
        Integer currentTemp = device.currentValue("targetTemperature") as Integer
        if (currentTemp != value) {
            String unit = tempUnit ?: "C"
            Integer displayTemp = (tempUnit == "F") ? celsiusToFahrenheit(value) : value
            
            sendEventSafe("targetTemperature", displayTemp, [unit: "°${unit}"])
            sendEventSafe("heatingSetpoint", displayTemp, [unit: "°${unit}"])
            sendEventSafe("thermostatSetpoint", displayTemp, [unit: "°${unit}"])
        }
    } catch (Exception e) {
        logError("Error handling target temperature: ${e.message}")
    }
}

private void handleCurrentTemperature(Integer value) {
    if (value == TEMP_PLACEHOLDER_VALUE) return
    
    try {
        Integer currentTemp = device.currentValue("temperature") as Integer
        if (currentTemp != value) {
            String unit = tempUnit ?: "C"
            Integer displayTemp = (tempUnit == "F") ? celsiusToFahrenheit(value) : value
            
            sendEventSafe("currentTemperature", displayTemp, [unit: "°${unit}"])
            sendEventSafe("temperature", displayTemp, [unit: "°${unit}"])
        }
    } catch (Exception e) {
        logError("Error handling current temperature: ${e.message}")
    }
}

private void handleCoreTemperature(Integer value) {
    if (value == TEMP_PLACEHOLDER_VALUE) return
    
    try {
        Integer currentCore = device.currentValue("coreTemperature") as Integer
        if (currentCore != value) {
            String unit = tempUnit ?: "C"
            Integer displayTemp = (tempUnit == "F") ? celsiusToFahrenheit(value) : value
            
            sendEventSafe("coreTemperature", displayTemp, [unit: "°${unit}"])
        }
    } catch (Exception e) {
        logError("Error handling core temperature: ${e.message}")
    }
}

private void handleTimeAttribute(String key, Integer value, String unit, Integer threshold = 0) {
    Integer currentValue = device.currentValue(key) as Integer
    if (currentValue != value) {
        sendEventSafe(key, value, [unit: unit])
        
        if (threshold > 0 && txtEnable && Math.abs((currentValue ?: 0) - value) > threshold) {
            logInfo("${key}: ${value} ${unit}")
        }
    }
}

private void handleStringAttribute(String key, String value) {
    String currentValue = device.currentValue(key)
    if (currentValue != value) {
        sendEventSafe(key, value)
    }
}

private void handleStringAttributeWithLogging(String key, String value) {
    String currentValue = device.currentValue(key)
    if (currentValue != value) {
        sendEventSafe(key, value)
        if (txtEnable) {
            logInfo("${key}: ${value}")
        }
    }
}

private void handleGenericAttribute(String key, value) {
    if (hasAttributeSafe(key)) {
        String currentValue = device.currentValue(key)
        if (currentValue != value?.toString()) {
            sendEventSafe(key, value)
        }
    } else {
        logDebug("Unknown attribute: ${key} = ${value}")
    }
}

private void updateThermostatModeFromOperationState(String operationState) {
    try {
        String lowerState = operationState?.toLowerCase() ?: "unknown"
        String thermostatMode = OPERATION_TO_THERMOSTAT_MODE[lowerState] ?: "auto"
        
        String currentMode = device.currentValue("thermostatMode")
        if (currentMode != thermostatMode) {
            sendEventSafe("thermostatMode", thermostatMode)
        }
    } catch (Exception e) {
        logError("Error updating thermostat mode: ${e.message}")
    }
}

// Utility methods with enhanced error handling and validation
private Integer validateTemperature(Integer temp) {
    if (!tempValidation) return temp
    
    try {
        // Convert to Celsius for validation if needed
        Integer tempC = (tempUnit == "F") ? fahrenheitToCelsius(temp) : temp
        
        if (tempC < MIN_OVEN_TEMP_C) {
            logWarn("Temperature ${temp} too low, using minimum ${MIN_OVEN_TEMP_C}°C")
            return (tempUnit == "F") ? celsiusToFahrenheit(MIN_OVEN_TEMP_C) : MIN_OVEN_TEMP_C
        }
        
        if (tempC > MAX_OVEN_TEMP_C) {
            logWarn("Temperature ${temp} too high, using maximum ${MAX_OVEN_TEMP_C}°C")
            return (tempUnit == "F") ? celsiusToFahrenheit(MAX_OVEN_TEMP_C) : MAX_OVEN_TEMP_C
        }
        
        return temp
    } catch (Exception e) {
        logError("Error validating temperature: ${e.message}")
        return DEFAULT_OVEN_TEMP_C
    }
}

private boolean parseBoolean(value) {
    if (value == null) return false
    
    if (value instanceof Boolean) return value
    if (value instanceof Number) return value != 0
    
    String strValue = value.toString().toLowerCase()
    return strValue in ["true", "on", "1", "yes"]
}

private Integer celsiusToFahrenheit(Integer celsius) {
    try {
        return Math.round(celsius * 9/5 + 32) as Integer
    } catch (Exception e) {
        logError("Error converting C to F: ${e.message}")
        return celsius
    }
}

private Integer fahrenheitToCelsius(Integer fahrenheit) {
    try {
        return Math.round((fahrenheit - 32) * 5/9) as Integer
    } catch (Exception e) {
        logError("Error converting F to C: ${e.message}")
        return fahrenheit
    }
}

// Utility methods for robust operation
protected boolean executeCommand(String command, Map commandBody, Integer maxRetries = MAX_RETRY_ATTEMPTS) {
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            if (safeParentCall("childDriverCommand", [device.deviceNetworkId, command, commandBody])) {
                logDebug("Command ${command} executed successfully")
                return true
            }
        } catch (Exception e) {
            logError("Command ${command} attempt ${attempt} failed: ${e.message}")
            if (attempt < maxRetries) {
                pauseExecution(1000 * attempt) // Exponential backoff
            }
        }
    }
    
    logError("Command ${command} failed after ${maxRetries} attempts")
    return false
}

protected boolean safeParentCall(String method, List args = []) {
    try {
        if (!parent) {
            logError("No parent app available for ${method}")
            return false
        }
        
        if (args.isEmpty()) {
            return parent."${method}"()
        } else {
            return parent."${method}"(*args)
        }
    } catch (Exception e) {
        logError("Parent call ${method} failed: ${e.message}")
        return false
    }
}

protected void sendEventSafe(String name, value, Map options = [:]) {
    try {
        Map eventMap = [name: name, value: value] + options
        sendEvent(eventMap)
    } catch (Exception e) {
        logError("Failed to send event ${name}: ${e.message}")
    }
}

protected boolean hasAttributeSafe(String attrName) {
    try {
        return device.hasAttribute(attrName)
    } catch (Exception e) {
        logDebug("Error checking attribute ${attrName}: ${e.message}")
        return false
    }
}

// Logging methods with consistent formatting
protected void logDebug(String message) {
    if (logEnable) log.debug("[${device.displayName}] ${message}")
}

protected void logInfo(String message) {
    log.info("[${device.displayName}] ${message}")
}

protected void logWarn(String message) {
    log.warn("[${device.displayName}] ${message}")
}

protected void logError(String message) {
    log.error("[${device.displayName}] ${message}")
}

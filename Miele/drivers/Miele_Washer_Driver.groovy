/**
 *  Miele Washer Driver - Ultra-Optimized Clean Slate Version
 *
 *  Copyright 2025, Oliver Beisser
 *
 *  This driver is ultra-minimal and delegates all common functionality
 *  to the parent app for maximum optimization and maintainability.
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

// Washer-specific Power States (Watts) - Only device-specific data
@Field static final Map WASHER_POWER_STATES = [
    "running": 1500, "in use": 1500, "heating": 2000, "spinning": 800,
    "rinsing": 1200, "draining": 300, "pause": 50, "off": 0
]

metadata {
    definition (name: "Miele Washer", namespace: "obeisser", author: "obeisser") {
        capability "Switch"
        capability "Refresh"
        capability "PowerMeter"
        capability "EnergyMeter"

        // Washer-specific attributes
        attribute "operationState", "string"
        attribute "remainingTime", "number"
        attribute "elapsedTime", "number"
        attribute "spinSpeed", "number"
        attribute "programPhase", "string"
        attribute "waterConsumption", "number"
        attribute "targetTemperature", "number"
        attribute "currentTemperature", "number"
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
        attribute "availablePrograms", "string"
        attribute "selectedProgram", "string"
        attribute "selectedProgramId", "string"
        
        command "startProgram"
        command "pauseProgram"
        command "stopProgram"
        command "refreshPrograms"
        command "setProgram", [[name: "Program", type: "STRING", description: "Program name (e.g. 'Cottons') or ID (e.g. '1') - requires Mobile Start enabled on device"]]
    }
    
    preferences {
        input name: "logEnable", type: "bool", title: "Enable Debug Logging", defaultValue: false
        input name: "txtEnable", type: "bool", title: "Enable Description Text Logging", defaultValue: true
        input name: "powerEstimation", type: "bool", title: "Enable Power Estimation", defaultValue: true
        input name: "energyTracking", type: "bool", title: "Enable Energy Tracking", defaultValue: true
    }
}

// ========================================
// LIFECYCLE METHODS - Delegate to Parent
// ========================================

def installed() {
    parent.driverLogInfo(device.displayName, "Installed Miele Washer")
    initialize()
}

def updated() {
    parent.driverLogInfo(device.displayName, "Updated Miele Washer")
    initialize()
}

def initialize() {
    Map deviceConfig = getDeviceConfig()
    parent.driverInitializeDevice(device, deviceConfig)
    
    if (logEnable) {
        runIn(1800, "logsOff") // 30 minutes
    }
}

def logsOff() {
    parent.driverLogWarn(device.displayName, "Debug logging disabled")
    device.updateSetting("logEnable", [value: "false", type: "bool"])
}

// ========================================
// COMMAND METHODS - Delegate to Parent
// ========================================

def on() {
    parent.driverLogWarn(device.displayName, "Power On command not supported by Miele API - use startProgram() instead")
}

def off() {
    parent.driverLogWarn(device.displayName, "Power Off command not supported by Miele API - use stopProgram() instead")
}

def startProgram() {
    String currentState = device.currentValue("operationState")?.toLowerCase() ?: ""
    
    // Start/Resume works in "Waiting to start" and "In use" (paused) states
    if (!currentState.contains("waiting to start") && !currentState.contains("in use")) {
        parent.driverLogWarn(device.displayName, "Cannot start/resume program - washer must be in 'Waiting to start' or 'In use' (paused) state. Current state: ${device.currentValue('operationState')}")
        return
    }
    
    String action = currentState.contains("in use") ? "Resuming" : "Starting"
    parent.driverLogDebug(device.displayName, "${action} program", logEnable)
    Map commandBody = [processAction: 1] // START (works for both start and resume)
    if (parent.driverExecuteCommand(device.deviceNetworkId, "startProgram", commandBody)) {
        if (txtEnable) parent.driverLogInfo(device.displayName, "Program ${action.toLowerCase()}")
    }
}

def pauseProgram() {
    parent.driverLogDebug(device.displayName, "Pausing program", logEnable)
    Map commandBody = [processAction: 3] // PAUSE
    if (parent.driverExecuteCommand(device.deviceNetworkId, "pauseProgram", commandBody)) {
        if (txtEnable) parent.driverLogInfo(device.displayName, "Program paused")
    }
}

def stopProgram() {
    parent.driverLogDebug(device.displayName, "Stopping program", logEnable)
    Map commandBody = [processAction: 2] // STOP
    if (parent.driverExecuteCommand(device.deviceNetworkId, "stopProgram", commandBody)) {
        if (txtEnable) parent.driverLogInfo(device.displayName, "Program stopped")
    }
}

def refresh() {
    parent.driverLogDebug(device.displayName, "Refreshing state", logEnable)
    parent.refreshDevices()
}

def refreshPrograms() {
    parent.driverLogDebug(device.displayName, "Refreshing available programs", logEnable)
    parent.getDevicePrograms(device.deviceNetworkId)
}

// Removed selectProgram and selectAndStartProgram - use setProgram instead

def setProgram(String program) {
    if (!program) {
        parent.driverLogWarn(device.displayName, "No program specified")
        return
    }
    
    // Handle special refresh command
    if (program.toLowerCase() == "refresh" || program.toLowerCase().contains("refresh")) {
        refreshPrograms()
        return
    }
    
    // Check if Mobile Start is enabled (only warn, don't block)
    if (!isMobileStartEnabled()) {
        parent.driverLogWarn(device.displayName, "⚠️ Mobile Start appears to be disabled. Program selection may not work. Enable 'Mobile Start' on the device if commands fail.")
    }
    
    parent.driverLogDebug(device.displayName, "Setting program: ${program}", logEnable)
    
    // Handle format "Program Name (ID)" from dropdown
    String cleanProgram = program
    if (program.contains(" (") && program.endsWith(")")) {
        cleanProgram = program.substring(0, program.lastIndexOf(" ("))
    }
    
    // Try to find program by name first, then by ID
    String programId = findProgramId(cleanProgram)
    
    if (programId) {
        // Select and start the program in one action using washer-specific method
        if (parent.setWasherProgram(device.deviceNetworkId, programId)) {
            parent.driverSendEventSafe(device, "selectedProgramId", programId)
            Map programs = getAvailablePrograms()
            String programName = programs[programId] ?: cleanProgram
            
            // Wait a moment then start the program
            runIn(2, "startSelectedProgram")
            
            if (txtEnable) parent.driverLogInfo(device.displayName, "Program set and will start: ${programName} (ID: ${programId})")
        }
    } else {
        parent.driverLogWarn(device.displayName, "Program '${cleanProgram}' not found. Available programs: ${getAvailableProgramNames()}")
        parent.driverLogInfo(device.displayName, "💡 Try: 'Cottons', 'Delicates', 'Quick Wash', or program IDs like '1', '2', '3'")
    }
}

def startSelectedProgram() {
    startProgram()
}

// ========================================
// STATE PROCESSING - Delegate to Parent
// ========================================

def receiveMieleState(Map stateData) {
    Map deviceConfig = getDeviceConfig()
    parent.driverProcessState(device, stateData, deviceConfig)
}

// ========================================
// DEVICE CONFIGURATION - Only Device-Specific Data
// ========================================

private Map getDeviceConfig() {
    return [
        deviceType: "Washer",
        logEnable: logEnable,
        txtEnable: txtEnable,
        powerEstimation: powerEstimation,
        energyTracking: energyTracking,
        powerStates: WASHER_POWER_STATES
    ]
}

// ========================================
// PROGRAM MANAGEMENT METHODS
// ========================================

def updateAvailablePrograms(Map programs) {
    try {
        // Store programs as JSON string for the attribute
        String programsJson = new groovy.json.JsonBuilder(programs).toString()
        parent.driverSendEventSafe(device, "availablePrograms", programsJson)
        
        // Store programs in device state for easy access
        state.availablePrograms = programs
        
        parent.driverLogInfo(device.displayName, "Available programs updated: ${programs.size()} programs")
        
        // If we have a selected program ID, update the name
        String selectedId = device.currentValue("selectedProgramId")
        if (selectedId && programs[selectedId]) {
            parent.driverSendEventSafe(device, "selectedProgram", programs[selectedId])
        }
    } catch (Exception e) {
        parent.driverLogError(device.displayName, "Error updating available programs: ${e.message}")
    }
}

def getAvailablePrograms() {
    return state.availablePrograms ?: [:]
}

def getAvailableProgramNames() {
    Map programs = getAvailablePrograms()
    return programs.values().join(", ")
}

def findProgramId(String programInput) {
    Map programs = getAvailablePrograms()
    
    // First try exact name match (case insensitive)
    String matchingId = programs.find { id, name -> 
        name.toLowerCase() == programInput.toLowerCase() 
    }?.key
    
    if (matchingId) {
        return matchingId
    }
    
    // Then try partial name match
    matchingId = programs.find { id, name -> 
        name.toLowerCase().contains(programInput.toLowerCase()) 
    }?.key
    
    if (matchingId) {
        return matchingId
    }
    
    // Finally try direct ID match
    if (programs.containsKey(programInput)) {
        return programInput
    }
    
    return null
}

def isMobileStartEnabled() {
    String mobileStart = device.currentValue("mobileStart")
    return mobileStart == "enabled" || mobileStart == "true"
}

// Enhanced initialization to fetch programs
protected void initializeDeviceSpecific() {
    // Initialize washer-specific states
    parent.driverSendEventSafe(device, "spinSpeed", 0, [unit: "rpm"])
    parent.driverSendEventSafe(device, "waterConsumption", 0, [unit: "L"])
    parent.driverSendEventSafe(device, "availablePrograms", "{}")
    parent.driverSendEventSafe(device, "selectedProgram", "Unknown")
    parent.driverSendEventSafe(device, "selectedProgramId", "")
    
    // Fetch available programs after a short delay
    runIn(5, "refreshPrograms")
}

// That's it! Enhanced with program management capabilities
// All common functionality is in the parent app - single source of truth
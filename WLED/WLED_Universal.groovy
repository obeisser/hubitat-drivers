/**
 * WLED Universal Driver for Hubitat Elevation
 *
 * Author: Oliver Beisser
 * Original by bryan@joyful.house
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 * for the specific language governing permissions and limitations under the License.
 *
 * Changelog
 *
 * v1.3.3 (2025-10-05)
 * - feat: Unified command interface - setEffect, setPalette, setPreset, and setPlaylist now accept either ID numbers or names for improved usability.
 * - refactor: Consolidated duplicate ID/name-based commands into single, more intuitive commands. Legacy ByName commands remain available for backward compatibility but are clearly marked in UI.
 * - feat: Added nextPresetInPlaylist command to advance through playlist presets (WLED v0.15+ feature).
 * - feat: Enhanced preset management - savePreset now accepts custom parameters (brightness, effect, palette, colors, etc.) with optional preset ID - if no ID provided, automatically finds next available slot (1-250).
 * - feat: Update existing presets - providing existing preset ID updates that preset, including name changes.
 * - feat: Added deletePreset command for preset management.
 * - fix: Corrected preset naming API - now uses "n" parameter instead of "pname" per WLED documentation.
 * - feat: Enhanced preset saving with proper WLED parameters (ib: include brightness, sb: save segment bounds).
 * - refactor: Consolidated use of NETWORK_CONFIG constants for timeouts, retries, and intervals.
 * - refactor: Simplified retry logic by removing redundant sendWledCommandWithRetry method.
 * - refactor: Improved code clarity with better timeout calculations and reduced redundant error handling.
 * 
 * v1.3.2 (2025-10-04)
 * - feat: Improve preset management and handeling, including loading, listing, setting by name, and saving presets.
 * - fix: Corrected the API endpoint for fetching presets to `/presets.json` and updated parsing logic.
 * - fix: Corrected preset parsing to use the 'n' key for the preset name.
 * - fix: Ignore preset 0 and separate playlists from presets.
 * 
 * v1.3.1 (2025-10-03)
 * - fix: The setNightlight command now ensures the master power is turned on, providing a more intuitive user experience.
 *
 * v1.3.0 (2025-10-01)
 * - feat: Added complete nightlight implementation with on/off and parameter control.
 * - feat: Added nightlight status attributes for active state, duration, mode, and target brightness.
 *
 * v1.2.1 (2025-09-30)
 * - feat: Added command descriptions for better user interface clarity.
 * - fix: Corrected alarm effects to use actual available WLED effects.
 *
 * v1.2.0 (2025-09-29)
 * - feat: Added name-based selection for effects, palettes, and playlists with smart matching.
 * - feat: Added comprehensive playlist and reverse effect switch support.
 * - feat: Added discovery commands (listEffects, listPalettes, listPlaylists) and device info tracking.
 * - refactor: Improved retry logic, error recovery, and connection health monitoring.
 * - refactor: Optimized state synchronization and code organization.
 * - fix: Resolved null pointer exceptions and improved boolean handling.
 *
 * v1.1.0
 * - refactor: Improved error handling and retry logic for network operations.
 * - refactor: Enhanced state synchronization and attribute updates.
 *
 * v1.0.0
 * - Initial release with asynchronous HTTP calls, robust error handling, and extensive WLED API coverage.
 */

import groovy.json.JsonOutput
import groovy.transform.Field

// WLED API Constants
@Field Map WLED_ENDPOINTS = [
    STATE: "/json/state",
    FULL: "/json",
    INFO: "/json/info",
    LIVE: "/json/live",
    CONFIG: "/json/cfg",
    PRESETS: "/presets.json"
]

@Field Map WLED_LIMITS = [
    MAX_BRIGHTNESS: 255,
    MAX_SEGMENTS: 32,
    MAX_EFFECTS: 255,
    MAX_PALETTES: 255,
    MIN_KELVIN: 2000,
    MAX_KELVIN: 6500
]

@Field Map NETWORK_CONFIG = [
    DEFAULT_TIMEOUT: 5,
    MAX_RETRIES: 3,
    RETRY_DELAY: 2,
    CONNECTION_CHECK_INTERVAL: 30
]

metadata {
    definition (name: "WLED Universal", namespace: "obeisser", author: "obeisser") {
        capability "Actuator"
        capability "Sensor"
        capability "Color Control"
        capability "Color Temperature"
        capability "ColorMode"
        capability "Refresh"
        capability "Switch"
        capability "Switch Level"
        capability "Light"
        capability "Alarm"

        attribute "colorName", "string"
        attribute "effectName", "string"
        attribute "paletteName", "string"
        attribute "presetValue", "number"
        attribute "effectDirection", "string"
        attribute "reverse", "string"
        attribute "firmwareVersion", "string"
        attribute "connectionState", "string"
        attribute "lastUpdate", "string"
        attribute "effectId", "number"
        attribute "paletteId", "number"
        attribute "availableEffects", "string"
        attribute "availablePalettes", "string"
        attribute "playlistName", "string"
        attribute "playlistId", "number"
        attribute "availablePlaylists", "string"
        attribute "playlistState", "string"
        attribute "presetName", "string"
        attribute "availablePresets", "string"
        attribute "nightlightActive", "string"
        attribute "nightlightDuration", "number"
        attribute "nightlightMode", "string"
        attribute "nightlightTargetBrightness", "number"
        attribute "nightlightRemaining", "number"
        
        // Effect Control Commands
        command "setEffect", [
            [name:"effect", type: "STRING", description: "Effect ID (0-255) or Effect Name (e.g., 'Rainbow', 'Fire 2012')"],
            [name:"speed", type: "NUMBER", description: "Relative Effect Speed (0-255)"],
            [name:"intensity", type: "NUMBER", description: "Effect Intensity (0-255)"],
            [name:"palette", type: "STRING", description: "Palette ID (0-255) or Palette Name (e.g., 'Rainbow', 'Fire') - optional"]
        ]
        command "setPalette", [
            [name:"palette", type: "STRING", description: "Palette ID (0-255) or Palette Name (e.g., 'Rainbow', 'Fire')"]
        ]
        command "listEffects", [[name:"List all available effects with ID numbers"]]
        command "listPalettes", [[name:"List all available palettes with ID numbers"]]
        
        // Playlist Control Commands
        command "setPlaylist", [
            [name:"playlist", type: "STRING", description: "Playlist ID (1-250) or Playlist Name"]
        ]
        command "stopPlaylist", [[name:"Stop currently running playlist"]]
        command "nextPresetInPlaylist", [[name:"Advance to next preset in current playlist"]]
        command "listPlaylists", [[name:"List all available playlists with ID numbers"]]
        
        // Preset Control Commands
        command "setPreset", [
            [name:"preset", type: "STRING", description: "Preset ID (1-250) or Preset Name"]
        ]
        command "savePreset", [
            [name:"presetName", type: "STRING", description: "Name for the preset"],
            [name:"presetId", type: "NUMBER", description: "Preset ID (1-250) - optional. If provided, will UPDATE existing preset. If omitted, uses next available slot."],
            [name:"brightness", type: "NUMBER", description: "Brightness (0-255) - optional"],
            [name:"effect", type: "STRING", description: "Effect ID or name - optional"],
            [name:"palette", type: "STRING", description: "Palette ID or name - optional"],
            [name:"speed", type: "NUMBER", description: "Effect speed (0-255) - optional"],
            [name:"intensity", type: "NUMBER", description: "Effect intensity (0-255) - optional"],
            [name:"primaryColor", type: "STRING", description: "Primary color as hex (e.g., 'FF0000') - optional"],
            [name:"secondaryColor", type: "STRING", description: "Secondary color as hex (e.g., '00FF00') - optional"]
        ]
        command "saveCurrentAsPreset", [
            [name:"presetName", type: "STRING", description: "Name for the preset"],
            [name:"presetId", type: "NUMBER", description: "Preset ID (1-250) - optional. If provided, will UPDATE existing preset. If omitted, uses next available slot."]
        ]
        command "deletePreset", [
            [name:"presetId", type: "NUMBER", description: "Preset ID to delete (1-250)"]
        ]
        command "listPresets", [[name:"List all available presets with ID numbers"]]

        // Effect Direction Control Commands
        command "toggleEffectDirection", [[name:"Toggle effect animation direction (forward/reverse)"]]
        command "reverseOn", [[name:"Turn effect reverse direction ON"]]
        command "reverseOff", [[name:"Turn effect reverse direction OFF"]]
        
        // Alarm Control Commands
        command "siren", [[name:"Activate siren alarm (Chase Flash effect with red colors)"]]
        command "strobe", [[name:"Activate strobe alarm (white flashing strobe effect)"]]
        command "both", [[name:"Activate both siren and strobe alarm (Strobe Mega effect with red/blue colors)"]]

        // Nightlight Control Commands
        command "setNightlight", [
            [name:"duration", type: "NUMBER", description: "Duration in minutes (1-255)"],
            [name:"mode", type: "ENUM", constraints: ["Instant", "Fade", "Color Fade", "Sunrise"], description: "Nightlight mode"],
            [name:"targetBrightness", type: "NUMBER", description: "Target brightness (0-255)"]
        ]
        command "nightlightOff", []

        // Diagnostics
        command "forceRefresh", [[name:"Force refresh device state, effects, and palettes"]]
        command "getDeviceInfo", [[name:"Get WLED firmware version and device information"]]
        command "testConnection", [[name:"Test network connection to WLED device"]]
        
        // Legacy Commands (for backward compatibility only - use unified commands above)
        command "setEffectByName", [
            [name:"effectName", type: "STRING", description: "⚠️ LEGACY: Use setEffect instead - Effect Name"],
            [name:"speed", type: "NUMBER", description: "Effect Speed (0-255)"],
            [name:"intensity", type: "NUMBER", description: "Effect Intensity (0-255)"],
            [name:"paletteName", type: "STRING", description: "Palette Name (optional)"]
        ]
        command "setPaletteByName", [
            [name:"paletteName", type: "STRING", description: "⚠️ LEGACY: Use setPalette instead - Palette Name"]
        ]
        command "setPresetByName", [
            [name:"presetName", type: "STRING", description: "⚠️ LEGACY: Use setPreset instead - Preset Name"]
        ]
        command "setPlaylistByName", [
            [name:"playlistName", type: "STRING", description: "⚠️ LEGACY: Use setPlaylist instead - Playlist Name"]
        ]
    }
    
    preferences {
        input "uri", "text", title: "WLED URI", description: "Example: http://[wled_ip_address]", required: true, displayDuringSetup: true
        input name: "ledSegment", type: "number", title: "LED Segment ID", defaultValue: 0, required: true
        input name: "transitionTime", type: "enum", title: "Default Transition Time", options: [[0:"0ms"], [400:"400ms"], [700:"700ms"], [1000:"1s"], [2000:"2s"], [5000:"5s"]], defaultValue: 700
        input name: "refreshInterval", type: "enum", title: "Polling Refresh Interval", options: [[0:"Disabled"], [30:"30 Seconds"], [60:"1 Minute"], [300:"5 Minutes"], [600:"10 Minutes"], [1800:"30 Minutes"], [3600:"1 Hour"]], defaultValue: 300
        input name: "powerOffParent", type: "bool", title: "Power Off Main Controller with Segment", description: "If enabled, the main WLED power will be turned off when this segment is.", defaultValue: false
        input name: "enableRetry", type: "bool", title: "Enable Auto-Retry on Network Errors", description: "Automatically retry failed commands", defaultValue: true
        input name: "connectionMonitoring", type: "bool", title: "Enable Connection Monitoring", description: "Monitor and report connection status", defaultValue: true
        input name: "showDeprecatedCommands", type: "bool", title: "Show Deprecated Commands", description: "Show legacy ByName commands in device page (still available for automations)", defaultValue: false
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

//--- LIFECYCLE METHODS ---//
def installed() {
    log.info "Installing WLED Universal Driver v1.3.3..."
    initializeState()
    runIn(1, updated)
}

def updated() {
    log.info "Settings updated. Initializing..."
    state.initialized = false
    state.retryCount = 0
    unschedule()
    
    // Add/remove deprecated commands based on user preference
    updateCommandVisibility()
    
    if (settings.uri) {
        initializeState()
        runIn(1, forceRefresh)
        if (settings.connectionMonitoring) {
            runIn(30, checkConnection)
        }
    }
}

def initialize() {
    log.info "Initializing..."
    runIn(1, updated)
}

private initializeState() {
    state.connectionState = "unknown"
    state.lastSuccessfulContact = now()
    state.retryCount = 0
    state.presets = [:]
    state.playlists = [:]
    updateAttr("connectionState", "initializing")
}

private updateCommandVisibility() {
    // Note: Hubitat doesn't support dynamic command addition/removal
    // The deprecated commands are always available programmatically
    // This method is here for future enhancement if Hubitat adds this capability
    if (logEnable) {
        if (settings.showDeprecatedCommands) {
            log.info "Deprecated ByName commands are enabled for UI visibility"
        } else {
            log.info "Deprecated ByName commands are hidden from UI but remain available for automations"
        }
    }
}

def parse(hubitat.scheduling.AsyncResponse response, Map data = null) {
    try {
        if (response.hasError()) {
            handleNetworkError("HTTP Error: ${response.getErrorMessage()}", data)
            return
        }
        
        state.retryCount = 0
        state.lastSuccessfulContact = now()
        updateAttr("connectionState", "connected")
        updateAttr("lastUpdate", new Date().format("yyyy-MM-dd HH:mm:ss"))
        
        def msg = response.getJson()
        if (logEnable) log.debug "Response JSON: ${msg}"

        if (!msg) {
            log.warn "Received empty or invalid JSON response."
            return
        }

        String requestPath = data?.path

        switch (requestPath) {
            case WLED_ENDPOINTS.FULL:
                handleFullResponse(msg)
                break
            case WLED_ENDPOINTS.STATE:
                synchronizeState(msg)
                break
            case WLED_ENDPOINTS.INFO:
                handleDeviceInfo(msg)
                break
            case WLED_ENDPOINTS.PRESETS:
                handlePresetsInfo(msg)
                break
            default:
                if (msg.containsKey("state") && msg.containsKey("effects") && msg.containsKey("palettes")) {
                    handleFullResponse(msg)
                } else if (msg.containsKey("state")) {
                    synchronizeState(msg.state)
                } else {
                    log.warn "Unknown response type received."
                }
                break
        }
    } catch (e) {
        log.error "Fatal error parsing response: ${e.message}"
        handleNetworkError("Parse error: ${e.message}", data)
    }
}

private handleFullResponse(Map msg) {
    boolean wasInitialized = state.initialized
    state.effects = msg.effects
    state.palettes = msg.palettes
    state.segments = msg.state.seg ?: []
    synchronizeState(msg.state)
    
    if (!wasInitialized) {
        log.info "Full device info received. Driver initialization complete."
        log.info "Loaded ${state.effects.size()} effects and ${state.palettes.size()} palettes"
        setSchedule()
        state.initialized = true
        runIn(2, getDeviceInfo)
        runIn(4, getPresets)
        runIn(5, listEffects)
        runIn(6, listPalettes)
        runIn(7, listPresets)
        runIn(8, listPlaylists)
    }
}

//--- CAPABILITY COMMANDS ---//
def on() { sendWledCommand([on:true, seg: [[id: ledSegment.toInteger(), on:true]]]) }
def off() {
    def payload = [seg: [[id: ledSegment.toInteger(), on:false]]]
    if (powerOffParent) { payload.on = false }
    sendWledCommand(payload)
}

def setLevel(value, rate = null) {
    if (value > 99) value = 100
    if (value < 0) value = 0
    if (value == 0) { off() } 
    else {
        def brightness = (value * 2.55).toInteger()
        sendWledCommand([on: true, seg: [[id: ledSegment.toInteger(), on: true, bri: brightness]]], rate)
    }
}

def setColor(value) {
    if (value.level == 0) { off(); return }
    def rgb = hsvToRgb(value.hue, value.saturation, 100)
    def level = (value.level * 2.55).toInteger()
    def payload = [on: true, seg: [[id: ledSegment.toInteger(), on: true, bri: level, col: [rgb], fx: 0]]]
    sendWledCommand(payload)
}

def setColorTemperature(value) {
    if (logEnable) log.debug "Setting color temperature to ${value}K"
    def rgb = colorTempToRgb(value)
    def payload = [on: true, seg: [[id: ledSegment.toInteger(), on: true, col: [rgb], fx: 0]]]
    sendWledCommand(payload)
    sendEvent(name: "colorTemperature", value: value)
    sendEvent(name: "colorMode", value: "CT")
    setGenericNameFromTemp(value)
}

def refresh() { sendEthernetGet(WLED_ENDPOINTS.STATE) }

//--- CUSTOM COMMANDS ---//
def forceRefresh() {
    log.info "Forcing full refresh of state, effects, palettes, and presets..."
    sendEthernetGet(WLED_ENDPOINTS.FULL)
}

def toggleEffectDirection() {
    def currentDirection = device.currentValue("effectDirection")
    boolean newReverseState = (currentDirection == "reverse") ? false : true
    if (logEnable) log.debug "Toggling effect direction. Current: ${currentDirection}, New reverse state: ${newReverseState}"
    setEffectReverse(newReverseState)
}

def reverseOn() {
    if (logEnable) log.debug "Turning reverse effect ON"
    setEffectReverse(true)
}

def reverseOff() {
    if (logEnable) log.debug "Turning reverse effect OFF"
    setEffectReverse(false)
}

def getDeviceInfo() {
    if (logEnable) log.debug "Requesting device information..."
    sendEthernetGet(WLED_ENDPOINTS.INFO)
}

def testConnection() {
    log.info "Testing connection to WLED device..."
    sendEthernetGet(WLED_ENDPOINTS.STATE)
}

private setEffectReverse(boolean reverse) {
    if (!validateSegment(settings.ledSegment.toInteger())) return
    def payload = [seg: [[id: settings.ledSegment.toInteger(), rev: reverse]]]
    sendWledCommand(payload)
}

def setEffect(String effect, Number speed = null, Number intensity = null, String palette = null) {
    def effectId = resolveEffectId(effect)
    if (effectId == null) {
        log.error "Effect '${effect}' not found. Available effects: ${getEffectsList()}"
        return
    }
    
    def paletteId = null
    if (palette) {
        paletteId = resolvePaletteId(palette)
        if (paletteId == null) {
            log.warn "Palette '${palette}' not found. Using current palette. Available palettes: ${getPalettesList()}"
        }
    }
    
    if (logEnable) log.debug "Setting effect '${effect}' (ID: ${effectId}) with speed: ${speed}, intensity: ${intensity}, palette: ${palette} (ID: ${paletteId})"
    
    def seg = [id: ledSegment.toInteger(), fx: effectId.toInteger()]
    if (speed != null) seg.sx = speed.toInteger()
    if (intensity != null) seg.ix = intensity.toInteger()
    if (paletteId != null) seg.pal = paletteId.toInteger()
    sendWledCommand([on: true, seg: [seg]])
}

def setPalette(String palette) {
    def paletteId = resolvePaletteId(palette)
    if (paletteId == null) {
        log.error "Palette '${palette}' not found. Available palettes: ${getPalettesList()}"
        return
    }
    
    if (logEnable) log.debug "Setting palette '${palette}' (ID: ${paletteId})"
    def payload = [seg: [[id: settings.ledSegment.toInteger(), pal: paletteId]]]
    sendWledCommand(payload)
}

def listEffects() {
    if (!state.effects) {
        log.warn "Effects list not available. Refreshing device..."
        forceRefresh()
        return
    }
    
    def effectsList = getEffectsList()
    log.info "Available Effects (${state.effects.size()}): ${effectsList}"
    updateAttr("availableEffects", effectsList)
}

def listPalettes() {
    if (!state.palettes) {
        log.warn "Palettes list not available. Refreshing device..."
        forceRefresh()
        return
    }
    
    def palettesList = getPalettesList()
    log.info "Available Palettes (${state.palettes.size()}): ${palettesList}"
    updateAttr("availablePalettes", palettesList)
}

def setPreset(String preset) {
    def presetId = resolvePresetId(preset)
    if (presetId == null) {
        log.error "Preset '${preset}' not found. Available presets: ${getPresetsList()}"
        return
    }
    
    if (logEnable) log.debug "Activating preset '${preset}' (ID: ${presetId})"
    sendWledCommand([ps: presetId.toInteger()])
}

def savePreset(String presetName, Number presetId = null, Number brightness = null, String effect = null, String palette = null, Number speed = null, Number intensity = null, String primaryColor = null, String secondaryColor = null) {
    // Examples:
    // savePreset("Bright Rainbow", null, 255, "Rainbow", "Default", 150, 128)  // Auto-assign next available ID
    // savePreset("Red Alert", 11, 200, "Strobe", null, 255, 255, "FF0000", "000000")  // Update/create preset 11
    // savePreset("Sunset")  // Simple preset with auto-assigned ID, current device settings
    // savePreset("Blue Mood", null, 100, null, null, null, null, "0000FF")  // Auto-assign with blue color
    
    if (!presetName || presetName.trim().isEmpty()) {
        log.error "Preset name cannot be empty."
        return
    }
    
    // Determine preset ID
    def targetPresetId = presetId
    if (targetPresetId == null) {
        targetPresetId = findNextAvailablePresetId()
        if (targetPresetId == null) {
            log.error "No available preset slots found (1-250)."
            return
        }
        if (logEnable) log.debug "Auto-assigned preset ID: ${targetPresetId}"
    } else {
        if (targetPresetId < 1 || targetPresetId > 250) {
            log.error "Invalid preset ID: ${targetPresetId}. Must be between 1-250."
            return
        }
        // Check if we're updating an existing preset
        if (state.presets && state.presets[targetPresetId.toString()]) {
            def existingName = state.presets[targetPresetId.toString()].n
            if (logEnable) log.debug "Updating existing preset ${targetPresetId} (was: '${existingName}', now: '${presetName}')"
        }
    }
    
    // Build the state object for the preset
    def presetState = [:]
    def segmentConfig = [id: settings.ledSegment.toInteger()]
    
    // Add brightness if specified
    if (brightness != null) {
        if (brightness < 0 || brightness > 255) {
            log.error "Invalid brightness: ${brightness}. Must be between 0-255."
            return
        }
        presetState.bri = brightness.toInteger()
        segmentConfig.bri = brightness.toInteger()
    }
    
    // Add effect if specified
    if (effect != null) {
        def effectId = resolveEffectId(effect)
        if (effectId == null) {
            log.error "Effect '${effect}' not found. Available effects: ${getEffectsList()}"
            return
        }
        segmentConfig.fx = effectId
    }
    
    // Add palette if specified
    if (palette != null) {
        def paletteId = resolvePaletteId(palette)
        if (paletteId == null) {
            log.error "Palette '${palette}' not found. Available palettes: ${getPalettesList()}"
            return
        }
        segmentConfig.pal = paletteId
    }
    
    // Add speed if specified
    if (speed != null) {
        if (speed < 0 || speed > 255) {
            log.error "Invalid speed: ${speed}. Must be between 0-255."
            return
        }
        segmentConfig.sx = speed.toInteger()
    }
    
    // Add intensity if specified
    if (intensity != null) {
        if (intensity < 0 || intensity > 255) {
            log.error "Invalid intensity: ${intensity}. Must be between 0-255."
            return
        }
        segmentConfig.ix = intensity.toInteger()
    }
    
    // Add colors if specified
    def colors = []
    if (primaryColor != null) {
        def rgb = parseHexColor(primaryColor)
        if (rgb == null) {
            log.error "Invalid primary color format: ${primaryColor}. Use hex format like 'FF0000'."
            return
        }
        colors.add(rgb)
    }
    
    if (secondaryColor != null) {
        def rgb = parseHexColor(secondaryColor)
        if (rgb == null) {
            log.error "Invalid secondary color format: ${secondaryColor}. Use hex format like '00FF00'."
            return
        }
        // Ensure we have primary color slot filled
        if (colors.size() == 0) {
            colors.add([255, 255, 255]) // Default white for primary if not specified
        }
        colors.add(rgb)
    }
    
    if (colors.size() > 0) {
        segmentConfig.col = colors
    }
    
    // Add segment configuration to preset state
    presetState.seg = [segmentConfig]
    
    // Add preset save command and name (using correct WLED parameter "n")
    presetState.psave = targetPresetId
    presetState.n = presetName
    presetState.ib = true  // Include brightness
    presetState.sb = true  // Save segment bounds
    
    if (logEnable) log.debug "Saving custom preset ID: ${targetPresetId} with name: '${presetName}' and config: ${presetState}"
    sendWledCommand(presetState)
    
    // Refresh presets after saving
    runIn(2, getPresets)
}

def saveCurrentAsPreset(String presetName, Number presetId = null) {
    if (!presetName || presetName.trim().isEmpty()) {
        log.error "Preset name cannot be empty."
        return
    }
    
    // Determine preset ID
    def targetPresetId = presetId
    if (targetPresetId == null) {
        targetPresetId = findNextAvailablePresetId()
        if (targetPresetId == null) {
            log.error "No available preset slots found (1-250)."
            return
        }
        if (logEnable) log.debug "Auto-assigned preset ID: ${targetPresetId}"
    } else {
        if (targetPresetId < 1 || targetPresetId > 250) {
            log.error "Invalid preset ID: ${targetPresetId}. Must be between 1-250."
            return
        }
        // Check if we're updating an existing preset
        if (state.presets && state.presets[targetPresetId.toString()]) {
            def existingName = state.presets[targetPresetId.toString()].n
            if (logEnable) log.debug "Updating existing preset ${targetPresetId} (was: '${existingName}', now: '${presetName}')"
        }
    }
    
    if (logEnable) log.debug "Saving current state to preset ID: ${targetPresetId} with name: '${presetName}'"
    def payload = [psave: targetPresetId, n: presetName, ib: true, sb: true]
    sendWledCommand(payload)
    
    // Refresh presets after saving
    runIn(2, getPresets)
}

def deletePreset(Number presetId) {
    if (presetId < 1 || presetId > 250) {
        log.error "Invalid preset ID: ${presetId}. Must be between 1-250."
        return
    }
    
    if (logEnable) log.debug "Deleting preset ID: ${presetId}"
    def payload = [pdel: presetId]
    sendWledCommand(payload)
    
    // Refresh presets after deletion
    runIn(2, getPresets)
}

def listPresets() {
    if (!state.presets) {
        log.warn "Presets not available. Refreshing device..."
        getPresets()
        return
    }
    
    def presetsList = getPresetsList()
    log.info "Available Presets (${state.presets.size()}): ${presetsList}"
    updateAttr("availablePresets", presetsList)
}

def getPresets() {
    if (logEnable) log.debug "Requesting presets information..."
    sendEthernetGet(WLED_ENDPOINTS.PRESETS)
}

def setPlaylist(String playlist) {
    def playlistId = resolvePlaylistId(playlist)
    if (playlistId == null) {
        log.error "Playlist '${playlist}' not found. Available playlists: ${getPlaylistsList()}"
        return
    }
    
    if (playlistId < 1 || playlistId > 250) {
        log.error "Invalid playlist ID: ${playlistId}. Must be between 1-250."
        return
    }
    
    if (logEnable) log.debug "Starting playlist '${playlist}' (ID: ${playlistId})"
    sendWledCommand([playlist: [ps: playlistId, on: true]])
}

def stopPlaylist() {
    if (logEnable) log.debug "Stopping current playlist"
    sendWledCommand([playlist: [on: false]])
}

def nextPresetInPlaylist() {
    // Check if a playlist is currently running
    def currentPlaylistState = device.currentValue("playlistState")
    if (currentPlaylistState != "running") {
        log.warn "No playlist is currently running. Cannot advance to next preset."
        return
    }
    
    if (logEnable) log.debug "Advancing to next preset in playlist"
    sendWledCommand([np: true])
}

def listPlaylists() {
    if (!state.playlists) {
        log.warn "Playlists not available. Refreshing device..."
        getPresets() // Playlists are included in presets
        return
    }
    
    def playlistsList = getPlaylistsList()
    log.info "Available Playlists (${state.playlists.size()}): ${playlistsList}"
    updateAttr("availablePlaylists", playlistsList)
}

//--- NIGHTLIGHT COMMANDS ---//
def setNightlight(Number duration, String mode, Number targetBrightness) {
    if (logEnable) log.debug "Activating nightlight with duration: ${duration} min, mode: ${mode}, brightness: ${targetBrightness}"
    def modeId = ["Instant":0, "Fade":1, "Color Fade":2, "Sunrise":3][mode]
    def payload = [on: true, nl: [on: true, dur: duration, mode: modeId, tbri: targetBrightness]]
    sendWledCommand(payload)
}

def nightlightOff() {
    if (logEnable) log.debug "Deactivating nightlight"
    def payload = [nl: [on: false]]
    sendWledCommand(payload)
}

//--- ALARM CAPABILITY ---//
def siren() { 
    // Use Chase Flash effect with Fire palette for intense red siren alarm
    setEffect("Chase Flash", 255, 255, "Fire") 
}

def strobe() { 
    // Use Strobe effect with white/default palette for visual alarm
    setEffect("Strobe", 255, 255, "Default") 
}

def both() { 
    // Use Lightning effect with Red & Blue palette for emergency alarm
    // Strobe Mega provides intense, attention-grabbing multicolor strobe effect suitable for "both" alarm
    setEffect("Strobe Mega", 255, 255, "Red & Blue") 
}

//--- BACKWARD COMPATIBILITY METHODS (LEGACY) ---//
def setEffectByName(String effectName, Number speed = null, Number intensity = null, String paletteName = null) {
    if (logEnable && settings.showDeprecatedCommands) log.info "Using legacy setEffectByName - consider upgrading to setEffect"
    setEffect(effectName, speed, intensity, paletteName)
}

def setPaletteByName(String paletteName) {
    if (logEnable && settings.showDeprecatedCommands) log.info "Using legacy setPaletteByName - consider upgrading to setPalette"
    setPalette(paletteName)
}

def setPresetByName(String presetName) {
    if (logEnable && settings.showDeprecatedCommands) log.info "Using legacy setPresetByName - consider upgrading to setPreset"
    setPreset(presetName)
}

def setPlaylistByName(String playlistName) {
    if (logEnable && settings.showDeprecatedCommands) log.info "Using legacy setPlaylistByName - consider upgrading to setPlaylist"
    setPlaylist(playlistName)
}

//--- SYNCHRONIZATION ---//
private synchronizeState(wledState) {
    try {
        if (logEnable) log.debug "Synchronizing state..."
        if (!wledState) { 
            log.warn "Synchronize called with null state."
            return 
        }
        
        def segmentId = settings.ledSegment?.toInteger() ?: 0
        def seg = wledState.seg?.find { it.id == segmentId }
        if (!seg) { 
            log.warn "Segment ID ${segmentId} not found in state. Cannot synchronize."
            return 
        }
        
        // Update basic switch and level
        updateSwitchAndLevel(seg)
        
        // Update color information
        updateColorInformation(seg)
        
        // Update effect, playlist, and preset information
        updateEffectInformation(seg, wledState)
        updatePlaylistInformation(wledState)
        updatePresetInformation(wledState)

        // Update nightlight information
        updateNightlightInformation(wledState)
    } catch (Exception e) {
        log.error "Error synchronizing state: ${e.message}"
    }
}

private updateSwitchAndLevel(seg) {
    if (logEnable) log.debug "updateSwitchAndLevel called with seg: ${seg}"
    
    def isOn = seg?.on == true
    def switchValue = isOn ? "on" : "off"
    if (logEnable) log.debug "Setting switch to: ${switchValue} (seg.on = ${seg?.on})"
    updateAttr("switch", switchValue)
    
    def brightness = seg?.bri ?: 255
    if (logEnable) log.debug "Brightness value: ${brightness}"
    
    def newLevel = 0
    if (isOn && brightness != null) {
        newLevel = Math.round(brightness / 2.55)
    }
    if (logEnable) log.debug "Setting level to: ${newLevel} (brightness: ${brightness}, isOn: ${isOn})"
    updateAttr("level", newLevel, "%")
    
    if (logEnable) log.debug "updateSwitchAndLevel completed successfully"
}

private updateColorInformation(seg) {
    try {
        def isEffect = (seg?.fx ?: 0) > 0
        updateAttr("colorMode", isEffect ? "CT" : "RGB")
        
        if (!isEffect && seg?.col && seg.col[0]) {
            def rgb = seg.col[0]
            def estimatedKelvin = estimateColorTemperature(rgb[0], rgb[1], rgb[2])
            if (estimatedKelvin) {
                updateAttr("colorTemperature", estimatedKelvin)
                setGenericNameFromTemp(estimatedKelvin)
            } else {
                def hsv = rgbToHsv(rgb[0], rgb[1], rgb[2])
                updateAttr("hue", hsv?.H ?: 0)
                updateAttr("saturation", hsv?.S ?: 0)
                setGenericNameFromHue(hsv?.H ?: 0)
            }
        } else {
            updateAttr("colorName", "Effect")
        }
    } catch (Exception e) {
        log.error "Error updating color information: ${e.message}"
        updateAttr("colorName", "Unknown")
    }
}

private updateEffectInformation(seg, wledState) {
    try {
        // Update effect information with ID numbers
        updateAttr("effectId", seg?.fx ?: 0)
        updateAttr("paletteId", seg?.pal ?: 0)
        
        if (state.effects) { 
            def effectId = seg?.fx ?: 0
            updateAttr("effectName", state.effects[effectId] ?: "Unknown") 
        }
        if (state.palettes) { 
            def paletteId = seg?.pal ?: 0
            updateAttr("paletteName", state.palettes[paletteId] ?: "Unknown") 
        }
        
        updateAttr("presetValue", wledState?.ps ?: 0)
        
        // Update playlist information
        updatePlaylistInformation(wledState)
        
        def isReverse = seg?.rev ?: false
        updateAttr("effectDirection", isReverse ? "reverse" : "forward")
        updateAttr("reverse", isReverse ? "on" : "off")
        
        // Update available lists when effects/palettes are loaded
        if (state.effects && !device.currentValue("availableEffects")) {
            updateAttr("availableEffects", getEffectsList())
        }
        if (state.palettes && !device.currentValue("availablePalettes")) {
            updateAttr("availablePalettes", getPalettesList())
        }
        if (state.playlists && !device.currentValue("availablePlaylists")) {
            updateAttr("availablePlaylists", getPlaylistsList())
        }
    } catch (Exception e) {
        log.error "Error updating effect information: ${e.message}"
    }
}

private updateNightlightInformation(wledState) {
    try {
        def nl = wledState?.nl
        if (nl) {
            updateAttr("nightlightActive", nl.on ? "on" : "off")
            updateAttr("nightlightDuration", nl.dur ?: 0)
            def modeName = ["Instant", "Fade", "Color Fade", "Sunrise"][nl.mode ?: 0]
            updateAttr("nightlightMode", modeName)
            updateAttr("nightlightTargetBrightness", nl.tbri ?: 0)
            
            // Track remaining time if available (WLED v0.10.2+)
            if (nl.rem != null) {
                updateAttr("nightlightRemaining", nl.rem)
            }
        } else {
            updateAttr("nightlightActive", "off")
            updateAttr("nightlightDuration", 0)
            updateAttr("nightlightMode", "Instant")
            updateAttr("nightlightTargetBrightness", 0)
            updateAttr("nightlightRemaining", -1)
        }
    } catch (Exception e) {
        log.error "Error updating nightlight information: ${e.message}"
    }
}

private handleDeviceInfo(deviceInfo) {
    if (logEnable) log.debug "Processing device info: ${deviceInfo}"
    
    if (deviceInfo.ver) {
        state.wledVersion = deviceInfo.ver
        updateAttr("firmwareVersion", deviceInfo.ver)
    }
    
    if (deviceInfo.vid) {
        state.wledBuild = deviceInfo.vid
    }
    
    if (deviceInfo.name) {
        state.deviceName = deviceInfo.name
    }
    
    log.info "WLED Device Info - Version: ${deviceInfo.ver}, Build: ${deviceInfo.vid}, Name: ${deviceInfo.name}"
}

//--- HTTP COMMUNICATIONS ---//
private sendWledCommand(Map payload, Number transitionRate = null) {
    payload.v = true
    payload.tt = (transitionRate != null) ? (transitionRate * 100).toInteger() : settings.transitionTime.toInteger()
    sendEthernetPost(WLED_ENDPOINTS.STATE, JsonOutput.toJson(payload))
}



private sendEthernetGet(String path, Map data = null) {
    if (!settings.uri) { 
        log.error "WLED URI is not set."
        updateAttr("connectionState", "error")
        return 
    }
    
    def fullData = data ? new HashMap(data) : new HashMap()
    fullData.path = path

    try { 
        asynchttpGet("parse", [
            uri: settings.uri, 
            path: path, 
            timeout: NETWORK_CONFIG.DEFAULT_TIMEOUT
        ], fullData) 
    }
    catch (e) { 
        log.error "asynchttpGet error: ${e.message}"
        handleNetworkError("GET request failed: ${e.message}", fullData)
    }
}

private sendEthernetPost(String path, String body, Map data = null) {
    if (!settings.uri) { 
        log.error "WLED URI is not set."
        updateAttr("connectionState", "error")
        return 
    }
    
    def fullData = data ? new HashMap(data) : new HashMap()
    fullData.path = path

    try { 
        asynchttpPost("parse", [
            uri: settings.uri, 
            path: path, 
            timeout: NETWORK_CONFIG.DEFAULT_TIMEOUT, 
            contentType: 'application/json', 
            body: body
        ], fullData) 
    }
    catch (e) { 
        log.error "asynchttpPost error: ${e.message}"
        handleNetworkError("POST request failed: ${e.message}", fullData)
    }
}

private handleNetworkError(String errorMessage, Map data = null) {
    log.warn "Network error: ${errorMessage}"
    updateAttr("connectionState", "error")
    
    if (!settings.enableRetry || !data || !data.retryCount) return
    
    def retryCount = data.retryCount + 1
    if (retryCount <= NETWORK_CONFIG.MAX_RETRIES) {
        log.info "Retrying command in ${NETWORK_CONFIG.RETRY_DELAY} seconds (attempt ${retryCount}/${NETWORK_CONFIG.MAX_RETRIES})"
        runIn(NETWORK_CONFIG.RETRY_DELAY, "retryCommand", [data: data])
    } else {
        log.error "Max retry attempts (${NETWORK_CONFIG.MAX_RETRIES}) exceeded. Command failed permanently."
    }
}

def retryCommand(Map data) {
    if (data.payload) {
        log.info "Retrying WLED command (attempt ${data.retryCount + 1})"
        sendWledCommand(data.payload, data.transitionRate)
    }
}







def checkConnection() {
    if (!settings.connectionMonitoring) return
    
    def CONNECTION_TIMEOUT_MS = NETWORK_CONFIG.CONNECTION_CHECK_INTERVAL * 2000 // 60 seconds
    def timeSinceLastContact = now() - (state.lastSuccessfulContact ?: 0)
    if (timeSinceLastContact > CONNECTION_TIMEOUT_MS) {
        log.warn "No successful contact with WLED device for ${timeSinceLastContact/1000} seconds"
        updateAttr("connectionState", "disconnected")
        testConnection()
    }
    
    // Schedule next check
    runIn(NETWORK_CONFIG.CONNECTION_CHECK_INTERVAL, checkConnection)
}

//--- HELPER METHODS ---//
private updateAttr(String attrName, newValue, String unit = "") {
    try {
        def currentValue = device.currentValue(attrName)
        if ("${currentValue}" != "${newValue}") {
            sendEvent(name: attrName, value: newValue, unit: unit)
        }
    } catch (Exception e) {
        log.error "Error updating attribute ${attrName}: ${e.message}"
        // Try to send the event anyway
        try {
            sendEvent(name: attrName, value: newValue, unit: unit)
        } catch (Exception e2) {
            log.error "Failed to send event for ${attrName}: ${e2.message}"
        }
    }
}

private validateSegment(int segmentId) {
    if (!state.segments) {
        log.warn "Segment information not available. Proceeding with command."
        return true // Allow command to proceed, WLED will handle invalid segments
    }
    
    def segment = state.segments.find { it.id == segmentId }
    if (!segment) {
        log.warn "Segment ${segmentId} not found on device. Available segments: ${state.segments.collect{it.id}}"
        return false
    }
    return true
}

private setSchedule() {
    def interval = settings.refreshInterval.toInteger()
    if (interval == 0) { 
        log.info "Polling disabled."
        return 
    }
    
    String cron
    switch(interval) {
        case 30:   cron = "0/30 * * * * ?"; break
        case 60:   cron = "0 * * * * ?"; break
        case 300:  cron = "0 0/5 * * * ?"; break
        case 600:  cron = "0 0/10 * * * ?"; break
        case 1800: cron = "0 0/30 * * * ?"; break
        case 3600: cron = "0 0 * * * ?"; break
        default: 
            log.warn "Unsupported refresh interval: ${interval}. Disabling polling."
            return
    }
    
    try {
        schedule(cron, refresh)
        log.info "Device polling scheduled with cron: '${cron}' (every ${interval} seconds)"
    } catch (e) {
        log.error "Failed to create schedule with cron '${cron}'. Polling will be disabled. Error: ${e.message}"
    }
}

//--- COLOR CONVERSION AND NAMING ---//
private Integer estimateColorTemperature(int r, int g, int b) {
    if (r == null || g == null || b == null) return null
    if (Math.abs(r - g) > 30 && Math.abs(r - b) > 30 && Math.abs(g - b) > 30) return null
    
    float r_f = r / 255.0f
    float g_f = g / 255.0f 
    float b_f = b / 255.0f
    float max = Math.max(r_f, Math.max(g_f, b_f))
    float min = Math.min(r_f, Math.min(g_f, b_f))
    
    if (max - min < 0.2) return 6600
    
    float temperature
    if (r > g && g > b) { 
        temperature = 40000.0 / Math.pow(r_f * 1.1 + 0.1, 1.2) 
    } else { 
        temperature = 6600 + (b_f - g_f) * 5000 
    }
    
    int kelvin = clamp(temperature.toInteger(), 2000, 6500)
    if (kelvin > 4000 && kelvin < 5000 && (r < 200 || g < 200 || b < 200)) return null
    return kelvin
}

private List<Integer> hsvToRgb(float hue, float saturation, float value) {
    hue /= 100
    saturation /= 100
    value /= 100
    
    int h = (int)(hue * 6)
    float f = hue * 6 - h
    float p = value * (1 - saturation)
    float q = value * (1 - f * saturation)
    float t = value * (1 - (1 - f) * saturation)
    
    switch (h) {
        case 0: return [(int)(value * 255), (int)(t * 255), (int)(p * 255)]
        case 1: return [(int)(q * 255), (int)(value * 255), (int)(p * 255)]
        case 2: return [(int)(p * 255), (int)(value * 255), (int)(t * 255)]
        case 3: return [(int)(p * 255), (int)(q * 255), (int)(value * 255)]
        case 4: return [(int)(t * 255), (int)(p * 255), (int)(value * 255)]
        case 5: return [(int)(value * 255), (int)(p * 255), (int)(q * 255)]
        default: return [0, 0, 0]
    }
}

private Map rgbToHsv(int r, int g, int b) {
    if (r == null || g == null || b == null) return [H:0, S:0]
    
    float R = r / 255f
    float G = g / 255f
    float B = b / 255f
    float cmax = [R, G, B].max()
    float cmin = [R, G, B].min()
    float delta = cmax - cmin
    float hue = 0
    
    if (delta != 0) {
        if (cmax == R) {
            hue = 60 * (((G - B) / delta) % 6)
        } else if (cmax == G) {
            hue = 60 * (((B - R) / delta) + 2)
        } else if (cmax == B) {
            hue = 60 * (((R - G) / delta) + 4)
        }
    }
    
    if (hue < 0) hue += 360
    float saturation = (cmax == 0) ? 0 : (delta / cmax)
    
    return [H: (hue/3.6).round(), S: (saturation * 100).round()]
}

private List<Integer> colorTempToRgb(kelvin) {
    def temp = kelvin.toInteger() / 100
    def red, green, blue
    
    if (temp <= 66) {
        red = 255
        green = 99.4708025861 * Math.log(temp) - 161.1195681661
        if (temp <= 19) { 
            blue = 0 
        } else { 
            blue = 138.5177312231 * Math.log(temp - 10) - 305.0447927307 
        }
    } else {
        red = 329.698727446 * Math.pow(temp - 60, -0.1332047592)
        green = 288.1221695283 * Math.pow(temp - 60, -0.0755148492)
        blue = 255
    }
    
    return [
        clamp(red, 0, 255), 
        clamp(green, 0, 255), 
        clamp(blue, 0, 255)
    ]
}

private int clamp(num, min, max) { 
    return Math.max(min, Math.min(num, max)).toInteger() 
}

private List<Integer> parseHexColor(String hexColor) {
    try {
        // Remove # if present and ensure uppercase
        def cleanHex = hexColor.replaceAll("#", "").toUpperCase()
        
        // Support both 3-digit and 6-digit hex
        if (cleanHex.length() == 3) {
            // Convert RGB to RRGGBB
            cleanHex = cleanHex[0] + cleanHex[0] + cleanHex[1] + cleanHex[1] + cleanHex[2] + cleanHex[2]
        }
        
        if (cleanHex.length() != 6) {
            return null
        }
        
        // Parse RGB components
        def r = Integer.parseInt(cleanHex.substring(0, 2), 16)
        def g = Integer.parseInt(cleanHex.substring(2, 4), 16)
        def b = Integer.parseInt(cleanHex.substring(4, 6), 16)
        
        return [r, g, b]
    } catch (Exception e) {
        if (logEnable) log.debug "Error parsing hex color '${hexColor}': ${e.message}"
        return null
    }
}

private setGenericNameFromHue(hue) {
    def colorName
    hue = hue.toInteger()
    def hue360 = hue * 3.6
    
    switch (hue360.toInteger()) {
        case 0..15: colorName = "Red"; break
        case 16..45: colorName = "Orange"; break
        case 46..75: colorName = "Yellow"; break
        case 76..105: colorName = "Chartreuse"; break
        case 106..135: colorName = "Green"; break
        case 136..165: colorName = "Spring"; break
        case 166..195: colorName = "Cyan"; break
        case 196..225: colorName = "Azure"; break
        case 226..255: colorName = "Blue"; break
        case 256..285: colorName = "Violet"; break
        case 286..315: colorName = "Magenta"; break
        case 316..345: colorName = "Rose"; break
        default: colorName = "Red"; break
    }
    updateAttr("colorName", colorName)
}

private setGenericNameFromTemp(temp) {
    def genericName
    def value = temp.toInteger()
    
    if (value <= 2000) {
        genericName = "Sodium"
    } else if (value < 2800) {
        genericName = "Incandescent"
    } else if (value < 3500) {
        genericName = "Warm White"
    } else if (value <= 5000) {
        genericName = "Daylight"
    } else if (value <= 6500) {
        genericName = "Skylight"
    } else {
        genericName = "Polar"
    }
    
    updateAttr("colorName", genericName)
}

//--- UNIFIED RESOLVER METHODS ---//
private Integer resolveEffectId(String effect) {
    if (!effect) return null
    
    // Try to parse as number first
    if (effect.isNumber()) {
        def id = effect.toInteger()
        if (id >= 0 && id <= 255) {
            return id
        } else {
            log.warn "Effect ID ${id} is out of valid range (0-255)"
            return null
        }
    }
    
    // If not a number, treat as name
    return findEffectIdByName(effect)
}

private Integer resolvePaletteId(String palette) {
    if (!palette) return null
    
    // Try to parse as number first
    if (palette.isNumber()) {
        def id = palette.toInteger()
        if (id >= 0 && id <= 255) {
            return id
        } else {
            log.warn "Palette ID ${id} is out of valid range (0-255)"
            return null
        }
    }
    
    // If not a number, treat as name
    return findPaletteIdByName(palette)
}

private Integer resolvePresetId(String preset) {
    if (!preset) return null
    
    // Try to parse as number first
    if (preset.isNumber()) {
        def id = preset.toInteger()
        if (id >= 1 && id <= 250) {
            return id
        } else {
            log.warn "Preset ID ${id} is out of valid range (1-250)"
            return null
        }
    }
    
    // If not a number, treat as name
    return findPresetIdByName(preset)
}

private Integer resolvePlaylistId(String playlist) {
    if (!playlist) return null
    
    // Try to parse as number first
    if (playlist.isNumber()) {
        def id = playlist.toInteger()
        if (id >= 1 && id <= 250) {
            return id
        } else {
            log.warn "Playlist ID ${id} is out of valid range (1-250)"
            return null
        }
    }
    
    // If not a number, treat as name
    return findPlaylistIdByName(playlist)
}

//--- EFFECT AND PALETTE HELPER METHODS ---//
private Integer findEffectIdByName(String effectName) {
    if (!state.effects) return null
    
    // Try exact match first
    def exactMatch = state.effects.findIndexOf { it.toLowerCase() == effectName.toLowerCase() }
    if (exactMatch >= 0) return exactMatch
    
    // Try partial match
    def partialMatch = state.effects.findIndexOf { it.toLowerCase().contains(effectName.toLowerCase()) }
    if (partialMatch >= 0) {
        if (logEnable) log.debug "Found partial match for '${effectName}': '${state.effects[partialMatch]}' (ID: ${partialMatch})"
        return partialMatch
    }
    
    return null
}

private Integer findPaletteIdByName(String paletteName) {
    if (!state.palettes) return null
    
    // Try exact match first
    def exactMatch = state.palettes.findIndexOf { it.toLowerCase() == paletteName.toLowerCase() }
    if (exactMatch >= 0) return exactMatch
    
    // Try partial match
    def partialMatch = state.palettes.findIndexOf { it.toLowerCase().contains(paletteName.toLowerCase()) }
    if (partialMatch >= 0) {
        if (logEnable) log.debug "Found partial match for '${paletteName}': '${state.palettes[partialMatch]}' (ID: ${partialMatch})"
        return partialMatch
    }
    
    return null
}

private String getEffectsList() {
    try {
        if (!state.effects) return "Effects not loaded"
        
        def effectsWithIds = []
        state.effects.eachWithIndex { effect, index ->
            effectsWithIds.add("${index}: ${effect ?: 'Unknown'}")
        }
        return effectsWithIds.join(", ")
    } catch (Exception e) {
        log.error "Error getting effects list: ${e.message}"
        return "Error loading effects"
    }
}

private String getPalettesList() {
    try {
        if (!state.palettes) return "Palettes not loaded"
        
        def palettesWithIds = []
        state.palettes.eachWithIndex { palette, index ->
            palettesWithIds.add("${index}: ${palette ?: 'Unknown'}")
        }
        return palettesWithIds.join(", ")
    } catch (Exception e) {
        log.error "Error getting palettes list: ${e.message}"
        return "Error loading palettes"
    }
}

//--- PRESET HELPER METHODS ---//
private Integer findPresetIdByName(String presetName) {
    if (!state.presets) return null
    
    def preset = state.presets.find { id, p -> id != "0" && p.n?.toLowerCase() == presetName.toLowerCase() }
    if (preset) return preset.key.toInteger()
    
    def partialMatch = state.presets.find { id, p -> id != "0" && p.n?.toLowerCase()?.contains(presetName.toLowerCase()) }
    if (partialMatch) {
        if (logEnable) log.debug "Found partial match for '${presetName}': '${partialMatch.value.n}' (ID: ${partialMatch.key})"
        return partialMatch.key.toInteger()
    }
    
    return null
}

private String getPresetsList() {
    try {
        if (!state.presets) return "Presets not loaded"
        
        def presetsWithIds = []
        state.presets.each { id, preset ->
            if (id != "0") {
                def name = preset?.n ?: "Unnamed Preset"
                presetsWithIds.add("${id}: ${name}")
            }
        }
        return presetsWithIds.join(", ")
    } catch (Exception e) {
        log.error "Error getting presets list: ${e.message}"
        return "Error loading presets"
    }
}

private Integer findNextAvailablePresetId() {
    try {
        // If no presets loaded yet, start with ID 1
        if (!state.presets) {
            return 1
        }
        
        // Find the first available slot from 1 to 250
        for (int i = 1; i <= 250; i++) {
            if (!state.presets.containsKey(i.toString())) {
                return i
            }
        }
        
        // No available slots
        return null
    } catch (Exception e) {
        log.error "Error finding next available preset ID: ${e.message}"
        return 1 // Fallback to ID 1
    }
}

private updatePresetInformation(wledState) {
    try {
        def currentPresetId = wledState?.ps
        if (currentPresetId && currentPresetId > 0) {
            updateAttr("presetValue", currentPresetId)
            
            if (state.presets && state.presets[currentPresetId.toString()]) {
                def presetName = state.presets[currentPresetId.toString()].n ?: "Unnamed Preset"
                updateAttr("presetName", presetName)
            } else {
                updateAttr("presetName", "Unknown Preset")
            }
        } else {
            updateAttr("presetValue", 0)
            updateAttr("presetName", "None")
        }
    } catch (Exception e) {
        if (logEnable) log.debug "Error updating preset information: ${e.message}"
        updateAttr("presetValue", 0)
        updateAttr("presetName", "None")
    }
}

private handlePresetsInfo(presetsData) {
    try {
        if (logEnable) log.debug "Processing presets info: ${presetsData}"
        
        if (presetsData) {
            def newPresets = [:]
            def newPlaylists = [:]
            presetsData.each { id, preset ->
                if (id == "0") return

                if (preset.containsKey("playlist")) {
                    newPlaylists[id] = preset
                } else {
                    newPresets[id] = preset
                }
            }

            state.presets = newPresets
            state.playlists = newPlaylists

            log.info "Loaded ${state.presets.size()} presets and ${state.playlists.size()} playlists"
            updateAttr("availablePresets", getPresetsList())
            updateAttr("availablePlaylists", getPlaylistsList())
        } else {
            state.presets = [:]
            state.playlists = [:]
            updateAttr("availablePresets", "No presets available")
            updateAttr("availablePlaylists", "No playlists available")
            log.info "No presets or playlists found on device"
        }
    } catch (Exception e) {
        log.warn "Error processing presets info: ${e.message}"
        state.presets = [:]
        state.playlists = [:]
        updateAttr("availablePresets", "Error loading presets")
        updateAttr("availablePlaylists", "Error loading playlists")
    }
}

//--- PLAYLIST HELPER METHODS ---//
private Integer findPlaylistIdByName(String playlistName) {
    if (!state.playlists) return null
    
    def playlist = state.playlists.find { id, p -> p.n?.toLowerCase() == playlistName.toLowerCase() }
    if (playlist) return playlist.key.toInteger()
    
    def partialMatch = state.playlists.find { id, p -> p.n?.toLowerCase()?.contains(playlistName.toLowerCase()) }
    if (partialMatch) {
        if (logEnable) log.debug "Found partial match for '${playlistName}': '${partialMatch.value.n}' (ID: ${partialMatch.key})"
        return partialMatch.key.toInteger()
    }
    
    return null
}

private String getPlaylistsList() {
    try {
        if (!state.playlists) return "Playlists not loaded"
        
        def playlistsWithIds = []
        state.playlists.each { id, playlist ->
            def name = playlist?.n ?: "Unnamed Playlist"
            playlistsWithIds.add("${id}: ${name}")
        }
        return playlistsWithIds.join(", ")
    } catch (Exception e) {
        log.error "Error getting playlists list: ${e.message}"
        return "Error loading playlists"
    }
}

private updatePlaylistInformation(wledState) {
    try {
        // Check if a playlist is currently running
        // Note: WLED API uses 'pl' for current playlist ID in state response
        def currentPlaylistId = wledState?.pl
        if (currentPlaylistId && currentPlaylistId > 0) {
            updateAttr("playlistId", currentPlaylistId)
            updateAttr("playlistState", "running")
            
            if (state.playlists && state.playlists[currentPlaylistId.toString()]) {
                def playlistName = state.playlists[currentPlaylistId.toString()].n ?: "Unnamed Playlist"
                updateAttr("playlistName", playlistName)
            } else {
                updateAttr("playlistName", "Unknown Playlist")
            }
        } else {
            updateAttr("playlistId", 0)
            updateAttr("playlistState", "none")
            updateAttr("playlistName", "None")
        }
    } catch (Exception e) {
        if (logEnable) log.debug "Error updating playlist information: ${e.message}"
        updateAttr("playlistId", 0)
        updateAttr("playlistState", "none")
        updateAttr("playlistName", "None")
    }
}

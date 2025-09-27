/**
 *
 * WLED Driver for Hubitat
 *
 * Original Author: bryan@joyful.house (GitHub repo: https://github.com/joyfulhouse/WLED)
 * Optimized and Updated by: Oliver Beisser
/**
 *
 *
 * Author: Original by bryan@joyful.house
 * Optimization & Refactoring: obeisser
 * Date: 2025-09-28
 *
 * Version: 1.0 (Initial Release)
 *
 * -------------------------------------------------------------------------------------
 * Key Changes over Original Driver:
 * -------------------------------------------------------------------------------------
 *
 * - Fully Asynchronous: All network calls are non-blocking to keep the Hubitat hub responsive.
 * - Robust Initialization: A state machine ensures the driver only becomes active after successfully loading WLED effects & palettes, eliminating startup race conditions.
 * - Resilient Error Handling: The driver gracefully handles network errors, invalid API responses, and attempts to self-heal from segment desynchronization.
 * - Stable Scheduler: Polling scheduler uses pre-validated cron expressions to prevent crashes.
 * - Optimized API Calls: State-changing commands request the new state in the same response (using "v":true), eliminating redundant refresh calls.
 * - Efficient State Updates: Hubitat events are only sent when an attribute's value has actually changed, reducing unnecessary hub processing load.
 * - Replaced manual string-based JSON creation with safe, map-based generation and hardened all methods against null exceptions.
 * - Refactored with helper methods for logging and state updates.
 *
 */
import groovy.json.JsonOutput

metadata {
    definition (name: "WLED Optimized", namespace: "obeisser", author: "obeisser") {
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
        
        command "setEffect", [
            [name:"effectId", type: "NUMBER", description: "Effect ID (0-255)"],
            [name:"speed", type: "NUMBER", description: "Relative Effect Speed (0-255)"],
            [name:"intensity", type: "NUMBER", description: "Effect Intensity (0-255)"],
            [name:"paletteId", type: "NUMBER", description: "Color Palette ID (0-255)"]
        ]
        command "setPreset", [
            [name:"presetId", type: "NUMBER", description: "Preset ID"]
        ]
        command "forceRefresh"
    }
    
    preferences {
        input "uri", "text", title: "WLED URI", description: "Example: http://[wled_ip_address]", required: true, displayDuringSetup: true
        input name: "ledSegment", type: "number", title: "LED Segment ID", defaultValue: 0, required: true
        input name: "transitionTime", type: "enum", title: "Default Transition Time", options: [[0:"0ms"], [400:"400ms"], [700:"700ms"], [1000:"1s"], [2000:"2s"], [5000:"5s"]], defaultValue: 700
        input name: "refreshInterval", type: "enum", title: "Polling Refresh Interval", options: [[0:"Disabled"], [30:"30 Seconds"], [60:"1 Minute"], [300:"5 Minutes"], [600:"10 Minutes"], [1800:"30 Minutes"], [3600:"1 Hour"]], defaultValue: 300
        input name: "powerOffParent", type: "bool", title: "Power Off Main Controller with Segment", description: "If enabled, the main WLED power will be turned off when this segment is.", defaultValue: false
        input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
    }
}

//--- LIFECYCLE METHODS ---//
def installed() {
    log.info "Installing WLED Optimized Driver..."
    runIn(1, updated)
}

def updated() {
    log.info "Settings updated. Initializing..."
    state.initialized = false
    unschedule()
    if (settings.uri) {
        state.clear()
        runIn(1, forceRefresh)
    }
}

def initialize() {
    log.info "Initializing..."
    runIn(1, updated)
}

def parse(hubitat.scheduling.AsyncResponse response, Map data = null) {
    try {
        if (response.hasError()) {
            log.error "HTTP Error: ${response.getErrorMessage()}"
            return
        }
        
        def msg = response.getJson()
        debugLog("Response JSON: ${msg}")

        if (!msg) {
            log.warn "Received empty or invalid JSON response."
            return
        }

        if (msg.containsKey("state") && msg.containsKey("effects") && msg.containsKey("palettes")) {
            boolean wasInitialized = state.initialized
            state.effects = msg.effects
            state.palettes = msg.palettes
            synchronize(msg.state)
            
            if (!wasInitialized) {
                log.info "Full device info received. Driver initialization complete."
                setSchedule()
                state.initialized = true
            }
        }
        else if (state.initialized) {
            synchronize(msg.state ?: msg)
        }
        else {
            log.warn "Driver not fully initialized. Ignoring partial state update. Waiting for full refresh."
        }
    } catch (e) {
        log.error "Fatal error parsing response: ${e.message}"
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
    value = Math.max(0, Math.min(value, 100)) // Clamp value between 0-100
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
    debugLog("Setting color temperature to ${value}K")
    def rgb = colorTempToRgb(value)
    def payload = [on: true, seg: [[id: ledSegment.toInteger(), on: true, col: [rgb], fx: 0]]]
    sendWledCommand(payload)
    sendEvent(name: "colorTemperature", value: value)
    sendEvent(name: "colorMode", value: "CT")
    setGenericNameFromTemp(value)
}

def refresh() { sendEthernetGet("/json/state") }

//--- CUSTOM COMMANDS ---//
def forceRefresh() {
    log.info "Forcing full refresh of state, effects, and palettes..."
    sendEthernetGet("/json")
}

def setEffect(Number effectId, Number speed = null, Number intensity = null, Number paletteId = null) {
    def seg = [id: ledSegment.toInteger(), fx: effectId.toInteger()]
    if (speed != null) seg.sx = speed.toInteger()
    if (intensity != null) seg.ix = intensity.toInteger()
    if (paletteId != null) seg.pal = paletteId.toInteger()
    sendWledCommand([on: true, seg: [seg]])
}

def setPreset(Number presetId) { sendWledCommand([ps: presetId.toInteger()]) }

//--- ALARM CAPABILITY ---//
def siren() { setEffect(38, 255, 255, 0) }
def strobe() { setEffect(23, 255, 255, 0) }
def both() { strobe() }

//--- SYNCHRONIZATION ---//
private synchronize(wledState) {
    debugLog("Synchronizing state...")
    if (!wledState) { log.warn "Synchronize called with null state."; return }
    
    def seg = wledState.seg?.find { it.id == settings.ledSegment.toInteger() }
    if (!seg) {
        log.warn "Segment ID ${settings.ledSegment} not found in state. Attempting recovery..."
        runIn(2, forceRefresh)
        return
    }
    
    updateAttr("switch", seg.on ? "on" : "off")
    def newLevel = seg.on ? (seg.bri / 2.55).round() : 0
    updateAttr("level", newLevel, "%")

    def isEffect = seg.fx > 0

    if (!isEffect && seg.col && seg.col[0]) {
        def rgb = seg.col[0]
        def estimatedKelvin = estimateColorTemperature(rgb[0], rgb[1], rgb[2])
        if (estimatedKelvin) {
            updateAttr("colorTemperature", estimatedKelvin)
            updateAttr("colorMode", "CT")
            setGenericNameFromTemp(estimatedKelvin)
        } else {
            def hsv = rgbToHsv(rgb[0], rgb[1], rgb[2])
            updateAttr("hue", hsv.H)
            updateAttr("saturation", hsv.S)
            updateAttr("colorMode", "RGB")
            setGenericNameFromHue(hsv.H)
        }
    } else {
        updateAttr("colorMode", "CT")
        updateAttr("colorName", "Effect")
    }

    if (state.effects) { updateAttr("effectName", state.effects[seg.fx] ?: "Unknown") }
    if (state.palettes) { updateAttr("paletteName", state.palettes[seg.pal] ?: "Unknown") }
    updateAttr("presetValue", wledState.ps)
}

//--- HTTP COMMUNICATIONS ---//
private sendWledCommand(Map payload, Number transitionRate = null) {
    payload.v = true
    payload.tt = (transitionRate != null) ? (transitionRate * 100).toInteger() : settings.transitionTime.toInteger()
    sendEthernetPost("/json/state", new JsonOutput().toJson(payload))
}

private sendEthernetGet(path) {
    if (!settings.uri) { log.error "WLED URI is not set."; return }
    try { asynchttpGet("parse", [ uri: settings.uri, path: path, timeout: 5 ]) }
    catch (e) { log.error "asynchttpGet error: ${e.message}" }
}

private sendEthernetPost(path, body) {
    if (!settings.uri) { log.error "WLED URI is not set."; return }
    try { asynchttpPost("parse", [ uri: settings.uri, path: path, timeout: 5, contentType: 'application/json', body: body ]) }
    catch (e) { log.error "asynchttpPost error: ${e.message}" }
}

//--- HELPER METHODS ---//
private updateAttr(String attrName, newValue, String unit = "") {
    if ("${device.currentValue(attrName)}" != "${newValue}") {
        sendEvent(name: attrName, value: newValue, unit: unit)
    }
}

private debugLog(String msg) {
    if (logEnable) log.debug msg
}

private setSchedule() {
    def interval = settings.refreshInterval.toInteger()
    if (interval == 0) { log.info "Polling disabled."; return }
    String cron
    switch(interval) {
        case 30:   cron = "0/30 * * * * ?"; break; case 60:   cron = "0 * * * * ?"; break
        case 300:  cron = "0 0/5 * * * ?"; break; case 600:  cron = "0 0/10 * * * ?"; break
        case 1800: cron = "0 0/30 * * * ?"; break; case 3600: cron = "0 0 * * * ?"; break
        default: log.warn "Unsupported refresh interval. Disabling polling."; return
    }
    try {
        schedule(cron, refresh)
        log.info "Device polling scheduled with cron: '${cron}'"
    } catch (e) {
        log.error "Failed to create schedule with cron '${cron}'. Polling will be disabled. Error: ${e.message}"
    }
}

//--- COLOR CONVERSION AND NAMING ---//
private Integer estimateColorTemperature(int r, int g, int b) {
    if (Math.abs(r - g) > 30 && Math.abs(r - b) > 30 && Math.abs(g - b) > 30) return null
    float r_f = r / 255.0f; float g_f = g / 255.0f; float b_f = b / 255.0f
    float max = Math.max(r_f, Math.max(g_f, b_f)); float min = Math.min(r_f, Math.min(g_f, b_f))
    if (max - min < 0.2) return 6600
    float temperature
    if (r > g && g > b) { temperature = 40000.0 / Math.pow(r_f * 1.1 + 0.1, 1.2) } 
    else { temperature = 6600 + (b_f - g_f) * 5000 }
    int kelvin = clamp(temperature.toInteger(), 2000, 6500)
    if (kelvin > 4000 && kelvin < 5000 && (r < 200 || g < 200 || b < 200)) return null
    return kelvin
}

private List<Integer> hsvToRgb(float hue, float saturation, float value) {
    hue /= 100; saturation /= 100; value /= 100
    int h = (int)(hue * 6); float f = hue * 6 - h; float p = value * (1 - saturation); float q = value * (1 - f * saturation); float t = value * (1 - (1 - f) * saturation)
    switch (h) {
        case 0: return [(int)(value * 255), (int)(t * 255), (int)(p * 255)]; case 1: return [(int)(q * 255), (int)(value * 255), (int)(p * 255)]
        case 2: return [(int)(p * 255), (int)(value * 255), (int)(t * 255)]; case 3: return [(int)(p * 255), (int)(q * 255), (int)(value * 255)]
        case 4: return [(int)(t * 255), (int)(p * 255), (int)(value * 255)]; case 5: return [(int)(value * 255), (int)(p * 255), (int)(q * 255)]
        default: return [0, 0, 0]
    }
}

private Map rgbToHsv(int r, int g, int b) {
    float R = r / 255f; float G = g / 255f; float B = b / 255f
    float cmax = [R, G, B].max(); float cmin = [R, G, B].min(); float delta = cmax - cmin; float hue = 0
    if (delta != 0) {
        if (cmax == R) hue = 60 * (((G - B) / delta) % 6)
        else if (cmax == G) hue = 60 * (((B - R) / delta) + 2)
        else if (cmax == B) hue = 60 * (((R - G) / delta) + 4)
    }
    if (hue < 0) hue += 360
    float saturation = (cmax == 0) ? 0 : (delta / cmax)
    return [H: (hue/3.6).round(), S: (saturation * 100).round()]
}

private List<Integer> colorTempToRgb(kelvin) {
    def temp = kelvin.toInteger() / 100; def red, green, blue
    if (temp <= 66) {
        red = 255; green = 99.4708025861 * Math.log(temp) - 161.1195681661
        if (temp <= 19) { blue = 0 } else { blue = 138.5177312231 * Math.log(temp - 10) - 305.0447927307 }
    } else {
        red = 329.698727446 * Math.pow(temp - 60, -0.1332047592); green = 288.1221695283 * Math.pow(temp - 60, -0.0755148492); blue = 255
    }
    return [clamp(red, 0, 255), clamp(green, 0, 255), clamp(blue, 0, 255)]
}

private int clamp(num, min, max) { return Math.max(min, Math.min(num, max)).toInteger() }

private setGenericNameFromHue(hue) {
    def colorName; hue = hue.toInteger(); def hue360 = hue * 3.6
    switch (hue360.toInteger()) {
        case 0..15: colorName = "Red"; break; case 16..45: colorName = "Orange"; break; case 46..75: colorName = "Yellow"; break
        case 76..105: colorName = "Chartreuse"; break; case 106..135: colorName = "Green"; break; case 136..165: colorName = "Spring"; break
        case 166..195: colorName = "Cyan"; break; case 196..225: colorName = "Azure"; break; case 226..255: colorName = "Blue"; break
        case 256..285: colorName = "Violet"; break; case 286..315: colorName = "Magenta"; break; case 316..345: colorName = "Rose"; break
        default: colorName = "Red"; break
    }
    updateAttr("colorName", colorName)
}

private setGenericNameFromTemp(temp) {
    def genericName; def value = temp.toInteger()
    if (value <= 2000) genericName = "Sodium"; else if (value < 2800) genericName = "Incandescent"; else if (value < 3500) genericName = "Warm White"
    else if (value <= 5000) genericName = "Daylight"; else if (value <= 6500) genericName = "Skylight"; else genericName = "Polar"
    updateAttr("colorName", genericName)
}

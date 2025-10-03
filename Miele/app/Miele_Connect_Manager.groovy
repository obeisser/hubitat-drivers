/**
 *  Miele Connect Manager
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
import groovy.json.JsonSlurper

definition(
    name: "Miele Connect Manager",
    namespace: "obeisser",
    author: "obeisser",
    description: "Connects Hubitat to the Miele@home Cloud",
    category: "App",
    iconUrl: "https://s3.amazonaws.com/smartapp-icons/Partner/miele.png",
    iconX2Url: "https://s3.amazonaws.com/smartapp-icons/Partner/miele@2x.png",
    iconX3Url: "https://s3.amazonaws.com/smartapp-icons/Partner/miele@3x.png",
    oauth: true
)

preferences {
    page(name: "mainPage", title: "Miele@home Connection", install: false, uninstall: true, nextPage: "authPage") {
        section {
            input name: "clientId", type: "text", title: "Client ID", required: true
            input name: "clientSecret", type: "password", title: "Client Secret", required: true
            input name: "logLevel", type: "enum", title: "Log Level", options: ["Error", "Warn", "Info", "Debug", "Trace"], defaultValue: "Info", required: true
        }
    }
    page(name: "authPage", title: "Authorize Miele Account", install: false, uninstall: false, nextPage: "deviceSelectionPage") {
        section {
            if (!state.accessToken) {
                href(name: "authLink", title: "Click to authorize with Miele", url: getAuthorizationUrl(), description: "You must authorize this app to connect to your Miele account.")
            } else {
                paragraph "You are connected to Miele. Click 'Next' to select your devices."
            }
        }
    }
    page(name: "deviceSelectionPage", title: "Select Miele Devices", install: true, uninstall: true) {
        section {
            input "selectedDevices", "enum", title: "Select Miele Devices to Install", required: false, multiple: true, options: getDiscoveredDeviceOptions()
        }
    }
}

def getDiscoveredDeviceOptions() {
    return state.discoveredDevices?.collectEntries { id, details -> [ (id): details.name ] } ?: [:]
}

def installed() {
    log.info "Installed with settings: ${settings}"
    initialize()
}

def updated() {
    log.info "Updated with settings: ${settings}"
    unsubscribe()
    initialize()
    if (state.accessToken) {
        manageChildDevices()
    }
}

def uninstalled() {
    log.info "Uninstalling Miele Connect Manager"
    revokeToken()
    unschedule()
    getChildDevices().each { deleteChildDevice(it.deviceNetworkId) }
}

def initialize() {
    // Clear discovered devices on re-initialization to force a fresh pull
    if (!state.accessToken) {
        state.discoveredDevices = [:]
    }
    state.tokenExpiresAt = 0
}

// OAuth Methods
def oauthCallback(response) {
    log.debug "oauthCallback called with response: ${response}"
    state.oauthCode = response.code
    getTokenFromCode()
}

String getAuthorizationUrl() {
    state.oauthInitState = UUID.randomUUID().toString()
    def oauthParams = [
        "response_type": "code",
        "client_id": settings.clientId,
        "redirect_uri": "https://cloud.hubitat.com/oauth/stateredirect",
        "scope": "read:mcs_thirdparty_read write:mcs_thirdparty_write openid",
        "state": state.oauthInitState
    ]
    return "https://auth.domestic.miele-iot.com/partner/realms/mcs/protocol/openid-connect/auth?${toQueryString(oauthParams)}"
}

def getTokenFromCode() {
    def tokenParams = [
        "grant_type": "authorization_code",
        "code": state.oauthCode,
        "redirect_uri": "https://cloud.hubitat.com/oauth/stateredirect",
        "client_id": settings.clientId,
        "client_secret": settings.clientSecret
    ]

    asynchttpPost("tokenHandler", [
        uri: "https://auth.domestic.miele-iot.com/partner/realms/mcs/protocol/openid-connect/token",
        body: toQueryString(tokenParams),
        headers: ["Content-Type": "application/x-www-form-urlencoded"]
    ])
}

def tokenHandler(response, data) {
    if (response.status == 200) {
        def json = new JsonSlurper().parseText(response.data)
        state.accessToken = json.access_token
        state.refreshToken = json.refresh_token
        state.tokenExpiresAt = now() + (json.expires_in * 1000) - 60000 // 1 minute buffer
        log.info "Access token obtained. Expires at ${new Date(state.tokenExpiresAt)}"
        scheduleTokenRefresh()
        discoverDevices()
    } else {
        log.error "Error getting token: ${response.status} - ${response.errorMessage}"
    }
}

def scheduleTokenRefresh() {
    unschedule("tokenRefreshJob")
    def refreshIn = (state.tokenExpiresAt - now()) / 1000 * 0.9
    runIn(refreshIn.toInteger(), "tokenRefreshJob", [overwrite: true])
    log.info "Scheduled token refresh in ${refreshIn.toInteger()} seconds"
}

def tokenRefreshJob() {
    log.info "Refreshing Miele API token"
    def tokenParams = [
        "grant_type": "refresh_token",
        "refresh_token": state.refreshToken,
        "client_id": settings.clientId,
        "client_secret": settings.clientSecret
    ]

    asynchttpPost("tokenHandler", [
        uri: "https://auth.domestic.miele-iot.com/partner/realms/mcs/protocol/openid-connect/token",
        body: toQueryString(tokenParams),
        headers: ["Content-Type": "application/x-www-form-urlencoded"]
    ])
}

def revokeToken() {
    if (state.accessToken) {
        asynchttpPost("revokedTokenHandler", [
            uri: "https://auth.domestic.miele-iot.com/partner/realms/mcs/protocol/openid-connect/revoke",
            body: toQueryString([token: state.accessToken, client_id: settings.clientId, client_secret: settings.clientSecret]),
            headers: ["Content-Type": "application/x-www-form-urlencoded"]
        ])
    }
}

def revokedTokenHandler(response, data) {
    if (response.status == 200) {
        log.info "Token revoked successfully"
    } else {
        log.warn "Could not revoke token: ${response.status}"
    }
}

// Device Discovery
def refreshDevices() {
    log.info "Manual device refresh triggered."
    discoverDevices()
}

def discoverDevices() {
    log.info "Discovering Miele devices..."
    authenticatedHttpGet("/v1/devices", "discoverDevicesHandler")
}

def discoverDevicesHandler(response, data) {
    if (response.status == 200) {
        def devices = new JsonSlurper().parseText(response.data)
        log.info "Found ${devices.size()} devices"
        def discovered = [:]
        devices.each { String deviceId, Map deviceData ->
            def deviceName = deviceData.ident.deviceName ?: deviceId
            def deviceType = deviceData.ident.type.value_raw
            discovered[deviceId] = [name: deviceName, type: deviceType]
        }
        state.discoveredDevices = discovered
        log.debug "Stored discovered devices in state: ${state.discoveredDevices}"

        // If this is the first time discovering, we don't manage children yet.
        // The user will do that from the selection page.
        // If it's a subsequent refresh, we can call manageChildDevices.
        if (getChildDevices()) {
            manageChildDevices()
        }
        startEventStream()
    } else {
        log.error "Error discovering devices: ${response.status} - ${response.errorMessage}"
    }
}

def manageChildDevices() {
    log.info "Managing child devices based on selection."
    def currentChildDnis = getChildDevices().collect { it.deviceNetworkId }
    def selectedDnis = selectedDevices ?: []

    def toAdd = selectedDnis - currentChildDnis
    def toRemove = currentChildDnis - selectedDnis

    log.debug "Current: ${currentChildDnis}, Selected: ${selectedDnis}, To Add: ${toAdd}, To Remove: ${toRemove}"

    toRemove.each { dni ->
        def child = getChildDevice(dni)
        if (child) {
            log.info "Removing device: ${child.label}"
            deleteChildDevice(dni)
        }
    }

    toAdd.each { dni ->
        def deviceDetails = state.discoveredDevices[dni]
        if (deviceDetails) {
            def driverName = getDriverForType(state.discoveredDevices[dni].type)
            if (driverName) {
                log.info "Adding device ${state.discoveredDevices[dni].name} (${dni}) with driver ${driverName}"
                def d = addChildDevice("obeisser", driverName, dni, [name: state.discoveredDevices[dni].name, label: state.discoveredDevices[dni].name])
            } else {
                log.warn "No driver found for device type ${state.discoveredDevices[dni].type}"
            }
        } else {
            log.warn "Could not find details for selected device ${dni} to add it."
        }
    }
}

String getDriverForType(Integer type) {
    switch (type) {
        case 1: return "Miele Washer"
        case 2: return "Miele Dryer"
        case 3: return "Miele Dishwasher"
        case 7: return "Miele Oven"
        case 8: return "Miele Oven Microwave"
        case 10: return "Miele Hob Highlight"
        case 12: return "Miele Steamoven"
        case 13: return "Miele Microwave"
        case 15: return "Miele Coffee System"
        case 16: return "Miele Hood"
        case 17: return "Miele Fridge"
        case 18: return "Miele Freezer"
        case 19: return "Miele Fridge Freezer"
        case 21: return "Miele Robot Vacuum Cleaner"
        case 23: return "Miele Washer Dryer"
        case 24: return "Miele Warming Drawer"
        case 27: return "Miele Hob Induction"
        case 31: return "Miele Steam Oven Combi"
        case 32: return "Miele Wine Cabinet"
        case 33: return "Miele Wine Conditioning Unit"
        case 34: return "Miele Wine Unit"
        case 45: return "Miele Steam Oven Micro"
        case 67: return "Miele Dialog Oven"
        case 68: return "Miele Wine Cabinet Freezer"
        case 128: return "Miele Hob With Vapour Extraction"
        default: return "Miele Generic Device"
    }
}

// SSE Event Stream
def startEventStream() {
    log.info "Starting Miele event stream..."
    authenticatedHttpGet("/v1/events", "eventStreamHandler", [handleSse: true])
}

def eventStreamHandler(response, data) {
    if (response.status == 200) {
        def slurper = new groovy.json.JsonSlurper(type: groovy.json.JsonSlurper.LAX)
        try {
            def event = slurper.parseText(response.data)
            if (event.devices) {
                event.devices.each { String deviceId, Map deviceData ->
                    def child = getChildDevice(deviceId)
                    if (child) {
                        def stateMap = [:]
                        if (deviceData.state) {
                            stateMap.operationState = deviceData.state.status.value_localized
                            stateMap.programName = deviceData.state.programType.value_localized
                            stateMap.programPhase = deviceData.state.programPhase.value_localized
                            stateMap.remainingTime = (deviceData.state.remainingTime[0] * 60) + deviceData.state.remainingTime[1]
                            stateMap.spinSpeed = deviceData.state.spinningSpeed?.value_raw != -32768 ? deviceData.state.spinningSpeed?.value_raw : null
                            stateMap.targetTemperature = deviceData.state.targetTemperature?.value_raw != -32768 ? deviceData.state.targetTemperature.value_raw : null
                            stateMap.currentTemperature = deviceData.state.temperature?.value_raw != -32768 ? deviceData.state.temperature.value_raw : null
                            stateMap.coreTemperature = deviceData.state.coreTemperature?.value_raw != -32768 ? deviceData.state.coreTemperature.value_raw : null
                            stateMap.drynessLevel = deviceData.state.dryingStep?.value_localized
                            if (deviceData.state.ecoFeedback) {
                                stateMap.energyConsumption = deviceData.state.ecoFeedback.energyConsumption
                                stateMap.waterConsumption = deviceData.state.ecoFeedback.waterConsumption
                            }
                        }
                        child.receiveMieleState(stateMap.findAll { it.value != null })
                    }
                }
            }
        } catch (e) {
            log.error "Error parsing SSE event: ${e.message} - Data: ${response.data}"
        }
    } else if (response.hasError()) {
        log.error "Event stream error: ${response.errorMessage}. Reconnecting in 1 minute."
        runIn(60, startEventStream)
    } else {
        log.warn "Event stream closed with status ${response.status}. Reconnecting."
        startEventStream()
    }
}

// Command Proxy
def childDriverCommand(String deviceId, String commandName, Map params = [:]) {
    log.debug "Received command '${commandName}' for device ${deviceId} with params ${params}"
    def body = [:]
    switch (commandName) {
        case "on":
            body = [powerOn: true]
            break
        case "off":
            body = [powerOff: true]
            break
        case "startProgram":
            body = [processAction: 1] // 1 = Start
            break
        case "pauseProgram":
            body = [processAction: 3] // 3 = Pause
            break
        case "stopProgram":
            body = [processAction: 2] // 2 = Stop
            break
        case "setThermostatSetpoint":
            body = [targetTemperature: [[value: params.temp, unit: "Celsius"]]]
            break
        case "setLight":
            body = [light: params.value == "on" ? 1 : 2] // 1 = On, 2 = Off
            break;
        // Add other commands here
    }

    if (body) {
        authenticatedHttpPut("/v1/devices/${deviceId}/actions", body, "commandResponseHandler")
    }
}

def commandResponseHandler(response, data) {
    if (response.status == 200) {
        log.info "Command sent successfully"
    } else {
        log.error "Error sending command: ${response.status} - ${response.errorMessage}"
    }
}

// HTTP Helpers
private void authenticatedHttpGet(String path, String callback, Map options = [:]) {
    def headers = [
        "Authorization": "Bearer ${state.accessToken}",
        "Accept": "application/json"
    ]
    if (options.handleSse) {
        headers["Accept"] = "text/event-stream"
    }
    
    asynchttpGet(callback, [
        uri: "https://api.mcs3.miele.com",
        path: path,
        headers: headers
    ], options.data)
}

private void authenticatedHttpPut(String path, Map body, String callback) {
    asynchttpPut(callback, [
        uri: "https://api.mcs3.miele.com",
        path: path,
        headers: [
            "Authorization": "Bearer ${state.accessToken}",
            "Content-Type": "application/json"
        ],
        body: new groovy.json.JsonOutput().toJson(body)
    ])
}

private String toQueryString(Map params) {
    return params.collect { k, v -> "${k}=${java.net.URLEncoder.encode(v.toString())}" }.join("&")
}

void log(String level, String msg) {
    if (logLevel == "Trace" || (logLevel == "Debug" && level != "Trace") || (logLevel == "Info" && (level == "Info" || level == "Warn" || level == "Error")) || (logLevel == "Warn" && (level == "Warn" || level == "Error")) || (logLevel == "Error" && level == "Error")) {
        log."${level.toLowerCase()}"(msg)
    }
}

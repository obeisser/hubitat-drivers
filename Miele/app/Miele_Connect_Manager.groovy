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
import groovy.transform.Field

// API Constants
@Field static final String MIELE_API_BASE = "https://api.mcs3.miele.com"
@Field static final String MIELE_AUTH_BASE_NEW = "https://auth.domestic.miele-iot.com/partner/realms/mcs/protocol/openid-connect"
@Field static final String MIELE_AUTH_BASE_LEGACY = "https://api.mcs3.miele.com/thirdparty"

// API Endpoints
@Field static final String MIELE_AUTH_PATH_NEW = "/auth"
@Field static final String MIELE_TOKEN_PATH_NEW = "/token"
@Field static final String MIELE_REVOKE_PATH_NEW = "/revoke"
@Field static final String MIELE_LOGIN_PATH_LEGACY = "/login"
@Field static final String MIELE_TOKEN_PATH_LEGACY = "/token"

@Field static final String MIELE_DEVICES_PATH = "/v1/devices"
@Field static final String MIELE_EVENT_PATH = "/v1/devices/all/events"
@Field static final String MIELE_ACTIONS_PATH = "/v1/devices/{deviceId}/actions"
@Field static final String MIELE_PROGRAMS_PATH = "/v1/devices"

// OAuth Configuration
@Field static final String OAUTH_REDIRECT_URI = "https://cloud.hubitat.com/oauth/stateredirect"
@Field static final String OAUTH_SCOPES = "read:mcs_thirdparty_read write:mcs_thirdparty_write openid"

// Device Type Mappings
@Field static final Map DEVICE_TYPE_MAP = [
    1: "Miele Washer",
    2: "Miele Dryer", 
    3: "Miele Dishwasher",
    7: "Miele Oven",
    8: "Miele Oven Microwave",
    10: "Miele Hob Highlight",
    12: "Miele Steamoven",
    13: "Miele Microwave",
    15: "Miele Coffee System",
    16: "Miele Hood",
    17: "Miele Fridge",
    18: "Miele Freezer",
    19: "Miele Fridge Freezer",
    21: "Miele Robot Vacuum Cleaner",
    23: "Miele Washer Dryer",
    24: "Miele Warming Drawer",
    27: "Miele Hob Induction",
    31: "Miele Steam Oven Combi",
    32: "Miele Wine Cabinet",
    33: "Miele Wine Conditioning Unit",
    34: "Miele Wine Unit",
    45: "Miele Steam Oven Micro",
    67: "Miele Dialog Oven",
    68: "Miele Wine Cabinet Freezer",
    128: "Miele Hob With Vapour Extraction"
]

// Timing Constants
@Field static final Integer TOKEN_REFRESH_BUFFER_MS = 60000 // 1 minute
@Field static final Integer POLLING_INTERVAL_SEC = 300 // 5 minutes
@Field static final Integer SSE_RETRY_DELAY_BASE_SEC = 30
@Field static final Integer TOKEN_REFRESH_MIN_INTERVAL_SEC = 30
@Field static final Integer TOKEN_EXPIRY_CHECK_MIN_INTERVAL_SEC = 60
@Field static final Integer MAX_TOKEN_REFRESH_ATTEMPTS = 10
@Field static final Integer HTTP_TIMEOUT_SEC = 30
@Field static final Integer MAX_SSE_RETRIES = 5

// Error Handling Constants
@Field static final Integer TEMP_PLACEHOLDER_VALUE = -32768
@Field static final Integer MIN_TOKEN_EXPIRY_SEC = 60
@Field static final Integer MAX_TOKEN_EXPIRY_SEC = 365 * 24 * 3600
@Field static final Integer FALLBACK_TOKEN_EXPIRY_HOURS = 24

definition(
    name: "Miele Connect Manager",
    namespace: "obeisser",
    author: "obeisser",
    description: "Connects Hubitat to the Miele@home Cloud",
    category: "Convenience",
    iconUrl: "", iconX2Url: "", iconX3Url: "",
    oauth: true
)

preferences {
    page(name: "mainPage", title: "Miele@home Connection", install: false, uninstall: true, nextPage: "authPage") {
        section("Miele API Credentials") {
            input name: "clientId", type: "text", title: "Client ID", required: true, description: "From your Miele developer account"
            input name: "clientSecret", type: "password", title: "Client Secret", required: true, description: "From your Miele developer account"
        }
        section("API Configuration") {
            input name: "apiVersion", type: "enum", title: "Miele API Version", options: [
                "new": "New OpenID Connect API (default)",
                "legacy": "Legacy API (api.mcs3.miele.com)"
            ], defaultValue: "new", required: true, description: "Choose based on your Miele developer account type"
            input name: "logLevel", type: "enum", title: "Log Level", options: ["Error", "Warn", "Info", "Debug", "Trace"], defaultValue: "Info", required: true
        }
        section("Troubleshooting") {
            paragraph "If you get 'Client not found' error:"
            paragraph "1. Try switching API versions above"
            paragraph "2. Verify your Client ID is correct (no extra spaces)"
            paragraph "3. Ensure your Miele developer account is approved"
            paragraph "4. Check that your redirect URI is configured correctly"
            
            paragraph "<b>⚠️ API Compatibility Notice:</b>"
            paragraph "Next-generation washers, dryers, and vacuum cleaners have limited API support until Fall 2025."
            paragraph "If commands don't work, your appliance may be a newer model with restricted API endpoints."
            paragraph "Monitoring features should work on all devices, but control commands may be limited."
            
            if (settings?.clientId) {
                paragraph "Current Client ID: ${settings.clientId}"
                paragraph "Current API: ${settings?.apiVersion == 'legacy' ? 'Legacy (api.mcs3.miele.com)' : 'New OpenID Connect'}"
                
                // Generate test URL for manual testing
                String testClientId = java.net.URLEncoder.encode(settings.clientId, "UTF-8")
                String baseUrl = settings?.apiVersion == 'legacy' ? 
                    "${MIELE_AUTH_BASE_LEGACY}${MIELE_LOGIN_PATH_LEGACY}" :
                    "${MIELE_AUTH_BASE_NEW}${MIELE_AUTH_PATH_NEW}"
                String encodedRedirectUri = java.net.URLEncoder.encode(OAUTH_REDIRECT_URI, "UTF-8")
                String encodedScopes = java.net.URLEncoder.encode(OAUTH_SCOPES, "UTF-8")
                String testUrl = "${baseUrl}?response_type=code&client_id=${testClientId}&redirect_uri=${encodedRedirectUri}&scope=${encodedScopes}&state=test123"
                
                paragraph "Test URL (copy to browser): <a href='${testUrl}' target='_blank'>${testUrl}</a>"
            }
            
            paragraph "Your Miele app must be configured with redirect URI: <strong>${OAUTH_REDIRECT_URI}</strong>"
        }
        
        section("Manual OAuth Completion") {
            if (state.accessToken) {
                paragraph "✅ OAuth completed successfully!"
                paragraph "Access token: ${state.accessToken?.take(20)}..."
                paragraph "Token expires: ${new Date(state.tokenExpiresAt ?: 0)}"
                paragraph "Discovered devices: ${state.discoveredDevices?.size() ?: 0}"
            } else {
                paragraph "If OAuth redirect fails, you can manually complete the process:"
                paragraph "From your redirect URL, copy ONLY the authorization code (after 'code=' and before '&state')"
                paragraph "Example: If URL contains 'code=DE_abc123&state=xyz', enter only: DE_abc123"
                input name: "manualAuthCode", type: "text", title: "Authorization Code", description: "Enter only the code value (e.g., DE_abc123...)"
                input name: "completeOAuth", type: "button", title: "Complete OAuth", submitOnChange: true
            }
        }
        
        section("Manual Device Discovery") {
            if (state.accessToken) {
                paragraph "If devices don't appear automatically, click the button below to manually discover devices."
                paragraph "Current status: ${state.discoveredDevices ? "${state.discoveredDevices.size()} devices found" : "No devices discovered yet"}"
                paragraph "Last discovery attempt: ${state.lastDiscoveryAttempt ? new Date(state.lastDiscoveryAttempt).format('yyyy-MM-dd HH:mm:ss') : 'Never'}"
                input name: "manualDiscover", type: "button", title: "Discover Devices Now", submitOnChange: true
                input name: "testConnection", type: "button", title: "Test API Connection", submitOnChange: true
            } else {
                paragraph "Complete OAuth authorization first before discovering devices."
            }
        }
    }
    page(name: "authPage", title: "Authorize Miele Account", install: false, uninstall: false, nextPage: "deviceSelectionPage") {
        section {
            if (!settings?.clientId || !settings?.clientSecret) {
                paragraph "Please go back and configure your Client ID and Client Secret first."
                href(name: "backToMain", title: "← Back to Configuration", page: "mainPage")
            } else if (!state.accessToken) {
                href(name: "authLink", title: "Click to authorize with Miele", url: getAuthorizationUrl(), description: "You must authorize this app to connect to your Miele account.")
            } else {
                paragraph "✓ You are connected to Miele. Click 'Next' to select your devices."
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
    log.debug "Getting device options from state: ${state.discoveredDevices}"
    def options = state.discoveredDevices?.collectEntries { id, details -> [ (id): details.name ] } ?: [:]
    log.debug "Device options for selection: ${options}"
    return options
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

// Manual OAuth completion for when automatic callback fails
def appButtonHandler(btn) {
    switch (btn) {
        case "completeOAuth":
            if (settings.manualAuthCode) {
                log.info "Attempting manual OAuth completion with provided authorization code"
                
                // Clean the authorization code - remove any extra parameters
                String cleanCode = settings.manualAuthCode.trim()
                if (cleanCode.contains('&')) {
                    cleanCode = cleanCode.split('&')[0]
                }
                if (cleanCode.contains('=')) {
                    cleanCode = cleanCode.split('=')[-1]
                }
                
                log.info "Cleaned authorization code: ${cleanCode.take(10)}..."
                state.oauthCode = cleanCode
                
                // Clear the manual auth code to prevent reuse
                app.updateSetting("manualAuthCode", "")
                
                getTokenFromCode()
            } else {
                log.error "No authorization code provided for manual OAuth completion"
            }
            break
            
        case "manualDiscover":
            log.info "Manual device discovery triggered by user"
            if (state.accessToken) {
                discoverDevices()
            } else {
                log.error "Cannot discover devices - no access token available"
            }
            break
            
        case "testConnection":
            log.info "=== Testing API Connection ==="
            if (state.accessToken) {
                log.info "Access Token: ${state.accessToken?.take(30)}..."
                log.info "Token Expires: ${new Date(state.tokenExpiresAt ?: 0)}"
                log.info "API Version: ${settings?.apiVersion ?: 'new'}"
                log.info "Endpoint: ${MIELE_API_BASE}${MIELE_DEVICES_PATH}"
                log.info "Authorization: Bearer ${state.accessToken?.take(20)}..."
                log.info "Sending test request..."
                discoverDevices()
            } else {
                log.error "Cannot test connection - no access token available"
            }
            break
            

    }
}

// OAuth Methods
def oauthCallback(response) {
    log.info "oauthCallback called with response: ${response}"
    
    // Handle different response formats
    String authCode = null
    String receivedState = null
    
    if (response?.code) {
        authCode = response.code
        receivedState = response.state
    } else if (response?.params?.code) {
        authCode = response.params.code
        receivedState = response.params.state
    } else {
        log.error "No authorization code found in OAuth callback response"
        log.error "Full response: ${response}"
        return
    }
    
    // Validate state parameter
    if (receivedState != state.oauthInitState) {
        log.error "OAuth state mismatch. Expected: ${state.oauthInitState}, Received: ${receivedState}"
        return
    }
    
    log.info "Authorization code received: ${authCode?.take(10)}..."
    state.oauthCode = authCode
    getTokenFromCode()
}

String getAuthorizationUrl() {
    if (!settings?.clientId) {
        log.error "Client ID not configured"
        return "javascript:alert('Please configure Client ID first');"
    }
    
    state.oauthInitState = UUID.randomUUID().toString()
    
    // Use the standard Hubitat OAuth redirect URI
    String redirectUri = OAUTH_REDIRECT_URI
    
    def oauthParams = [
        "response_type": "code",
        "client_id": settings.clientId,
        "redirect_uri": OAUTH_REDIRECT_URI,
        "scope": OAUTH_SCOPES,
        "state": state.oauthInitState
    ]
    
    // Choose OAuth endpoint based on API version
    String authEndpoint = (settings?.apiVersion == "legacy") ? 
        "${MIELE_AUTH_BASE_LEGACY}${MIELE_LOGIN_PATH_LEGACY}" :
        "${MIELE_AUTH_BASE_NEW}${MIELE_AUTH_PATH_NEW}"
    
    String authUrl = "${authEndpoint}?${toQueryString(oauthParams)}"
    log.info "Generated OAuth URL using ${settings?.apiVersion == 'legacy' ? 'Legacy' : 'New'} API"
    
    return authUrl
}

def getTokenFromCode() {
    if (!settings?.clientId || !settings?.clientSecret) {
        log.error "Client credentials not configured"
        return
    }
    
    def tokenParams = [
        "grant_type": "authorization_code",
        "code": state.oauthCode,
        "redirect_uri": OAUTH_REDIRECT_URI,
        "client_id": settings.clientId,
        "client_secret": settings.clientSecret
    ]

    log.info "Exchanging authorization code for tokens"
    
    // Choose token endpoint based on API version
    String tokenEndpoint = (settings?.apiVersion == "legacy") ? 
        "${MIELE_AUTH_BASE_LEGACY}${MIELE_TOKEN_PATH_LEGACY}" :
        "${MIELE_AUTH_BASE_NEW}${MIELE_TOKEN_PATH_NEW}"
    
    try {
        asynchttpPost("tokenHandler", [
            uri: tokenEndpoint,
            body: toQueryString(tokenParams),
            requestContentType: "application/x-www-form-urlencoded",
            headers: [
                "Accept": "application/json",
                "User-Agent": "Hubitat-Miele-Integration/1.0"
            ],
            timeout: 30
        ])
    } catch (Exception e) {
        log.error "Error making token request: ${e.message}"
    }
}

def tokenHandler(response, data) {
    try {
        if (response.hasError()) {
            log.error "❌ HTTP Error in token exchange: ${response.getErrorMessage()}"
            
            // Handle 503 errors with exponential backoff
            if (response.status == 503) {
                Integer attempts = state.tokenRefreshAttempts ?: 1
                Integer backoffSeconds = Math.min(30 * Math.pow(2, attempts - 1), 3600) as Integer
                log.error "Miele API temporarily unavailable (503). Will retry in ${backoffSeconds} seconds."
                safeRunIn(backoffSeconds, "tokenRefreshJob")
            } else if (response.status == 401) {
                log.error "Token refresh failed with 401 Unauthorized. You may need to re-authorize the app."
                state.tokenRefreshAttempts = 0 // Reset counter
            }
            return
        }
        
        if (response.status == 200) {
            try {
                def json = new JsonSlurper().parseText(response.data)
                log.info "=== Full Token Response ==="
                log.info "Response data: ${response.data}"
                log.info "Parsed JSON: ${json}"
                
                state.accessToken = json.access_token
                state.refreshToken = json.refresh_token
                
                // Calculate token expiry from expires_in (seconds from now)
                Long currentTime = now()
                Long expiresInSeconds = json.expires_in as Long
                Long expiresInMs = expiresInSeconds * 1000
                Long calculatedExpiry = currentTime + expiresInMs - 60000 // Add expires_in, subtract 1 minute buffer
                
                log.info "=== Token Expiry Calculation ==="
                log.info "Miele API expires_in: ${expiresInSeconds} seconds (${Math.round(expiresInSeconds / 3600 * 10) / 10} hours)"
                log.info "Current time: ${new Date(currentTime)}"
                log.info "Calculated expiry: ${new Date(calculatedExpiry)}"
                
                // Validation: Check if calculated expiry makes sense
                Long minExpiry = currentTime + (60 * 1000) // At least 1 minute in future
                Long maxExpiry = currentTime + (365 * 24 * 3600 * 1000L) // At most 1 year in future
                
                Long expiryTime
                if (calculatedExpiry < minExpiry) {
                    log.warn "⚠️ Calculated expiry is too soon (less than 1 minute). Using 24-hour fallback."
                    expiryTime = currentTime + (24 * 3600 * 1000) - 60000
                } else if (calculatedExpiry > maxExpiry) {
                    log.warn "⚠️ Calculated expiry is too far in future (more than 1 year). Using 24-hour fallback."
                    expiryTime = currentTime + (24 * 3600 * 1000) - 60000
                } else if (calculatedExpiry < currentTime) {
                    log.error "❌ Calculated expiry is in the PAST! Using 24-hour fallback."
                    expiryTime = currentTime + (24 * 3600 * 1000) - 60000
                } else {
                    log.info "✅ Expiry calculation looks valid"
                    expiryTime = calculatedExpiry
                }
                
                log.info "Final token expiry: ${new Date(expiryTime)}"
                log.info "Time until expiry: ${Math.round((expiryTime - currentTime) / 60000)} minutes"
                
                state.tokenExpiresAt = expiryTime
                
                // Reset refresh attempt counters on success
                state.tokenRefreshAttempts = 0
                state.lastTokenRefreshAttempt = 0
                
                log.info "✅ Access token obtained successfully"
                log.info "Token will be valid for 24 hours"
                log.info "Time until expiry: ${Math.round((expiryTime - currentTime) / 60000)} minutes"
                
                scheduleTokenRefresh()
                
                // Wait a moment before discovering devices to ensure token is fully set
                safeRunIn(2, "discoverDevices")
            } catch (Exception e) {
                log.error "Error parsing token response: ${e.message}"
                log.debug "Token response data: ${response.data}"
            }
        } else {
            log.error "Token exchange failed: HTTP ${response.status}"
            if (response.errorMessage) {
                log.error "Error message: ${response.errorMessage}"
            }
            if (response.data) {
                log.error "Response data: ${response.data}"
                
                // Try to parse error details
                try {
                    def errorJson = new JsonSlurper().parseText(response.data)
                    if (errorJson.error) {
                        log.error "OAuth Error: ${errorJson.error}"
                    }
                    if (errorJson.error_description) {
                        log.error "Error Description: ${errorJson.error_description}"
                    }
                } catch (Exception e) {
                    log.debug "Could not parse error response as JSON"
                }
            }
            
            // Provide specific guidance based on common errors
            switch (response.status) {
                case 400:
                    log.error "Bad Request - Check your Client ID, Client Secret, and authorization code"
                    break
                case 401:
                    log.error "Unauthorized - Verify your Client Secret is correct"
                    break
                case 404:
                    log.error "Not Found - Check if you're using the correct token endpoint"
                    break
                default:
                    log.error "Unexpected error during token exchange"
            }
        }
    } catch (Exception e) {
        log.error "Exception in tokenHandler: ${e.message}"
    }
}

def scheduleTokenRefresh() {
    unschedule("tokenRefreshJob")
    
    if (!state.tokenExpiresAt) {
        log.error "No token expiry time available for scheduling refresh"
        return
    }
    
    Long timeUntilExpiry = state.tokenExpiresAt - now()
    Integer refreshIn = Math.max((timeUntilExpiry / 1000 * 0.9).toInteger(), 300) // Minimum 5 minutes
    
    log.debug "Token expires at: ${new Date(state.tokenExpiresAt)}"
    log.debug "Time until expiry: ${timeUntilExpiry}ms"
    log.debug "Calculated refresh delay: ${refreshIn}s"
    
    if (refreshIn > 0 && refreshIn < 2147483) { // Max runIn delay is ~24 days
        try {
            runIn(refreshIn, "tokenRefreshJob", [overwrite: true])
            log.info "Scheduled token refresh in ${refreshIn} seconds (${Math.round(refreshIn/3600 * 10) / 10} hours)"
        } catch (Exception e) {
            log.error "Error scheduling token refresh: ${e.message}"
            // Fallback to 1 hour refresh
            runIn(3600, "tokenRefreshJob", [overwrite: true])
            log.info "Fallback: Scheduled token refresh in 1 hour"
        }
    } else {
        log.warn "Invalid refresh delay (${refreshIn}s), scheduling 1 hour refresh"
        runIn(3600, "tokenRefreshJob", [overwrite: true])
    }
}

def tokenRefreshJob() {
    if (!settings?.clientId || !settings?.clientSecret) {
        log.error "Client credentials not configured for token refresh"
        return
    }
    
    // Prevent rapid-fire refresh attempts
    Long lastAttempt = state.lastTokenRefreshAttempt ?: 0
    Long timeSinceLastAttempt = now() - lastAttempt
    
    if (timeSinceLastAttempt < 30000) { // 30 seconds minimum between attempts
        log.warn "Token refresh attempted too recently (${timeSinceLastAttempt}ms ago). Skipping."
        return
    }
    
    state.lastTokenRefreshAttempt = now()
    state.tokenRefreshAttempts = (state.tokenRefreshAttempts ?: 0) + 1
    
    // Safety check: stop after 10 failed attempts
    if (state.tokenRefreshAttempts > 10) {
        log.error "❌ Token refresh has failed 10 times. Stopping automatic refresh attempts."
        log.error "Please re-authorize the app manually or check Miele API status."
        state.tokenRefreshAttempts = 0 // Reset for next manual attempt
        return
    }
    
    log.info "Refreshing Miele API token (attempt ${state.tokenRefreshAttempts})"
    
    def tokenParams = [
        "grant_type": "refresh_token",
        "refresh_token": state.refreshToken,
        "client_id": settings.clientId,
        "client_secret": settings.clientSecret
    ]

    // Choose token endpoint based on API version
    String tokenEndpoint = (settings?.apiVersion == "legacy") ? 
        "${MIELE_AUTH_BASE_LEGACY}${MIELE_TOKEN_PATH_LEGACY}" :
        "${MIELE_AUTH_BASE_NEW}${MIELE_TOKEN_PATH_NEW}"

    asynchttpPost("tokenHandler", [
        uri: tokenEndpoint,
        body: toQueryString(tokenParams),
        requestContentType: "application/x-www-form-urlencoded",
        headers: [
            "Accept": "application/json",
            "User-Agent": "Hubitat-Miele-Integration/1.0"
        ],
        timeout: 30
    ])
}

def revokeToken() {
    if (state.accessToken && settings?.clientId && settings?.clientSecret) {
        asynchttpPost("revokedTokenHandler", [
            uri: "${MIELE_AUTH_BASE_NEW}${MIELE_REVOKE_PATH_NEW}",
            body: toQueryString([token: state.accessToken, client_id: settings.clientId, client_secret: settings.clientSecret]),
            requestContentType: "application/x-www-form-urlencoded",
            headers: [
                "Accept": "application/json",
                "User-Agent": "Hubitat-Miele-Integration/1.0"
            ],
            timeout: 30
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

def pollDeviceStates() {
    log.debug "Polling device states (SSE unavailable)"
    
    if (!state.accessToken) {
        log.warn "Cannot poll devices - no access token"
        return
    }
    
    // Poll each device individually
    getChildDevices().each { device ->
        String deviceId = device.deviceNetworkId
        log.debug "Polling device: ${deviceId}"
        authenticatedHttpGet("/v1/devices/${deviceId}", "devicePollHandler")
    }
    
    // Schedule next poll based on device activity
    Integer nextPollInterval = getOptimalPollingInterval()
    safeRunIn(nextPollInterval, "pollDeviceStates")
    log.debug "Next poll scheduled in ${nextPollInterval} seconds"
}

def getOptimalPollingInterval() {
    // Check if any devices are actively running
    boolean anyDeviceActive = false
    getChildDevices().each { device ->
        String operationState = device.currentValue("operationState")?.toLowerCase() ?: ""
        if (operationState.contains("running") || operationState.contains("in use") || 
            operationState.contains("heating") || operationState.contains("spinning")) {
            anyDeviceActive = true
        }
    }
    
    // More frequent polling when devices are active
    if (anyDeviceActive) {
        return 60  // 1 minute when active
    } else {
        return POLLING_INTERVAL_SEC  // 5 minutes when idle
    }
}

def devicePollHandler(response, data) {
    if (response.hasError()) {
        log.warn "Error polling device: ${response.getErrorMessage()}"
        return
    }
    
    if (response.status == 200) {
        try {
            def deviceData = new JsonSlurper().parseText(response.data)
            String deviceId = deviceData.ident?.deviceIdentLabel?.fabNumber
            
            if (deviceId) {
                def child = getChildDevice(deviceId)
                if (child) {
                    Map stateMap = buildStateMap(deviceData)
                    if (stateMap && !stateMap.isEmpty()) {
                        child.receiveMieleState(stateMap)
                    }
                }
            }
        } catch (Exception e) {
            log.error "Error parsing device poll response: ${e.message}"
        }
    }
}

def discoverDevices() {
    log.info "=== Starting Miele Device Discovery ==="
    
    if (!state.accessToken) {
        log.error "Cannot discover devices - no access token available"
        return
    }
    
    state.lastDiscoveryAttempt = now()
    
    log.info "Using access token: ${state.accessToken?.take(20)}..."
    log.info "Current time: ${new Date(now())}"
    log.info "Token expires at: ${new Date(state.tokenExpiresAt ?: 0)}"
    log.info "Time until expiry: ${Math.round(((state.tokenExpiresAt ?: 0) - now()) / 60000)} minutes"
    log.info "Token is ${now() < (state.tokenExpiresAt ?: 0) ? 'VALID' : 'EXPIRED'}"
    log.info "API endpoint: ${MIELE_API_BASE}${MIELE_DEVICES_PATH}"
    log.info "Authorization header: Bearer ${state.accessToken?.take(20)}..."
    log.info "Making authenticated GET request..."
    
    authenticatedHttpGet("/v1/devices", "discoverDevicesHandler")
}

def discoverDevicesHandler(response, data) {
    log.info "=== Device Discovery Response Received ==="
    log.info "Response status: ${response.status}"
    
    if (response.hasError()) {
        log.error "❌ Error in device discovery: ${response.getErrorMessage()}"
        log.error "Response status: ${response.status}"
        log.error "Response headers: ${response.headers}"
        
        if (response.status == 503) {
            log.error "Miele API is temporarily unavailable (503). Will retry in 30 seconds."
            runIn(30, "discoverDevices")
        } else if (response.status == 401) {
            log.error "Unauthorized (401). Token may be invalid. Try re-authorizing."
        } else if (response.status == 404) {
            log.error "Not Found (404). Check if the endpoint is correct."
        }
        return
    }
    
    if (response.status == 200) {
        try {
            log.info "✅ Successful response from Miele API"
            def devices = new JsonSlurper().parseText(response.data)
            log.info "📱 Found ${devices.size()} devices"
            log.debug "Raw device data: ${response.data}"
            
            def discovered = [:]
            devices.each { String deviceId, Map deviceData ->
                def deviceName = deviceData.ident?.deviceName ?: deviceId
                def deviceType = deviceData.ident?.type?.value_raw ?: 0
                discovered[deviceId] = [name: deviceName, type: deviceType]
                log.info "Device: ${deviceName} (ID: ${deviceId}, Type: ${deviceType})"
                
                // Update existing child devices with current state
                def child = getChildDevice(deviceId)
                if (child) {
                    log.info "Updating state for existing device: ${deviceId}"
                    Map stateMap = buildStateMap(deviceData)
                    if (stateMap && !stateMap.isEmpty()) {
                        log.debug "Sending state to device ${deviceId}: ${stateMap}"
                        child.receiveMieleState(stateMap)
                        
                        // Fetch available programs for this device
                        runIn(3, "fetchDeviceProgramsDelayed", [data: [deviceId: deviceId]])
                    } else {
                        log.warn "No state data to send to device ${deviceId}"
                    }
                }
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
        } catch (Exception e) {
            log.error "Error parsing device discovery response: ${e.message}"
            log.debug "Raw response data: ${response.data}"
        }
    } else {
        log.error "Error discovering devices: HTTP ${response.status}"
        if (response.errorMessage) {
            log.error "Error message: ${response.errorMessage}"
        }
        if (response.data) {
            log.error "Response data: ${response.data}"
        }
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
                // Create descriptive device name: "Miele Washer 000187642328"
                String deviceTypeName = driverName.replace("Miele ", "")
                String deviceLabel = "Miele ${deviceTypeName} ${dni}"
                log.info "Adding device ${deviceLabel} with driver ${driverName}"
                def d = addChildDevice("obeisser", driverName, dni, [name: deviceLabel, label: deviceLabel])
            } else {
                log.warn "No driver found for device type ${state.discoveredDevices[dni].type}"
            }
        } else {
            log.warn "Could not find details for selected device ${dni} to add it."
        }
    }
}

String getDriverForType(Integer type) {
    return DEVICE_TYPE_MAP[type] ?: "Miele Base Driver"
}

// SSE Event Stream
def startEventStream() {
    if (!state.accessToken) {
        log.warn "Cannot start event stream - no access token available"
        return
    }
    
    log.info "Starting Miele event stream..."
    state.sseRetryCount = state.sseRetryCount ?: 0
    state.sseConnectionState = "connecting"
    state.sseBuffer = state.sseBuffer ?: ""
    
    // Miele SSE endpoint for real-time device updates
    // Try different endpoints based on API version
    String sseEndpoint
    if (settings?.apiVersion == "legacy") {
        // Legacy API might not support SSE or use different endpoint
        sseEndpoint = "/v1/devices/all/events"
        log.warn "SSE may not be available for legacy API"
    } else {
        sseEndpoint = "/v1/devices/all/events"
    }
    
    log.info "Using SSE endpoint: ${sseEndpoint}"
    log.info "Note: SSE may not be available for all API versions/accounts"
    log.info "If SSE fails, the integration will use polling instead"
    
    authenticatedHttpGet(sseEndpoint, "eventStreamHandler", [handleSse: true])
}

def eventStreamHandler(hubitat.scheduling.AsyncResponse response, Map data) {
    try {
        if (response.hasError()) {
            handleEventStreamError("HTTP Error: ${response.getErrorMessage()}")
            return
        }
        
        if (response.status == 200) {
            // Reset retry count on successful connection
            state.sseRetryCount = 0
            state.sseConnectionState = "connected"
            state.lastEventStreamContact = now()
            
            String responseData = response.data
            if (!responseData || responseData.trim().isEmpty()) {
                log.debug "Received empty SSE data - keeping connection alive"
                return
            }
            
            // Buffer incomplete chunks for proper SSE parsing
            state.sseBuffer = (state.sseBuffer ?: "") + responseData
            
            // Process complete SSE events (separated by double newlines)
            String[] events = state.sseBuffer.split('\n\n')
            
            // Keep the last incomplete event in buffer
            if (!state.sseBuffer.endsWith('\n\n')) {
                state.sseBuffer = events[-1]
                events = events[0..-2] // Remove last incomplete event
            } else {
                state.sseBuffer = ""
            }
            
            // Process each complete event
            events.each { String event ->
                String[] lines = event.split('\n')
                String eventType = null
                String eventData = null
                
                lines.each { String line ->
                    if (line.startsWith('event:')) {
                        eventType = line.substring(6).trim()
                    } else if (line.startsWith('data:')) {
                        eventData = line.substring(5).trim()
                    }
                }
                
                if (eventData && !eventData.isEmpty()) {
                    processEventData(eventData, eventType)
                }
            }
        } else {
            handleEventStreamError("Unexpected status code: ${response.status}")
        }
    } catch (Exception e) {
        handleEventStreamError("Exception in eventStreamHandler: ${e.message}")
    }
}

private void processEventData(String jsonData, String eventType = null) {
    try {
        String currentLogLevel = settings?.logLevel ?: "Info"
        if (currentLogLevel == "Debug" || currentLogLevel == "Trace") {
            log.debug "Processing SSE event${eventType ? " (${eventType})" : ""}: ${jsonData}"
        }
        
        def slurper = new groovy.json.JsonSlurper(type: groovy.json.JsonSlurper.LAX)
        def event = slurper.parseText(jsonData)
        
        if (event.devices) {
            event.devices.each { String deviceId, Map deviceData ->
                def child = getChildDevice(deviceId)
                if (child) {
                    Map stateMap = buildStateMap(deviceData)
                    if (stateMap && !stateMap.isEmpty()) {
                        if (currentLogLevel == "Debug" || currentLogLevel == "Trace") {
                            log.debug "Sending state to device ${deviceId}: ${stateMap}"
                        }
                        child.receiveMieleState(stateMap)
                    }
                } else if (currentLogLevel == "Debug" || currentLogLevel == "Trace") {
                    log.debug "No child device found for ID: ${deviceId}"
                }
            }
        } else if (event.deviceId) {
            // Handle single device events
            def child = getChildDevice(event.deviceId)
            if (child) {
                Map stateMap = buildStateMap(event)
                if (stateMap && !stateMap.isEmpty()) {
                    child.receiveMieleState(stateMap)
                }
            }
        }
    } catch (Exception e) {
        log.error "Error parsing SSE event data: ${e.message} - Data: ${jsonData}"
    }
}

private Map buildStateMap(Map deviceData) {
    Map stateMap = [:]
    
    if (!deviceData.state) {
        log.warn "No state data in device response"
        return stateMap
    }
    
    def state = deviceData.state
    
    // Operation state - status field
    if (state.status?.value_localized) {
        stateMap.operationState = state.status.value_localized
    }
    
    // Program information
    if (state.ProgramID) {
        stateMap.ProgramID = state.ProgramID  // Pass the full ProgramID object for processing
    }
    if (state.programPhase?.value_localized) {
        stateMap.programPhase = state.programPhase.value_localized
    }
    
    // Time handling - convert [hours, minutes] array to total minutes
    Integer remainingMinutes = convertTimeArrayToMinutes(state.remainingTime)
    if (remainingMinutes != null && remainingMinutes > 0) {
        stateMap.remainingTime = remainingMinutes
    }
    
    Integer elapsedMinutes = convertTimeArrayToMinutes(state.elapsedTime)
    if (elapsedMinutes != null && elapsedMinutes > 0) {
        stateMap.elapsedTime = elapsedMinutes
    }
    
    String startTimeFormatted = formatTimeArray(state.startTime)
    if (startTimeFormatted != null) {
        stateMap.startTime = startTimeFormatted
    }
    
    // Temperature handling - filter out placeholder values (-32768)
    // Temperature values are in hundredths (6000 = 60.0°C)
    // targetTemperature can be an array
    if (state.targetTemperature instanceof List && state.targetTemperature.size() > 0) {
        def temp = state.targetTemperature[0]
        if (isValidTemperature(temp?.value_raw)) {
            stateMap.targetTemperature = Math.round(temp.value_raw / 100.0)
        }
    } else if (isValidTemperature(state.targetTemperature?.value_raw)) {
        stateMap.targetTemperature = Math.round(state.targetTemperature.value_raw / 100.0)
    }
    
    // temperature can be an array
    if (state.temperature instanceof List && state.temperature.size() > 0) {
        def temp = state.temperature[0]
        if (isValidTemperature(temp?.value_raw)) {
            stateMap.currentTemperature = Math.round(temp.value_raw / 100.0)
        }
    } else if (isValidTemperature(state.temperature?.value_raw)) {
        stateMap.currentTemperature = Math.round(state.temperature.value_raw / 100.0)
    }
    
    // Washer/Dryer specific attributes
    if (isValidValue(state.spinningSpeed?.value_raw)) {
        stateMap.spinSpeed = state.spinningSpeed.value_raw
    }
    if (state.dryingStep?.value_localized && state.dryingStep.value_localized != "") {
        stateMap.drynessLevel = state.dryingStep.value_localized
    }
    
    // Signal states (boolean)
    if (state.signalInfo != null) {
        stateMap.signalInfo = state.signalInfo ? "true" : "false"
    }
    if (state.signalFailure != null) {
        stateMap.signalFailure = state.signalFailure ? "true" : "false"
    }
    if (state.signalDoor != null) {
        stateMap.signalDoor = state.signalDoor ? "true" : "false"
        stateMap.doorState = state.signalDoor ? "open" : "closed"
    }
    
    // Remote control capabilities
    if (state.remoteEnable) {
        if (state.remoteEnable.fullRemoteControl != null) {
            stateMap.fullRemoteControl = state.remoteEnable.fullRemoteControl ? "enabled" : "disabled"
        }
        if (state.remoteEnable.smartGrid != null) {
            stateMap.smartGrid = state.remoteEnable.smartGrid ? "enabled" : "disabled"
        }
        if (state.remoteEnable.mobileStart != null) {
            stateMap.mobileStart = state.remoteEnable.mobileStart ? "enabled" : "disabled"
        }
    }
    
    // Light state
    if (state.light != null) {
        stateMap.lightSwitch = state.light ? "on" : "off"
    }
    
    // Energy consumption data
    if (state.ecoFeedback) {
        // Current consumption during operation
        if (state.ecoFeedback.currentEnergyConsumption?.value != null) {
            stateMap.energyConsumption = state.ecoFeedback.currentEnergyConsumption.value
        }
        if (state.ecoFeedback.currentWaterConsumption?.value != null) {
            stateMap.waterConsumption = state.ecoFeedback.currentWaterConsumption.value
        }
        // Forecast values
        if (state.ecoFeedback.energyForecast != null) {
            stateMap.energyForecast = state.ecoFeedback.energyForecast
        }
        if (state.ecoFeedback.waterForecast != null) {
            stateMap.waterForecast = state.ecoFeedback.waterForecast
        }
    }
    
    // Filter out null values
    return stateMap.findAll { it.value != null }
}

private void handleEventStreamError(String errorMsg) {
    log.error "Event stream error: ${errorMsg}"
    state.sseConnectionState = "error"
    state.sseRetryCount = (state.sseRetryCount ?: 0) + 1
    
    // Handle 404 errors - SSE may not be available
    if (errorMsg.contains("Not Found")) {
        log.warn "⚠️ SSE endpoint not available (404 Not Found)"
        log.warn "This is normal for some Miele API accounts/versions"
        log.warn "Devices will work without SSE - updates via polling instead"
        state.sseConnectionState = "unavailable"
        state.sseRetryCount = 0
        
        // Don't retry SSE if it's not available
        // Set up periodic polling as fallback (every 5 minutes)
        log.info "Setting up periodic polling (every 5 minutes) as fallback"
        runIn(300, "pollDeviceStates")
        return
    }
    
    // For other errors, retry with backoff
    if (state.sseRetryCount <= 3) {
        // Exponential backoff: 30s, 60s, 120s
        Integer delaySeconds = Math.min(30 * Math.pow(2, state.sseRetryCount - 1), 120) as Integer
        log.info "Reconnecting event stream in ${delaySeconds} seconds (attempt ${state.sseRetryCount}/3)"
        runIn(delaySeconds, "startEventStream")
    } else {
        log.warn "⚠️ Event stream failed after 3 attempts"
        log.warn "Setting up periodic polling as fallback"
        state.sseConnectionState = "unavailable"
        
        // Set up periodic polling as fallback (every 5 minutes)
        runIn(300, "pollDeviceStates")
    }
}

// Command Proxy
def childDriverCommand(String deviceId, String commandName, Map commandBody) {
    log.debug "Received command '${commandName}' for device ${deviceId}"
    
    if (!state.accessToken) {
        log.error "Cannot execute command - no access token available"
        return false
    }
    
    if (!commandBody) {
        log.error "No command body provided for '${commandName}'"
        return false
    }
    
    // Add API compatibility warning for program control commands
    if (commandName in ["startProgram", "pauseProgram", "stopProgram", "resumeProgram"]) {
        log.info "⚠️ API Compatibility: If this command fails, your appliance may be a next-generation model with limited API support until Fall 2025"
    }
    
    log.info "Sending command '${commandName}' to device ${deviceId}: ${commandBody}"
    authenticatedHttpPut("${MIELE_DEVICES_PATH}/${deviceId}/actions", commandBody, "commandResponseHandler", [deviceId: deviceId, command: commandName])
    return true
}



def commandResponseHandler(hubitat.scheduling.AsyncResponse response, Map data) {
    String deviceId = data?.deviceId ?: "unknown"
    String command = data?.command ?: "unknown"
    
    try {
        if (response.hasError()) {
            String errorMsg = response.getErrorMessage()
            log.error "Error sending command '${command}' to device ${deviceId}: ${errorMsg}"
            
            // Provide specific guidance for common API limitations
            if (errorMsg.contains("404") || errorMsg.contains("Not Found")) {
                log.warn "💡 This may indicate your appliance is a next-generation model with limited API endpoints"
                log.warn "📅 Full API support for next-generation washers/dryers is planned for Fall 2025"
            } else if (errorMsg.contains("405") || errorMsg.contains("Method Not Allowed")) {
                log.warn "💡 This command may not be supported by your appliance model or current state"
            } else if (errorMsg.contains("400") || errorMsg.contains("Bad Request")) {
                if (command == "startProgram") {
                    log.warn "💡 Miele API returned 'Bad Request' but the command may have worked - check if the program actually started/resumed"
                } else {
                    log.warn "💡 Command format may be incorrect or appliance not in correct state for this operation"
                }
            }
            return
        }
        
        if (response.status == 200 || response.status == 204) {
            log.info "Command '${command}' sent successfully to device ${deviceId}"
            
            // Parse response if available
            if (response.data) {
                try {
                    def json = new groovy.json.JsonSlurper().parseText(response.data)
                    log.debug "Command response: ${json}"
                } catch (Exception e) {
                    log.debug "Command response (non-JSON): ${response.data}"
                }
            }
        } else {
            log.error "Command '${command}' failed for device ${deviceId}: HTTP ${response.status}"
        }
    } catch (Exception e) {
        log.error "Exception handling command response: ${e.message}"
    }
}

// HTTP Helpers
private void authenticatedHttpGet(String path, String callback, Map options = [:]) {
    if (!state.accessToken) {
        log.error "Cannot make authenticated request - no access token"
        return
    }
    
    // Check if token needs refresh before making request
    // Add safety check to prevent infinite refresh loops
    if (now() >= (state.tokenExpiresAt ?: 0)) {
        Long lastRefreshAttempt = state.lastTokenRefreshAttempt ?: 0
        Long timeSinceLastAttempt = now() - lastRefreshAttempt
        
        // Only attempt refresh if we haven't tried recently
        if (timeSinceLastAttempt > (TOKEN_EXPIRY_CHECK_MIN_INTERVAL_SEC * 1000)) {
            log.warn "Token expired (expires: ${new Date(state.tokenExpiresAt ?: 0)}), attempting refresh"
            state.lastTokenRefreshAttempt = now()
            tokenRefreshJob()
            runIn(5, "retryAuthenticatedRequest", [data: [path: path, callback: callback, options: options]])
            return
        } else {
            log.error "Token refresh attempted too recently (${timeSinceLastAttempt}ms ago). Skipping to prevent loop."
            log.error "Please re-authorize the app if token refresh continues to fail."
            return
        }
    }
    
    // Build Authorization header with Bearer token
    String authHeader = "Bearer ${state.accessToken}"
    
    Map headers = [
        "Authorization": authHeader,
        "Accept": "application/json",
        "User-Agent": "Hubitat-Miele-Integration/1.0"
    ]
    
    // Log token format for debugging (first 20 chars only)
    log.debug "Authorization header: Bearer ${state.accessToken?.take(20)}..."
    
    if (options.handleSse) {
        headers["Accept"] = "text/event-stream"
        headers["Cache-Control"] = "no-cache"
        headers["Connection"] = "keep-alive"
    }
    
    Map requestParams = [
        uri: MIELE_API_BASE,
        path: path,
        headers: headers,
        timeout: options.handleSse ? 0 : 30
    ]
    
    log.info "Making authenticated GET request to: ${MIELE_API_BASE}${path}"
    
    try {
        asynchttpGet(callback, requestParams, options.data)
        log.info "HTTP GET request sent successfully, waiting for response..."
    } catch (Exception e) {
        log.error "❌ Error making HTTP GET request: ${e.message}"
        log.error "Exception details: ${e}"
        if (options.handleSse) {
            handleEventStreamError("Failed to initiate SSE connection: ${e.message}")
        }
    }
}

def retryAuthenticatedRequest(data) {
    authenticatedHttpGet(data.path, data.callback, data.options)
}

def refreshAppPage() {
    // This method exists to trigger app page refresh after OAuth completion
    log.info "OAuth completed successfully. Please refresh the app page to continue."
}

private void authenticatedHttpPut(String path, Map body, String callback, Map callbackData = [:]) {
    if (!state.accessToken) {
        log.error "Cannot make authenticated request - no access token"
        return
    }
    
    Map headers = [
        "Authorization": "Bearer ${state.accessToken}",
        "Content-Type": "application/json",
        "Accept": "application/json",
        "User-Agent": "Hubitat-Miele-Integration/1.0"
    ]
    
    String jsonBody = new groovy.json.JsonBuilder(body).toString()
    
    Map requestParams = [
        uri: MIELE_API_BASE,
        path: path,
        headers: headers,
        body: jsonBody,
        requestContentType: "application/json",
        contentType: "application/json",
        timeout: 30
    ]
    
    log.debug "Making authenticated PUT request to ${path} with body: ${jsonBody}"
    asynchttpPut(callback, requestParams, callbackData)
}

private String toQueryString(Map params) {
    return params.collect { k, v -> "${k}=${java.net.URLEncoder.encode(v.toString())}" }.join("&")
}

// Helper Methods for Data Validation and Processing
private boolean isValidTemperature(Integer value) {
    return value != null && value != TEMP_PLACEHOLDER_VALUE
}

private boolean isValidValue(Integer value) {
    return value != null && value != TEMP_PLACEHOLDER_VALUE
}

private boolean isValidString(String value) {
    return value != null && !value.trim().isEmpty()
}

private Integer convertTimeArrayToMinutes(List timeArray) {
    if (!timeArray || timeArray.size() < 2) return null
    try {
        Integer hours = timeArray[0] as Integer
        Integer minutes = timeArray[1] as Integer
        return (hours * 60) + minutes
    } catch (Exception e) {
        log.warn "Error converting time array ${timeArray}: ${e.message}"
        return null
    }
}

private String formatTimeArray(List timeArray) {
    if (!timeArray || timeArray.size() < 2) return null
    try {
        Integer hours = timeArray[0] as Integer
        Integer minutes = timeArray[1] as Integer
        return String.format("%02d:%02d", hours, minutes)
    } catch (Exception e) {
        log.warn "Error formatting time array ${timeArray}: ${e.message}"
        return null
    }
}

private void safeRunIn(Integer seconds, String method, Map options = [:]) {
    try {
        runIn(seconds, method, options)
    } catch (Exception e) {
        log.error "Error scheduling ${method}: ${e.message}"
    }
}

void log(String level, String msg) {
    String currentLogLevel = settings?.logLevel ?: "Info"
    if (currentLogLevel == "Trace" || (currentLogLevel == "Debug" && level != "Trace") || (currentLogLevel == "Info" && (level == "Info" || level == "Warn" || level == "Error")) || (currentLogLevel == "Warn" && (level == "Warn" || level == "Error")) || (currentLogLevel == "Error" && level == "Error")) {
        log."${level.toLowerCase()}"(msg)
    }
}

// ========================================
// UNIVERSAL DRIVER METHODS - Single Source of Truth
// ========================================

// All common functionality is centralized here to eliminate code duplication
// Child drivers call these methods instead of implementing their own

// Universal Logging Methods
def driverLogDebug(String deviceName, String message, Boolean logEnable) {
    if (logEnable) log.debug("[${deviceName}] ${message}")
}

def driverLogInfo(String deviceName, String message) {
    log.info("[${deviceName}] ${message}")
}

def driverLogWarn(String deviceName, String message) {
    log.warn("[${deviceName}] ${message}")
}

def driverLogError(String deviceName, String message) {
    log.error("[${deviceName}] ${message}")
}

// Universal Event Handling
def driverSendEventSafe(childDevice, String name, value, Map options = [:]) {
    try {
        Map eventMap = [name: name, value: value] + options
        childDevice.sendEvent(eventMap)
        return true
    } catch (Exception e) {
        log.error("[${childDevice.displayName}] Failed to send event ${name}: ${e.message}")
        return false
    }
}

// Universal Command Execution with Retry Logic
def driverExecuteCommand(String deviceId, String command, Map commandBody, Integer maxRetries = 3) {
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            if (childDriverCommand(deviceId, command, commandBody)) {
                log.debug("[${deviceId}] Command ${command} executed successfully")
                return true
            }
        } catch (Exception e) {
            log.error("[${deviceId}] Command ${command} attempt ${attempt} failed: ${e.message}")
            if (attempt < maxRetries) {
                pauseExecution(1000 * attempt) // Exponential backoff
            }
        }
    }
    
    log.error("[${deviceId}] Command ${command} failed after ${maxRetries} attempts")
    return false
}

// Universal Attribute Checking
def driverHasAttributeSafe(childDevice, String attrName) {
    try {
        return childDevice.hasAttribute(attrName)
    } catch (Exception e) {
        log.debug("[${childDevice.displayName}] Error checking attribute ${attrName}: ${e.message}")
        return false
    }
}

// Universal Power Estimation
def driverUpdatePowerEstimation(childDevice, String operationState, Map powerStates, Boolean powerEstimation) {
    if (!powerEstimation) return
    
    try {
        String lowerState = operationState?.toLowerCase() ?: "unknown"
        Integer power = powerStates[lowerState] ?: 0
        
        // Handle special cases for more accurate estimation
        if (lowerState.contains("heating")) {
            power = powerStates["heating"] ?: power
        } else if (lowerState.contains("spinning")) {
            power = powerStates["spinning"] ?: power
        }
        
        Integer currentPower = childDevice.currentValue("power") as Integer
        if (currentPower != power) {
            driverSendEventSafe(childDevice, "power", power, [unit: "W"])
        }
    } catch (Exception e) {
        log.error("[${childDevice.displayName}] Error updating power estimation: ${e.message}")
    }
}

// Universal State Processing
def driverProcessState(childDevice, Map stateData, Map deviceConfig) {
    if (!stateData) {
        driverLogWarn(childDevice.displayName, "Received null or empty state data")
        return
    }
    
    driverLogDebug(childDevice.displayName, "Processing state update: ${stateData}", deviceConfig.logEnable)
    
    try {
        String previousOperationState = childDevice.currentValue("operationState")
        
        // Process each state update with individual error handling
        stateData.each { String key, value ->
            driverProcessStateAttribute(childDevice, key, value, previousOperationState, deviceConfig)
        }
        
        // Update last update timestamp
        driverSendEventSafe(childDevice, "lastUpdate", new Date().format("yyyy-MM-dd HH:mm:ss"))
        
    } catch (Exception e) {
        driverLogError(childDevice.displayName, "Error processing state update: ${e.message}")
    }
}

// Universal State Attribute Processing
def driverProcessStateAttribute(childDevice, String key, value, String previousOperationState, Map deviceConfig) {
    if (value == null) return
    
    try {
        switch (key) {
            case "remainingTime":
                driverHandleTimeAttribute(childDevice, key, value, "min", 5, deviceConfig)
                break
                
            case "elapsedTime":
                driverHandleTimeAttribute(childDevice, key, value, "min", 0, deviceConfig)
                break
                
            case "operationState":
                driverHandleOperationStateChange(childDevice, value, previousOperationState, deviceConfig)
                break
                
            case "programName":
            case "programPhase":
                driverHandleStringAttributeWithLogging(childDevice, key, value, deviceConfig)
                break
                
            case "drynessLevel":
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
                driverHandleStringAttribute(childDevice, key, value)
                break
                
            case "targetTemperature":
            case "currentTemperature":
            case "coreTemperature":
                driverHandleTemperatureAttribute(childDevice, key, value)
                break
                
            case "energyConsumption":
                driverHandleEnergyAttribute(childDevice, value, deviceConfig)
                break
                
            case "waterConsumption":
                driverHandleNumericAttribute(childDevice, key, value, "L")
                break
                
            case "spinSpeed":
                driverHandleNumericAttribute(childDevice, key, value, "rpm")
                break
                
            case "programId":
            case "ProgramID":
                driverHandleProgramId(childDevice, key, value)
                break
                
            default:
                driverHandleGenericAttribute(childDevice, key, value)
                break
        }
    } catch (Exception e) {
        driverLogError(childDevice.displayName, "Error processing ${key}: ${e.message}")
    }
}

// Universal State Handlers
def driverHandleOperationStateChange(childDevice, String newState, String previousState, Map deviceConfig) {
    if (previousState != newState) {
        driverSendEventSafe(childDevice, "operationState", newState)
        driverUpdateSwitchFromOperationState(childDevice, newState)
        
        // Clear elapsed time when program finishes or device is off
        driverHandleStateClearingLogic(childDevice, newState, previousState)
        
        // Update power estimation if enabled
        if (deviceConfig.powerEstimation && deviceConfig.powerStates) {
            driverUpdatePowerEstimation(childDevice, newState, deviceConfig.powerStates, true)
        }
        
        if (deviceConfig.txtEnable) {
            driverLogInfo(childDevice.displayName, "Operation state: ${previousState} → ${newState}")
        }
    }
}

def driverHandleStateClearingLogic(childDevice, String newState, String previousState) {
    String lowerNewState = newState?.toLowerCase() ?: ""
    String lowerPreviousState = previousState?.toLowerCase() ?: ""
    
    // Clear elapsed time when program finishes or device goes off
    if (lowerNewState.contains("off") || 
        lowerNewState.contains("not connected") ||
        lowerNewState.contains("end") ||
        lowerNewState.contains("finished") ||
        lowerNewState.contains("programme selected") ||
        (lowerPreviousState.contains("running") && lowerNewState.contains("waiting"))) {
        
        log.debug "[${childDevice.displayName}] Clearing elapsed time due to state change: ${previousState} → ${newState}"
        driverSendEventSafe(childDevice, "elapsedTime", 0, [unit: "min"])
    }
    
    // Clear remaining time when device is off
    if (lowerNewState.contains("off") || lowerNewState.contains("not connected")) {
        driverSendEventSafe(childDevice, "remainingTime", 0, [unit: "min"])
    }
}

def driverUpdateSwitchFromOperationState(childDevice, String operationState) {
    String switchState = driverDetermineSwitchState(operationState)
    String currentSwitch = childDevice.currentValue("switch")
    
    if (currentSwitch != switchState) {
        driverSendEventSafe(childDevice, "switch", switchState)
    }
}

def driverDetermineSwitchState(String operationState) {
    if (!operationState) return "unknown"
    
    String lowerState = operationState.toLowerCase()
    
    if (lowerState.contains("running") || lowerState.contains("in use") || lowerState.contains("heating") || 
        lowerState.contains("cooling") || lowerState.contains("pause") || lowerState.contains("programmed")) {
        return "on"
    } else if (lowerState.contains("off") || lowerState.contains("not connected") || lowerState.contains("failure")) {
        return "off"
    } else {
        return "unknown"
    }
}

def driverHandleTimeAttribute(childDevice, String key, Integer value, String unit, Integer threshold, Map deviceConfig) {
    Integer currentValue = childDevice.currentValue(key) as Integer
    if (currentValue != value) {
        driverSendEventSafe(childDevice, key, value, [unit: unit])
        
        if (threshold > 0 && deviceConfig.txtEnable && Math.abs((currentValue ?: 0) - value) > threshold) {
            driverLogInfo(childDevice.displayName, "${key}: ${value} ${unit}")
        }
    }
}

def driverHandleNumericAttribute(childDevice, String key, value, String unit) {
    def currentValue = childDevice.currentValue(key)
    if (currentValue != value) {
        driverSendEventSafe(childDevice, key, value, [unit: unit])
    }
}

def driverHandleStringAttribute(childDevice, String key, String value) {
    String currentValue = childDevice.currentValue(key)
    if (currentValue != value) {
        driverSendEventSafe(childDevice, key, value)
    }
}

def driverHandleStringAttributeWithLogging(childDevice, String key, String value, Map deviceConfig) {
    String currentValue = childDevice.currentValue(key)
    if (currentValue != value) {
        driverSendEventSafe(childDevice, key, value)
        if (deviceConfig.txtEnable) {
            driverLogInfo(childDevice.displayName, "${key}: ${value}")
        }
    }
}

def driverHandleTemperatureAttribute(childDevice, String key, value) {
    try {
        // Handle different value types (Integer, Long, Double)
        Integer tempValue = value as Integer
        Integer currentTemp = childDevice.currentValue(key) as Integer
        
        if (currentTemp != tempValue && tempValue != TEMP_PLACEHOLDER_VALUE) {
            driverSendEventSafe(childDevice, key, tempValue, [unit: "°C"])
        }
    } catch (Exception e) {
        driverLogError(childDevice.displayName, "Error handling temperature ${key}: ${e.message}")
    }
}

def driverHandleEnergyAttribute(childDevice, Double value, Map deviceConfig) {
    if (deviceConfig.energyTracking) {
        Double currentEnergy = childDevice.currentValue("energy") as Double
        if (currentEnergy != value) {
            driverSendEventSafe(childDevice, "energy", value, [unit: "kWh"])
        }
    }
}

def driverHandleProgramId(childDevice, String key, value) {
    try {
        // Handle program ID from device state
        String programId = null
        String programName = null
        
        log.debug "[${childDevice.displayName}] Processing program data - Key: ${key}, Value: ${value}"
        
        if (value instanceof Map) {
            programId = value.value_raw?.toString()
            programName = value.value_localized?.toString()
            log.debug "[${childDevice.displayName}] Extracted from Map - ID: ${programId}, Name: ${programName}"

        } else {
            programId = value?.toString()
            log.debug "[${childDevice.displayName}] Program ID from string: ${programId}"
        }
        
        // Update program ID if we have it
        if (programId) {
            String currentProgramId = childDevice.currentValue("selectedProgramId")
            if (currentProgramId != programId) {
                driverSendEventSafe(childDevice, "selectedProgramId", programId)
                log.info "[${childDevice.displayName}] Updated program ID: ${programId}"
            }
        }
        
        // Update program name if we have it
        if (programName) {
            String currentProgramName = childDevice.currentValue("selectedProgram")
            if (currentProgramName != programName) {
                driverSendEventSafe(childDevice, "selectedProgram", programName)
                log.info "[${childDevice.displayName}] Updated program name: ${programName}"
            }
        } else if (programId) {
            // Try to get program name from available programs
            try {
                if (childDevice.hasCommand("getAvailablePrograms")) {
                    Map availablePrograms = childDevice.getAvailablePrograms()
                    String name = availablePrograms[programId] ?: "Program ${programId}"
                    String currentProgramName = childDevice.currentValue("selectedProgram")
                    if (currentProgramName != name) {
                        driverSendEventSafe(childDevice, "selectedProgram", name)
                        log.info "[${childDevice.displayName}] Updated program name from available programs: ${name}"
                    }
                }
            } catch (Exception e) {
                log.debug "[${childDevice.displayName}] Could not get available programs: ${e.message}"
            }
        }
        
    } catch (Exception e) {
        driverLogError(childDevice.displayName, "Error handling program data: ${e.message}")
    }
}

def driverHandleGenericAttribute(childDevice, String key, value) {
    if (driverHasAttributeSafe(childDevice, key)) {
        String currentValue = childDevice.currentValue(key)
        if (currentValue != value?.toString()) {
            driverSendEventSafe(childDevice, key, value)
        }
    }
}

// ========================================
// PROGRAM MANAGEMENT METHODS
// ========================================

// Get available programs for a device
def getDevicePrograms(String deviceId) {
    if (!state.accessToken) {
        log.error "Cannot get programs - no access token available"
        return null
    }
    
    String endpoint = "${MIELE_PROGRAMS_PATH}/${deviceId}/programs"
    log.info "=== Fetching Programs for Device ${deviceId} ==="
    log.info "Full URL: ${MIELE_API_BASE}${endpoint}"
    log.info "Authorization: Bearer ${state.accessToken?.take(20)}..."
    log.info "Device ID: ${deviceId}"
    
    authenticatedHttpGet(endpoint, "programsResponseHandler", [deviceId: deviceId])
}

def programsResponseHandler(hubitat.scheduling.AsyncResponse response, Map data) {
    String deviceId = data?.deviceId ?: "unknown"
    
    try {
        if (response.hasError()) {
            String errorMsg = response.getErrorMessage()
            log.error "=== Programs API Error for Device ${deviceId} ==="
            log.error "HTTP Status: ${response.status}"
            log.error "Error Message: ${errorMsg}"
            log.error "Response Data: ${response.data}"
            log.error "Request URL: ${MIELE_API_BASE}${MIELE_PROGRAMS_PATH}/${deviceId}/programs"
            
            // Provide specific guidance for program fetch errors
            if (errorMsg.contains("400") || errorMsg.contains("Bad Request")) {
                log.warn "💡 Programs endpoint may not be available for your appliance model"
                log.warn "📋 This is common with next-generation devices - program selection may still work with known program IDs"
                
                // Create a basic program map based on common Miele programs
                def child = getChildDevice(deviceId)
                if (child) {
                    Map basicPrograms = [
                        "1": "Cottons",
                        "2": "Delicates", 
                        "3": "Quick Wash",
                        "4": "Eco 40-60",
                        "5": "Synthetics"
                    ]
                    child.updateAvailablePrograms(basicPrograms)
                    log.info "Using basic program list for device ${deviceId}: ${basicPrograms}"
                }
            } else if (errorMsg.contains("404") || errorMsg.contains("Not Found")) {
                log.warn "💡 Programs endpoint not found - your appliance may not support program listing"
            }
            return
        }
        
        if (response.status == 200) {
            def programs = new groovy.json.JsonSlurper().parseText(response.data)
            log.info "Available programs for device ${deviceId}: ${programs}"
            
            // Store programs in device state
            def child = getChildDevice(deviceId)
            if (child) {
                // Convert programs to a more usable format
                Map programMap = [:]
                programs.each { programId, programData ->
                    String programName = programData?.name ?: programData?.value_localized ?: "Program ${programId}"
                    programMap[programId] = programName
                }
                
                // Send programs to device
                child.updateAvailablePrograms(programMap)
                log.info "Updated available programs for device ${deviceId}: ${programMap}"
            }
        } else {
            log.error "Failed to fetch programs for device ${deviceId}: HTTP ${response.status}"
            if (response.data) {
                log.debug "Response data: ${response.data}"
            }
        }
    } catch (Exception e) {
        log.error "Exception handling programs response: ${e.message}"
    }
}

// Set/start a specific program on a device
def setDeviceProgram(String deviceId, String programId, Map programOptions = [:]) {
    if (!state.accessToken) {
        log.error "Cannot set program - no access token available"
        return false
    }
    
    // Convert programId to integer for API
    Integer programIdInt = programId as Integer
    
    // Create device-specific program body
    Map programBody = [programId: programIdInt]
    
    // Add device-specific options if provided
    if (programOptions) {
        programBody.putAll(programOptions)
    }
    
    log.info "Setting program ${programIdInt} for device ${deviceId}: ${programBody}"
    authenticatedHttpPut("${MIELE_PROGRAMS_PATH}/${deviceId}/programs", programBody, "setProgramResponseHandler", [deviceId: deviceId, programId: programId])
    return true
}

// Device-specific program setting methods
def setWasherProgram(String deviceId, String programId) {
    // Washers only need programId
    Map programBody = [programId: programId as Integer]
    return setDeviceProgramWithBody(deviceId, programBody)
}

def setOvenProgram(String deviceId, String programId, Integer temperature = null, List duration = null) {
    // Ovens can have programId, temperature, and duration
    Map programBody = [programId: programId as Integer]
    
    if (temperature != null) {
        programBody.temperature = temperature
    }
    
    if (duration != null && duration.size() == 2) {
        programBody.duration = duration  // [hours, minutes]
    }
    
    return setDeviceProgramWithBody(deviceId, programBody)
}

def setDeviceProgramWithBody(String deviceId, Map programBody) {
    if (!state.accessToken) {
        log.error "Cannot set program - no access token available"
        return false
    }
    
    log.info "Setting program for device ${deviceId}: ${programBody}"
    authenticatedHttpPut("${MIELE_PROGRAMS_PATH}/${deviceId}/programs", programBody, "setProgramResponseHandler", [deviceId: deviceId, programId: programBody.programId])
    return true
}

def setProgramResponseHandler(hubitat.scheduling.AsyncResponse response, Map data) {
    String deviceId = data?.deviceId ?: "unknown"
    String programId = data?.programId ?: "unknown"
    
    try {
        if (response.hasError()) {
            String errorMsg = response.getErrorMessage()
            log.error "Error setting program ${programId} for device ${deviceId}: ${errorMsg}"
            
            // Provide specific guidance for program setting errors
            if (errorMsg.contains("404") || errorMsg.contains("Not Found")) {
                log.warn "💡 Program endpoint may not be available for your appliance model"
            } else if (errorMsg.contains("400") || errorMsg.contains("Bad Request")) {
                log.warn "💡 Program ID may be invalid or appliance not ready for program selection"
            }
            return
        }
        
        if (response.status == 200 || response.status == 204) {
            log.info "Program ${programId} set successfully for device ${deviceId}"
            
            // Parse response if available
            if (response.data) {
                try {
                    def json = new groovy.json.JsonSlurper().parseText(response.data)
                    log.debug "Set program response: ${json}"
                } catch (Exception e) {
                    log.debug "Set program response (non-JSON): ${response.data}"
                }
            }
        } else {
            log.error "Failed to set program ${programId} for device ${deviceId}: HTTP ${response.status}"
        }
    } catch (Exception e) {
        log.error "Exception handling set program response: ${e.message}"
    }
}

def fetchDeviceProgramsDelayed(data) {
    String deviceId = data?.deviceId
    if (deviceId) {
        getDevicePrograms(deviceId)
    }
}

// Universal Initialization
def driverInitializeDevice(childDevice, Map deviceConfig) {
    try {
        driverSendEventSafe(childDevice, "switch", "unknown")
        driverSendEventSafe(childDevice, "operationState", "Unknown")
        driverSendEventSafe(childDevice, "power", 0, [unit: "W"])
        driverSendEventSafe(childDevice, "connectionState", "Unknown")
        driverSendEventSafe(childDevice, "remainingTime", 0, [unit: "min"])
        driverSendEventSafe(childDevice, "elapsedTime", 0, [unit: "min"])
        driverSendEventSafe(childDevice, "targetTemperature", TEMP_PLACEHOLDER_VALUE, [unit: "°C"])
        driverSendEventSafe(childDevice, "currentTemperature", TEMP_PLACEHOLDER_VALUE, [unit: "°C"])
        
        // Initialize energy tracking if enabled
        if (deviceConfig.energyTracking && !childDevice.currentValue("energy")) {
            driverSendEventSafe(childDevice, "energy", 0, [unit: "kWh"])
        }
        
        // Device-specific initialization
        if (deviceConfig.deviceType == "Washer") {
            driverSendEventSafe(childDevice, "spinSpeed", 0, [unit: "rpm"])
            driverSendEventSafe(childDevice, "waterConsumption", 0, [unit: "L"])
        } else if (deviceConfig.deviceType == "Dishwasher") {
            driverSendEventSafe(childDevice, "waterConsumption", 0, [unit: "L"])
        }
        
    } catch (Exception e) {
        driverLogError(childDevice.displayName, "Error initializing device: ${e.message}")
    }
}

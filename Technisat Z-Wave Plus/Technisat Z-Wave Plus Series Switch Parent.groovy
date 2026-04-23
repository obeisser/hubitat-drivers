/*
 * Technisat Smart Home Serienschalter (Parent Driver)
 * Designed for Hubitat Elevation C-8
 *
 * Capabilities: Buttons (Push, Hold, Release, Double Tap), Multi-Tap, Metering, Multi-Channel Child Support
 */

import groovy.transform.Field

@Field static Map commandClassVersions = [
    0x20: 1,    // Basic
    0x25: 1,    // Switch Binary
    0x32: 3,    // Meter
    0x5A: 1,    // Device Reset Locally
    0x5B: 3,    // Central Scene (v3 supports keyAttributes 0-6 / up to 5x taps)
    0x59: 1,    // Association Group Info
    0x60: 3,    // Multi Channel
    0x6C: 1,    // Supervision
    0x70: 1,    // Configuration
    0x71: 4,    // Notification
    0x72: 2,    // Manufacturer Specific
    0x85: 2,    // Association
    0x86: 2,    // Version
    0x8E: 3     // Multi Channel Association
]

// Command class names for the version report state variable
@Field static Map CC_NAMES = [
    0x20: "Basic",                  0x25: "Switch Binary",
    0x32: "Meter",                  0x5A: "Device Reset Locally",
    0x5B: "Central Scene",          0x59: "Association Group Info",
    0x60: "Multi Channel",          0x6C: "Supervision",
    0x70: "Configuration",          0x71: "Notification",
    0x72: "Manufacturer Specific",  0x85: "Association",
    0x86: "Version",                0x8E: "Multi Channel Association",
    0x55: "Transport Service",      0x98: "Security",
    0x9F: "Security 2",             0x73: "Powerlevel",
    0x7A: "Firmware Update",        0x8F: "Multi Instance"
]

metadata {
    definition (name: "Technisat Z-Wave Plus Series Switch Parent", namespace: "obeisser", author: "obeisser") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "PushableButton"
        capability "HoldableButton"
        capability "ReleasableButton"
        capability "DoubleTapableButton"

        // Custom attribute supporting [Button].[TapCount] format (e.g., "4.2")
        attribute "multiTapButton", "STRING"
        attribute "powerNotification", "NUMBER"

        command "push", ["NUMBER"]
        command "hold", ["NUMBER"]
        command "release", ["NUMBER"]
        command "doubleTap", ["NUMBER"]
        command "multiTap", [
            [name:"Button Number*", type: "NUMBER", description: "Enter 1, 2, 3, or 4"],
            [name:"Number of Taps*",  type: "NUMBER", description: "Enter 2, 3, 4, or 5"]
        ]
        command "recreateChildDevices"
        command "getDeviceInfo"

        fingerprint mfr: "0299", prod: "0003", deviceId: "1A91",
            inClusters: "0x5E,0x25,0x32,0x55,0x59,0x5A,0x5B,0x6C,0x70,0x71,0x72,0x73,0x7A,0x85,0x86,0x98,0x9F,0x60,0x8E",
            deviceJoinName: "Technisat Series Switch"
    }

    preferences {
        input name: "param1",     type: "enum",   title: "(1) Central Scene notifications (2x-5x press)", options:[0:"Disable", 1:"Enable"], defaultValue: 1
        input name: "param2",     type: "number", title: "(2) Current wattage meter report interval (in 10s)",   range: "0..8640",  defaultValue: 6
        input name: "param3",     type: "number", title: "(3) Active energy meter report interval (in minutes)", range: "0..30240", defaultValue: 10
        input name: "logEnable",  type: "bool",   title: "Enable debug logging",       defaultValue: true
        input name: "txtEnable",  type: "bool",   title: "Enable descriptionText logging", defaultValue: true
    }
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

void logsOff() {
    log.warn "Debug logging disabled."
    device.updateSetting("logEnable", [value:"false", type:"bool"])
}

void installed() {
    log.warn "Installed."
    sendEvent(name: "numberOfButtons", value: 4)
    configure()
}

void configure() {
    log.warn "Configuring."
    if (logEnable) runIn(1800, logsOff)
    sendEvent(name: "numberOfButtons", value: 4)
    recreateChildDevices()
    sendToDevice(delayBetween(buildConfigCmds(), 500))
    runIn(3, "getDeviceInfo")
}

void updated() {
    log.info "Updated."
    if (logEnable) runIn(1800, logsOff)
    // Child devices are managed via configure() or the manual recreateChildDevices command.
    // Do not recreate here to avoid unnecessary overhead on every preference save.
    sendToDevice(delayBetween(buildConfigCmds(), 500))
}

// Shared helper — avoids duplicating the three configurationSet calls
private List<String> buildConfigCmds() {
    return [
        secure(zwave.associationV2.associationSet(groupingIdentifier: 1, nodeId: [zwaveHubNodeId])),
        secure(zwave.configurationV1.configurationSet(parameterNumber: 1, size: 1, scaledConfigurationValue: param1 ? param1.toInteger() : 1)),
        secure(zwave.configurationV1.configurationSet(parameterNumber: 2, size: 2, scaledConfigurationValue: param2 ? param2.toInteger() : 6)),
        secure(zwave.configurationV1.configurationSet(parameterNumber: 3, size: 2, scaledConfigurationValue: param3 ? param3.toInteger() : 10))
    ]
}

void recreateChildDevices() {
    log.info "Checking child devices."
    for (i in 1..2) {
        String childDni  = "${device.deviceNetworkId}-${i}"
        String childName = "${device.displayName} - Switch ${i}"
        if (!getChildDevice(childDni)) {
            try {
                addChildDevice("hubitat", "Generic Component Metering Switch", childDni, [name: childName, label: childName, isComponent: true])
                log.info "Created child device for endpoint ${i}: ${childName}"
            } catch (Exception e) {
                log.error "Failed to create child device for endpoint ${i}: ${e}"
            }
        } else {
            if (logEnable) log.debug "Child ${childDni} already exists — skipping."
        }
    }
}

// ---------------------------------------------------------------------------
// Parse
// ---------------------------------------------------------------------------

void parse(String description) {
    if (logEnable) log.debug "parse: ${description}"
    def cmd = zwave.parse(description, commandClassVersions)
    if (cmd) zwaveEvent(cmd)
}

// ---------------------------------------------------------------------------
// Z-Wave Events — Root Endpoint
// ---------------------------------------------------------------------------

void zwaveEvent(hubitat.zwave.commands.centralscenev3.CentralSceneNotification cmd) {
    if (logEnable) log.debug "CentralSceneNotification: ${cmd}"

    Integer button = cmd.sceneNumber
    Integer key    = cmd.keyAttributes

    switch (key) {
        case 0:
            sendButtonEvent("pushed",      button, "pushed")
            break
        case 1:
            sendButtonEvent("released",    button, "released")
            break
        case 2:
            sendButtonEvent("held",        button, "held")
            break
        case 3:
            sendButtonEvent("doubleTapped", button, "double-tapped")
            sendMultiTapEvent(button, 2)
            break
        case 4:
            sendMultiTapEvent(button, 3)
            break
        case 5:
            sendMultiTapEvent(button, 4)
            break
        case 6:
            sendMultiTapEvent(button, 5)
            break
        default:
            if (logEnable) log.debug "CentralScene: unhandled keyAttributes ${key}"
    }
}

// Basic/SwitchBinary reports arriving at root without multi-channel encapsulation.
// We can't determine which endpoint triggered it, so refresh both to sync state.
void zwaveEvent(hubitat.zwave.commands.basicv1.BasicReport cmd) {
    if (logEnable) log.debug "Root BasicReport (non-encapsulated): ${cmd} — refreshing all endpoints"
    refresh()
}

void zwaveEvent(hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    if (logEnable) log.debug "Root SwitchBinaryReport (non-encapsulated): ${cmd} — refreshing all endpoints"
    refresh()
}

void zwaveEvent(hubitat.zwave.commands.meterv3.MeterReport cmd) {
    if (logEnable) log.debug "Root MeterReport (non-encapsulated): ${cmd} — refreshing all endpoints"
    refresh()
}

void zwaveEvent(hubitat.zwave.commands.multichannelv3.MultiChannelCmdEncap cmd) {
    def encapCmd = cmd.encapsulatedCommand(commandClassVersions)
    if (!encapCmd) {
        if (logEnable) log.debug "MultiChannelCmdEncap: could not encapsulate (missing commandClassVersions entry?)"
        return
    }
    
    // Some Z-Wave Plus devices report using destinationEndPoint instead of sourceEndPoint
    // Try sourceEndPoint first, fall back to destinationEndPoint
    def ep = cmd.sourceEndPoint ?: cmd.destinationEndPoint
    if (logEnable) log.debug "MultiChannelCmdEncap ep=${ep} (src=${cmd.sourceEndPoint}, dst=${cmd.destinationEndPoint}): ${encapCmd}"
    
    def child = getChildDevice("${device.deviceNetworkId}-${ep}")
    if (child) {
        childZwaveEvent(child, encapCmd)
    } else {
        if (logEnable) log.debug "No child for endpoint ${ep} — dropped: ${encapCmd}"
    }
}

void zwaveEvent(hubitat.zwave.Command cmd) {
    if (logEnable) log.debug "Unhandled Z-Wave command: ${cmd}"
}

// Supervision — required for Z-Wave Plus S2; acknowledge supervised frames
void zwaveEvent(hubitat.zwave.commands.supervisionv1.SupervisionGet cmd) {
    if (logEnable) log.debug "SupervisionGet: ${cmd}"
    def encapCmd = cmd.encapsulatedCommand(commandClassVersions)
    if (encapCmd) zwaveEvent(encapCmd)
    sendToDevice(secure(zwave.supervisionV1.supervisionReport(
        sessionID: cmd.sessionID, reserved: 0, moreStatusUpdates: false, status: 0xFF)))
}

// Notification — device advertises 0x71; log and handle overload/fault reports
void zwaveEvent(hubitat.zwave.commands.notificationv4.NotificationReport cmd) {
    if (logEnable) log.debug "NotificationReport: notificationType=${cmd.notificationType} event=${cmd.event}"
    // Notification type 0x08 = Power Management (e.g. overload)
    if (cmd.notificationType == 0x08) {
        String msg = "${device.displayName}: Power Management notification — event ${cmd.event}"
        log.warn msg
        sendEvent(name: "powerNotification", value: cmd.event, descriptionText: msg, isStateChange: true)
    }
}

// Device was factory-reset locally — log a prominent warning to aid mesh troubleshooting
void zwaveEvent(hubitat.zwave.commands.deviceresetlocallyv1.DeviceResetLocallyNotification cmd) {
    log.warn "${device.displayName}: Device was factory-reset locally! It may have left the Z-Wave mesh. Re-pair the device and run Configure."
}

// Configuration echo-back — logs the value the device actually stored for a parameter.
// Useful when verifying preference writes or diagnosing rejected parameter values.
void zwaveEvent(hubitat.zwave.commands.configurationv1.ConfigurationReport cmd) {
    if (logEnable) log.debug "ConfigurationReport: parameter ${cmd.parameterNumber} = ${cmd.scaledConfigurationValue}"
}

// ---------------------------------------------------------------------------
// Child Z-Wave Event Dispatch
// ---------------------------------------------------------------------------

void childZwaveEvent(def child, hubitat.zwave.commands.basicv1.BasicReport cmd) {
    routeSwitchToChild(child, cmd.value)
}

void childZwaveEvent(def child, hubitat.zwave.commands.switchbinaryv1.SwitchBinaryReport cmd) {
    routeSwitchToChild(child, cmd.value)
}

void childZwaveEvent(def child, hubitat.zwave.commands.meterv3.MeterReport cmd) {
    routeMeterToChild(child, cmd)
}

void childZwaveEvent(def child, hubitat.zwave.Command cmd) {
    if (logEnable) log.debug "Unhandled child encap from ${child.displayName}: ${cmd}"
}

// ---------------------------------------------------------------------------
// Routing Helpers
// ---------------------------------------------------------------------------

private void routeSwitchToChild(def childOrEp, Number rawValue) {
    def child = (childOrEp instanceof Integer) ? getChildDevice("${device.deviceNetworkId}-${childOrEp}") : childOrEp
    if (!child) { if (logEnable) log.debug "routeSwitchToChild: no child found for ${childOrEp}"; return }
    String value = rawValue > 0 ? "on" : "off"
    String desc  = "${child.displayName} was turned ${value}"
    if (txtEnable) log.info desc
    child.sendEvent(name: "switch", value: value, descriptionText: desc)
}

private void routeMeterToChild(def childOrEp, hubitat.zwave.commands.meterv3.MeterReport cmd) {
    def child = (childOrEp instanceof Integer) ? getChildDevice("${device.deviceNetworkId}-${childOrEp}") : childOrEp
    if (!child) { if (logEnable) log.debug "routeMeterToChild: no child found for ${childOrEp}"; return }
    // meterType 1 = Electric. scale 0 = kWh (energy), scale 2 = W (power)
    if (cmd.meterType == 1) {
        if (cmd.scale == 0) {
            child.sendEvent(name: "energy", value: cmd.scaledMeterValue, unit: "kWh")
        } else if (cmd.scale == 2) {
            child.sendEvent(name: "power",  value: cmd.scaledMeterValue, unit: "W")
        } else {
            if (logEnable) log.debug "routeMeterToChild: unhandled electric meter scale ${cmd.scale} from ${child.displayName}"
        }
    } else {
        if (logEnable) log.debug "routeMeterToChild: unhandled meterType ${cmd.meterType} from ${child.displayName}"
    }
}

// ---------------------------------------------------------------------------
// Button Event Helpers
// ---------------------------------------------------------------------------

private void sendButtonEvent(String action, Integer button, String label, String type = "physical") {
    String desc = "${device.displayName} Button ${button} ${label}."
    if (txtEnable) log.info desc
    sendEvent(name: action, value: button, descriptionText: desc, isStateChange: true, type: type)
}

private void sendMultiTapEvent(Integer button, Integer taps, String type = "physical") {
    String value = "${button}.${taps}"
    String desc  = "${device.displayName} MultiTapButton ${value}"
    if (txtEnable) log.info desc
    sendEvent(name: "multiTapButton", value: value, descriptionText: desc, isStateChange: true, type: type)
}

// ---------------------------------------------------------------------------
// Digital Button Commands
// ---------------------------------------------------------------------------

void push(def button) {
    Integer btn = button?.toInteger() ?: 1
    if (btn < 1 || btn > 4) { log.warn "push: invalid button ${btn} (must be 1-4)"; return }
    sendButtonEvent("pushed", btn, "pushed", "digital")
}

void hold(def button) {
    Integer btn = button?.toInteger() ?: 1
    if (btn < 1 || btn > 4) { log.warn "hold: invalid button ${btn} (must be 1-4)"; return }
    sendButtonEvent("held", btn, "held", "digital")
}

void release(def button) {
    Integer btn = button?.toInteger() ?: 1
    if (btn < 1 || btn > 4) { log.warn "release: invalid button ${btn} (must be 1-4)"; return }
    sendButtonEvent("released", btn, "released", "digital")
}

void doubleTap(def button) {
    Integer btn = button?.toInteger() ?: 1
    if (btn < 1 || btn > 4) { log.warn "doubleTap: invalid button ${btn} (must be 1-4)"; return }
    sendButtonEvent("doubleTapped", btn, "double-tapped", "digital")
    sendMultiTapEvent(btn, 2, "digital")
}

void multiTap(def button, def taps) {
    Integer btn = button?.toInteger() ?: 0
    Integer tps = taps?.toInteger()   ?: 0

    if (btn < 1 || btn > 4) { log.warn "multiTap: invalid button ${btn} (must be 1-4)"; return }
    if (tps < 2 || tps > 5) { log.warn "multiTap: invalid tap count ${tps} (must be 2-5)"; return }

    sendMultiTapEvent(btn, tps, "digital")
    if (tps == 2) sendButtonEvent("doubleTapped", btn, "double-tapped", "digital")
}

// ---------------------------------------------------------------------------
// Refresh
// ---------------------------------------------------------------------------

void refresh() {
    List<String> cmds = []
    for (i in 1..2) {
        cmds.add(secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: i).encapsulate(zwave.switchBinaryV1.switchBinaryGet())))
        cmds.add(secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: i).encapsulate(zwave.meterV3.meterGet(scale: 0))))
        cmds.add(secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: i).encapsulate(zwave.meterV3.meterGet(scale: 2))))
    }
    sendToDevice(delayBetween(cmds, 200))
}

// ---------------------------------------------------------------------------
// Component Commands (from Child Devices)
// ---------------------------------------------------------------------------

void componentOn(def child) {
    int ep = childEp(child)
    sendToDevice(delayBetween([
        secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(zwave.switchBinaryV1.switchBinarySet(switchValue: 0xFF))),
        secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(zwave.switchBinaryV1.switchBinaryGet()))
    ], 200))
}

void componentOff(def child) {
    int ep = childEp(child)
    sendToDevice(delayBetween([
        secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(zwave.switchBinaryV1.switchBinarySet(switchValue: 0x00))),
        secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(zwave.switchBinaryV1.switchBinaryGet()))
    ], 200))
}

void componentRefresh(def child) {
    int ep = childEp(child)
    sendToDevice(delayBetween([
        secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(zwave.switchBinaryV1.switchBinaryGet())),
        secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(zwave.meterV3.meterGet(scale: 0))),
        secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(zwave.meterV3.meterGet(scale: 2)))
    ], 200))
}

void componentReset(def child) {
    int ep = childEp(child)
    if (logEnable) log.debug "Resetting energy meter for endpoint ${ep}"
    sendToDevice(secure(zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: ep).encapsulate(zwave.meterV3.meterReset())))
}

// ---------------------------------------------------------------------------
// Device Info — queries firmware, manufacturer, CC versions, and associations
// ---------------------------------------------------------------------------

void getDeviceInfo() {
    log.info "Requesting device info (firmware, manufacturer, CC versions, associations)."
    List<String> cmds = [
        secure(zwave.versionV2.versionGet()),
        secure(zwave.manufacturerSpecificV2.manufacturerSpecificGet()),
        secure(zwave.associationV2.associationGet(groupingIdentifier: 1)),
        secure(zwave.multiChannelAssociationV3.multiChannelAssociationGet(groupingIdentifier: 1))
    ]
    // Request version for each supported CC so we know what the device actually implements
    commandClassVersions.each { cc, ver ->
        cmds << secure(zwave.versionV2.versionCommandClassGet(requestedCommandClass: cc))
    }
    sendToDevice(delayBetween(cmds, 200))
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionReport cmd) {
    if (logEnable) log.debug "VersionReport: ${cmd}"
    String fw = "${cmd.firmware0Version}.${String.format('%02d', cmd.firmware0SubVersion)}"
    String proto = "${cmd.zWaveProtocolVersion}.${String.format('%02d', cmd.zWaveProtocolSubVersion)}"
    state.firmware          = fw
    state.zWaveProtocol     = proto
    state.hardwareVersion   = cmd.hardwareVersion ?: "N/A"
    if (txtEnable) log.info "${device.displayName}: Firmware ${fw}, Z-Wave protocol ${proto}"
}

void zwaveEvent(hubitat.zwave.commands.versionv2.VersionCommandClassReport cmd) {
    if (logEnable) log.debug "VersionCommandClassReport: CC=0x${Integer.toHexString(cmd.requestedCommandClass).toUpperCase()} v${cmd.commandClassVersion}"
    if (cmd.commandClassVersion == 0) return   // 0 = not supported by device
    String ccHex  = "0x${Integer.toHexString(cmd.requestedCommandClass).toUpperCase().padLeft(2,'0')}"
    String ccName = CC_NAMES[cmd.requestedCommandClass] ?: ccHex
    Map ccVersions = state.ccVersions ?: [:]
    ccVersions["${ccName} (${ccHex})"] = cmd.commandClassVersion
    state.ccVersions = ccVersions.sort()
}

void zwaveEvent(hubitat.zwave.commands.manufacturerspecificv2.ManufacturerSpecificReport cmd) {
    if (logEnable) log.debug "ManufacturerSpecificReport: ${cmd}"
    state.manufacturer   = "0x${Integer.toHexString(cmd.manufacturerId).toUpperCase().padLeft(4,'0')}"
    state.productType    = "0x${Integer.toHexString(cmd.productTypeId).toUpperCase().padLeft(4,'0')}"
    state.productId      = "0x${Integer.toHexString(cmd.productId).toUpperCase().padLeft(4,'0')}"
    if (txtEnable) log.info "${device.displayName}: Manufacturer ${state.manufacturer}, ProductType ${state.productType}, ProductId ${state.productId}"
}

void zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
    if (logEnable) log.debug "AssociationReport: group ${cmd.groupingIdentifier} nodes ${cmd.nodeId}"
    Map assoc = state.associations ?: [:]
    assoc["Group ${cmd.groupingIdentifier}"] = cmd.nodeId ?: []
    state.associations = assoc
}

void zwaveEvent(hubitat.zwave.commands.multichannelassociationv3.MultiChannelAssociationReport cmd) {
    if (logEnable) log.debug "MultiChannelAssociationReport: group ${cmd.groupingIdentifier} nodes ${cmd.nodeId} multiChannelNodes ${cmd.multiChannelNodeIds}"
    Map assoc = state.mcAssociations ?: [:]
    assoc["Group ${cmd.groupingIdentifier}"] = [nodes: cmd.nodeId ?: [], mcNodes: cmd.multiChannelNodeIds ?: []]
    state.mcAssociations = assoc
}

// ---------------------------------------------------------------------------
// Utility / Transport
// ---------------------------------------------------------------------------

private int childEp(def child) {
    return child?.deviceNetworkId?.split("-")?.last()?.toInteger() ?: 1
}

String secure(String cmd)                        { return zwaveSecureEncap(cmd) }
String secure(hubitat.zwave.Command cmd)         { return zwaveSecureEncap(cmd) }

void sendToDevice(List<String> cmds) {
    sendHubCommand(new hubitat.device.HubMultiAction(cmds, hubitat.device.Protocol.ZWAVE))
}

void sendToDevice(String cmd) {
    sendHubCommand(new hubitat.device.HubAction(cmd, hubitat.device.Protocol.ZWAVE))
}

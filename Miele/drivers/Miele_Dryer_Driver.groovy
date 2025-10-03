/**
 *  Miele Dryer Driver
 *
 *  Copyright 2025, Your Name
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
metadata {
    definition (name: "Miele Dryer", namespace: "your-namespace", author: "Your Name") {
        capability "Switch"
        capability "Refresh"
        capability "PowerMeter"

        attribute "operationState", "string"
        attribute "programName", "string"
        attribute "remainingTime", "number", [unit: "min"]
        attribute "drynessLevel", "string"
        attribute "programPhase", "string"

        command "startProgram"
        command "pauseProgram"
        command "stopProgram"
    }
}

def installed() {
    log.info "Installed Miele Dryer Driver"
}

def on() {
    parent.childDriverCommand(device.deviceNetworkId, "on")
}

def off() {
    parent.childDriverCommand(device.deviceNetworkId, "off")
}

def refresh() {
    parent.discoverDevices()
}

def startProgram() {
    parent.childDriverCommand(device.deviceNetworkId, "startProgram")
}

def pauseProgram() {
    parent.childDriverCommand(device.deviceNetworkId, "pauseProgram")
}

def stopProgram() {
    parent.childDriverCommand(device.deviceNetworkId, "stopProgram")
}

def receiveMieleState(Map stateData) {
    log.debug "Receiving state: ${stateData}"
    stateData.each { String key, value ->
        if (hasAttribute(key)) {
            sendEvent(name: key, value: value)
        }
    }
    if (stateData.operationState) {
        def power = stateData.operationState == "In use" ? 1500 : 0
        sendEvent(name: "power", value: power, unit: "W")
    }
}

private boolean hasAttribute(String attrName) {
    try {
        device.hasAttribute(attrName)
        return true
    } catch (e) {
        return false
    }
}

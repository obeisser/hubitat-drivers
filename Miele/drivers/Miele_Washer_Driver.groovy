/**
 *  Miele Washer Driver
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
metadata {
    definition (name: "Miele Washer", namespace: "obeisser", author: "obeisser") {
        capability "Switch"
        capability "Refresh"
        capability "PowerMeter"
        capability "EnergyMeter"

        attribute "operationState", "string"
        attribute "programName", "string"
        attribute "remainingTime", "number"
        attribute "spinSpeed", "number"
        attribute "programPhase", "string"

        command "startProgram"
        command "pauseProgram"
        command "stopProgram"
    }
}

def installed() {
    log.info "Installed Miele Washer Driver"
}

def on() {
    parent.childDriverCommand(device.deviceNetworkId, "on")
}

def off() {
    parent.childDriverCommand(device.deviceNetworkId, "off")
}

def refresh() {
    parent.refreshDevices()
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

    if (stateData.remainingTime) {
        sendEvent(name: "remainingTime", value: stateData.remainingTime, unit: "min")
    }
    if (stateData.spinSpeed) {
        sendEvent(name: "spinSpeed", value: stateData.spinSpeed, unit: "rpm")
    }
    if (stateData.operationState) { sendEvent(name: "operationState", value: stateData.operationState) }
    if (stateData.programName) { sendEvent(name: "programName", value: stateData.programName) }
    if (stateData.programPhase) { sendEvent(name: "programPhase", value: stateData.programPhase) }

    // You can add logic here to set power consumption based on operationState
    if (stateData.operationState) {
        def power = stateData.operationState == "In use" ? 1500 : 0
        sendEvent(name: "power", value: power, unit: "W")
    }
    if (stateData.energyConsumption) {
        sendEvent(name: "energy", value: stateData.energyConsumption, unit: "kWh")
    }
}

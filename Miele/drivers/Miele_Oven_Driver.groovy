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
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 */
metadata {
    definition (name: "Miele Oven", namespace: "obeisser", author: "obeisser") {
        capability "Switch"
        capability "Light"
        capability "Thermostat"
        capability "Refresh"

        attribute "targetTemperature", "number", [unit: "C"]
        attribute "currentTemperature", "number", [unit: "C"]
        attribute "coreTemperature", "number", [unit: "C"]
        attribute "programPhase", "string"
    }
}

def installed() {
    log.info "Installed Miele Oven Driver"
}

def on() {
    parent.childDriverCommand(device.deviceNetworkId, "on")
}

def off() {
    parent.childDriverCommand(device.deviceNetworkId, "off")
}

def setLight(value) {
    parent.childDriverCommand(device.deviceNetworkId, "setLight", [value: value])
}

def setHeatingSetpoint(temp) {
    parent.childDriverCommand(device.deviceNetworkId, "setThermostatSetpoint", [temp: temp])
}

def refresh() {
    parent.discoverDevices()
}

def receiveMieleState(Map stateData) {
    log.debug "Receiving state: ${stateData}"
    stateData.each { String key, value ->
        if (hasAttribute(key)) {
            sendEvent(name: key, value: value)
        }
    }
    if (stateData.targetTemperature) {
        sendEvent(name: "heatingSetpoint", value: stateData.targetTemperature, unit: "C")
    }
    if (stateData.currentTemperature) {
        sendEvent(name: "temperature", value: stateData.currentTemperature, unit: "C")
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

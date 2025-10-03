/**
 *  Miele Base Driver
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
    definition (name: "Miele Generic Device", namespace: "obeisser", author: "obeisser") {
        capability "Refresh"
        capability "Switch"
    }
}

def installed() {
    log.info "Installed Miele Generic Device"
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

def receiveMieleState(Map stateData) {
    log.debug "Receiving state: ${stateData}"
    stateData.each { String key, value ->
        if (hasAttribute(key)) {
            sendEvent(name: key, value: value)
        }
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

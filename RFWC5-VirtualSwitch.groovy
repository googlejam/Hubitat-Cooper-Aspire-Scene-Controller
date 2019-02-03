/**
 *  Cooper RFWC5 Virtual Switch
 *
 *  Copyright 2019 Joel Wetzel
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
	definition (name: "Cooper RFWC5 Virtual Switch", namespace: "joelwetzel", author: "Joel Wetzel") {
		capability "Actuator"
		capability "Sensor"
		capability "Switch"
		
		command "markOn"
		command "markOff"
		command "setButtonIndex"		
		
		attribute "buttonIndex", "int"
	}
	
	preferences {
		section {
			input (
				type: "bool",
				name: "enableDebugLogging",
				title: "Enable Debug Logging?",
				required: true,
				defaultValue: false
			)
		}
	}
}


def parse(String description) {
}


def log(msg) {
	if (enableDebugLogging) {
		log.debug msg	
	}
}


def on() {
	log "${device.displayName}: on"
	
	sendEvent(name: "switch", value: "on", isStateChange: true)
	
	// Delay one second because we might get multiple events in a short period.  Only send the last one.
	runIn(1, pushStateToKeypad)
}


def off() {
	log "${device.displayName}: off"
	
    sendEvent(name: "switch", value: "off", isStateChange: true)
	
	// Delay one second because we might get multiple events in a short period.  Only send the last one.
	runIn(1, pushStateToKeypad)
}


def pushStateToKeypad() {
	def parent = getParent()
	if (parent) {
		parent.syncVirtualStateToIndicators()
	}
}


// Set the virtual switch on without sending state back to keypad.  Prevents cyclical firings.
def markOn() {
	sendEvent(name: "switch", value: "on")	
}


// Set the virtual switch off without sending state back to keypad.  Prevents cyclical firings.
def markOff() {
	sendEvent(name: "switch", value: "off")	
}


def setButtonIndex(newValue) {
	sendEvent(name: "buttonIndex", value: newValue)	
}


def installed() {
	log.info "${device.displayName}: installed"
	
	sendEvent(name: "switch", value: "off", isStateChange: true)
}


def uninstalled() {
	log.info "${device.displayName}: uninstalled"
}


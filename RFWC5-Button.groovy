/**
 *  Cooper RFWC5 RFWC5D Button
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
	definition (name: "Cooper RFWC5 RFWC5D Button", namespace: "joelwetzel", author: "Joel Wetzel") {
		capability "Actuator"
		capability "Sensor"
		capability "Switch"
		
		command "markOn"
		command "markOff"
	}
}


def parse(String description) {
}


def on() {
	log.debug "${device.displayName}: on"
	sendEvent(name: "switch", value: "on", isStateChange: true)
	
	def parent = getParent()
	if (parent) {
		def childsIndex = device.deviceNetworkId[device.deviceNetworkId.size() - 1] as Integer
		parent.IndicatorSet(childsIndex, 1)
	}
}


def off() {
	log.debug "${device.displayName}: off"
    sendEvent(name: "switch", value: "off", isStateChange: true)
	
	def parent = getParent()
	if (parent) {
		def childsIndex = device.deviceNetworkId[device.deviceNetworkId.size() - 1] as Integer
		parent.IndicatorSet(childsIndex, 0)
	}
}


def markOn() {
	sendEvent(name: "switch", value: "on")	
}


def markOff() {
	sendEvent(name: "switch", value: "off")	
}



def installed() {
	log.debug "${device.displayName}: installed"
}


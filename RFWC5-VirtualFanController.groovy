/**
 *  Cooper RFWC5 Virtual Fan Controller
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
	definition (name: "Cooper RFWC5 Virtual Fan Controller", namespace: "joelwetzel", author: "Joel Wetzel") {
		capability "Actuator"
		capability "Sensor"
		capability "Switch"
		capability "Switch Level"
		capability "Fan Control"
		
		command "processIndicatorValue"
		command "markOn"
		command "markOff"
		
		command "initialize"
		
		attribute "lastSpeed", "string"
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


def log (msg) {
	if (enableDebugLogging) {
		log.debug msg
	}
}


def parse(String description) {
}


def pushStateToKeypad() {
	def parent = getParent()
	if (parent) {
		parent.syncVirtualStateToIndicators()
	}
}


def on() {
	log "${device.displayName}.on()"
	
	def lastSpeed = device.currentValue("lastSpeed")
	
	sendEvent(name: "switch", value: "on", isStateChange: true)
	sendEvent(name: "speed", value: lastSpeed, isStateChange: true)
	sendEvent(name: "level", value: convertSpeedToLevel(lastSpeed), isStateChange: true)
	
	// Delay one second because we might get multiple events in a short period.  Only send the last one.
	runIn(1, pushStateToKeypad)
}


def off() {
	log "${device.displayName}.off()"
	
    sendEvent(name: "switch", value: "off", isStateChange: true)
	sendEvent(name: "speed", value: "off", isStateChange: true)
	sendEvent(name: "level", value: 0, isStateChange: true)
	
	// Delay one second because we might get multiple events in a short period.  Only send the last one.
	runIn(1, pushStateToKeypad)
}


def setSpeed(speed) {
	log "${device.displayName}.setSpeed($speed)"
	
	def adjustedSpeed = restrictSpeedLevels(speed)						// Only allow certain speed settings.  For example, don't allow "medium-high".
	def adjustedLevel = convertSpeedToLevel(adjustedSpeed)				// Some fan controllers depend on speed, some depend on level.  Convert the speed to a level.
	def adjustedSwitch = (adjustedSpeed == "off") ? "off" : "on"		// If speed is "off", then turn off the switch too.
	
	// Keep track of the last speed while on.  Then if the fan is off, and 
	// we turn it back on, we can go back to the last on speed.
	if (adjustedSpeed != "off") {
		sendEvent(name: "lastSpeed", value: adjustedSpeed, isStateChange: true)		
	}

	sendEvent(name: "switch", value: adjustedSwitch, isStateChange: true)
	sendEvent(name: "speed", value: adjustedSpeed, isStateChange: true)
	sendEvent(name: "level", value: adjustedLevel, isStateChange: true)
	
	// Delay one second because we might get multiple events in a short period.  Only send the last one.
	runIn(1, pushStateToKeypad)
}


// If our input is level, convert it to a speed input.
def setLevel(level) {
	log "${device.displayName}.setLevel($level)"
	
	def requestedSpeed = convertLevelToSpeed(level)
	
	setSpeed(requestedSpeed)
}



// ***************************************************************************
// The next three functions are the ones that might need to be modified
// to support different fans and fan controller devices.  These are values
// that work well with my fan and with a GE Z-Wave Plus Fan Controller.
// Different values might be needed for something like the Hampton Bay
// Zigbee fan controller
// ***************************************************************************

// This maps ranges of levels into speed values.  Right now it's set for just
// three speeds and off.
def convertLevelToSpeed(level) {
	if (level == 0) {
		return "off"
	}
	else if (level < 34) {
		return "low"
	}
	else if (level < 67) {
		return "medium"
	}
	else {
		return "high"
	}		
}

// This converts speeds back into levels.  These values work well for my GE
// Z-Wave Plus Fan Controller, but might need to change for other smart fan
// controllers.
def convertSpeedToLevel(speed) {
	switch (speed) {
		case "off":
			return 0
		case "low":
			return 10
		case "medium":
			return 50
		case "high":
			return 99
		default:
			return 10
	}
}

// This restricts allowed speed levels.  The GE Z-Wave Plus Smart Fan
// Controller doesn't support medium-low, medium-high, or auto, so
// this converts them into something else.
def restrictSpeedLevels(speed) {
	switch (speed) {
		case "off":
			return "off"
		case "low":
			return "low"
		case "medium-low":
			return "medium"
		case "medium":
			return "medium"
		case "medium-high":
			return "high"
		case "high":
			return "high"
		case "on":
			return "medium"
		case "auto":
			return "off"
		default:
			return "medium"
	}
}

// ***************************************************************************



// This is basically the implementation of the state machine.  It knows what the
// previous speed setting was, and then it interprets a new set of indicator values
// from the keypad, and decides what the new speed setting should be.  It has to be
// able to deal with "invalid" inputs too.  Like, what if someone just presses ALL
// the buttons on the keypad?  I have it generally preferring to turn off if the off
// button is lit, but otherwise preferring the highest speed button that is lit.
def processIndicatorValue(indicators) {
	log "${device.displayName}.processIndicatorValue($indicators)"
	
	def currentSpeed = device.currentValue("speed")
	def newSpeed = currentSpeed
	
	// What is the state of each of the buttons corresponding to the fan controller?
	// Is its indicator turned on?
	def i1 = indicators[0] == "1"
	def i2 = indicators[1] == "1"
	def i3 = indicators[2] == "1"
	def i4 = indicators[3] == "1"	
	
	if (currentSpeed == "off") {			// If we're already off, transition to the highest speed that is lit.
		if (i1) {
			newSpeed = "high"
		}
		else if (i2) {
			newSpeed = "medium"	
		}
		else if (i3) {
			newSpeed = "low"
		}
		else if (!i4) {						// If we were already off, and pressed the off button again, turn the fan on.
			newSpeed = 	device.currentValue("lastSpeed")
		}
	}
	else if (currentSpeed == "low") {
		if (i4) {							// Prefer turning off if multiple buttons had been pressed
			newSpeed = "off"
		}
		else if (i1) {						// Otherwise, prefer the highest speed that is lit
			newSpeed = "high"
		}
		else if (i2) {
			newSpeed = "medium"
		}
		else if (!i3) {						// And if we were already in low speed, and somebody pressed the low button again to turn off its light, transition to "off"
			newSpeed = "off"
		}
	}
	else if (currentSpeed == "medium") {	// Medium follows similar logic to low
		if (i4) {
			newSpeed = "off"
		}
		else if (i1) {
			newSpeed = "high"
		}
		else if (i3) {
			newSpeed = "low"
		}
		else if (!i2) {
			newSpeed = "off"
		}
	}
	else if (currentSpeed == "high") {		// High follows similar logic to low
		if (i4) {
			newSpeed = "off"
		}
		else if (i2) {
			newSpeed = "medium"
		}
		else if (i3) {
			newSpeed = "low"
		}
		else if (!i1) {
			newSpeed = "off"
		}
	}

	// Note that after setSpeed is called, we're going to send a new indicatorSet command to 
	// the keypad, so if it was in an "invalid" indicator state before, that will fix it up 
	// to a "valid" indicator state that matches our new fan speed.
	setSpeed(newSpeed)
}


// Set the virtual switch on without syncing state back to the keypad.  Prevents cyclical firings.
def markOn() {
	log "${device.displayName}.markOn()"
	
	def lastSpeed = device.currentValue("lastSpeed")
	
	sendEvent(name: "switch", value: "on")
	sendEvent(name: "speed", value: lastSpeed)
	sendEvent(name: "level", value: convertSpeedToLevel(lastSpeed))
}

// Set the virtual switch off without syncing state back to the keypad.  Prevents cyclical firings.
def markOff() {
	log "${device.displayName}.markOff()"
	
	sendEvent(name: "switch", value: "off")
	sendEvent(name: "speed", value: "off")
	sendEvent(name: "level", value: 0)
}


def installed() {
	log.info "${device.displayName}.installed()"
	
	initialize()
}


def uninstalled() {
	log.info "${device.displayName}.uninstalled()"
}


def initialize() {
	log.info "${device.displayName}.initialize()"
	
	// Default values
	sendEvent(name: "switch", value: "off", isStateChange: true)
	sendEvent(name: "level", value: "0", isStateChange: true)
	sendEvent(name: "speed", value: "off", isStateChange: true)
	sendEvent(name: "lastSpeed", value: "off", isStateChange: true)
}



/**
 *  Cooper RFWC5 Keypad
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
	definition (name: "Cooper RFWC5 Keypad", namespace: "joelwetzel", author: "Joel Wetzel") {
		capability "Actuator"
		capability "Configuration"
		capability "Sensor"
		capability "Refresh"
        
//		command "runDevCommands"				// This is just something I might need to use occasionally while developing new code.  Keep it commented out generally.
		
		command "syncVirtualStateToIndicators"
		command "removeExistingChildDevices"
		command "configureChildDevicesAsVirtualSwitches"
		command "configureChildDevicesAsVirtualFanController"
		command "configureChildDeviceAsVirtualButton"
        
        attribute "Indicators", "STRING"
		attribute "VirtualDeviceMode", "STRING"
        
		fingerprint type: "0202", mfr: "001A", prod: "574D", model: "0000",  cc:"87,77,86,22,2D,85,72,21,70" 
	}	
    
    preferences {
		section {
			input (
				type: "bool",
				name: "enableDebugLogging",
				title: "Enable Debug Logging?",
				required: true,
				defaultValue: true
			)
		}
	}
}


// *******************************************************
// *******************  Z-WAVE CODE **********************
// *******************************************************

def parse(String description) {
	def result = null
	
	def cmd = zwave.parse(description)

	//log.debug "DESCRIPTION: $description"
	//log "CMD: $cmd"
	//log "CLASSNAME: ${cmd.class.name}"
	
	if (cmd) {
		result = zwaveEvent(cmd)
		
		if (result != null && result.inspect() != null) {
			log "PARSED ${cmd} to: ${result.inspect()}"
		}
		else {
			log.debug "UNPARSED CMD: ${cmd}"	
		}
	}
	else {
		log.debug "UNPARSED DESCRIPTION: ${description}"	
	}
	
	result
}

def zwaveEvent(hubitat.zwave.commands.associationv2.AssociationReport cmd) {
	// Do nothing.  This is here to prevent an error during z-wave repair.
}

def zwaveEvent(hubitat.zwave.commands.scenecontrollerconfv1.SceneControllerConfReport cmd) {
	// Do nothing.  This is here to prevent an error during configuration.
}

def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
	// Fired twice when a button is physically pushed off
	
    def result = []
    def cmds = []
	
	state.buttonpush = 1	// Upcoming IndicatorReport is the result of a button push
    cmds << response(zwave.indicatorV1.indicatorGet())
    sendHubCommand(cmds)  
	
    result
}
    
    
def zwaveEvent(hubitat.zwave.commands.sceneactivationv1.SceneActivationSet cmd) {
	// Fired twice when a button is physically pushed on
	
    def result = []
    def cmds = []

    state.buttonpush = 1	// Upcoming IndicatorReport is the result of a button push

	// This clause is a shortcut that only works if we're in virtualSwitches mode.
	// The SceneActivationSet command has enough info to know which button was pushed,
	// so we can turn on a virtual switch immediately, to reduce delay.  That won't 
	// work in other modes.  Only in virtualSwitches mode.
	if (device.currentValue("VirtualDeviceMode") == "virtualSwitches" && cmd.sceneId >= 251 && cmd.sceneId <= 255) {
		getChildDevices()[cmd.sceneId - 251].markOn()
		
		runIn(1, requestIndicatorState)
		return result
	}

    cmds << response(zwave.indicatorV1.indicatorGet())
    sendHubCommand(cmds)  

	result
}


def requestIndicatorState() {
	def cmds = []

    cmds << response(zwave.indicatorV1.indicatorGet())
    sendHubCommand(cmds)  
}

 
def zwaveEvent(hubitat.zwave.commands.indicatorv1.IndicatorReport cmd) {
	def ibit = 0
	def onOff = 0
	def events = []
    def event = []
	
    def indval = cmd.value
	def payload = cmd.payload
	
	// Set the human-readable indicators attribute (mostly for debugging)
	def indicators = convertIndvalToReadable(indval)
	event = createEvent(name: "Indicators", value: "$indicators", descriptionText: "Indicators: $indicators", linkText: "Indicators: $indicators")
	events << event

	// Only propagate indicator values to the child devices if this IndicatorReport was the result of a physical button push.
	if (state.buttonpush == 1) {
		def currentMode = device.currentValue("VirtualDeviceMode")
		
		if (currentMode == "virtualFanController" || currentMode == "virtualSwitches") {
			// Delay processing one second because we could get multiple IndicatorReports back in a 
			// short period of time.  We want the fan controller to only process the final one.
			runIn(1, sendIndicatorValueToChildDevices)	
		}
		else if (currentMode == "virtualButton") {
			// Delay processing one second because we could get multiple IndicatorReports back in a 
			// short period of time.  We want the button controller to only process the final one.
			runIn(1, sendIndicatorValueToVirtualButton)		
		}
	}
		
	return events
}


def sendIndicatorValueToChildDevices() {
	// Tell the virtual fan controller what the new states are.
	// It will compare this to its internal understanding, update itself
	// and then make make the keypad sync again, so that we're not displaying
	// invalid combinations of lights.

	def existingChildDevices = getChildDevices()
	def childCount = existingChildDevices.size()
	for (def i = 0; i < childCount; i++) {
		existingChildDevices[i].processIndicatorValue(device.currentValue("Indicators"))
	}
}


def sendIndicatorValueToVirtualButton() {
	def virtualButton = getChildDevices()[0]
	def indicators = device.currentValue("Indicators")
	
	for (i in 0..4) {
		if (indicators[i] == "1") {
			virtualButton.push(i+1)	
		}
	}
	
	syncVirtualStateToIndicators()
}


// *******************************************************
// *******************  PUBLIC API ***********************
// *******************************************************


//
// Make the keypad's indicators match the states of the virtual child devices.
//
def syncVirtualStateToIndicators() {

	def existingChildDevices = getChildDevices()
	def currentMode = device.currentValue("VirtualDeviceMode")

	def newValue = 0	
	def ibit = 0
	
	if (currentMode == "virtualSwitches") {
		// Check the state of each virtual switch
		for (i in 0..4) {
			if (existingChildDevices[i].currentValue("switch") == "on") {
				ibit = 2**i
				newValue = newValue | ibit
			}
		}

		state.buttonpush = 0	// upcoming indicatorGet command and IndicatorReport is from this API call, and not the result of a physical button press

		log "${device.displayName}.syncVirtualStateToIndicators(${convertIndvalToReadable(newValue)})"

		delayBetween([
			zwave.indicatorV1.indicatorSet(value: newValue).format(),
			zwave.indicatorV1.indicatorGet().format(),
		], 300)
	}
	else if (currentMode == "virtualFanController") {
		// Check the virtual switch's state
		if (existingChildDevices[1].currentValue("switch") == "on") {
			ibit = 2**4
			newValue = newValue | ibit
		}
		
		// Check the virtual fan controller's state
		switch (existingChildDevices[0].currentValue("speed")) {
			case "high":
				newValue = newValue | 2**0
				break
			case "medium":
				newValue = newValue | 2**1
				break
			case "low":
				newValue = newValue | 2**2
				break
			case "off":
				newValue = newValue | 2**3
				break
			default:
				newValue = newValue | 2**3
				break
		}
		
		state.buttonpush = 0	// upcoming indicatorGet command and IndicatorReport is from this API call, and not the result of a physical button press

		log "${device.displayName}.syncVirtualStateToIndicators(${convertIndvalToReadable(newValue)})"
		
		delayBetween([
			zwave.indicatorV1.indicatorSet(value: newValue).format(),
			zwave.indicatorV1.indicatorGet().format(),
		], 300)
	}
	else if (currentMode == "virtualButton") {
		state.buttonpush = 0	// upcoming indicatorGet command and IndicatorReport is from this API call, and not the result of a physical button press

		// Don't change anything about "newValue"  We're sending zeros to turn the indicators off.
		
		log "${device.displayName}.syncVirtualStateToIndicators(${convertIndvalToReadable(newValue)})"
		
		delayBetween([
			zwave.indicatorV1.indicatorSet(value: newValue).format()
		], 300)
	}
}


//
// Request the current state from the keypad, and set any child devices to match
//
def refresh() {
	log "${device.displayName}.refresh()"
	
	// If anything has changed on the indicators, allow those changes to push to the
	// virtual child devices, as if a physical button press had happened.
	state.buttonpush = 1
	
	delayBetween([
		zwave.indicatorV1.indicatorGet().format(),
    ], 100)    
}



// *******************************************************
// *******************  UTILITY CODE *********************
// *******************************************************


def log(msg) {
	if (enableDebugLogging) {
		log.debug(msg)	
	}
}


def convertIndvalToReadable(indval) {
	def istring = "" + Integer.toString(indval+128,2).reverse().take(5) // create a string to display for user	
	return istring
}



// *******************************************************
// *************  CONFIGURATION CODE *********************
// *******************************************************


//
// This is only here for while developing new code.  Contains dev utility code I might uncomment and 
// use once.  Uncomment the "initialize" command in the metadata to enable it.
//
def runDevCommands() {
	log.info "${device.displayName}.runDevCommands()"
	
	//unschedule("syncIndicators")
	//runEvery5Minutes(syncIndicators)
	//state.remove("flashing")	
}


def installed() {
	log.info "${device.displayName}.installed()"
	
	// Set default values
	sendEvent(name: "Indicators", value: "000000", isStateChange: true)
	sendEvent(name: "VirtualDeviceMode", value: "none", isStateChange: true)
}


def uninstalled() {
	log.info "${device.displayName}.uninstalled()"
}


def updated() {
	log "${device.displayName}.updated()"
}


//
// Remove all current child virtual devices.
//
def removeExistingChildDevices() {
	def existingChildDevices = getChildDevices()
	if (existingChildDevices.size() > 0) {
		log.info "${device.displayName}: Removing existing child devices..."
		
		existingChildDevices.each {
			deleteChildDevice(it.deviceNetworkId)
		}
	}
	
	sendEvent(name: "VirtualDeviceMode", value: "none", isStateChange: true)
}


//
// Configure 5 virtual switches as child devices.  Each will map to a button on the keypad.
//
def configureChildDevicesAsVirtualSwitches() {
	removeExistingChildDevices()
	
	log.info "Adding child devices..."
	for (i in 1..5) {
		addChildDevice("joelwetzel", "Cooper RFWC5 Virtual Switch", "${device.displayName}-${i}", [completedSet: true, label: "${device.displayName} (Switch ${i})", isComponent: true, componentName: "ch$i", componentLabel: "Switch $i"])
	}
	
	// Let them know the number of the button they correspond to
	def existingChildDevices = getChildDevices()
	for (i in 0..4) {
		existingChildDevices[i].setButtonIndex(i)
	}
	
	sendEvent(name: "VirtualDeviceMode", value: "virtualSwitches", isStateChange: true)
	
	runIn(1, syncVirtualStateToIndicators)
}


def configureChildDevicesAsVirtualFanController() {
	removeExistingChildDevices()
	
	log.info "Adding child devices..."

	addChildDevice("joelwetzel", "Cooper RFWC5 Virtual Fan Controller", "${device.displayName}-FanController", [completedSet: true, label: "${device.displayName} (Fan Controller)", isComponent: true, componentName: "chFC", componentLabel: "Fan Controller"])
	addChildDevice("joelwetzel", "Cooper RFWC5 Virtual Switch", "${device.displayName}-LightSwitch", [completedSet: true, label: "${device.displayName} (Light Switch)", isComponent: true, componentName: "ch5", componentLabel: "Light Switch"])

	// Let the switch know the index of the button it corresponds to
	def existingChildDevices = getChildDevices()[1].setButtonIndex(4)

	sendEvent(name: "VirtualDeviceMode", value: "virtualFanController", isStateChange: true)
	
	runIn(1, syncVirtualStateToIndicators)
}


def configureChildDeviceAsVirtualButton() {
	removeExistingChildDevices()
	
	log.info "Adding child devices..."
	
	addChildDevice("hubitat", "Virtual Button", "${device.displayName}-Buttons", [completedSet: true, label: "${device.displayName} (Virtual Button)", isComponent: true, componentName: "ch5", componentLabel: "Keypad Virtual Button"])

	sendEvent(name: "VirtualDeviceMode", value: "virtualButton", isStateChange: true)
	
	runIn(1, syncVirtualStateToIndicators)
}

//
// This sets the Z-Wave configuration on the keypad device.
//
def configure() {
	log.info "${device.displayName}.configure() - setting the Z-Wave configuration on the physical keypad device."
	
    def cmds = []
    
    //the buttons on the controller will not work with out a scene load in.  Use 251-255 if no scene number is specified in the preferences
    def s1 = 251
    def s2 = 252
    def s3 = 253
    def s4 = 254
    def s5 = 255
    
    if (sceneNum1) s1 = sceneNum1
    if (sceneNum2) s2 = sceneNum2
    if (sceneNum3) s3 = sceneNum3
    if (sceneNum4) s4 = sceneNum4
    if (sceneNum5) s5 = sceneNum5

    //will use 0 for dimming durations unless a value is entered
    def d1 = 0x00
    def d2 = 0x00
    def d3 = 0x00
    def d4 = 0x00
    def d5 = 0x00   

    if (dimdur1) d1=dimdur1 else d1 = 0x00
    if (dimdur2) d2=dimdur2 else d2 = 0x00
    if (dimdur3) d3=dimdur3 else d3 = 0x00
    if (dimdur4) d4=dimdur4 else d4 = 0x00
    if (dimdur5) d5=dimdur5 else d5 = 0x00
    
	//for each button group create a sub to run for each button
	cmds += buttonCmds(1, s1, sceneCap1, assocCap1, d1)
	cmds << zwave.associationV1.associationGet(groupingIdentifier:1).format()
	cmds << zwave.sceneControllerConfV1.sceneControllerConfGet(groupId:1).format()
	
	cmds += buttonCmds(2, s2, sceneCap2, assocCap2, d2)
	cmds << zwave.associationV1.associationGet(groupingIdentifier:2).format()
	cmds << zwave.sceneControllerConfV1.sceneControllerConfGet(groupId:2).format()
	
	cmds += buttonCmds(3, s3, sceneCap3, assocCap3, d3)
	cmds << zwave.associationV1.associationGet(groupingIdentifier:3).format()
	cmds << zwave.sceneControllerConfV1.sceneControllerConfGet(groupId:3).format()
	
	cmds += buttonCmds(4, s4, sceneCap4, assocCap4, d4)
	cmds << zwave.associationV1.associationGet(groupingIdentifier:4).format()
	cmds << zwave.sceneControllerConfV1.sceneControllerConfGet(groupId:4).format()
	
	cmds += buttonCmds(5, s5, sceneCap5, assocCap5, d5)
	cmds << zwave.associationV1.associationGet(groupingIdentifier:5).format()
	cmds << zwave.sceneControllerConfV1.sceneControllerConfGet(groupId:5).format()

	// send commands
	log.info "$cmds"
	log.info "Please Wait this can take a few minutes..."
	delayBetween(cmds,3500)
}


//
// Parse the user input and create commands to set up the controller -- called from config
//
def buttonCmds(btn, scene, scenelist, assoclist, dimdur) { 
	def cmds = []
	def lList = 0
	def alist = []
	def alists = []
	def atlist = []
	def amap = [level:0, anodes: []]

	//add clear associaton cmd to the list
	cmds << zwave.associationV1.associationRemove(groupingIdentifier: btn, nodeId:[]).format()

	// add the levels to the data structure.
	if (assoclist) {
		atlist = assoclist.tokenize(', ')
		
		lList = atlist.size()
		for (int i = 1; i <= lList; i+=2) {
			if (alist.every { it != atlist[i]}) {
				alist << atlist[i] 						// if the value is not in alist then add it.
			} 
		}
		alist.each {
			amap = [level: it, anodes: []]      //add the levels to the data structure
			alists << amap  //build the matrix
		}

		// fill the matrix with nodes ordered with levels
		for (int i = 1; i <= lList; i+=2) {
			for (int x = 0; x < alist.size(); x++){
				def bob = alists[x]
				if (bob.level == atlist[i]) {
					bob.anodes << atlist[i-1]
				}
			}
		}
	}
	
	// for each list of ids
	// <<create association set commands
	// <<create configuration set commands
	
	for (int i = 0; i <= alists.size(); i++) {   
		def thisset = alists[i]
		def nodestring = ""
		def thislevel = [0x32]

		if (thisset) {
			def alevel = thisset.level as int
			nodestring = thisset.anodes.join(", ")
			if (alevel <= 99 && alevel >= 0) {        	
				thislevel[0] = thisset.level as int
			}
			if (alevel == 255) {
				thislevel[0] = thisset.level as int
			}
		}
		cmds << assocNodes(nodestring,btn,0)
		log.info "setting configuration commands for button:$btn Level:$thislevel"        
		cmds << zwave.configurationV1.configurationSet(parameterNumber:btn, size:1, configurationValue: thislevel).format()
	}

	cmds << zwave.sceneControllerConfV1.sceneControllerConfSet(groupId:btn, sceneId:scene, dimmingDuration:dimdur).format()
	log.info "setting scene commands for button:$btn scene:$scene dimmingduration:$dimdur"
	cmds << assocNodes(scenelist, btn, 1)

	return (cmds)
}


def assocNodes(txtlist,group,hub) {
	def List1
	def List3 = []
	def cmd = ""
	if (txtlist){
		List1 = txtlist.tokenize(', ')
		List3 = List1.collect{Integer.parseInt(it,16)}
		if (hub){
			List3 << zwaveHubNodeId
		}
	} 
	else if (hub) {
		List3 = zwaveHubNodeId
	}

	log.info "associating group for button:$group: $List3"

	cmd = zwave.associationV1.associationSet(groupingIdentifier:group, nodeId:List3).format()

	return (cmd)
}




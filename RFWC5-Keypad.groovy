/**
 *  Cooper RFWC5 RFWC5D Keypad
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
	definition (name: "Cooper RFWC5 RFWC5D Keypad", namespace: "joelwetzel", author: "Joel Wetzel") {
		capability "Actuator"
		capability "Configuration"
		capability "Sensor"
        
		command "CheckIndicators" 	//use to poll the indicator status
		command "initialize"
		command "SyncIndicators"
        
        attribute "IndDisplay", "STRING"
        
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
	if (cmd) {
		result = zwaveEvent(cmd)
		
		if (result != null && result.inspect() != null) {
			log "Parsed ${cmd} to ${result.inspect()}"
		}
	}
	
	result
}


def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
	// Fired twice when a button is physically pushed off
	
    def result = []
    def cmds = []
	
	// Only perform an indicatorGet for half of these events
	if (state.basicSet) {
		//log "ignoring extra basicSet"
		state.basicSet = false
		return result	
	}
	state.basicSet = true

	state.buttonpush = 1	// Upcoming IndicatorReport is the result of a button push
    cmds << response(zwave.indicatorV1.indicatorGet())
    sendHubCommand(cmds)  
	
    result
}
    
    
def zwaveEvent(hubitat.zwave.commands.sceneactivationv1.SceneActivationSet cmd) {
	// Fired twice when a button is physically pushed on
	
    def result = []
    def cmds = []

	// Only perform an indicatorGet for half of these events
	if (state.sceneActivationSet) {
		//log "ignoring extra sceneActivationSet"
		state.sceneActivationSet = false
		return result	
	}
	state.sceneActivationSet = true

    state.buttonpush = 1	// Upcoming IndicatorReport is the result of a button push
    cmds << response(zwave.indicatorV1.indicatorGet())
    sendHubCommand(cmds)  

	result
}

 
def zwaveEvent(hubitat.zwave.commands.indicatorv1.IndicatorReport cmd) {
	def events = []
    def event = []
	
    def indval = cmd.value
	
	def istring = "IND " + Integer.toString(indval+128,2).reverse().take(5) // create a string to display for user
	event = createEvent(name: "IndDisplay", value: "$istring", descriptionText: "Indicators: $istring", linkText: "device.label Indicators: $istring")
	events << event

	def existingChildDevices = getChildDevices()

	for (i in 0..4) {
		def ibit = 2**i
		def onOff = indval & ibit

		if (onOff) {
			existingChildDevices[i].markOn()
		}
		else {
			existingChildDevices[i].markOff()
		}
	}
		
	state.sceneActivationSet = false
	state.basicSet = false

	return events
}


def zwaveEvent(hubitat.zwave.Command cmd) {
	def event = [isStateChange: true]
	event.linkText = device.label ?: device.name
	event.descriptionText = "Cooper $event.linkText: $cmd"
	event
}



// *******************************************************
// *******************  PUBLIC API ***********************
// *******************************************************


def SyncIndicators() {
	//log "SyncIndicators()"

	def newValue = 0	
	def ibit = 0
	
	def existingChildDevices = getChildDevices()
	for (i in 0..4) {
		if (existingChildDevices[i].currentValue("switch") == "on") {
			ibit = 2**i
			newValue = newValue | ibit
		}
	}
	
	state.buttonpush = 0	// upcoming indicatorGet command is from this API call, and not the result of a button press
	
	delayBetween([
		zwave.indicatorV1.indicatorSet(value: newValue).format(),
		zwave.indicatorV1.indicatorGet().format(),
	],300)
}


def CheckIndicators() {
	//log "CheckIndicators()"
	
	state.buttonpush = 0  // upcoming indicatorGet command is from this API call, and not the result of a button press
	
	delayBetween([
		zwave.indicatorV1.indicatorGet().format(),
    ],100)    
}



// *******************************************************
// *******************  UTILITY CODE *********************
// *******************************************************


def log(msg) {
	if (enableDebugLogging) {
		log.debug(msg)	
	}
}



// *******************************************************
// *************  CONFIGURATION CODE *********************
// *******************************************************

def initialize() {
	// These two booleans are to prevent double requests for IndicatorReports when buttons are physically pushed.
	state.sceneActivationSet = false
	state.basicSet = false
	
	runEvery5Minutes(SyncIndicators)
}


def installed() {
	initialize()
	configure()
	state.updatedLastRanAt = now()
}


def updated() {
	if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 5000) {
		state.updatedLastRanAt = now()
		log "${device.displayName}:updated()"
        
        initialize() 
	}
	else {
		log.trace "updated(): Ran within last 5 seconds so skipping."
	}
}


def createChildDevices() {
	def existingChildDevices = getChildDevices()
	if (existingChildDevices.size() > 0) {
		log.info "Child devices already exist.  Removing..."
		
		existingChildDevices.each {
			log.info "Removing ${it.displayName}"
			deleteChildDevice(it.deviceNetworkId)
		}
	}
	
	log.info "Adding child devices..."
	for (i in 1..5) {
		log.info "Adding ${device.displayName} (Switch ${i})"
		addChildDevice("joelwetzel", "Cooper RFWC5 RFWC5D Button", "${device.displayName}-${i}", [completedSet: true, label: "${device.displayName} (Switch ${i})", isComponent: true, componentName: "ch$i", componentLabel: "Switch $i"])
	}
}


def configure() {
	log.info "${device.displayName}:configure"
	
	createChildDevices()
	
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
	cmds += buttoncmds(1, s1, sceneCap1, assocCap1, d1)
	cmds << zwave.associationV1.associationGet(groupingIdentifier:1).format()
	cmds << zwave.sceneControllerConfV1.sceneControllerConfGet(groupId:1).format()
	cmds += buttoncmds(2, s2, sceneCap2, assocCap2, d2)
	cmds << zwave.associationV1.associationGet(groupingIdentifier:2).format()
	cmds << zwave.sceneControllerConfV1.sceneControllerConfGet(groupId:2).format()
	cmds += buttoncmds(3, s3, sceneCap3, assocCap3, d3)
	cmds << zwave.associationV1.associationGet(groupingIdentifier:3).format()
	cmds << zwave.sceneControllerConfV1.sceneControllerConfGet(groupId:3).format()
	cmds += buttoncmds(4, s4, sceneCap4, assocCap4, d4)
	cmds << zwave.associationV1.associationGet(groupingIdentifier:4).format()
	cmds << zwave.sceneControllerConfV1.sceneControllerConfGet(groupId:4).format()
	cmds += buttoncmds(5, s5, sceneCap5, assocCap5, d5)
	cmds << zwave.associationV1.associationGet(groupingIdentifier:5).format()
	cmds << zwave.sceneControllerConfV1.sceneControllerConfGet(groupId:5).format()

	// send commands
	log.info "$cmds"
	log.info "Please Wait this can take a few minutes"
	delayBetween(cmds,3500)
}


// Parse the user input and create commands to set up the controller -- called from config
def buttoncmds(btn, scene, scenelist, assoclist, dimdur) { 
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
			if(alist.every { it != atlist[i]}) {	alist << atlist[i] } // if the value is not in alist then add it.
		}
		alist.each{amap = [level: it, anodes: []]      //add the levels to the data structure
			alists << amap  //build the matrix
		}

		// fill the matrix with nodes ordered with levels
		for (int i = 1; i <= lList; i+=2) {
			for (int x = 0; x < alist.size(); x++){
				def bob = alists[x]
				if (bob.level == atlist[i]) {bob.anodes << atlist[i-1]}
			}
		}
	}
	
	// for each list of ids
	// <<create association set commands
	// <<create configuration set commands
	
	for (int i = 0; i <=alists.size(); i++) {   
		def thisset = alists[i]
		def nodestring = ""
		def thislevel = [0x32]

		if (thisset) {
			def alevel = thisset.level as int
			nodestring = thisset.anodes.join(", ")
			if (alevel <= 99 && alevel >= 0){        	
				thislevel[0] = thisset.level as int

				}
			if (alevel == 255){
				thislevel[0] = thisset.level as int
				}
		}
		cmds << AssocNodes(nodestring,btn,0)
		log.debug "setting configuration commands for button:$btn Level:$thislevel"        
		cmds << zwave.configurationV1.configurationSet(parameterNumber:btn, size:1, configurationValue: thislevel).format()
	}

	cmds << zwave.sceneControllerConfV1.sceneControllerConfSet(groupId:btn, sceneId:scene, dimmingDuration:dimdur).format()
	log.debug "setting scene commands for button:$btn scene:$scene dimmingduration:$dimdur"
	cmds << AssocNodes(scenelist, btn, 1)

	return (cmds)
}


def AssocNodes(txtlist,group,hub) {
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




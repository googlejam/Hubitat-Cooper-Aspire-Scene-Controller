/**
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
	definition (name: "Cooper Aspire Scene Controller RFWC5 RFWC5D", namespace: "saains", author: "Scott Ainsworth") {
		capability "Actuator"
		capability "PushableButton"
		capability "Configuration"
		capability "Sensor"
        //capability "Switch"
        //capability "switchLevel"
        
		command "CheckIndicators" //use to poll the indicator status
		command "initialize"
		command "Indicator1On"
		command "Indicator1Off"
		command "Indicator2On"
		command "Indicator2Off"
		command "Indicator3On"
		command "Indicator3Off"
		command "Indicator4On"
		command "Indicator4Off"
		command "Indicator5On"
		command "Indicator5Off"
        
        attribute "currentButton", "STRING"
        attribute "numberOfButtons", "number"
        attribute "Indicator1", "enum",  ["on", "off"]
        attribute "Indicator2", "enum",  ["on", "off"]
        attribute "Indicator3","enum",  ["on", "off"]
        attribute "Indicator4","enum",  ["on", "off"]
        attribute "Indicator5","enum",  ["on", "off"]
        attribute "IndDisplay", "STRING"
        
		fingerprint type: "0202", mfr: "001A", prod: "574D", model: "0000",  cc:"87,77,86,22,2D,85,72,21,70" 
	}	
    
    preferences {
	}
}


def parse(String description) {
	def result = null
	
	def cmd = zwave.parse(description)
	if (cmd) {
		result = zwaveEvent(cmd)
		log.debug "Parsed ${cmd} to ${result.inspect()}"
	} else {
		log.debug "Non-parsed event: ${description}"
	}
	
	result
}


def zwaveEvent(hubitat.zwave.commands.basicv1.BasicSet cmd) {
    def result = []
    def cmds = []

	state.buttonpush = 1
    cmds << response(zwave.indicatorV1.indicatorGet())
    sendHubCommand(cmds)  
	
    result
}
    
    
def zwaveEvent(hubitat.zwave.commands.sceneactivationv1.SceneActivationSet cmd) {
    def result = []
    def cmds = []
	
    state.buttonpush = 1
    cmds << response(zwave.indicatorV1.indicatorGet())
    sendHubCommand(cmds)  

	result
}

 
def zwaveEvent(hubitat.zwave.commands.indicatorv1.IndicatorReport cmd) {
	def events = []
    def event = []
    def event2 =[]
    def indval = 0
    def onoff = 0
    def priorOnoff = 0
    def ino = 0
    def ibit = 0
    def istring = ""
	
    indval = cmd.value
	
    if (state.lastindval  == indval &&(now() -state.repeatStart <2000 )) {  // test to see if it is actually a change.  The controller sends double commands by design. 
    	//log.debug "skipping and repeat"
    	createEvent([:])
    }
    else {
		istring = "IND " + Integer.toString(indval+128,2).reverse().take(5) // create a string to display for user
		event = createEvent(name: "IndDisplay", value: "$istring", descriptionText: "Indicators: $istring", linkText: "device.label Indicators: $istring")
		events << event

		for (i in 0..4) {
			ibit = 2**i
			ino = i + 1
			onoff = indval & ibit
			offOffset = 0

			priorOnoff = state.lastindval & ibit
			//log.debug "$ino is $onoff , piorOnoff is:$priorOnoff ibit is $ibit"
			
			if (onoff != priorOnoff){
				//log.debug "$ino first if true"
				if (onoff) { //log.debug "$ino second if true"
				   event = createEvent(name: "Indicator$ino", value: "on", descriptionText: "$device.label Indicator:$ino on", linkText: "$device.label Indicator:$ino on")
				} else { //log.debug "$ino second if false"
					event = createEvent(name: "Indicator$ino", value: "off", descriptionText: "$device.label Indicator:$ino off", linkText: "$device.label Indicator:$ino off")
				}
				events << event
				if (state.buttonpush == 1){
					//log.debug "PUSHED $ino , $onoff"

					if (onoff == 0) {
						offOffset = 5
					}

					event2 = createEvent(name:"pushed",value: (ino + offOffset),descriptionText:"$device.displayName button $ino pushed",linkText:"$device.label Button:$ino pushed",isStateChange: true)
					events << event2
				}
			} //else { log.debug "$ino first if false"}
		}
		state.lastindval = indval
		state.repeatStart = now()

		events
	}
}


def zwaveEvent(hubitat.zwave.Command cmd) {
	def event = [isStateChange: true]
	event.linkText = device.label ?: device.name
	event.descriptionText = "Cooper $event.linkText: $cmd"
	event
}


def configure() {
	log.debug("executing configure hub id is: $zwaveHubNodeId")
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
	log.debug "$cmds"
	log.debug "Please Wait this can take a few minutes"
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

	//log.debug "assoclist is:$assoclist"

	//add clear associaton cmd to the list
	cmds << zwave.associationV1.associationRemove(groupingIdentifier: btn, nodeId:[]).format()

	// add the levels to the data structure.
	if (assoclist) {
		atlist = assoclist.tokenize(', ')
		
		// log.debug "atlist is: $atlist"
		
		lList = atlist.size()
		for (int i = 1; i <= lList; i+=2) {
			if(alist.every { it != atlist[i]}) {	alist << atlist[i] } // if the value is not in alist then add it.
		}
		alist.each{amap = [level: it, anodes: []]      //add the levels to the data structure
			alists << amap  //build the matrix
		}

		// fill the matrix with nodes ordered with levels            log.debug "afterif alists:$alists  i is:$i x is:$x"
		for (int i = 1; i <= lList; i+=2) {
			for (int x = 0; x < alist.size(); x++){
				def bob = alists[x]
				if (bob.level == atlist[i]) {bob.anodes << atlist[i-1]}
			}
		}
    	// log.debug "alists is now: $alists"
	}
	
	// for each list of ids
	// <<create association set commands
	// <<create configuration set commands
	
	for (int i = 0; i <=alists.size(); i++) {   
		def thisset = alists[i]
		def nodestring = ""
		def thislevel = [0x32]
		//log.debug "alists $i is $thisset"

		if (thisset) {
			def alevel = thisset.level as int
			nodestring = thisset.anodes.join(", ")
			//log.debug "nodestring is: $nodestring"
			//log.debug "xxxx $thisset.level.value"f
			if (alevel <= 99 && alevel >= 0){        	
				thislevel[0] = thisset.level as int

				}
			if (alevel == 255){
				thislevel[0] = thisset.level as int
				}
			//log.debug "thislevel $i is $thislevel"
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


def Indicator1On() {
	IndicatorSet(1, 1)	
}


def Indicator1Off() {
	IndicatorSet(1, 0)	
}


def Indicator2On() {
	IndicatorSet(2, 1)	
}


def Indicator2Off() {
	IndicatorSet(2, 0)	
}


def Indicator3On() {
	IndicatorSet(3, 1)	
}


def Indicator3Off() {
	IndicatorSet(3, 0)	
}


def Indicator4On() {
	IndicatorSet(4, 1)	
}


def Indicator4Off() {
	IndicatorSet(4, 0)	
}


def Indicator5On() {
	IndicatorSet(5, 1)	
}


def Indicator5Off() {
	IndicatorSet(5, 0)	
}


// because of delay in getting state.lastindval delays of at least 1 second should be used between calling this command.
def IndicatorSet(Inumber, OnorOff) {
	def Onoff = 0
	def ibit = 2**(Inumber-1)

	if (Inumber >=1 && Inumber <=5) {
		if (OnorOff == "On" || OnorOff ==1){
			Onoff = state.lastindval | ibit
			//log.debug "this is $ibit Onoff on: $Onoff lastindval is: $state.lastindval"
			} else {
			Onoff = state.lastindval & ~ibit
			//log.debug "this is $ibit Onoff off: $Onoff lastindval is: $state.lastindval"
			}
		state.buttonpush = 0  //upcoming indicatorGet command is not the result of a button press
		delayBetween([
			zwave.indicatorV1.indicatorSet(value: Onoff).format(),
			zwave.indicatorV1.indicatorGet().format(),
		],300)
	} 
	else {
		log.debug "$device.id Indicator set out of range"
	}
}


def CheckIndicators() {
	state.buttonpush = 0  //upcoming indicatorGet command is not the result of a button press
	
	delayBetween([
		zwave.indicatorV1.indicatorGet().format(),
    ],100)    
}


def initialize() {
	sendEvent(name: "numberOfButtons", value: 10)
    state.lastindval = 0
}


def installed() {
	initialize()
	configure()
	state.updatedLastRanAt = now()
}


def updated() {
	if (!state.updatedLastRanAt || now() >= state.updatedLastRanAt + 5000) {
		state.updatedLastRanAt = now()
		log.debug "Executing 'updated'"
        
        initialize() 
	}
	else {
		log.trace "updated(): Ran within last 5 seconds so skipping."
	}
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

	log.debug "associating group for button:$group: $List3"

	cmd = zwave.associationV1.associationSet(groupingIdentifier:group, nodeId:List3).format()

	return (cmd)
}



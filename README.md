# Cooper Eaton Aspire RFWC5 RFWC5D Keypad device for Hubitat

This is a Hubitat driver for the Cooper Eaton Aspire RFWC5 and RFWC5D 5-button keypads.  These are z-wave scene controllers, but this driver allows them to be used as keypads for a variety of purposes.

### Acknowledgments
Many thanks to Scott Ainsworth for figuring out the z-wave configuration steps for the keypads.  I borrowed the configuration code from his original driver for SmartThings:  https://github.com/saains/SmartThingsPublic/blob/master/devicetypes/saains/cooper-aspire-scene-controller-rfwc5-rfwc5d.src/cooper-aspire-scene-controller-rfwc5-rfwc5d.groovy

## Supported Devices
- **RFWC5** - This is the easiest to find.  It can easily be purchased online.  However, its design has wavy curves that you may or may not like.  https://www.amazon.com/Eaton-RFWC5AW-ASPIRE-5-Scene-120-volt/dp/B0053ZIRWK
- **RFWC5D** - This is my favorite.  It's the Decorator Series model.  It looks rectangular and subtle, and matches Decora switches nicely.  However, it is difficult (but not impossible) to find or order without being an electrical contractor. http://www.cooperindustries.com/content/public/en/wiring_devices/products/lighting_controls/aspire_rf_wireless/aspire_rf_5_button_scene_control_keypad_rfwdc_rfwc5.html

## Device Behavior
The physical keypad has 5 main buttons with indicators.  Pressing a button causes its indicator to toggle, and a z-wave message is sent to the hub, which can then determine what all the indicator states are.

There is also a 6th button.  If held for a few seconds, all the lights will flash and then every button will turn off.  This also sends a z-wave message to the hub, indicating that all buttons are off.

(The keypad is also capable of being associated to other z-wave devices.  However, I do not find this useful, since then the hub is unaware of what is happening.  So my driver disables that, and all messages go through the hub.)

## Device Virtualization
This is where the magic happens.  To a passer-by, the keypad looks and behaves like 5 toggle switches.  They are toggled by pressing the buttons, and the lights indicate if each of the switches is on.  *So we can use child virtual devices in the Hubitat hub to make it look like 5 switches.*  This makes it easy to use Rule Machine, Switch Bindings, or other automation apps to drive behavior based on keypad presses.

In fact, because the hub can also control the indicator states, we can give the keypad even more complex behaviors, and expose those behaviors as child virtual devices that match common capabilities.

Currently, this driver has 3 modes:
1. Configure Children as Virtual Switches *(This creates 5 virtual switches as child devices.)*
2. Configure Children as Virtual Fan Controller *(This creates a virtual fan controller and one virtual switch as child devices.  Then the keypad can be used to control a Hampton Bay Zigbee Fan Controller with Light, or control a GE Fan Controller and a GE Light Switch.)*
3. Configure Children as Virtual Button *(This creates a Virtual Button - with 5 buttons - as the child device.)*

## General Installation
The following steps should be followed, no matter which mode you are going to use.
1. On the Hubitat hub, go to the "Drivers Code" page
2. Click "+New Driver"
3. Copy in the code from RFWC5-Keypad.groovy
4. Click "Save"
5. Repeat these first 4 steps for the files RFWC5-VirtualSwitch.groovy and RFWC5D-VirtualFanControllery.groovy.  When you are done, you should have a total of 3 new drivers in your "Drivers Code" page.
6. Wire up your RFWC5 keypad in the wall.
7. Perform a factory reset on the keypad.  Do this by pressing and holding buttons 1, 3, and 5 for 5 seconds at the same time.  Release the buttons.  Then press and release the ALL OFF button.  If successful, all 5 LEDs should blink.
8. Join the keypad to your z-wave network.  On the "Devices" page of Hubitat, click the Discover button, then press the ALL OFF button on the keypad.
9. On the device page in Hubitat, change the device's Type to "Cooper RFWC5 Keypad" and click "Save Device".
10. Refresh the page.
11. Click the "Configure" command button.  This will send a bunch of configuration commands to the keypad.  It will take several minutes.  You can watch the progress by going to Hubitat's "Logs" page.
12. When it is finished, you can do some minimal testing.  Press buttons on the keypad.  On the device page in Hubitat, in the "Current States" section, you should see the value of "Indicators" update to show which lights are turned on.  If this works, proceed to one of the next scenarios.

## Installation Scenario 1 - Virtual Switches Bound to Other Lights and Switches
This is my main use case.  I have three smart lamps in my living room, and they didn't have switches on the wall.  I could control them through an app, or through Alexa, but visitors to the house didn't know what to do with them.  By installing an RFWC5D in the living room wall, there is now a simple and discoverable physical control for them.

1. Open the Hubitat device page for the keypad.
2. Click the command "Configure Child Devices As Virtual Switches"  It will take 3-5 seconds, and then you should see that the value of VirtualDeviceMode is "virtualSwitches".
3. Refresh the page.
4. Now if you scroll down, you should see 5 component devices.  They are virtual switches, and the driver will keep them exactly in sync with the lights on the keypad.  (It is a bi-directional sync too, which will be important in a moment.)
5. Now, we want to make those virtual switches control real devices.  You could do this with Rule Machine, but my Switch Bindings app was made specially for doing this in a fast, simple, and reliable way.  If you haven't already, install the Switch Bindings app from here:  https://github.com/joelwetzel/Hubitat-Switch-Bindings
6. In Switch Bindings, create up to 5 bindings.  In each one, bind one virtual switch from the keypad to the real smart device you want it to control.  In my case, I bound each of the first three virtual switches to one of my smart lamps.

Result:  You should now be able to press the buttons on the keypad to toggle your bound switches/lamps on and off.  Also, because Switch Bindings app is bi-directional, and the virtual switches are bi-directionally synced with the keypad indicators, if you use another means (such as Alexa or scheduled Scenes) to turn the lights on and off, the keypad indicators will stay in sync with what the devices are doing.

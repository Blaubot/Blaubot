Blaubot
=======
Blaubot is a lightweight framework to form small networks via P2P connections such as Bluetooth-RFCOMM, Adhoc-WiFi or simple socket connections.
Blaubot takes care of device discovery and connection establishment with the goal to minimize a developer's boilerplate code to set up these small networks.

Requirements Android
=======
Android 4.0.3 or higher

General Usage
=======
1. Obtain a Blaubot instance from a BlaubotFactory
2. (optional) Register a ILifecycleListener to the Blaubot instance
3. Start Blaubot via .startBlaubot()
4. Create a Channel via blaubot.createChannel(<YourChannelId>)
4.1 Subscribe to the channel via channel.subsribe(<YourListener>) to receive Messages
4.2 or send messages to all subscribers of this channel via channel.post(<YourMessage>)

If you registered the listener in step 2, you will be informed if your own device or other devices join or leave a network.

Quickstart Java
=======
1. Get the General-JAR and add it to your project's dependencies.
2. Create a Blaubot instance using de.hsrm.blaubot.core.BlaubotFactory

You can choose between a fixed set of devices to form net Blaubot network or to dynamically search and discover nearby running Blaubot instances via multicast from the Factory's methods. 
If your targeted environment supports multicasts, this should be the easiest option for you to get your app working.


Quickstart Android
=======

1. Get the Android-JAR and add it to your project's dependencies.
2. Create a Blaubot instance using de.hsrm.blaubot.android.BlaubotFactory

On Android you can rely on the standard ethernet-based Blaubot options provided (see Quickstart Java) but you can also use the Bluetooth enabled Blaubot.
Please note that you have to use a different Factory to create Blaubot instances optimized for the Android platform.


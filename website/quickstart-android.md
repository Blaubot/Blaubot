---
layout: page
title: Quickstart Android
permalink: /quickstart-android/
noToc: true
---

# Gradle configuration
If you use Gradle (or AndroidStudio) to build your Android app, just add the following lines to the `build.gradle` file of your app module (<b>not</b> the root project's build.gradle):

~~~groovy
 
android {
    // [...]
    packagingOptions {
        pickFirst 'META-INF/LICENSE.txt'
        pickFirst 'META-INF/NOTICE.txt'
        pickFirst 'META-INF/INDEX.LIST'
    }
}

dependencies {
    // [...]
    compile 'eu.hgross:blaubot-android:2+'
}
~~~


# Add permissions to AndroidManifest.xml
Add the needed permissions to the the root element of your `AndroidManifest.xml`.
Which of the below permissions you actually require depends on the Blaubot-Instance you intend to use.
For now we use Bluetooth:

~~~xml
 
<!-- Bluetooth -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_PRIVILEGED" />
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.VIBRATE"/>
~~~


# Obtain a Blaubot instance from a BlaubotFactory
For Android, use the `BlaubotAndroidFactory`.
From there you can create a `BlaubotAndroid` instance upon all sorts of technologies like Bluetooth, WiFi, NFC, ...

~~~
 
// Generate a UUID that is unique for your application
// see http://www.famkruithof.net/uuid/uuidgen
final UUID APP_UUID = UUID.fromString("ec127529-2e9c-4046-a5a5-144feb30465f");

// Now create Blaubot using RFCOMM-Bluetooth for the network and discovery
BlaubotAndroid blaubot = BlaubotAndroidFactory.createBluetoothBlaubot(APP_UUID);
~~~

Note that if you use the Bluetooth configuration, Blaubot will only find devices that are paired with each other. 
This means if you want to connect three devices, all of them need to be paired with the others.
You can bypass this requirement by using other beacon implementations (see [documentation](/documentation)). 
We recommend to create a sticky [Android Service](http://developer.android.com/guide/components/services.html) to encapsulate the Blaubot instance.

Make sure to dispatch the lifecycle events of your activity to the instance:

~~~
 
// Example usage of BlaubotAndroid from an activity or service
// onCreate()
   create a blaubot instance
// onResume() / onServiceStart():
   call blaubot.startBlaubot();
   call blaubot.registerReceivers(this);
   call blaubot.setContext(this);
   call blaubot.onResume(this) // if activity
// onPause()
   call blaubot.unregisterReceivers(this);
   call blaubot.onPause(this) // if activity
// onStop()
   call blaubot.stopBlaubot();
// onNewIntent(Intent intent)
   call blaubot.onNewIntent(intent);
~~~



# Start Blaubot
Start blaubot via `blaubot.startBlaubot();`.
It will start to search for nearby devices using the beacons and connect to them using the adapters.

# Create and use a Channel
You can create message channels which are used to send and receive messages.
Once you have created an `IBlaubotChannel` and successfully connected (see next section) you can subscribe to or unsubscribe from it.
If subscribed, messages published to this channel will be received.

~~~
 
// create the channel
final IBlaubotChannel channel = blaubot.createChannel(1);

// Send messages to all subscribers of this channel
channel.publish("Hello world!".getBytes());

// Subscribe to the channel to receive messages
// Note that subscriptions can only be made when connected (after onConnected())!
channel.subscribe(new IBlaubotMessageListener() {
    @Override
    public void onMessage(BlaubotMessage message) {
        // we got a message - our payload is a byte array
        // deserialize
        String msg = new String(message.getPayload());
        // .. do something useful ..
    }
});
~~~

# Register to life cycle events
Attach a `ILifecycleListener` to the Blaubot instance to be informed about certain network events.
You will then be informed, if your own device or other devices join or leave the network.

~~~
 
// attach life cycle listener
blaubot.addLifecycleListener(new ILifecycleListener() {
    @Override
    public void onDisconnected() {
        // THIS device disconnected from the network
    }

    @Override
    public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
        // ANOTHER device disconnected from the network
    }

    @Override
    public void onDeviceJoined(IBlaubotDevice blaubotDevice) {
        // ANOTHER device connected to the network THIS device is on
    }

    @Override
    public void onConnected() {
        // THIS device connected to a network
        // you can now subscribe to channels and use them:
        myChannel.subscribe();
        myChannel.publish("Hello world!".getBytes());
        // onDeviceJoined(...) calls will follow for each OTHER device that was already connected
    }

    @Override
    public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {
        // if the network's king goes down, the prince will rule over the remaining peasants
    }

    @Override
    public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {

    }
});
~~~


# (optional) Connect to BlaubotServer
If you need connectivity to a central server, you can connect your blaubot network with a server instance.
See the general [documentation page ](/documentation) for an example how to do that.

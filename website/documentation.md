---
layout: page
title: Documentation
permalink: /documentation/
---

# Get the library
The preferred way to obtain the library is via the [jCenter Repository](https://bintray.com/bintray/jcenter).
Pick either the Java or Android library from the links below:


| blaubot (plain Java) &nbsp; &nbsp; | [ ![Download](https://api.bintray.com/packages/hgross/maven/blaubot/images/download.svg) ](https://bintray.com/hgross/maven/blaubot/_latestVersion) |
| blaubot-android | [ ![Download](https://api.bintray.com/packages/hgross/maven/blaubot-android/images/download.svg) ](https://bintray.com/hgross/maven/blaubot-android/_latestVersion) |

<br>

<b>Optionally</b> you can add the following components (descriptions in next section):


| blaubot-websockets &nbsp; &nbsp; | [ ![Download](https://api.bintray.com/packages/hgross/maven/blaubot-websockets/images/download.svg) ](https://bintray.com/hgross/maven/blaubot-websockets/_latestVersion) |
| blaubot-jsr82  | [ ![Download](https://api.bintray.com/packages/hgross/maven/blaubot-jsr82/images/download.svg) ](https://bintray.com/hgross/maven/blaubot-jsr82/_latestVersion) |


<br>
Click the "Download" button and then click the "SET ME UP!"-button for the Gradle/Maven markup to add the repository to your build system or alternatively just download the JAR/AAR-Files from there.


## Maven artifacts for blaubot
If you intend to use the plain java library with Maven or Gradle, you need the artifact `"eu.hgross:blaubot:2+"`.
You might want to change the 2+ to a specific version.
A list of all artifacts and their external dependencies:

|Artifact | Description | Dependencies |
|---------|-------------|-----------------------|
| <small>eu.hgross:blaubot:2+</small> | The Java library | [Google GSON](https://code.google.com/p/google-gson/), [JmDNS](http://jmdns.sourceforge.net/)|
| <small>eu.hgross:blaubot-android:2+</small> | The Android library (including Bluetooth, NFC, ...) | [Apache Commons Collections](https://commons.apache.org/proper/commons-collections/) |
| <small style="white-space:nowrap;">eu.hgross:blaubot-websockets:2+</small> | WebSockets-Adapter for blaubot and blaubot-android |  [Netty](http://netty.io/) |
| <small>eu.hgross:blaubot-jsr82:2+</small> | Bluetooth-Adapter for the Java library (Windows, Linux, MacOS, <b>not</b> compatible with Android)| JSR82 implementation (i.e. [Bluecove](http://bluecove.org/)) |

<br>

## Gradle configuration for Android
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
    compile 'eu.hgross:blaubot-websockets:2+' // optional
}

~~~


## Add permissions to AndroidManifest.xml
<small>(Android only)</small>
Add the neded permissions to the the root element of your `AndroidManifest.xml`.
Which of the below permissions you actually require depends on the Blaubot-Instance you intend to use.

~~~xml
 
<!-- NFC -->
<uses-permission android:name="android.permission.NFC" />
<uses-permission android:name="android.permission.VIBRATE"/>

<!--  Multicast- and Bonjour-Beacon  -->
<uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />

<!-- WIFI Direct, Ethernet, Multicast- and Bonjour-Beacon -->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_WIFI_STATE" />
<uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />

<!-- Bluetooth -->
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_PRIVILEGED" />
<uses-permission android:name="android.permission.INTERNET" />
~~~

# General usage

## Obtain a Blaubot instance from a BlaubotFactory
For Android, use the `BlaubotAndroidFactory` and for plain Java the `BlaubotFactory`.
From there you can create a `Blaubot` instance upon all sorts of technologies like Bluetooth, WiFi, NFC, ...
Blaubot discovers nearby devices and creates an adhoc network using a pre-defined `BlaubotAdapter`.
The factory methods provide many shortcut methods to create different configurations regarding which technologies are used for device discovery or to create the actual network.

![Beacons and adapter](/assets/images/beacons_connector_acceptor_concept.png "Beacons and adapter")

Usable implementations for discovery (beacons):

- Bluetooth (RFCOMM; Round-Robin)
- Multicast (requires IP-Network)
- Bonjour (requires IP-Network)
- WifiDirect (Android only)
- NFC (Android only)
- GeoLocation Server (requires IP-Network and a Server)

And for the actual network (adapters):

- Bluetooth (RFCOMMM)
- TCP-Sockets (requires IP network)
- WebSockets (requires IP-network)
- WifiAp (experimental; Android only)




~~~
 
// Generate a UUID that is unique for your application
// see http://www.famkruithof.net/uuid/uuidgen
final UUID APP_UUID = UUID.fromString("ec127529-2e9c-4046-a5a5-144feb30465f");

// Now create Blaubot using an existing wifi network and multicasts to discover
Blaubot blaubot = BlaubotFactory.createEthernetBlaubot(APP_UUID);
// or RFCOMM-Bluetooth (Android) for the network and discovery
BlaubotAndroid blaubot = BlaubotAndroidFactory.createBluetoothBlaubot(APP_UUID);
// or RFCOMM-Bluetooth (Android) for the network and NFC-Interactions for discovery
BlaubotAndroid blaubot = BlaubotAndroidFactory.createBluetoothBlaubotWithNFCBeacon(APP_UUID, [...]);
~~~

Note that if you use the Bluetooth-Only configuration, Blaubot will only find devices that are paired with each other. This means if you want to connect three devices, all of them need to be paired with the others.
For Android-Apps, we recommend to create a sticky [Android Service](http://developer.android.com/guide/components/services.html) to encapsulate the Blaubot instance.

If you use a `BlaubotAndroid` instance (not `Blaubot`), make sure to dispatch the lifecycle events of your activity to the instance:

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



## Start Blaubot
Start blaubot via `blaubot.startBlaubot();`.
It will start to search for nearby devices using the beacons and connect to them using the adapters.

## Create and use a Channel
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

## Register to life cycle events
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

## Configure channels
Whenever you publish a message to a channel, the messages are queued to send and will eventually be picked for transmission.
You can influence how messages are picked and sent.
You do this by assigning priorities, capacities and message-rate limits to the channel's configuration.

~~~
 
// force a minimum delay of 500 ms between messages
channel.getChannelConfig().setMessageRateLimit(500);
// set a picking strategy: When the messages in the queue are picked to send
// (with a minimum time period of 500 ms in between), just take the newest and
// discard all older messages
channel.getChannelConfig().setMessagePickerStrategy(BlaubotChannelConfig.MessagePickerStrategy.DISCARD_OLD);
// send the messages with a low priority
channel.getChannelConfig().setPriority(BlaubotMessage.Priority.LOW);
// limit the message queue capacity to 10 messages
channel.getChannelConfig().setQueueCapacity(10);

// reduce network traffic for some cases where we publish to 
// a channel to which we are subscribed.
channel.getChannelConfig().setTransmitReflexiveMessages(false); 
// always publish messages, even if we call publish() on a 
// channel without any subscribers
channel.getChannelConfig().setTransmitIfNoSubscribers(true);
~~~

# DebugViews
The asynchronous and distributed nature of networking has its very own challenges when it comes to debugging.
Blaubot provides built-in DebugViews wich can be attached to the Blaubot instance to get more detailed insights into the middleware.
There is a "main" DebugView which contains all implemented Views at once but all of the views can also be used standaone (like the start/stop button or the ConnectionView to simulate disconnects).

| ![DebugView]({{ site.baseurl }}/assets/images/screen_blaubotApp_king_websocketConnection.png) |  ![DebugView]({{ site.baseurl }}/assets/images/screen_blaubotApp_beaconAndAdminMessages.png) |

<br>


### Android example
Add the built-in view to your app's xml and register your Blaubot instance with it.

~~~xml
 
<!-- Add this to your app's ui -->
<eu.hgross.blaubot.android.views.DebugView
    android:id="@+id/debugView"
    android:layout_width="fill_parent"
    android:layout_height="wrap_content" />
~~~

~~~java
 
// in your onCreate() or onServiceConnected() or ...
mDebugView  = (DebugView) findViewById(R.id.debugView);
mDebugView.registerBlaubotInstance(blaubot);

[...]

// onStop()
mDebugView.unregisterBlaubotInstance();
~~~

### Swing (Java) example
The base Blaubot jar contains a Swing implementation for most of the android views.

~~~java
 
SwingUtilities.invokeLater(new Runnable() {
    @Override
    public void run() {
        SwingDebugView sdb = new SwingDebugView();
        sdb.registerBlaubotInstance(yourBlaubotInstance);

        // to view it in its own window ...
        JFrame frame = new JFrame();
        frame.add(sdb);
        frame.pack();
        frame.setVisible(true);
    }
});
~~~


# (optional) Usage of BlaubotServer
Blaubot offers an integrated solution to connect to a central server that may be needed by your applicaion.
The server instance can be easily obtained via one of the shortcut-methods of the `BlaubotFactory`.


## The server side
~~~java
 
// create a BlaubotServer using the WebSocketAdapter on port 8080
final int serverPort = 8080;
final int BlaubotDevice serverDevice = new BlaubotDevice("YourServerUniqueDeviceId");
final BlaubotServer websocketServer = BlaubotFactory.createBlaubotWebsocketServer(serverDevice, serverPort);

// attach a listener
websocketServer.addServerLifeCycleListener(new IBlaubotServerLifeCycleListener () {
    @Override
    public void onKingdomConnected(BlaubotKingdom kingdom) {
        // a Blaubot network established the connection to the server
        // you can now add an IBlaubotLifeCycleListeners to the kindom instance
        // and create channels

        // create a channel
        final short yourChannelId = 1;
        final IBlaubotChannel yourChannel = kingdom.getChannelManager().getOrCreateChannel(yourChannelId);

        // we send something
        yourChannel.publish("Hello World".getBytes());

        // and attach a listener to receive messages
        yourChannel.subscribe(new IBlaubotMessageListener() {
            @Override
            public void onMessage(BlaubotMessage blaubotMessage) {
                byte[] receivedData = blaubotMessage.getPayload();
                System.out.println(new String(receivedData));
            }
        });

        // we can also react to the network events
        kingdom.addLifecycleListener(new ILifecycleListener() {
            @Override
            public void onDisconnected() {
               // THIS device disconnected from the network => The server connection is down.
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
                // onDeviceJoined(...) calls will follow for each OTHER device that was already connected
            }

            @Override
            public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {

            }

            @Override
            public void onPrinceDeviceChanged(IBlaubotDevice oldPrince,	IBlaubotDevice newPrince) {
               // if the network's king goes down, the prince will rule over the remaining peasants
            }
        });
    }

    @Override
    public void onKingdomDisconnected(BlaubotKingdom kingdom) {
        // a Blaubot network's connection to the server is down

    }
});

websocketServer.startBlaubotServer();
~~~


## The client side
The client side works exactly as described at the top of this page except that we add a `BlaubotServerConnector` to the `Blaubot` instance.
For example:

~~~java
 
// We create a Blaubot instance using an existing wifi network and multicasts to discover
UUID APP_UUID = UUID.fromString("ec127529-2e9c-4046-a5a5-144feb30465f");
Blaubot blaubot = BlaubotFactory.createEthernetBlaubot(APP_UUID);

// we create a connector for our server side
BlaubotServerConnector connector;
String hostnameOrIp = "www.yourHost.de";                  // or IPv4 Address
int websocketServerPort = 8080;                           // has to match the server's port
String serverUniqueDeviceId = "YourServerUniqueDeviceId"; // has to match the server's id set by us (see server side above)
IBlaubotDevice ownDevice = blaubot.getOwnDevice();
connector = BlaubotFactory.createWebSocketServerConnector(hostOrIp, websocketServerPort, "/blaubot", ownDevice, serverUniqueDeviceId);

// we add it to our BlaubotInstance
blaubot.setServerConnector(connector);

// now we can activate/deactivate the connector as we like
// setting it to false also disconnects a server connection from our device
connector.setDoConnect(true);

// we create a the channel with same id as the server (channel 1, see above)
final IBlaubotChannel channel = blaubot.createChannel(1);

// if we connect to a network, we send a hello world once
blaubot.addLifecycleListener(new ILifecycleListener() {
    @Override
    public void onDisconnected() {
    }

    @Override
    public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
    }

    @Override
    public void onDeviceJoined(IBlaubotDevice blaubotDevice) {
    }

    @Override
    public void onConnected() {
        channel.publish("Hello Server!".getBytes());
    }

    @Override
    public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {
    }

    @Override
    public void onPrinceDeviceChanged(IBlaubotDevice oldPrince,	IBlaubotDevice newPrince) {
    }
});

// fire it up
blaubot.startBlaubot();
~~~

The server will then appear as a standard `IBlaubotDevice` in your `IBlaubotLifeCycleListener`-Events - just compare the uniqueDeviceId with your server's device id.
Note that you can have multiple devices on the network using the server connector.
Blaubot picks one of the available connections to the server and uses it until it goes down.
This means that as long as at least one device of the Blaubot network has a connection to the server, all devices can communicate with the server through the channels.



---
layout: page
title: FAQ
---

# How can I get the originator's device id from a received message?
If you need this information, you have to include it into your own messages.
You can get the unique device id of your `Blaubot` or `BlaubotKingdom` instance like this:

~~~java
 
// For Blaubot
Blaubot blaubot = // ...
String id = blaubot.getOwnDevice().getUniqueDeviceId();

// For BlaubotKingdom
BlaubotKingdom kingdom = // ...
String id = kingdom.getOwnDevice().getUniqueDeviceId();

~~~
 
# How can I publish messages to a subscribed channel without receiving my own message?
For each `publish()` signature vailable from `IBlaubotChannel` there is an equivalent which allows you to set a flag `excludeSender`.

~~~java
 
// will not be received by the publishing (this) device
myChannel.publish("Exclude me!".getBytes(), true);
~~~

# Can I use Bluetooth to discover nearby devices and WiFi to create a network?
Yes, just use a Bluetooth-Beacon implementation and the EthernetAdapter.
For Android, use `BlaubotBluetoothBeacon` from the `blaubot-android` package.

~~~java

// 
int acceptorPort = 17171;
IBlaubotDevice ownDevice = new BlaubotDevice();
BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(YOUR_APP_UUID);
InetAddress ownInetAddr = BlaubotFactory.getLocalIpAddress();

// create Blaubot instance
IBlaubotAdapter adapter = new BlaubotEthernetAdapter(ownDevice, uuidSet, acceptorPort, ownInetAddr);
IBlaubotBeacon beacon = new BlaubotBluetoothBeacon();
Blaubot blaubot = BlaubotFactory.createBlaubot(YOUR_APP_UUID, ownDevice, adapter, beacon);

// fire it up
blaubot.startBlaubot();

~~~

# Can I connect from Windows/Linux/MacOS to Android using Bluetooth?
Yes, use the `BlaubotBluetoothAdapter` from the `blaubot-android` package for Android and the `BlaubotJsr82BluetoothAdapter` from the `blaubot-jsr82` package for Windows/Linux/MacOS.
Note that you have to add `bluecove` (and on Linux additionally `bluecove-gpl`) to your dependencies to get it running on Windows/Linux/MacOS.
[BlueCove](http://bluecove.org) hasn't released an official version since 2009 and you will have difficulties using the older releases on 64-bit systems.
Use one of the newer [BlueCove snapshot realeases](http://snapshot.bluecove.org/) instead.

# I published a message to a channel before the `onConnected()` or after the `onDisconnected()` event. How do I prevent them from being sent on connection? 
You can clear the message queue of the channel:
 
~~~java
 
myChannel.clearMessageQueue();
~~~

# How can I send a File?
Here is an example using Bluetooth on Android.
First we create a Blaubot instance using Bluetooth and NFC for discovery (Beacons) and Bluetooth for the main network communication (Adapter).


~~~java
 
UUD MY_UUID = UUID.fromString("33bb1246-1472-11e5-b60b-1697f925ec7b");

// onCreate() or in a service, we create a blaubot instance
// using Bluetooth to form a network and Bluetooth + NFC to find devices
IBlaubotDevice ownDevice = new BlaubotDevice();
BlaubotUUIDSet uuidSet = new BlaubotUUIDSet(MY_UUID);

BlaubotBluetoothAdapter bluetoothAdapter = new BlaubotBluetoothAdapter(uuidSet, ownDevice);
BlaubotNFCBeacon nfcBeacon = new BlaubotNFCBeacon();
BlaubotBluetoothBeacon bluetoothBeacon = new BlaubotBluetoothBeacon();
this.mBlaubot = BlaubotAndroidFactory.createBlaubot(MY_UUID, ownDevice, adapter, nfcBeacon, bluetoothBeacon);

// start to connect
this.mBlaubot.startBlaubot();

// create a channel to which we will send the file
IBlaubotChannel fileChannel = this.mBlaubot.createChannel(1);
~~~

We can now publish messages to the channel.
They will be send after we got an `onConnected()` event. 

~~~java
 
// convert your file to its bytes
File yourFile = // ... however you get it
// see http://stackoverflow.com/questions/858980/file-to-byte-in-java
byte[] fileBytes = //...

// send it to all connected devices, except the sending device
fileChannel.publish(fileBytes, true);
~~~

To receive the file, you have to subscribe and attach a `IBlaubotMessageListener` to the channel.

~~~java
 
// to receive it on the other device, do this:
// subscribe to the channel
fileChannel.subscribe(new IBlaubotMessageListener() {
    @Override
    public void onMessage(BlaubotMessage message) {
        // extract your bytes from the message
        byte[] fileBytes = message.getPayload();
        // .. do something useful or write it to a file again
        // to write it to a file
        File file = new File(yourFilePath);
        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(file));
        bos.write(fileBytes);
        bos.flush();
        bos.close();
    }
});
~~~

# I never get `onConnected()`-Events when I register my `LifecycleListener` in `onKingomConnected(BlaubotKingdom)` to the `BlaubotKingdom`.
And you never will.
The `onKingdomConnected` event happens after `onConnected()`.
You may also have missed some `onDeviceConnected()` events at this point.
But you can poll the connected devices from the `KingdomCensusLifecycleListener` retrievable from the `BlaubotKingdom`:
 
~~~java
   
blaubotServer.addServerLifeCycleListener(new IBlaubotServerLifeCycleListener() {
    @Override
    public void onKingdomConnected(final BlaubotKingdom kingdom) {
        // contains all currently connected devices
        Set<IBlaubotDevice> devices = kingdom.getKingdomCensusLifecycleListener().getDevices();
    }
    
    @Override
    public void onKingdomDisconnected(BlaubotKingdom kingdom) {
       // ...
    }    
 });
~~~
 
 
# I want to know which devices are currently connected.
You can implement an `ILifecycleListener` and attach it (pre-start) to your `Blaubot` or `BlaubotKingdom` instance to keep track of that via the `onDeviceJoined(IBlaubotDevice)` and `onDeviceLeft(IBlaubotDevice)` methods or just use the built-in `KingdomCensusLifecycleListener`:
 
~~~java
    
IBlaubotDevice ownDevice = blaubot.getOwnDevice();
KingdomCensusLifecycleListener censusListener = new KingdomCensusLifecycleListener(ownDevice);
blaubot.addLifecycleListener(censusListener);
 
// contains all currently connected devices
Set<IBlaubotDevice> devices = censusListener.getDevices();
~~~

# How do I know if the BlaubotServer is reachable?
When any of the network's devices could establish a connection to your configured BlaubotServer, you will get an `onDeviceJoined`-Event on all Devices in the network.
Since you know your server's UniqueDeviceId (you configured it with the `ServerConnector` and on creation of the `BlaubotServer`), you can just compare the UniqueDeviceIds to tell if a newly joined device is the server or not.

~~~java
    
Blaubot blaubot = /* ... creation and configuration of Blaubot ... */;
/* ... create and add your ServerConnector here ... */
final String serverUniqueDeviceId = blaubot.getServerConnector().getServerUniqueDeviceId();

blaubot.addLifecycleListener(new LifecycleListenerAdapter {
    @Override
    public void onDeviceJoined(IBlaubotDevice device) {
    	if(device.getUniqueDeviceId().equals(serverUniqueDeviceId)) {
	    System.out.println("server is now part of the network");
	}
    }

    @Override
    public void onDeviceLeft(IBlaubotDevice device) {
        if(device.getUniqueDeviceId().equals(serverUniqueDeviceId)) {
	    System.out.println("server is not part of the network anymore");
	}
    }
});
~~~
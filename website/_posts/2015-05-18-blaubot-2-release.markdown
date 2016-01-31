---
layout: post
title:  "Blaubot 2.0.0.alpha"
date:   2015-05-19
categories: release
---
The first alpha release of Blaubot 2 is now available from jCenter and MavenCentral.
It contains plenty of new features like Bluetooth support for Windows, Mac OS, and Linux based on [BlueCove](http://bluecove.org/) and a WebSocket adapter based on [Netty](http://netty.io/).
You can now define a central server instance using the usual connectors and acceptors to which all of the Blaubot instances of a network try to establish a connection.
If one instance succeeds, you are able to reach your server from all instances (relay).
Note that the factory API will change with the next alpha release.
Check the [quickstart page](/quickstart-android) for example code and download links.

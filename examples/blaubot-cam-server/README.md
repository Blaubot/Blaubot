This is an example application to play around with the [Blaubot project](http://blaubot.hgross.eu).

# BlaubotCamServer
The BlaubotCamServer can be started headerless or with a gui.
There are plenty of options to configure the server (see Options section).

## Build
The BlaubotCamServer can be build using gradle.
The resulting distribution will contain all dependency jars and exectuable scripts for Unix and Windows systems.

cd into root project (not blaubotcamserver)
./gradlew --daemon installDist

## Run
cd blaubotcamserver/build/install/blaubotcamserver/bin
./blaubotcamserver
# or without GUI
./blaubotcamserver -n
-> Open your browser at http://localhost:8081

## Options
Options:
 -ds,--dontServe                              Do not start the http
                                              interface.
    --ipCamHttpPort <httpPort>                The http port used to serve
                                              images received from the
                                              video channel (default:
                                              8081).
 -l,--listCams                                Lists available cams and
                                              their ids connected to THIS
                                              host.
 -n,--noUi                                    Do not use any user
                                              interface.
 -nc,--noCam                                  If set, no cam will be
                                              opened. Overrides useCam
 -ncui,--noCamUi                              Do not use the cam ui (no ui
                                              to display received images)
 -nlcui,--noWebcamPreview                     Do not show a preview ui for
                                              the locally used webcam (if
                                              any).
 -nsui,--noServerView                         Do not use the debug ui that
                                              visualizes the blaubot
                                              server state.
    --serverUniqueDeviceId <uniqueDeviceId>   The server's unique device
                                              id (default:
                                              BlaubotCamServer).
    --useCam <webcamId>                       Use the specified cam (use
                                              --listCams for ids; defaults
                                              is the first discovered
                                              cam).
    --videoChannelId <channelId>              The channel id used to
                                              receive ImageMessages
                                              (default: 1).
    --websocketPort <webSocketPort>           The port to be used by the
                                              websocket acceptor (default:
                                              8080).

## Important notes
BlaubotCamServer uses webcam-capture (https://github.com/sarxos/webcam-capture) to read webcams.
There are known bugs that produce huge cpu load on MacOS (but not on Windows): https://github.com/sarxos/webcam-capture/issues/318 
If you experience huge cpu load, use the -nc option to disable the local usb cam.
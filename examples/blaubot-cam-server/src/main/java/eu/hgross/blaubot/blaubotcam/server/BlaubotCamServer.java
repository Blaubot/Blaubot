package eu.hgross.blaubot.blaubotcam.server;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamEvent;
import com.github.sarxos.webcam.WebcamListener;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamResolution;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JToggleButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import eu.hgross.blaubot.blaubotcam.server.model.ImageMessage;
import eu.hgross.blaubot.blaubotcam.server.ui.VideoViewerPanel;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.BlaubotFactory;
import eu.hgross.blaubot.core.BlaubotKingdom;
import eu.hgross.blaubot.core.BlaubotServer;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.IBlaubotServerLifeCycleListener;
import eu.hgross.blaubot.core.ILifecycleListener;
import eu.hgross.blaubot.messaging.BlaubotChannelConfig;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.ui.BlaubotServerPanel;

public class BlaubotCamServer {
    /**
     * The channel on which image data is sent
     */
    public static final short DEFAULT_VIDEO_CHANNEL_ID = 1;
    public static final int DEFAULT_BLAUBOT_WEBSOCKET_PORT = 8080;
    public static final String DEFAULT_BLAUBOT_SERVER_UNIQUE_DEVICE_ID = "BlaubotCamServer";
    public static final int DEFAULT_JPEG_QUALITY = 20;

    /**
     * The port over which the received image data is served as mjpeg stream.
     */
    public static final int DEFAULT_HTTP_PORT_IP_CAM_SERVER = 8081;


    /*
        CLI OPTIONS FROM HERE
     */
    private static final String CLI_OPTION_DONT_SERVE = "dontServe";
    private static final String CLI_OPTION_NO_SERVER_VIEW = "noServerView";
    private static final String CLI_OPTION_NO_CAM_UI = "noCamUi";
    private static final String CLI_OPTION_NO_UI = "noUi";
    private static final String CLI_OPTION_SERVER_UNIQUE_DEVICE_ID = "serverUniqueDeviceId";
    private static final String CLI_OPTION_WEBSOCKET_PORT = "websocketPort";
    private static final String CLI_OPTION_VIDEO_CHANNEL_ID = "videoChannelId";
    private static final String CLI_OPTION_IP_CAM_HTTP_PORT = "ipCamHttpPort";

    private static final String CLI_OPTION_LIST_WEBCAMS = "listCams";
    private static final String CLI_OPTION_USE_WEBCAM = "useCam";
    private static final String CLI_OPTION_DONT_USE_WEBCAM = "noCam";
    private static final String CLI_OPTION_NO_WEBCAM_PREVIEW= "noWebcamPreview";


    public static void main(String[] args) throws ClassNotFoundException {
        // Command line parsing ...
        Options options = new Options();
        options.addOption("n", CLI_OPTION_NO_UI, false, "Do not use any user interface.");
        options.addOption("ncui", CLI_OPTION_NO_CAM_UI, false, "Do not use the cam ui (no ui to display received images)");
        options.addOption("nsui", CLI_OPTION_NO_SERVER_VIEW, false, "Do not use the debug ui that visualizes the blaubot server state.");
        options.addOption("ds", CLI_OPTION_DONT_SERVE, false, "Do not start the http interface.");
        options.addOption("nlcui", CLI_OPTION_NO_WEBCAM_PREVIEW, false, "Do not show a preview ui for the locally used webcam (if any).");
        options.addOption(Option.builder().longOpt(CLI_OPTION_SERVER_UNIQUE_DEVICE_ID).desc("The server's unique device id (default: " + DEFAULT_BLAUBOT_SERVER_UNIQUE_DEVICE_ID + ").").hasArg().argName("uniqueDeviceId").type(String.class).build());
        options.addOption(Option.builder().longOpt(CLI_OPTION_WEBSOCKET_PORT).desc("The port to be used by the websocket acceptor (default: " + DEFAULT_BLAUBOT_WEBSOCKET_PORT + ").").hasArg().argName("webSocketPort").type(Number.class).build());
        options.addOption(Option.builder().longOpt(CLI_OPTION_VIDEO_CHANNEL_ID).desc("The channel id used to receive ImageMessages (default: " + DEFAULT_VIDEO_CHANNEL_ID + ").").hasArg().argName("channelId").type(Number.class).build());
        options.addOption(Option.builder().longOpt(CLI_OPTION_IP_CAM_HTTP_PORT).desc("The http port used to serve images received from the video channel (default: " + DEFAULT_HTTP_PORT_IP_CAM_SERVER + ").").hasArg().argName("httpPort").type(Number.class).build());

        options.addOption("l", CLI_OPTION_LIST_WEBCAMS, false, "Lists available cams and their ids connected to THIS host.");
        options.addOption("nc", CLI_OPTION_DONT_USE_WEBCAM, false, "If set, no cam will be opened. Overrides " + CLI_OPTION_USE_WEBCAM);
        options.addOption(Option.builder().longOpt(CLI_OPTION_USE_WEBCAM).desc("Use the specified cam (use --" + CLI_OPTION_LIST_WEBCAMS + " for ids; defaults is the first discovered cam).").hasArg().argName("webcamId").type(Number.class).build());

        CommandLineParser parser = new DefaultParser();



        try {
            // parse the command line arguments
            CommandLine cli = parser.parse(options, args);

            // fetch the parsed command line arguments
            boolean listOfWebcamsRequested = cli.hasOption(CLI_OPTION_LIST_WEBCAMS);
            boolean noUserInterface = cli.hasOption(CLI_OPTION_NO_UI);
            boolean showDebugView = !noUserInterface && !cli.hasOption(CLI_OPTION_NO_SERVER_VIEW);
            final boolean showVideoViewer = !noUserInterface && !cli.hasOption(CLI_OPTION_NO_CAM_UI);
            boolean startIpCamServer = !cli.hasOption(CLI_OPTION_DONT_SERVE);
            boolean showWebcamUi = !noUserInterface && !cli.hasOption(CLI_OPTION_NO_WEBCAM_PREVIEW);
            boolean useWebCam = !cli.hasOption(CLI_OPTION_DONT_USE_WEBCAM);

            final int webSocketPort = cli.hasOption(CLI_OPTION_WEBSOCKET_PORT) ? ((Number) cli.getParsedOptionValue(CLI_OPTION_WEBSOCKET_PORT)).intValue() : DEFAULT_BLAUBOT_WEBSOCKET_PORT;
            final int ipCamServerPort = cli.hasOption(CLI_OPTION_IP_CAM_HTTP_PORT) ? ((Number) cli.getParsedOptionValue(CLI_OPTION_IP_CAM_HTTP_PORT)).intValue() : DEFAULT_HTTP_PORT_IP_CAM_SERVER;
            final String serverUniqueDeviceId = cli.hasOption(CLI_OPTION_SERVER_UNIQUE_DEVICE_ID) ? (String) cli.getParsedOptionValue(CLI_OPTION_SERVER_UNIQUE_DEVICE_ID) : DEFAULT_BLAUBOT_SERVER_UNIQUE_DEVICE_ID;
            final short videoChannelId = cli.hasOption(CLI_OPTION_VIDEO_CHANNEL_ID) ? ((Number) cli.getParsedOptionValue(CLI_OPTION_VIDEO_CHANNEL_ID)).shortValue() : DEFAULT_VIDEO_CHANNEL_ID;
            final int webCamDeviceToUse = cli.hasOption(CLI_OPTION_USE_WEBCAM) ? ((Number)cli.getParsedOptionValue(CLI_OPTION_USE_WEBCAM)).intValue() : -1;


            // when a list of webcams is requested, check for available cams, print out the list and exit
            if (listOfWebcamsRequested) {
                printCamList();
                // nothing more to do
                System.exit(0);
            }

            // choose the webcam
            final List<Webcam> webcams = Webcam.getWebcams();
            final Webcam webcam;
            if (useWebCam && webCamDeviceToUse >= 0) {
                // search for this webcam
                if (webCamDeviceToUse < webcams.size()) {
                    webcam = webcams.get(webCamDeviceToUse);
                    System.out.println("Using cam " + webcam.getName() + ".");
                } else {
                    webcam = null;
                    System.out.println("Error: Cam with number " + webCamDeviceToUse + " does not exist.");
                    System.out.println("Available cams are:");
                    printCamList();
                    System.exit(1);
                }
            } else if (useWebCam) {
                webcam = Webcam.getDefault();
            } else {
                webcam = null;
            }

            // we set up a imageWriter for jpeg compression
            final ImageWriter imageWriter = ImageIO.getImageWritersByFormatName("jpeg").next();
            final ImageWriteParam imageWriterParam = imageWriter.getDefaultWriteParam();
            imageWriterParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); // Needed see javadoc
            imageWriterParam.setCompressionQuality((float) DEFAULT_JPEG_QUALITY/100f); // Highest quality

            // global boolean by which it is decided, if we publish images from our own webcam to the video channel
            final AtomicBoolean publishCamImagesToChannels = new AtomicBoolean(false);
            // open webcam, if any
            if (webcam != null) {
                webcam.setViewSize(WebcamResolution.VGA.getSize());
                // open in async mode
                final boolean opened = webcam.open(true);

                if (!opened) {
                    // failed to open the webcam
                    System.out.println("ERROR: could not open webcam device: " + webcam.getName());
                }

                // open preview ui, if cam could be opened and was not explicitly deactivated
                if (showWebcamUi && opened) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {


                            final WebcamPanel webcamPanel = new WebcamPanel(webcam);
                            webcamPanel.setFPSLimited(true);
//                            webcamPanel.setFPSLimit(1);
//                            webcamPanel.setFPSDisplayed(true);
//                            webcamPanel.setDisplayDebugInfo(true);
//                            webcamPanel.setImageSizeDisplayed(true);
//                            webcamPanel.setMirrored(true);

                            
                            // ToggleButton to enable/disable sending the cam images to the connected devices
                            final String buttonText = "publishing to connected devices";
                            final JToggleButton toggleSendVideoButton = new JToggleButton("", false);
                            final ActionListener l = new ActionListener() {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    final boolean selected = toggleSendVideoButton.isSelected();
                                    publishCamImagesToChannels.set(selected);
                                    toggleSendVideoButton.setText(selected ? buttonText : "not " + buttonText);
                                }
                            };
                            // set initial state text and attach
                            l.actionPerformed(null);
                            toggleSendVideoButton.addActionListener(l);

                            // Jpeg quality slider
                            final JSlider jpegQualitySlider = new JSlider(JSlider.HORIZONTAL, 1, 100, (int) (imageWriterParam.getCompressionQuality() * 100f));
                            jpegQualitySlider.setMajorTickSpacing(20);
                            jpegQualitySlider.setMinorTickSpacing(5);
                            jpegQualitySlider.setPaintTicks(true);
                            jpegQualitySlider.setPaintLabels(true);
                            Hashtable labelTable = new Hashtable();
                            labelTable.put(new Integer(1), new JLabel("Low quality"));
                            labelTable.put(new Integer(100), new JLabel("High quality"));
                            jpegQualitySlider.setLabelTable(labelTable);
                            jpegQualitySlider.addChangeListener(new ChangeListener() {
                                @Override
                                public void stateChanged(ChangeEvent e) {
                                    final int quality = jpegQualitySlider.getValue();
                                    final float qualityF = (float) quality / 100f;
                                    imageWriterParam.setCompressionQuality(qualityF);
                                }
                            });

                            // button to enable/disable webcam
                            JButton toggleWebCam = new JButton("Toggle camera");
                            toggleWebCam.addActionListener(new ActionListener() {
                                @Override
                                public void actionPerformed(ActionEvent e) {
                                    if (webcamPanel.isStarted()) {
                                        webcamPanel.stop();
                                    } else {
                                        webcamPanel.start();
                                    }
                                }
                            });
                            
                            // put it all together in a JFrame
                            JPanel mainPanel = new JPanel();
                            mainPanel.setLayout(new GridBagLayout());
                            GridBagConstraints gbc = new GridBagConstraints();
                            gbc.gridx = 0;
                            gbc.gridy = 0;
                            mainPanel.add(jpegQualitySlider, gbc);
                            gbc.gridy += 1;
                            JPanel buttonPanel = new JPanel();
                            buttonPanel.add(toggleWebCam);
                            buttonPanel.add(toggleSendVideoButton);
                            mainPanel.add(buttonPanel, gbc);
                            gbc.gridy += 1;
                            mainPanel.add(webcamPanel, gbc);
                            gbc.gridy += 1;
                            JFrame window = new JFrame("Cam " + webcam.getName() + " preview");
                            window.add(mainPanel);
                            window.setResizable(true);
                            window.pack();
                            window.setVisible(true);
                            window.addWindowListener(new WindowAdapter() {
                                @Override
                                public void windowClosing(WindowEvent e) {
                                    // TODO we are currently not able to bring it back ...
                                    
                                }
                            });
                        }
                    }).start();
                }
            }
            
            // create the server and service
            final BlaubotServer websocketServer = BlaubotFactory.createBlaubotWebsocketServer(new BlaubotDevice(serverUniqueDeviceId), webSocketPort);
            final eu.hgross.blaubot.blaubotcam.server.service.IPCamServer ipCamServer = new eu.hgross.blaubot.blaubotcam.server.service.IPCamServer(ipCamServerPort);

            // start, if not explicitly prevented
            if (startIpCamServer) {
                ipCamServer.startHTTPServer();
                System.out.println("IpCamServer started. Access it via http://localhost:" + DEFAULT_HTTP_PORT_IP_CAM_SERVER);
            }

            // open debug view, if not explicitly prevented
            if (showDebugView) {
                BlaubotServerPanel.createAndshowGui(websocketServer);
            }

            /**
             * A mapping to remember the windows we opened for each kingdom.
             */
            final ConcurrentHashMap<BlaubotKingdom, JFrame> frames = new ConcurrentHashMap<>();

            websocketServer.addServerLifeCycleListener(new IBlaubotServerLifeCycleListener() {
                @Override
                public void onKingdomConnected(final BlaubotKingdom kingdom) {
                    final IBlaubotChannel videoChannel = kingdom.getChannelManager().createOrGetChannel(videoChannelId);
                    videoChannel.getChannelConfig().setMessagePickerStrategy(BlaubotChannelConfig.MessagePickerStrategy.DISCARD_OLD);
                    videoChannel.getChannelConfig().setMessageRateLimit(200);
                    videoChannel.getChannelConfig().setQueueCapacity(10);
                    videoChannel.addMessageListener(ipCamServer);

                    // if a webcam is available, add a listener to publish their data to the channel
                    if (webcam != null && webcam.isOpen()) {
                        webcam.addWebcamListener(new WebcamListener() {
                            @Override
                            public void webcamOpen(WebcamEvent we) {

                            }

                            @Override
                            public void webcamClosed(WebcamEvent we) {

                            }

                            @Override
                            public void webcamDisposed(WebcamEvent we) {

                            }

                            @Override
                            public void webcamImageObtained(WebcamEvent we) {
                                // do nothing, if not explicitly activated
                                if (!publishCamImagesToChannels.get()) {
                                    return;
                                }
                                // convert to jpeg
                                // TODO this is messy, because we convert the jpg for each connected kingdom! Put some caching between listener calls and publish
                                final BufferedImage image = we.getImage();


                                try {
                                    final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                                    final ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(byteArrayOutputStream);
                                    synchronized (imageWriter) {
                                        imageWriter.setOutput(imageOutputStream);
                                        try {
                                            imageWriter.write(null, new IIOImage(image, null, null), imageWriterParam);
                                        } finally {
                                            imageOutputStream.flush();
                                        }
                                    }
                                    final byte[] bytes = byteArrayOutputStream.toByteArray();
//                                    final byte[] bytes = ImageUtils.toByteArray(image, ImageUtils.FORMAT_JPG);
                                    ImageMessage imageMessage = new ImageMessage(serverUniqueDeviceId, bytes, new Date());
                                    videoChannel.publish(imageMessage.toBytes());
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        });
                    }

                    // if the swing ui is not needed, we are finished here
                    if (!showVideoViewer) {
                        return;
                    }

                    // start the swing ui displaying incoming images
                    final VideoViewerPanel videoViewerPanel = new eu.hgross.blaubot.blaubotcam.server.ui.VideoViewerPanel();
                    kingdom.getChannelManager().addAdminMessageListener(videoViewerPanel);
                    videoChannel.addMessageListener(videoViewerPanel);
                    videoChannel.subscribe();

                    // show the panel in a frame
                    final JFrame frame = new JFrame();
                    frames.put(kingdom, frame);
                    frame.setMinimumSize(new Dimension(1024, 500));
                    frame.add(videoViewerPanel);
                    frame.pack();
                    frame.setVisible(true);
                    frame.setTitle("Blaubot cam server for kingdom of " + kingdom.getKingDevice().getUniqueDeviceID());
                    frame.addWindowListener(new WindowAdapter() {
                        @Override
                        public void windowClosing(WindowEvent e) {
                            kingdom.getChannelManager().removeAdminMessageListener(videoViewerPanel);
                            videoChannel.removeMessageListener(videoViewerPanel);
                            frames.remove(kingdom);
                        }
                    });
                }

                @Override
                public void onKingdomDisconnected(BlaubotKingdom kingdom) {
                    // unregister the IpCamServer
                    IBlaubotChannel videoChannel = kingdom.getChannelManager().createOrGetChannel(videoChannelId);
                    videoChannel.removeMessageListener(ipCamServer);

                    // close the frame
                    final JFrame frame = frames.get(kingdom);
                    if (frame == null) {
                        // already closed (by user or something else) or never opened
                        return;
                    }
                    // everything guit related is unregistered properly on close ... so reuse this logic.
                    frame.dispatchEvent(new WindowEvent(frame, WindowEvent.WINDOW_CLOSING));
                }
            });

            // start blaubot up
            websocketServer.startBlaubotServer();

        } catch (ParseException exp) {
            // oops, something went wrong
            System.err.println("Parsing failed.  Reason: " + exp.getMessage());
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp(BlaubotCamServer.class.getSimpleName(), options);
            System.exit(1);
        }
    }

    /**
     * Prints a list of available web cams to the console.
     */
    private static void printCamList() {
        final List<Webcam> webcams = Webcam.getWebcams();
        System.out.println("Found " + webcams.size() + " available cam" + (webcams.size() > 1 ? "s": "") + " on this machine.");
        if (webcams.size() > 0) {
            System.out.println("You can explicitly select one of these cams via the --" + CLI_OPTION_USE_WEBCAM + " <webCamId> command.");
            System.out.println("ID    # Name");
            System.out.println("------------------------");
        }
        int id = 0;
        for (Webcam cam : webcams) {
            final String name = cam.getDevice().getName();
            System.out.println(String.format("%4d  # %s", id++, name));
        }
    }
}

package eu.hgross.blaubot.blaubotcam.server.service;

import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.GrizzlyFuture;
import org.glassfish.grizzly.http.server.HttpHandler;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;
import org.glassfish.grizzly.http.util.ContentType;
import org.glassfish.grizzly.http.util.HttpStatus;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.LifecycleListenerAdapter;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;
import eu.hgross.blaubot.util.Log;

/**
 * A MessageListener for the BlaubotCam channel that uses a grizzly http server to server the received
 * ImageMessages as IP-Cam.
 * Each cam is served by the url
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class IPCamServer extends LifecycleListenerAdapter implements IBlaubotMessageListener {
    private static final String LOG_TAG = "IPCamServer";
    private static final long MAX_HTTP_SERVER_SHUTDOWN_TIME = 5000;
    public static final String HTTP_HEADER_SERVER_FIELD = "BlaubotIpCamServer";
    private HttpServer server;
    /**
     * The uri pattern used to serve a cam.
     * {{encodedeDeviceId}} will be replaced with the actual unqiue device id from the ImageMessage.
     */
    private static final String URI_PATTERN = "/cams/{{encodedDeviceId}}/video.mjpeg";
    private static final String URI_PATTERN_PARAM_ID = "{{encodedDeviceId}}";
    /**
     * If this ending is appended to the URI_PATTERN, a single image will be served instead of a mjpeg stream
     */
    private static final String URI_ENDING_SINGLE_IMAGE = "/poll";

    /**
     * Encapsualtes an id and the blocking logic of offering and polling ImageMessages.
     * Designed for
     */
    private static class CamDevice extends Observable {
        private String deviceId;
        private eu.hgross.blaubot.blaubotcam.server.model.ImageMessage lastImageMessage;

        public CamDevice(String deviceId) {
            this.deviceId = deviceId;
        }

        public String getDeviceId() {
            return deviceId;
        }

        public eu.hgross.blaubot.blaubotcam.server.model.ImageMessage getLastImageMessage() {
            return lastImageMessage;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            CamDevice camDevice = (CamDevice) o;

            return !(deviceId != null ? !deviceId.equals(camDevice.deviceId) : camDevice.deviceId != null);

        }

        @Override
        public int hashCode() {
            return deviceId != null ? deviceId.hashCode() : 0;
        }

        /**
         * Puts the latest ImageMessage for this device
         *
         * @param msg the message
         */
        public void putImageMessage(eu.hgross.blaubot.blaubotcam.server.model.ImageMessage msg) {
            lastImageMessage = msg;
            setChanged();
            notifyObservers();
        }


    }

    /**
     * A mapping from uniqueDeviceId -> CamDevice
     */
    private ConcurrentHashMap<String, CamDevice> mCamDevices;

    /**
     * @param httpPort the http port to server on
     */
    public IPCamServer(int httpPort) {
        mCamDevices = new ConcurrentHashMap<>();
        synchronized (serverMonitor) {
            server = HttpServer.createSimpleServer(null, httpPort);
            server.getServerConfiguration().addHttpHandler(new IndexHandler(mCamDevices));
        }
    }

    @Override
    public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
        // check if we have cam devices using this unique device id
        for (CamDevice device : mCamDevices.values()) {
            if (device.getLastImageMessage() != null && device.getLastImageMessage().getUniqueDeviceId().equals(blaubotDevice.getUniqueDeviceID())) {
                mCamDevices.remove(blaubotDevice.getUniqueDeviceID());
            }
        }
    }

    @Override
    public void onMessage(BlaubotMessage blaubotMessage) {
        eu.hgross.blaubot.blaubotcam.server.model.ImageMessage imageMessage = new eu.hgross.blaubot.blaubotcam.server.model.ImageMessage(blaubotMessage.getPayload());
        final String uniqueDeviceId = imageMessage.getUniqueDeviceId();
        CamDevice camDevice = mCamDevices.get(uniqueDeviceId);
        final boolean added;
        if (camDevice == null) {
            added = mCamDevices.putIfAbsent(uniqueDeviceId, new CamDevice(uniqueDeviceId)) == null;
        } else {
            added = false;
        }
        camDevice = mCamDevices.get(uniqueDeviceId);
        camDevice.putImageMessage(imageMessage);

        if (added) {
            // add the handler to the server
            HttpHandler httpHandler = new LiveVideoHttpHandler(camDevice);
            final String encodedDeviceId = EncodingUtil.encodeURIComponent(camDevice.getDeviceId());
            final String mjpegUri = URI_PATTERN.replace(URI_PATTERN_PARAM_ID, encodedDeviceId);
            final String singleJpegUri = URI_PATTERN.replace(URI_PATTERN_PARAM_ID, encodedDeviceId) + URI_ENDING_SINGLE_IMAGE;
            server.getServerConfiguration().addHttpHandler(httpHandler, mjpegUri, singleJpegUri);
        }
    }

    /**
     * Serves a mjpeg stream for a CamDevice
     */
    public static class LiveVideoHttpHandler extends HttpHandler {
        private static final String LOG_TAG = "IPCamServer.LiveVideoHttpHandler";
        private static final String MJPEG_BOUNDARY = "--hgross";
        private static final String MULTIPART_CONTENT_TYPE = "multipart/x-mixed-replace;boundary=" + MJPEG_BOUNDARY;
        private static final long INPUT_DATA_WAIT_TIME = 1500;
        private int MAX_WAIT_COUNT = 10; // MAX_WAIT_COUNT * INPUT_DATA_WAIT_TIME = time before connection gets closed
        private final CamDevice camDevice;

        public LiveVideoHttpHandler(CamDevice camDevice) {
            this.camDevice = camDevice;
        }

        private StringBuffer createHeader(int contentLength) {
            StringBuffer header = new StringBuffer(100);
            header.append("\r\n\r\n");
            header.append(MJPEG_BOUNDARY);
            header.append("\r\nContent-Type: image/jpeg\r\nContent-Length: ");
            header.append(contentLength);
            header.append("\r\n\r\n");
            return header;
        }

        public String getClientIpAddr(Request request) {
            String ip = request.getHeader("X-Forwarded-For");
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("WL-Proxy-Client-IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_CLIENT_IP");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getHeader("HTTP_X_FORWARDED_FOR");
            }
            if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
                ip = request.getRemoteAddr();
            }
            return ip;
        }

        @Override
        public void service(Request request, Response response) throws Exception {
            // check wether we should serve a single image or a mjpeg stream
            if (request.getRequestURI().endsWith(URI_ENDING_SINGLE_IMAGE)) {
                serveSingleJpeg(request, response);
            } else {
                serveMjpegStream(request, response);
            }
        }

        /**
         * Serves the latest image (if any) once.
         * @param request
         * @param response
         */
        private void serveSingleJpeg(Request request, Response response) throws IOException {
            final eu.hgross.blaubot.blaubotcam.server.model.ImageMessage lastImageMessage = camDevice.getLastImageMessage();
            response.setHeader("Server", HTTP_HEADER_SERVER_FIELD);
            if (lastImageMessage == null) {
                response.setStatus(HttpStatus.NO_CONTENT_204);
                return;
            }
            response.setContentType(ContentType.newContentType("image/jpeg"));
            response.getOutputStream().write(lastImageMessage.getJpegData());
        }

        /**
         * Serves the stream in mjpeg format
         * @param request
         * @param response
         */
        private void serveMjpegStream(Request request, Response response) throws IOException, InterruptedException {
            String clientIp = getClientIpAddr(request);
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Client " + clientIp + " connected to VideoStream.");
            }
            OutputStream os = response.getOutputStream();
            response.setContentType(MULTIPART_CONTENT_TYPE);
            response.setHeader("Server", "BlaubotIpCamServer");
            response.setHeader("Connection", "Close");
            os.write(MJPEG_BOUNDARY.getBytes());
            int waitCount = 0;
            long waitTimeUntilConnectionClose = MAX_WAIT_COUNT * INPUT_DATA_WAIT_TIME;
            eu.hgross.blaubot.blaubotcam.server.model.ImageMessage lastServedImageMessage = null;
            while (true) {
                eu.hgross.blaubot.blaubotcam.server.model.ImageMessage toServe = camDevice.getLastImageMessage();
                // initially we wait a specified amount for image data to arrive
                if (toServe == null) {
                    waitCount++;
                    Thread.sleep(INPUT_DATA_WAIT_TIME);
                    if (Log.logWarningMessages()) {
                        Log.w(LOG_TAG, "No image data to serve ... waiting -- maybe streaming is not started?.");
                    }
                    if (waitCount == MAX_WAIT_COUNT) {
                        if (Log.logWarningMessages()) {
                            Log.w(LOG_TAG, "Waited " + waitCount * INPUT_DATA_WAIT_TIME + " milliseconds but did not get any new video data - disconnecting stream.");
                        }
                        break;
                    }
                    continue;
                } else if (lastServedImageMessage == toServe) {
                    // -- same ImageMessage as served previously, don't send but create a countdown latch that is counted down on the arrival of the next new image
                    final CountDownLatch newImageLatch = new CountDownLatch(1);
                    Observer o = new Observer() {
                        @Override
                        public void update(Observable o, Object arg) {
                            newImageLatch.countDown();
                            camDevice.deleteObserver(this);
                        }
                    };
                    camDevice.addObserver(o);
                    newImageLatch.await(INPUT_DATA_WAIT_TIME, TimeUnit.MILLISECONDS);
                    continue;
                }

                // -- we got data at least once, send it to the client
                byte[] data = toServe.getJpegData();
                waitCount = 0;
                os.write(createHeader(data.length).toString().getBytes());
                os.write(data, 0, data.length);
                os.write(MJPEG_BOUNDARY.getBytes());
                os.flush();
                lastServedImageMessage = toServe;
            }
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Client " + clientIp + " disconnected from VideoStream.");
            }
        }
    }


    /**
     * Serves the index page listing all cams
     */
    public static class IndexHandler extends HttpHandler {
        private static final String LOG_TAG = "IPCamServer.IndexHandler";
        private final Map<String, CamDevice> deviceMap;
        private static final String INDEX_PAGE_TMPL = "" +
                "<html>" +
                "   <h1>BlaubotCam IPCam-Interface</h1>" +
                "   <p>Use the urls below with any mjpeg compatible viewer (VLC, Firefox, ...).</p>" +
                "   {{deviceList}}" +
                "</html>";
        private static final String REPLACE_DEVICE_LIST = "{{deviceList}}";

        public IndexHandler(Map<String, CamDevice> deviceMap) {
            this.deviceMap = deviceMap;
        }

        @Override
        public void service(Request request, Response response) throws Exception {
            response.setHeader("Server", HTTP_HEADER_SERVER_FIELD);

            StringBuilder sb = new StringBuilder("<table>");
            sb.append("<tr>");
            sb.append("   <th>Device ID</th>");
            sb.append("   <th>MJPEG stream</th>");
            sb.append("   <th>Links</th>");
            sb.append("</tr>");

            for (CamDevice device : deviceMap.values()) {
                final String deviceId = device.getDeviceId();
                final String uriEncodedDeviceId = EncodingUtil.encodeURIComponent(deviceId);
                // TODO escape html special chars in deviceId
                final String mjpegStreamUri = URI_PATTERN.replace(URI_PATTERN_PARAM_ID, uriEncodedDeviceId);
                final String singlePictureUri = URI_PATTERN.replace(URI_PATTERN_PARAM_ID, uriEncodedDeviceId) + URI_ENDING_SINGLE_IMAGE;
                final eu.hgross.blaubot.blaubotcam.server.model.ImageMessage lastImageMessage = device.getLastImageMessage();

                sb.append("<tr>");
                sb.append("     <td><a href=\"").append(mjpegStreamUri).append("\">").append(deviceId).append("</a></td>");
                sb.append("     <td>");
                if (lastImageMessage != null) {
                    sb.append(" <a target=\"blank\" href=\"").append(singlePictureUri).append("\"><img style=\"max-height: 200px;\" src=\"").append(mjpegStreamUri).append("\"></a><br>");
                    sb.append(lastImageMessage.getTime().toString());
                } else {
                    sb.append("never");
                }
                sb.append("     </td>");
                sb.append("     <td>");
                sb.append("         <ul>");
                sb.append("             <li><a href=\"").append(singlePictureUri).append("\">").append("Single JPEG").append("</a></li>");
                sb.append("             <li><a href=\"").append(mjpegStreamUri).append("\">").append("MJPEG-Stream").append("</a></li>");
                sb.append("         </ul>");
                sb.append("     </td>");

                sb.append("</tr>");
            }
            sb.append("</ul>");
            final String deviceList = sb.toString();
            response.getWriter().append(INDEX_PAGE_TMPL.replace(REPLACE_DEVICE_LIST, deviceList));
        }
    }


    private Object serverMonitor = new Object();

    /**
     * Starts the http server
     */
    public void startHTTPServer() {
        try {
            synchronized (serverMonitor) {
                server.start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * stops the integrated http server
     */
    public void stopHTTPServer() {
        synchronized (serverMonitor) {
            final CountDownLatch latch = new CountDownLatch(1);
            final GrizzlyFuture<HttpServer> shutdownFuture = server.shutdown(MAX_HTTP_SERVER_SHUTDOWN_TIME, TimeUnit.MILLISECONDS);
            shutdownFuture.addCompletionHandler(new CompletionHandler<HttpServer>() {
                @Override
                public void cancelled() {

                }

                @Override
                public void failed(Throwable throwable) {

                }

                @Override
                public void completed(HttpServer result) {
                    latch.countDown();
                }

                @Override
                public void updated(HttpServer result) {

                }
            });
            try {
                final boolean timedOut = !latch.await(MAX_HTTP_SERVER_SHUTDOWN_TIME, TimeUnit.MILLISECONDS);
                if (timedOut) {
                    throw new RuntimeException("Could not stop grizzly http server.");
                }
            } catch (InterruptedException e) {
            }
        }
    }

}

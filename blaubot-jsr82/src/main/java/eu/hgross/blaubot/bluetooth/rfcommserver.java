package eu.hgross.blaubot.bluetooth;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.Charset;

import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;

public class rfcommserver {
    java.util.UUID serviceUUid = java.util.UUID.fromString("b03e9d9c-ff1e-11e4-a322-1697f925ec7b");
    UUID uuid = new UUID(serviceUUid.toString().replace("-", ""), false);

    public void startserver() {
        try {
            String url = "btspp://localhost:" + uuid +
                    //  new UUID( 0x1101 ).toString() +
                    ";name=File Server";
            StreamConnectionNotifier service = (StreamConnectionNotifier) Connector.open(url);

            StreamConnection con = service.acceptAndOpen();
            OutputStream dos = con.openOutputStream();
            InputStream dis = con.openInputStream();

            InputStreamReader daf = new InputStreamReader(System.in);
            BufferedReader sd = new BufferedReader(daf);
            RemoteDevice dev = RemoteDevice.getRemoteDevice(con);

            String greeting = "hi";
            dos.write(greeting.getBytes(Charset.forName("utf-8")));
            dos.flush();
            byte buffer[] = new byte[1024];
            int bytes_read = dis.read(buffer);
            String received = new String(buffer, 0, bytes_read, Charset.forName("utf-8"));
            System.out.println
                    ("Message:" + received + "From:"
                            + dev.getBluetoothAddress());
            // con.close();
        } catch (IOException e) {
            System.err.print(e.toString());
        }
    }

    public static void main(String args[]) {
        try {
            LocalDevice local = LocalDevice.getLocalDevice();
            System.out.println("Server Started:\n"
                    + local.getBluetoothAddress()
                    + "\n" + local.getFriendlyName());

            rfcommserver ff = new rfcommserver();
            while (true) {
                ff.startserver();
            } //while
        }  //try
        catch (Exception e) {
            System.err.print(e.toString());
        }
    }
}  //main
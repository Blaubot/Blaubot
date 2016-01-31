package eu.hgross.blaubot.bluetooth;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;

public class rfcommclient {

    public void startclient() {
        try {
            String remoteAddr = "001F8100011C";
            String url = "btspp://" + remoteAddr + ":2";
            StreamConnection con = (StreamConnection) Connector.open(url);
            OutputStream os = con.openOutputStream();
            InputStream is = con.openInputStream();
            InputStreamReader isr = new InputStreamReader(System.in);
            BufferedReader bufReader = new BufferedReader(isr);
            RemoteDevice dev = RemoteDevice.getRemoteDevice(con);

            /**   if (dev !=null) {
             File f = new File("test.xml");
             InputStream sis = new FileInputStream("test.xml");
             OutputStream oo = new FileOutputStream(f);
             byte buf[] = new byte[1024];
             int len;
             while ((len=sis.read(buf))>0)
             oo.write(buf,0,len);
             sis.close();
             }  **/

            if (con != null) {
                while (true) {
                    //sender string
                    System.out.println("Server Found:"
                            + dev.getBluetoothAddress() + "\r\n" + "Put your string" + "\r\n");
                    String str = bufReader.readLine();
                    os.write(str.getBytes());
                    //reciever string
                    byte buffer[] = new byte[1024];
                    int bytes_read = is.read(buffer);
                    String received = new String(buffer, 0, bytes_read);
                    System.out.println("client: " + received + "from:" + dev.getBluetoothAddress());
                }
            }
        } catch (Exception e) {
        }
    }


    public static void main(String args[]) {
        try {
            LocalDevice local = LocalDevice.getLocalDevice();
            System.out.println("Address:" + local.getBluetoothAddress()
                    + "+n" + local.getFriendlyName());
        } catch (Exception e) {
            System.err.print(e.toString());
        }
        try {
            rfcommclient ss = new rfcommclient();
            while (true) {
                ss.startclient();
            }
        } catch (Exception e) {
            System.err.print(e.toString());
        }
    }
}//main
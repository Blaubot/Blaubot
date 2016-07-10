package eu.hgross.blaubot.bluetooth;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.DataElement;
import javax.bluetooth.DeviceClass;
import javax.bluetooth.DiscoveryAgent;
import javax.bluetooth.DiscoveryListener;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.ServiceRecord;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.UniqueDeviceIdHelper;
import eu.hgross.blaubot.core.acceptor.discovery.BeaconMessage;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.core.connector.IncompatibleBlaubotDeviceException;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.util.Log;

/**
 * Bluetooth connector implementation for android.
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class BlaubotJsr82BluetoothConnector implements IBlaubotConnector {
	private static final String LOG_TAG = "BlaubotJsr82BluetoothConnector";
    private static final List<String> SUPPORTED_ACCEPTOR_TYPES = Arrays.asList(BlaubotConstants.ACCEPTOR_TYPE_RFCOMM_JSR82_BLUETOOTH, BlaubotConstants.ACCEPTOR_TYPE_RFCOMM_ANDROID_BLUETOOTH);
    /**
     * connect timeout for bluetooth connections in milliseconds (does not work on bluez bluetooth stack!)
     */
    private static final long CONNECTION_TIMEOUT = 10000;
    /**
     * max ms to for a complete device and service inquiry before we stop
     */
    public static final long SERVICE_SEARCH_TIMEOUT = 30000;
    private final IBlaubotDevice ownDevice;
    private final LocalDevice localDevice;
    private IBlaubotIncomingConnectionListener incomingConnectionListener;
    private BlaubotJsr82BluetoothAdapter blaubotBluetoothAdapter;
    private IBlaubotBeaconStore beaconStore;

    /**
     * @param blaubotBluetoothAdapter the adapter
     * @param ownDevice our own device containing our own device id
     * @throws BluetoothStateException if we cannot access or detect the bluetooth stack or query the hardware
     */
    public BlaubotJsr82BluetoothConnector(BlaubotJsr82BluetoothAdapter blaubotBluetoothAdapter, IBlaubotDevice ownDevice) throws BluetoothStateException {
		this.ownDevice = ownDevice;
        this.blaubotBluetoothAdapter = blaubotBluetoothAdapter;
        this.localDevice = LocalDevice.getLocalDevice();
        try {
            Class clazz = Class.forName("com.intel.bluetooth.BlueCoveImpl");
            // -- we are using the bluecove bluetooth stack
            // configure a connection timeout
            String timeoutValue = String.valueOf(CONNECTION_TIMEOUT);
            String timeoutKey = "bluecove.connect.timeout";
            Method method = clazz.getMethod("setConfigProperty", String.class, String.class);
            // == BlueCoveImpl.setConfigProperty(timeoutKey, timeoutValue);
            method.invoke(clazz, timeoutKey, timeoutValue);
        } catch( ClassNotFoundException e ) {
        } catch (InvocationTargetException e) {
        } catch (NoSuchMethodException e) {
        } catch (IllegalAccessException e) {
        }

    }

    @Override
    public List<String> getSupportedAcceptorTypes() {
        return SUPPORTED_ACCEPTOR_TYPES;
    }

    @Override
    public IBlaubotAdapter getAdapter() {
        return blaubotBluetoothAdapter;
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }

	@Override
	public void setIncomingConnectionListener(IBlaubotIncomingConnectionListener incomingConnectionListener) {
		this.incomingConnectionListener = incomingConnectionListener;
	}

	@Override 
	public IBlaubotConnection connectToBlaubotDevice(IBlaubotDevice blaubotDevice) {
        final String uniqueDeviceID = blaubotDevice.getUniqueDeviceID();

        // check if we have meta data
        final List<ConnectionMetaDataDTO> lastKnownConnectionMetaData = beaconStore.getLastKnownConnectionMetaData(uniqueDeviceID);
        if(lastKnownConnectionMetaData == null) {
            if(Log.logErrorMessages()) {
                Log.e(LOG_TAG, "Could not get connection meta data for unique device id " + uniqueDeviceID);
            }
            return null;
        }

        // take the first supported acceptor, if any
        final List<ConnectionMetaDataDTO> supportedAcceptors = BlaubotAdapterHelper.filterBySupportedAcceptorTypes(lastKnownConnectionMetaData, getSupportedAcceptorTypes());
        if(supportedAcceptors.isEmpty()) {
            throw new IncompatibleBlaubotDeviceException(blaubotDevice + " could not get acceptor meta data for this device.");
        }
        Jsr82BluetoothConnectionMetaDataDTO bluetoothConnectionMetaData = new Jsr82BluetoothConnectionMetaDataDTO(supportedAcceptors.get(0));
        final String bluetoothMacAddress = bluetoothConnectionMetaData.getMacAddress();

        // strip ":" from mac address to comply with the bluecove formats
        final String remoteAddr = bluetoothMacAddress.replace(":", "");
        // build connect url
        final int channel = 1; // TODO: check if we can leave this out or detect a channel -> beacon UUID IS NOT USED currently!! FIX!
        // we have to do an inquiry on the device with remoteAddr to check, if it offers the beacon uuid (== beacon service)


        /*
            -- at this point we could not get our device from cache so we have to start an inquiry to
               get the bluetooth channel for our beacon-uuid (== bluetooth service uuid) from the remote
               device. To do this, we have to first find the remote device (device inquiry) and then fetch
               it's service records (service inquiry).
            - The whole thing is async so we use a future task to fetch the result later. If we succeeded or
              not can then be decided by the future calls outcome, which is either a connect url to connect
               to the remote device or null.
         */
        // TODO: extract this task from here to be reusable for the beacon implementation
        FutureTask<String> getConnectUrlBySDPLookup = new FutureTask<>(new Callable<String>() {
            private volatile String connectUrl = null;
            private volatile boolean foundSearchedDevice = false;
            private volatile int transactionId = -1;


            @Override
            public String call() throws Exception {
                // define which profile we want to use
                final UUID[] uuidSet = new UUID[]{new UUID(0x0003)}; // RFCOMM
                // and the attributes of the service record we wish to retrieve
                // https://www.bluetooth.org/en-us/specification/assigned-numbers/service-discovery
                final int[] attrIDs =  new int[] {
                        0x0100 // Service name
                };
                final DiscoveryAgent discoveryAgent = localDevice.getDiscoveryAgent();

                // check if we have this device cached
                RemoteDevice[] remoteDevices = discoveryAgent.retrieveDevices(DiscoveryAgent.CACHED);
                RemoteDevice dev = null;
                if (remoteDevices != null) {
                    for (RemoteDevice d : remoteDevices) {
                        if (d.getBluetoothAddress().equals(bluetoothMacAddress)) {
                            // -- found it in cache, use this instance
                            dev = d;
                            break;
                        }
                    }
                }

                final CountDownLatch latch = new CountDownLatch(1);
                /**
                 * Our discovery listener to get informed when we have found  our service (serviceDiscovered)
                 */
                final DiscoveryListener discoveryListener = new DiscoveryListener() {
                    @Override
                    public void deviceDiscovered(RemoteDevice btDevice, DeviceClass cod) {
                        // should be cached automatically
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "deviceDiscovered(" + btDevice + ")");
                        }
                        if (btDevice.getBluetoothAddress().equals(remoteAddr)) {
                            if (Log.logDebugMessages()) {
                                Log.d(LOG_TAG, "The device is the searched device. Stopping inquiry and starting search for services.");
                            }
                            foundSearchedDevice = true;
                            try {
                                boolean stopped = discoveryAgent.cancelInquiry(this);
                                if (stopped) {
                                    if (Log.logDebugMessages()) {
                                        Log.d(LOG_TAG, "Inquiry stopped.");
                                    }
                                }
                                int transId = discoveryAgent.searchServices(attrIDs, uuidSet, btDevice, this);
                                transactionId = transId;
                            } catch (BluetoothStateException e) {
                                if (Log.logErrorMessages()) {
                                    Log.e(LOG_TAG, "Number of concurrent service search transactions exceeded or system is unable to start one due to current conditions.");
                                }
                            }
                        } else {
                            if (Log.logDebugMessages()) {
                                Log.d(LOG_TAG, "Not the searched device.");
                            }
                        }
                    }

                    @Override
                    public void servicesDiscovered(int transID, ServiceRecord[] servRecord) {
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "servicesDiscovered(" + transID + ", " + servRecord +")");
                        }
                        if (transID == transactionId) {
                            if (Log.logDebugMessages()) {
                                Log.d(LOG_TAG, "The discovered service records belongs to our transactionId (" + transactionId + ").");
                            }
                            // -- matches our transaction id, check the service record
                            for (ServiceRecord record : servRecord) {
                                String url = record.getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
                                if (url == null) {
                                    continue;
                                }
                                DataElement serviceName = record.getAttributeValue(0x0100);
                                String serviceNameStr = (String) serviceName.getValue();
                                if (!serviceNameStr.startsWith(BlaubotConstants.BLUETOOTH_ACCEPTORS_RFCOMM_SDP_SERVICE_NAME)) {
                                    Log.w(LOG_TAG, "servicenamestr = " + serviceNameStr + "!=" + BlaubotConstants.BLUETOOTH_ACCEPTORS_RFCOMM_SDP_SERVICE_NAME);
                                    // wrong service name, skip
                                    continue;
                                }

                                // get and compare uuid
                                Enumeration<DataElement> serviceUuids = (Enumeration<DataElement>) record.getAttributeValue(1).getValue();
                                String wantedUuid = blaubotBluetoothAdapter.getUUIDSet().getAppUUID().toString().replace("-", "");
                                boolean containsOurUuid = false;
                                while (serviceUuids.hasMoreElements()) {
                                    DataElement uuidDataElement = serviceUuids.nextElement();
                                    UUID uuid = (UUID) uuidDataElement.getValue();
                                    if (wantedUuid.equals(uuid.toString())) {
                                        containsOurUuid = true;
                                        break;
                                    }
                                }
                                if (!containsOurUuid) {
                                    if (Log.logWarningMessages()) {
                                        Log.w(LOG_TAG, "Found a blaubot RFCOMM endpoint but it did not match our uuid (expected: " + wantedUuid + ", found: " + serviceUuids + ")");
                                    }
                                    continue;
                                }

                                if (Log.logDebugMessages()) {
                                    Log.d(LOG_TAG, "Discovered acceptor service " + serviceNameStr + " on device " + record.getHostDevice());
                                }

                                connectUrl = url;
                                for (int attrId : record.getAttributeIDs()) {
                                    Log.d(LOG_TAG, attrId + " => " + record.getAttributeValue(attrId));
                                }

                                // we have the url
                                break;
                            }

                            // we finished
                            latch.countDown();
                        } else {
                            if (Log.logDebugMessages()) {
                                Log.d(LOG_TAG, "The discovered service records DO NOT belong to our transactionId ("+ transactionId + ").");
                            }
                        }
                    }

                    @Override
                    public void serviceSearchCompleted(int transID, int respCode) {
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "serviceSearchCompleted(" + transID + ", " + serviceSearchResponseCodeToString(respCode) + ")");
                        }
                        if (transID == transactionId) {
                            // -- matches our transaction id, check the outcome
                            // we are done
                            latch.countDown();
                        }
                    }

                    @Override
                    public void inquiryCompleted(int discType) {
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "inquiryCompleted(" + inquiryResponseCodeToString(discType)+ ")");
                        }
                        if (discType == DiscoveryListener.INQUIRY_ERROR || discType == DiscoveryListener.INQUIRY_COMPLETED) {
                            if (transactionId < 0) {
                                // service search never started, we are done
                                latch.countDown();
                            }
                        }
                    }
                };

                if (dev == null) {
                    // if we could not find the device cached, we start an inquiry
                    try {
                        if (Log.logDebugMessages()) {
                            Log.d(LOG_TAG, "Don't have a cached device instance for, starting inquiry in GIAC mode ...");
                        }
                        boolean started = discoveryAgent.startInquiry(DiscoveryAgent.GIAC, discoveryListener);
                        if (!started) {
                            if (Log.logDebugMessages()) {
                                Log.w(LOG_TAG, "Could not start inquiry in GIAC mode. Trying LIAC mode ...");
                            }
                            // we try again wit the other inquiry method
                            started = discoveryAgent.startInquiry(DiscoveryAgent.LIAC, discoveryListener);
                            if (!started) {
                                if (Log.logErrorMessages()) {
                                    Log.e(LOG_TAG, "Could neither start inquiry in GIAC nor LIAC mode.");
                                }
                                // abort
                                return null;
                            } else {
                                if (Log.logDebugMessages()) {
                                    Log.d(LOG_TAG, "Device inquiry started (LIAC mode).");
                                }
                            }
                        } else {
                            if (Log.logDebugMessages()) {
                                Log.d(LOG_TAG, "Device inquiry started (GIAC mode).");
                            }
                        }
                    } catch (BluetoothStateException e) {
                        if (Log.logErrorMessages()) {
                            Log.e(LOG_TAG, "Failed to start GIAC and LIAC inquiry: " + e.getMessage(), e);
                        }
                    }
                } else {
                    if (Log.logDebugMessages()) {
                        Log.d(LOG_TAG, "We have a cached instance for this device. Skipping device inquiry and starting SDP lookup for RFCOMM service.");
                    }
                    // otherwise, we have the device and just need to start the service lookup
                    transactionId = discoveryAgent.searchServices(attrIDs, uuidSet, dev, discoveryListener);
                }


                boolean timedOut = !latch.await(SERVICE_SEARCH_TIMEOUT, TimeUnit.MILLISECONDS);
                if (timedOut) {
                    boolean terminatedSearch = discoveryAgent.cancelServiceSearch(transactionId);
                    boolean terminatedInquiry = discoveryAgent.cancelInquiry(discoveryListener);
                    if (!terminatedSearch || !terminatedInquiry) {
                        if (Log.logErrorMessages()) {
                            Log.e(LOG_TAG, "Could not stop inquiry or service search");
                        }
                        return connectUrl;
                    } else {
                        // once again await the events
                        latch.await(SERVICE_SEARCH_TIMEOUT, TimeUnit.MILLISECONDS);
                        // now, no matter what, return the task
                        return connectUrl;
                    }
                }
                return connectUrl;
            }
        });

        System.out.println("Task start");
        new Thread(getConnectUrlBySDPLookup).start();
        String connectUrl = null;
        try {
            connectUrl = getConnectUrlBySDPLookup.get();
        } catch (Exception e) {
            System.out.println("Task error");
            e.printStackTrace();
            return null;
        }
        System.out.println("Task finish: " + connectUrl);

        if (connectUrl == null) {
            return null;
        }

        //final String connectUrl = "btspp://" + remoteAddr + ":" + channel;

        try {
            BlaubotConstants.BLUETOOTH_ADAPTER_LOCK.acquire();

            StreamConnection con = null;
            try {
                con = (StreamConnection) Connector.open(connectUrl, Connector.READ_WRITE, true);
                RemoteDevice dev = RemoteDevice.getRemoteDevice(con);
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Got a new connection from " + dev);
                }

                // create the connection object
                BlaubotDevice device = new BlaubotDevice(uniqueDeviceID);
                BlaubotJsr82BluetoothConnection connection = new BlaubotJsr82BluetoothConnection(device, con);

                try {
                    String readableName = dev.getFriendlyName(false);
                    if (readableName != null && !readableName.isEmpty()) {
                        device.setReadableName(readableName);
                    } else {
                        device.setReadableName(dev.getBluetoothAddress());
                    }
                } catch (IOException e) {
                }

                // send our unique device id
                UniqueDeviceIdHelper.sendUniqueDeviceIdThroughOutputStream(ownDevice, connection.getDataOutputStream());

                // send our acceptor data
                final BeaconMessage currentBeaconMessage = getAdapter().getBlaubot().getConnectionStateMachine().getBeaconService().getCurrentBeaconMessage();
                connection.write(currentBeaconMessage.toBytes());

                this.incomingConnectionListener.onConnectionEstablished(connection);
                return connection;
            } catch (IOException e) {
                if(Log.logWarningMessages()) {
                    Log.w(LOG_TAG, "Bluetooth to " + blaubotDevice + " connect failed! Error: " + e.getMessage(), e);
                }
                if (con != null) {
                    try {
                        con.close();
                    } catch (IOException e1) {
                    }
                }
            } catch (Exception e) {
                if(Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "Reflection failure or something worse: " + e.getMessage(), e);
                    e.printStackTrace();
                }
            } finally {
                BlaubotConstants.BLUETOOTH_ADAPTER_LOCK.release();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

		return null;
	}

    private static String serviceSearchResponseCodeToString(int code) {
        if (code == DiscoveryListener.SERVICE_SEARCH_COMPLETED) {
            return "SERVICE_SEARCH_COMPLETED";
        } else if (code == DiscoveryListener.SERVICE_SEARCH_DEVICE_NOT_REACHABLE) {
            return "SERVICE_SEARCH_DEVICE_NOT_REACHABLE";
        } else if (code == DiscoveryListener.SERVICE_SEARCH_ERROR) {
            return "SERVICE_SEARCH_ERROR";
        } else if (code == DiscoveryListener.SERVICE_SEARCH_NO_RECORDS) {
            return "SERVICE_SEARCH_NO_RECORDS";
        } else if (code == DiscoveryListener.SERVICE_SEARCH_TERMINATED) {
            return "SERVICE_SEARCH_TERMINATED";
        } else {
            return "Unknown";
        }
    }

    private static String inquiryResponseCodeToString(int code) {
        if (code == DiscoveryListener.INQUIRY_COMPLETED) {
            return "INQUIRY_COMPLETED";
        } else if (code == DiscoveryListener.INQUIRY_ERROR) {
            return "INQUIRY_ERROR";
        } else if (code == DiscoveryListener.INQUIRY_TERMINATED) {
            return "INQUIRY_TERMINATED";
        } else {
            return "Unknown";
        }
    }

}

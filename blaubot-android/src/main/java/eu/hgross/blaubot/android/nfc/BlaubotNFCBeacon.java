package eu.hgross.blaubot.android.nfc;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.NfcEvent;
import android.os.Vibrator;
import android.util.Base64;

import java.util.List;

import eu.hgross.blaubot.android.IBlaubotAndroidComponent;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotIncomingConnectionListener;
import eu.hgross.blaubot.core.acceptor.IBlaubotListeningStateListener;
import eu.hgross.blaubot.core.acceptor.discovery.BeaconMessage;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeaconStore;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.util.Log;

/**
 * NFC Beacon
 * This beacon needs an android context so be aware that you have to update the blaubot instance on
 * some lifecycle events. See IBlaubotAndroidComponent and AndroidBlaubot.
 */
public class BlaubotNFCBeacon implements IBlaubotBeacon, IBlaubotAndroidComponent {
    private static final String SCHEME = "blaubot";
    private static final String LOG_TAG = "BlaubotNFCBeacon";
    private static final long VIBRATION_TIME_ON_SUCCESS = 200; // ms
    private boolean discoveryActive;
    private Blaubot blaubot;
    private IBlaubotBeaconStore beaconStore;
    private IBlaubotListeningStateListener listeningStateListener;
    private IBlaubotIncomingConnectionListener acceptorListener;
    private IBlaubotDiscoveryEventListener discoveryEventListener;
    private volatile boolean isStarted;
    private Context currentContext;
    private Vibrator vibratorService;

    public BlaubotNFCBeacon() {
        this.discoveryActive = false;
    }

    @Override
    public void setBlaubot(Blaubot blaubot) {
        this.blaubot = blaubot;
    }

    @Override
    public void setBeaconStore(IBlaubotBeaconStore beaconStore) {
        this.beaconStore = beaconStore;
    }

    @Override
    public IBlaubotAdapter getAdapter() {
        return null;
    }

    @Override
    public void startListening() {
        isStarted = true;
        if(this.listeningStateListener != null) {
            this.listeningStateListener.onListeningStarted(this);
        }
    }

    @Override
    public void stopListening() {
        isStarted = false;
        if(this.listeningStateListener != null) {
            this.listeningStateListener.onListeningStopped(this);
        }
    }

    @Override
    public boolean isStarted() {
        return isStarted;
    }

    @Override
    public void setListeningStateListener(IBlaubotListeningStateListener stateListener) {
        this.listeningStateListener = stateListener;
    }

    @Override
    public void setAcceptorListener(IBlaubotIncomingConnectionListener acceptorListener) {
        this.acceptorListener = acceptorListener;
    }

    @Override
    public ConnectionMetaDataDTO getConnectionMetaData() {
        NFCConnectionMetaDataDTO connectionMetaDataDTO = new NFCConnectionMetaDataDTO();
        return connectionMetaDataDTO;
    }

    @Override
    public void setDiscoveryEventListener(IBlaubotDiscoveryEventListener discoveryEventListener) {
        this.discoveryEventListener = discoveryEventListener;
    }

    private volatile Uri currentUri;
    @Override
    public void onConnectionStateMachineStateChanged(IBlaubotState state) {
        final BeaconMessage currentBeaconMessage = blaubot.getConnectionStateMachine().getBeaconService().getCurrentBeaconMessage();
        final String beaconMessage64 = Base64.encodeToString(currentBeaconMessage.toBytes(), Base64.URL_SAFE);

        // create and store uri
        StringBuilder sb = new StringBuilder();
        sb.append(SCHEME);
        sb.append("://");
        sb.append("beacon");
        sb.append("?beaconMessage=");
        sb.append(beaconMessage64);

        currentUri = Uri.parse(sb.toString());
    }

    @Override
    public void setDiscoveryActivated(boolean active) {
        this.discoveryActive = active;
    }

    @Override
    public void setCurrentContext(Context context) {

    }

    @Override
    public void onResume(Activity context) {
        this.currentContext = context;

        vibratorService = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
        final NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(context);
        if(nfcAdapter != null) {
            // Create a PendingIntent object so the Android system can populate it with the details of the tag when it is scanned.
            PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, new Intent(context, context.getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
            // Declare intent filters to handle the intents that the developer wants to intercept.
            IntentFilter ndef = new IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED);
            ndef.addDataScheme("blaubot");
            final IntentFilter[] intentFiltersArray = { ndef };



            Log.d(LOG_TAG, "Setting NFC callbacks");
            nfcAdapter.enableForegroundDispatch(context, pendingIntent, intentFiltersArray, null);
            nfcAdapter.setNdefPushMessageCallback(nfcCallback, context);
        } else {
            throw new RuntimeException("Could not get NFCAdapter - NFC not supported?");
        }
    }

    @Override
    public void onPause(Activity context) {
        final NfcAdapter nfcAdapter = NfcAdapter.getDefaultAdapter(context);
        if(nfcAdapter != null) {
            nfcAdapter.disableForegroundDispatch(context);
        }

    }

    @Override
    public void onNewIntent(Intent intent) {
        if(intent != null) {
            if (intent.getAction().equals(NfcAdapter.ACTION_NDEF_DISCOVERED)) {
                final String dataString = intent.getDataString();

                // parse
                final Uri uri = Uri.parse(dataString);
                final String beaconMessage64 = uri.getQueryParameter("beaconMessage");
                final byte[] beaconMessageBytes = Base64.decode(beaconMessage64, Base64.URL_SAFE);
                final BeaconMessage beaconMessage = BeaconMessage.fromBytes(beaconMessageBytes);
                final State currentState = beaconMessage.getCurrentState();
                final IBlaubotDevice device = new BlaubotDevice(beaconMessage.getUniqueDeviceId());

                // create and populate discovery event for partner
                final AbstractBlaubotDeviceDiscoveryEvent discoveryEventForPartner = currentState.createDiscoveryEventForDevice(device, beaconMessage.getOwnConnectionMetaDataList());
                discoveryEventListener.onDeviceDiscoveryEvent(discoveryEventForPartner);

                // if partner has a king, create an event for the king as well
                if(!beaconMessage.getKingDeviceUniqueId().isEmpty()) {
                    final String kingDeviceUniqueId = beaconMessage.getKingDeviceUniqueId();
                    final List<ConnectionMetaDataDTO> kingsConnectionMetaDataList = beaconMessage.getKingsConnectionMetaDataList();
                    final BlaubotDevice kingDevice = new BlaubotDevice(kingDeviceUniqueId);
                    final AbstractBlaubotDeviceDiscoveryEvent discoveryEventForPartnersKing= State.King.createDiscoveryEventForDevice(kingDevice, kingsConnectionMetaDataList);
                    discoveryEventListener.onDeviceDiscoveryEvent(discoveryEventForPartnersKing);
                }

                if(vibratorService != null) {
                    vibratorService.vibrate(VIBRATION_TIME_ON_SUCCESS);
                }

            }
        }
    }

    private  NfcAdapter.CreateNdefMessageCallback nfcCallback = new NfcAdapter.CreateNdefMessageCallback() {
        @Override
        public NdefMessage createNdefMessage(NfcEvent event) {
            if (currentUri == null) { // || !isStarted) ignore ...
                if (Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "I have no URI, can't send NFC message. ");
                }
                return null; // don't provide a message
            }
            NdefMessage msg = new NdefMessage(
                    new NdefRecord[]{
                            NdefRecord.createUri(currentUri)
                    });
            return msg;
        }
    };

}

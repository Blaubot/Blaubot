package eu.hgross.blaubot.core.acceptor.discovery;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotSubordinatedState;
import eu.hgross.blaubot.util.Log;

/**
 * A task that utilizes a given (and connected) {@link IBlaubotConnection} to exchange the given {@link State} with the
 * remote endpoint.
 * The remote endpoint is the BlaubotBeaconService's {@link eu.hgross.blaubot.core.acceptor.discovery.BlaubotBeaconService.BeaconConnectionHandler} runnable.
 * This task does the whole beacon exchange heavy lifting and can be reused in other beacon implementations.
 * 
 * If the exchange is successful, the {@link IBlaubotDiscoveryEventListener} is informed about the discovered state of
 * the remote endpoint.
 * 
 * After the execution the connection is closed.
 *
 *
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class ExchangeStatesTask implements Runnable {
	private static final ExecutorService executorService = Executors.newCachedThreadPool();
	private static final String LOG_TAG = "ExchangeStatesTask";
	private static final boolean LOGGING_ACTIVE = true;
	protected volatile IBlaubotDiscoveryEventListener eventListener;
	protected IBlaubotState ourState; 
	protected IBlaubotConnection connection;
	protected String kingDeviceUniqueId;
    protected List<ConnectionMetaDataDTO> ourAcceptorMetaDataList;
    private IBlaubotBeaconStore beaconStore;
    private IBlaubotDevice ownDevice;

    /**
     * Creates a new ExchangeStatesTask to be used with a IBlaubotBeaconInterface implementation.
     *
     * @param ownDevice our own device
     * @param connection the connection newly created connection to the remote beacon
     * @param ourState our own state that is going to be exchanged with the other side's (accepting) beacon
     * @param ourAcceptorMetaData the list of connection meta data objects gathered from OUR OWN acceptors
     * @param beaconStore the beacon store to get connection meta data for other devices from
     * @param eventListener the event listener of our beacon, which will be called with the appropriate discovery event by this task
     */
	public ExchangeStatesTask(IBlaubotDevice ownDevice, IBlaubotConnection connection, IBlaubotState ourState, List<ConnectionMetaDataDTO> ourAcceptorMetaData, IBlaubotBeaconStore beaconStore, IBlaubotDiscoveryEventListener eventListener) {
		setUp(ownDevice, connection, ourState, eventListener, ourAcceptorMetaData, beaconStore);
		// assert 
		if(this.ourState instanceof IBlaubotSubordinatedState) {
			this.kingDeviceUniqueId = ((IBlaubotSubordinatedState)ourState).getKingUniqueId();
		}
	}
	
	private void setUp(IBlaubotDevice ownDevice, IBlaubotConnection connection, IBlaubotState ourState, IBlaubotDiscoveryEventListener eventListener, List<ConnectionMetaDataDTO> connectionMetaDataList, IBlaubotBeaconStore beaconStore) {
		this.ownDevice = ownDevice;
        this.ourState = ourState;
		this.eventListener = eventListener;
		this.connection = connection;
        this.ourAcceptorMetaDataList = connectionMetaDataList;
        this.beaconStore = beaconStore;
	}
	
	private State getState() {
		if(ourState == null) {
			if(Log.logWarningMessages()) {
				Log.w(LOG_TAG, "ourState for the StateExchange is null! Assuming we are in StoppedState.");
			}
			return State.Stopped;
		}
		return State.getStateByStatemachineClass(ourState.getClass());
	}

	@Override
	public void run() {
		if(LOGGING_ACTIVE && Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Connected to " + connection.getRemoteDevice() + "'s beacon.");
			Log.d(LOG_TAG, "Starting state exchange.");
		}
        // first receive the other side's state and acceptor meta data via the message
		BeaconMessage beaconMessage;
		beaconMessage = BeaconMessage.fromBlaubotConnection(connection);
		if (beaconMessage == null) {
			if(Log.logWarningMessages()) {
				Log.w(LOG_TAG, "Something went wrong reading the beacon message from the connection!");
			}
			connection.disconnect();
			return;
		}
		if(LOGGING_ACTIVE && Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Successfully retrieved state from " + connection.getRemoteDevice() + "'s beacon: " + beaconMessage);
		}

        // now send our state and acceptor meta data
		if (ourState != null) {
            final String ownUniqueDeviceID = ownDevice.getUniqueDeviceID();
            BeaconMessage ourStateMessage;
            if(ourState instanceof IBlaubotSubordinatedState) {
                // if we have a king, we communicate the king's uniqueId and acceptor data
                final List<ConnectionMetaDataDTO> kingConnectionMetaData = beaconStore.getLastKnownConnectionMetaData(kingDeviceUniqueId);
                if(kingConnectionMetaData == null) {
                    throw new IllegalStateException("We don't have connection meta data for the king stored, but we are in a subordinate state");
                }
                ourStateMessage = new BeaconMessage(ownUniqueDeviceID, getState(), ourAcceptorMetaDataList, kingDeviceUniqueId, kingConnectionMetaData);
			} else {
				ourStateMessage = new BeaconMessage(ownUniqueDeviceID, getState(), ourAcceptorMetaDataList);
			}
            if(LOGGING_ACTIVE && Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Sending our state to " + connection.getRemoteDevice() + "'s beacon: " + ourStateMessage);
            }
			try {
				connection.write(ourStateMessage.toBytes());
			} catch (IOException e) {
				if(Log.logErrorMessages()) {
					Log.e(LOG_TAG, "Failed to send our state to beacon of " + connection.getRemoteDevice(), e);
				}
			}
		}
		connection.disconnect();

		if(LOGGING_ACTIVE && Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Got message from " + connection.getRemoteDevice() + "'s beacon: " + beaconMessage);
		}

        // dispatch THEIR state and connection info to the listener
		handleDiscoveredBlaubotDevice(connection.getRemoteDevice(), beaconMessage.getCurrentState(), beaconMessage.getOwnConnectionMetaDataList());
		// if they have a king, dispatch this informations as well.
        if (beaconMessage.getCurrentState().equals(State.Prince) || beaconMessage.getCurrentState().equals(State.Peasant)) {
            final String remoteDeviceKingUniqueDeviceId = beaconMessage.getKingDeviceUniqueId();
            final List<ConnectionMetaDataDTO> remoteDeviceKingConnectionMetaDataList = beaconMessage.getKingsConnectionMetaDataList();
            if (remoteDeviceKingUniqueDeviceId != null && !remoteDeviceKingUniqueDeviceId.isEmpty() && remoteDeviceKingConnectionMetaDataList != null) {
                handleDiscoveredBlaubotDevice(new BlaubotDevice(remoteDeviceKingUniqueDeviceId), State.King, remoteDeviceKingConnectionMetaDataList);
            }
        }
    }

	private void handleDiscoveredBlaubotDevice(final IBlaubotDevice device, final State state, final List<ConnectionMetaDataDTO> myConnectionMetaDataList) {
		if (eventListener != null) {
			executorService.execute(new Runnable() {
				@Override
				public void run() {
					AbstractBlaubotDeviceDiscoveryEvent event = state.createDiscoveryEventForDevice(device, myConnectionMetaDataList);
					if (eventListener != null) {
						eventListener.onDeviceDiscoveryEvent(event);
					}
				}
			});
		}
	}
}
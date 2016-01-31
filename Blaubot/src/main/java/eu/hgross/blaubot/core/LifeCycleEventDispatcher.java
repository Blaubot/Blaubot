package eu.hgross.blaubot.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.hgross.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import eu.hgross.blaubot.core.statemachine.states.FreeState;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotSubordinatedState;
import eu.hgross.blaubot.core.statemachine.states.KingState;
import eu.hgross.blaubot.core.statemachine.states.PeasantState;
import eu.hgross.blaubot.core.statemachine.states.PrinceState;
import eu.hgross.blaubot.core.statemachine.states.StoppedState;
import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.CensusMessage;
import eu.hgross.blaubot.messaging.IBlaubotAdminMessageListener;
import eu.hgross.blaubot.util.Log;
import eu.hgross.blaubot.util.Util;

/**
 * Listens for {@link eu.hgross.blaubot.admin.CensusMessage}s, calculates the diff (left or joined
 * devices and prince changes) and communicates them through the {@link eu.hgross.blaubot.core.ILifecycleListener}s attached to this {@link eu.hgross.blaubot.core.Blaubot} instance.
 * <p/>
 * If attached to a {@link eu.hgross.blaubot.core.statemachine.ConnectionStateMachine}, dispatches the corresponding
 * events to it's listeners when a kingdom merge takes place, the king dies, the prince takes over and so on.
 * <p/>
 */
public class LifeCycleEventDispatcher implements IBlaubotAdminMessageListener, IBlaubotConnectionStateMachineListener {
    public static final String LOG_TAG = "LifeCycleEventDispatchingListener";

    /**
     * The listeners
     */
    private final CopyOnWriteArrayList<ILifecycleListener> lifecycleListeners = new CopyOnWriteArrayList<>();

    /**
     * maps the last census message for different networks by the king's
     * uniqueId (kingUniqueId -> lastCensusMessage)
     */
    private final Map<String, CensusMessage> lastCensusMessages = new ConcurrentHashMap<>();
    private final IBlaubotDevice ownDevice;
    /**
     * The last known king's uniqueDeviceId.
     * ONLY used to trigger onKingDeviceChanged in certain cases (onDisconnected() and if we have no
     * former census message. Is reset on notifyDisconnectedFromNetwork() calls.
     */
    private String lastKnownKingUniqueDeviceId;
    /**
     * The last known prince device id.
     * ONLY used to trigger onPrinceDeviceChanged before onDisconnect() calls - not for any other
     * processing.
     */
    private String lastKnownPrinceUniqueDeviceId;

    public LifeCycleEventDispatcher(IBlaubotDevice ownDevice) {
        this.ownDevice = ownDevice;
    }

    @Override
    public void onAdminMessage(AbstractAdminMessage adminMessage) {
        if (adminMessage instanceof CensusMessage) {
            CensusMessage cm = (CensusMessage) adminMessage;
            String currentNetworkKingUniqueId = cm.extractKingUniqueId();
            boolean hasFormerCensusMessage = lastCensusMessages.containsKey(currentNetworkKingUniqueId);
            CensusMessage lastCensusMessage = hasFormerCensusMessage ? lastCensusMessages.get(currentNetworkKingUniqueId) : new CensusMessage(new HashMap<String, State>());

            // create a set containing all new uniqueIds in the network
            Set<String> newUniqueIds = new HashSet<>(cm.getDeviceStates().keySet());
            newUniqueIds.removeAll(lastCensusMessage.getDeviceStates().keySet());

            // create a set containing all removed uniqueIds since the last
            // census message
            Set<String> missingUniqueIds = new HashSet<>(lastCensusMessage.getDeviceStates().keySet());
            missingUniqueIds.removeAll(cm.getDeviceStates().keySet());

            // check if the prince has changed
            String oldPrince = lastCensusMessage.extractPrinceUniqueId();
            String newPrince = cm.extractPrinceUniqueId();
            boolean princeChanged = (oldPrince == null && newPrince != null) || (newPrince == null && oldPrince != null)
                    || (!(oldPrince == null && newPrince == null) && !newPrince.equals(oldPrince));

            String oldKing = lastCensusMessage.extractKingUniqueId();
            oldKing = oldKing == null ? lastKnownKingUniqueDeviceId : oldKing;
            String newKing = cm.extractKingUniqueId();
            boolean kingChanged = (oldKing == null && newKing != null) || (newKing == null && oldKing != null)
                    || (!(oldKing == null && newKing == null) && !newKing.equals(oldKing));

            // call the listeners for each joined/left device or prince change but ignore our own device id
            final String ownDeviceId = this.ownDevice.getUniqueDeviceID();
            newUniqueIds.remove(ownDeviceId);
            missingUniqueIds.remove(ownDeviceId);
            for (ILifecycleListener listener : lifecycleListeners) {
                // joined devices
                for (String uniqueId : newUniqueIds) {
                    IBlaubotDevice device = new BlaubotDevice(uniqueId);
                    listener.onDeviceJoined(device);
                }
                // king
                if (kingChanged) {
                    IBlaubotDevice oldKingD = null, newKingD = null;
                    if (oldKing != null) {
                        oldKingD = new BlaubotDevice(oldKing);
                    }
                    if (newKing != null) {
                        newKingD = new BlaubotDevice(newKing);
                    }
                    listener.onKingDeviceChanged(oldKingD, newKingD);
                }
                // prince
                if (princeChanged) {
                    IBlaubotDevice oldPrinceD = null, newPrinceD = null;
                    if (oldPrince != null) {
                        oldPrinceD = new BlaubotDevice(oldPrince);
                    }
                    if (newPrince != null) {
                        newPrinceD = new BlaubotDevice(newPrince);
                    }
                    listener.onPrinceDeviceChanged(oldPrinceD, newPrinceD);
                }
                // left devices
                for (String uniqueId : missingUniqueIds) {
                    IBlaubotDevice device = new BlaubotDevice(uniqueId);
                    listener.onDeviceLeft(device);
                }
            }
            lastCensusMessages.put(currentNetworkKingUniqueId, cm);
            lastKnownKingUniqueDeviceId = newKing;
            lastKnownPrinceUniqueDeviceId = newPrince;
        }
    }

    @Override
    public void onStateChanged(IBlaubotState oldState, IBlaubotState newState) {
        if (newState instanceof PeasantState) {
            PeasantState ps = (PeasantState) newState;
            if (ps.getConnectionAccomplishmentType().equals(PeasantState.ConnectionAccomplishmentType.BOWED_DOWN)) {
                // we bowed down to a new kingdom, so we have to disconnect from the current network
                // - clear the last census message from the old kingdom
                // -> for all device in the last census, call onDeviceLeft
                final String oldKingUniqueId;
                if (oldState instanceof IBlaubotSubordinatedState) {
                    oldKingUniqueId = ((IBlaubotSubordinatedState) oldState).getKingUniqueId();
                } else {
                    // -- if no subordinate and bowing down, we had to be part of a network and the only part of a network who is not a subordinate, is the king
                    oldKingUniqueId = this.ownDevice.getUniqueDeviceID();
                }

                CensusMessage oldKingdomMsg = lastCensusMessages.remove(oldKingUniqueId);
//                lastKnownKingUniqueDeviceId = ps.getKingConnection().getRemoteDevice().getUniqueDeviceID();
                if (oldKingdomMsg != null) {
                    notfiyOnDeviceLeftForKingdom(oldKingdomMsg);
                }
            } else if (ps.getConnectionAccomplishmentType().equals(PeasantState.ConnectionAccomplishmentType.FOLLOWED_THE_HEIR_TO_THE_THRONE)) {
                // we connected to the prince after the king died
                // - treat the old kingdom's census message as the new kingdom's census to let the diff logic for the onJoin/onLeft do their magic on arrival of the next census from this kingdom
                // - clear the last census message from the old kingdom
                final IBlaubotSubordinatedState _oldState = (IBlaubotSubordinatedState) oldState;
                CensusMessage oldKingdomMsg = lastCensusMessages.remove(_oldState.getKingUniqueId());
//                lastKnownKingUniqueDeviceId = ps.getKingConnection().getRemoteDevice().getUniqueDeviceID();
                if (oldKingdomMsg != null) {
                    lastCensusMessages.put(ps.getKingUniqueId(), oldKingdomMsg);
                }
                // the onLeft/onJoined events should follow by the arriving census messages
            } else if (!ps.getConnectionAccomplishmentType().equals(PeasantState.ConnectionAccomplishmentType.DEGRADATION)) {
                // if not a change from prince -> peasant inside the same network (degraded), notify that we connected to a new network
                notifyConnectedToNetwork();
                // the onDeviceJoined will be triggered from the
                // CensusMessage
                // TODO: the order of events: onConnected() and
                // onDeviceJoined() is not guaranteed at the moment! (the
                // messaging could be faster)
            }
        } else if (newState instanceof FreeState) {
            // ignore stopped->free transitions for disconnected events
            if (!(oldState instanceof StoppedState)) {
                // -- we disconnected from a network
                final String kingUniqueDeviceIdFromState = Util.extractKingUniqueDeviceIdFromState(oldState, this.ownDevice);
                notifyDisconnectedFromNetwork(kingUniqueDeviceIdFromState);
            }
        } else if (newState instanceof KingState) {
            if (oldState instanceof FreeState) {
                // -- we changed to KingState from a FreeState (excludes the
                // case when we change to KingState from PrinceState)
                notifyConnectedToNetwork();
            } else if (oldState instanceof PrinceState) {
                // -- we changed to KingState from a prince state -> we took the throne
                // - treat the old kingdom's census message as the new kingdom's census to let the diff logic for the onJoin/onLeft do their magic on arrival of the next census from this kingdom
                // - clear the last census message from the old kingdom
                final IBlaubotSubordinatedState _oldState = (IBlaubotSubordinatedState) oldState;
                CensusMessage oldKingdomMsg = lastCensusMessages.remove(_oldState.getKingUniqueId());
//                lastKnownKingUniqueDeviceId = _oldState.getKingUniqueId();
                if (oldKingdomMsg != null) {
                    lastCensusMessages.put(this.ownDevice.getUniqueDeviceID(), oldKingdomMsg);
                }
                // the onLeft/onJoined events should follow by the arriving census messages
            }
        } else if (newState instanceof StoppedState) {
            // stop was called explicitly
            if (oldState instanceof KingState || oldState instanceof IBlaubotSubordinatedState) {
                // only notify, if we actually were part of a network (previous state was not FreeState and not StoppedState)
                final String kingUniqueDeviceIdFromState = Util.extractKingUniqueDeviceIdFromState(oldState, this.ownDevice);
                notifyDisconnectedFromNetwork(kingUniqueDeviceIdFromState);
            }

            // in any case forget all census messages
            lastCensusMessages.clear();

        }

    }

    /**
     * Simply calls onConnected() on all registered listeners
     */
    public void notifyConnectedToNetwork() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Notifying lifecycle listeners onConnected() ...");
        }
        for (ILifecycleListener listener : lifecycleListeners) {
            listener.onConnected();
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Done notifying onConnected()");
        }
    }


    /**
     * Triggers onDeviceLeft(..) for all of the known devices from the old
     * network (except the own devices) followed by a onDisconnected() on
     * the {@link eu.hgross.blaubot.core.ILifecycleListener} added to this {@link eu.hgross.blaubot.core.Blaubot}
     * instance.
     *
     * @param oldKingUniqueId the uniqueDeviceId of the former network's king device.
     */
    public void notifyDisconnectedFromNetwork(String oldKingUniqueId) {
        // -- the uniqueId of our old king is known as well as the new
        // king's uniqueId
        // as of
        // https://scm.mi.hs-rm.de/trac/2014maprojekt/2014maprojekt01/ticket/22
        // we have to trigger onDeviceLeft for
        // each device of the former network (lastCensusMessage) followed by
        // onDisconnected()
        // then we have to call onConnected() and onDeviceJoined(device) for
        // each device of the new network

        // trigger onDeviceLeft for each of the former connected devices
        // except ourselves
        CensusMessage oldNetworksLastCensusMessage = lastCensusMessages.remove(oldKingUniqueId);
        if (oldNetworksLastCensusMessage != null) {
            notfiyOnDeviceLeftForKingdom(oldNetworksLastCensusMessage);
        } else {
            if (Log.logWarningMessages()) {
                Log.w(LOG_TAG, "Never got a CensusMessage from my old network (King was: " + oldKingUniqueId + ")");
            }
        }

        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Notifying lifecycle listeners onDisconnected()");
        }


        // fire the princeChanged, kingChangend and onDisconnected 
        IBlaubotDevice kDevice = new BlaubotDevice(oldKingUniqueId);
        IBlaubotDevice pDevice;
        if (oldNetworksLastCensusMessage != null && oldNetworksLastCensusMessage.extractPrinceUniqueId() != null) {
            pDevice = new BlaubotDevice(oldNetworksLastCensusMessage.extractPrinceUniqueId());
        } else if (lastKnownPrinceUniqueDeviceId != null) {
            pDevice = new BlaubotDevice(lastKnownPrinceUniqueDeviceId);
        } else {
            pDevice = null;
        }
        for (ILifecycleListener listener : lifecycleListeners) {
            // trigger onPrinceDeviceChanged and onKingDeviceChanged with king and prince = null
            listener.onKingDeviceChanged(kDevice, null);
            listener.onPrinceDeviceChanged(pDevice, null);
            lastKnownKingUniqueDeviceId = null;
            lastKnownPrinceUniqueDeviceId = null;
            listener.onDisconnected();
        }
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Done notifying onDisconnected()");
        }
    }

    /**
     * Calls onDeviceLeft for each device in oldNetworksLastCensusMessage, that does not match our uniqueDeviceId
     *
     * @param oldNetworksLastCensusMessage the message to derive the onDeviceLeft calls from
     */
    private void notfiyOnDeviceLeftForKingdom(CensusMessage oldNetworksLastCensusMessage) {
        // create the set of IBlaubotDevices to trigger a onDeviceLeft event for
        Set<IBlaubotDevice> leftDevices = new HashSet<>();
        for (final String deviceUniqueId : oldNetworksLastCensusMessage.getDeviceStates().keySet()) {
            // check if we can ignore this device because it is one of
            // our own uniqueIds
            if (deviceUniqueId.equals(this.ownDevice.getUniqueDeviceID())) {
                continue;
            }
            // if we can't skip, add a blaubotDevice instance
            IBlaubotDevice device = new BlaubotDevice(deviceUniqueId);
            leftDevices.add(device);
        }

        // finally trigger the onDeviceLeft events
        for (ILifecycleListener listener : lifecycleListeners) {
            for (IBlaubotDevice leftDevice : leftDevices) {
                listener.onDeviceLeft(leftDevice);
            }
        }

    }

    @Override
    public void onStateMachineStopped() {
        // handled in onStateChange
    }

    @Override
    public void onStateMachineStarted() {
        // handled in onStateChange
    }

    /**
     * Adds an {@link eu.hgross.blaubot.core.ILifecycleListener}
     *
     * @param lifecycleListener the listener to add
     */
    public void addLifecycleListener(ILifecycleListener lifecycleListener) {
        this.lifecycleListeners.add(lifecycleListener);
    }

    /**
     * Removes an {@link eu.hgross.blaubot.core.ILifecycleListener}
     *
     * @param lifecycleListener the listener to remove
     */
    public void removeLifecycleListener(ILifecycleListener lifecycleListener) {
        this.lifecycleListeners.remove(lifecycleListener);
    }
}

package eu.hgross.blaubot.util;

import java.util.HashSet;
import java.util.Observable;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.ILifecycleListener;

/**
 * An utility lifecycle listener that keeps track of the currently connected
 * devices inside the kingdom. It extends Observable so any interest party
 * can attach themselve to changes via addObserver().
 */
public class KingdomCensusLifecycleListener extends Observable implements ILifecycleListener {
    private IBlaubotDevice currentPrinceDevice;
    private Set<IBlaubotDevice> devices;
    private IBlaubotDevice ownDevice;
    private IBlaubotDevice currentKing;

    /**
     * @param ownDevice the own device
     */
    public KingdomCensusLifecycleListener(IBlaubotDevice ownDevice) {
        this.devices = new CopyOnWriteArraySet<>();
        this.ownDevice = ownDevice;
        this.currentPrinceDevice = null;
    }

    /**
     * Get the connected devices
     *
     * @return A set of devices inside the current blaubot network.
     */
    public Set<IBlaubotDevice> getDevices() {
        return devices;
    }

    /**
     * Get the unique ids for all connected devices
     *
     * @return A set of device unique ids inside the current blaubot network.
     */
    public Set<String> getConnectedUniqueIds() {
        Set<String> out = new HashSet<>();
        for(IBlaubotDevice d : devices) {
            out.add(d.getUniqueDeviceID());
        }
        return out;
    }

    /**
     * Get the current prince device.
     *
     * @return the current prince device; may be null
     */
    public IBlaubotDevice getCurrentPrinceDevice() {
        return currentPrinceDevice;
    }

    /**
     * Get the current king device
     * @return the current king devce - may be null, if no king
     */
    public IBlaubotDevice getCurrentKing() {
        return currentKing;
    }

    @Override
    public void onConnected() {
        this.devices.add(ownDevice);
        setChanged();
        notifyObservers();
    }

    @Override
    public void onDisconnected() {
        this.devices.remove(ownDevice);
        setChanged();
        notifyObservers();
    }

    @Override
    public void onDeviceJoined(IBlaubotDevice blaubotDevice) {
        this.devices.add(blaubotDevice);
        setChanged();
        notifyObservers();
    }

    @Override
    public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
        this.devices.remove(blaubotDevice);
        setChanged();
        notifyObservers();
    }

    @Override
    public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {
        this.currentPrinceDevice = newPrince;
        setChanged();
        notifyObservers();
    }

    @Override
    public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {
        this.currentKing = newKing;
        setChanged();
        notifyObservers();
    }
}

package eu.hgross.blaubot.core;

/**
 * Adapter pattern for the ILifecycleListener interface.
 */
public class LifecycleListenerAdapter implements ILifecycleListener {

    @Override
    public void onConnected() {

    }

    @Override
    public void onDisconnected() {

    }

    @Override
    public void onDeviceJoined(IBlaubotDevice blaubotDevice) {

    }

    @Override
    public void onDeviceLeft(IBlaubotDevice blaubotDevice) {

    }

    @Override
    public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {

    }

    @Override
    public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {

    }
}

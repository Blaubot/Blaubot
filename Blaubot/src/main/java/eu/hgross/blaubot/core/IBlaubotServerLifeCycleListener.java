package eu.hgross.blaubot.core;

/**
 * Listener interface to be attached to the BlaubotServer.
 */
public interface IBlaubotServerLifeCycleListener {
    /**
     * Called when a connection to a kingdom is available
     * @param kingdom the newly connected kingdom
     */
    public void onKingdomConnected(BlaubotKingdom kingdom);

    /**
     * Called when a connection to a blaubot kingdom was lost or disconnected on purpose.
     * @param kingdom the disconnected kingdom
     */
    public void onKingdomDisconnected(BlaubotKingdom kingdom);
}

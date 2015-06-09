package eu.hgross.blaubot.core;

/**
 * An {@link ILifecycleListener} can be registered with 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface ILifecycleListener {
	/**
	 * Triggered when the {@link Blaubot} instance becomes part of a blaubot kingdom. 
	 */
	public void onConnected();
	
	/**
	 * Triggered when the {@link Blaubot} instance is no longer part of a blaubot kingdom.
	 */
	public void onDisconnected();
	
	/**
	 * Triggered when another {@link Blaubot} instance on another {@link IBlaubotDevice} joined the network.
	 * It is guaranteed that for one specific {@link IBlaubotDevice} {@link ILifecycleListener#onDeviceJoined(IBlaubotDevice)}
	 * is never called twice without a triggered {@link ILifecycleListener#onDeviceLeft(IBlaubotDevice)} in between.
	 * @param blaubotDevice
	 */
	public void onDeviceJoined(IBlaubotDevice blaubotDevice);
	
	/**
	 * Triggered when an {@link IBlaubotDevice} left the blaubot kingdom.
	 * @param blaubotDevice
	 */
	public void onDeviceLeft(IBlaubotDevice blaubotDevice);
	
	/**
	 * Triggered when a prince nomination was completed.
	 * @param oldPrince the old prince (may be null, in case no prince was nominated previously)
	 * @param newPrince the new prince (may be null, in case there is no device to be prince)
	 */
	public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince);

    /**
     * Triggered when the king device was changed.
     * Both params may be null, if there was no king before or is no king now.
     * @param oldKing the old king
     * @param newKing the new king
     */
    public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing);
}

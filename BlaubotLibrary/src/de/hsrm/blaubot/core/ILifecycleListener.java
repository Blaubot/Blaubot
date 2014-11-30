package de.hsrm.blaubot.core;

import de.hsrm.blaubot.message.admin.CensusMessage;
import de.hsrm.blaubot.protocol.ProtocolManager;

/**
 * An {@link ILifecycleListener} can be registered with 
 * 
 * TODO: onConnected, onDisconnected not implemented
 * TODO: onDeviceJoined and onDeviceLeft is determined by a diff of {@link CensusMessage}s in {@link Blaubot} but should be triggered from the {@link ProtocolManager} after a succeeded handshake.
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
}

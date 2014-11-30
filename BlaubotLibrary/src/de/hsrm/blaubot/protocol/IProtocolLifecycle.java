package de.hsrm.blaubot.protocol;

/**
 * implement this interface to respond to lifecycle events in the protocol
 * layer. these events are somewhat similar to the better known android
 * lifecycle callbacks like onDestroy() or onStart().
 * 
 * @author manuelpras
 *
 */
public interface IProtocolLifecycle {

	/**
	 * start processes like consuming message from a queue and sending them to
	 * other parties in the network
	 */
	public void activate();

	/**
	 * stop everything (like threads) and release resources
	 */
	public void deactivate();

}

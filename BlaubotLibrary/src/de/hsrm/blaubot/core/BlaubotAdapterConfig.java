package de.hsrm.blaubot.core;

import de.hsrm.blaubot.core.acceptor.BlaubotConnectionManager;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotBeaconInterface;
import de.hsrm.blaubot.core.statemachine.states.PrinceState;

/**
 * Holds hardware specific configuration data for the {@link IBlaubotAdapter}s.
 *
 * TODO: add a config attribute to configure a "softer" discovery timeout strategy for
 * the {@link IBlaubotBeaconInterface}s when in {@link PrinceState}.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotAdapterConfig {
	private int keepAliveInterval = 500;
	private int connectorRetryTimeout = 500;
	private float exponentialBackoffFactor = 1.5f;
	private int maxConnectionRetries = 3;
	private boolean mergeKingdomsActivated = true;
	
	public int getKeepAliveInterval() {
		return keepAliveInterval;
	}
	
	public void setKeepAliveInterval(int keepAliveInterval) {
		this.keepAliveInterval = keepAliveInterval;
	}

	public int getConnectorRetryTimeout() {
		return connectorRetryTimeout;
	}

	/**
	 * The initial timeout to wait if using the exponential backoff method of
	 * {@link BlaubotConnectionManager}.
	 * @param connectorRetryTimeout
	 */
	public void setConnectorRetryTimeout(int connectorRetryTimeout) {
		this.connectorRetryTimeout = connectorRetryTimeout;
	}

	public float getExponentialBackoffFactor() {
		return exponentialBackoffFactor;
	}

	
	/**
	 * The factor to be used in the exponential backoff method of {@link BlaubotConnectionManager}.
	 * 
	 * @param exponentialBackoffFactor
	 */
	public void setExponentialBackoffFactor(float exponentialBackoffFactor) {
		this.exponentialBackoffFactor = exponentialBackoffFactor;
	}

	public int getMaxConnectionRetries() {
		return maxConnectionRetries;
	}

	/**
	 * The global maximum number of retries when connecting to another device via this adapter.
	 * @param maxConnectionRetries
	 */
	public void setMaxConnectionRetries(int maxConnectionRetries) {
		this.maxConnectionRetries = maxConnectionRetries;
	}

	public boolean isMergeKingdomsActivated() {
		return mergeKingdomsActivated;
	}

	/**
	 * Set if the merge of kingdoms is activated.
	 * This implies that the {@link IBlaubotBeaconInterface}s of devices in {@link PrinceState} 
	 * will actively try to discover and determine the state of devices nearby and not connected
	 * with their own network.
	 * 
	 * @param mergeKingdomsActivated
	 */
	public void setMergeKingdomsActivated(boolean mergeKingdomsActivated) {
		this.mergeKingdomsActivated = mergeKingdomsActivated;
	}

}

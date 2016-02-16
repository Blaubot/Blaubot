package eu.hgross.blaubot.core;

import eu.hgross.blaubot.core.statemachine.states.PrinceState;

/**
 * Holds hardware specific configuration data for the {@link IBlaubotAdapter}s.
 *
 * 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotAdapterConfig {
    // TODO: add a config attribute to configure a "softer" discovery timeout strategy for
    // the {@link eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon}s when in {@link PrinceState}.
    
	private int keepAliveInterval = 500;
	private int connectorRetryTimeout = 500;
	private float exponentialBackoffFactor = 1.5f;
	private int maxConnectionRetries = 4;
	private boolean mergeKingdomsActivated = true;
	private int connectionTimeout = 10000;
	
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
	 * This implies that the {@link eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon}s of devices in {@link PrinceState}
	 * will actively try to discover and determine the state of devices nearby and not connected
	 * with their own network.
	 * 
	 * @param mergeKingdomsActivated
	 */
	public void setMergeKingdomsActivated(boolean mergeKingdomsActivated) {
		this.mergeKingdomsActivated = mergeKingdomsActivated;
	}

	/**
	 * Max connect duration.
	 * @return timeout in ms
	 */
	public int getConnectionTimeout() {
		return connectionTimeout;
	}

	/**
	 * Sets the max connection time before we timeout.
	 * @param connectionTimeout the timeout in ms
	 */
	public void setConnectionTimeout(int connectionTimeout) {
		this.connectionTimeout = connectionTimeout;
	}
}

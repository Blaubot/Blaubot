package eu.hgross.blaubot.core;

import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.core.statemachine.ConnectionStateMachine;

/**
 * Interface for blaubot adapters representing a medium to exchange data with
 * (like Bluetooth, WIFI, ...).
 * 
 * An implementation can provide adapter specific constants for the {@link ConnectionStateMachine}
 * via it's getConnectionStateMachineConfig() implementation. The implementation should create a 
 * final {@link ConnectionStateMachineConfig} retrievable by this method.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface IBlaubotAdapter {

    /**
     * Get the connector for this adapter. The connector is the counterpart of this adapter's acceptor
     * and allows to make connections to the corresponding acceptor.
     *
     * @return the connector
     */
	public IBlaubotConnector getConnector();

    /**
     * Get the connection acceptor that is in charge to retrieve incoming connections issued by compatible
     * IBlaubotConnector implementations.
     *
     * @return the acceptor
     */
	public IBlaubotConnectionAcceptor getConnectionAcceptor();

	/**
	 * Setter for dependency injecton of the blaubot instance.
     *
	 * @param blaubotInstance
	 */
	public void setBlaubot(Blaubot blaubotInstance);

    /**
     * Get the current blaubot instance
     */
    public Blaubot getBlaubot();
	
	/**
	 * The adapter specific {@link ConnectionStateMachine} configuration.
     *
	 * @return the connection state machine configuration specific for this adapter. 
	 */
	public ConnectionStateMachineConfig getConnectionStateMachineConfig();
	
	/**
	 * Configuration object for hardware specific adapter settings.
     *
	 * @return the config for this adapter
	 */
	public BlaubotAdapterConfig getBlaubotAdapterConfig();
}

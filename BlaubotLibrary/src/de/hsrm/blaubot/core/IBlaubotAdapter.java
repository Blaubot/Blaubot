package de.hsrm.blaubot.core;

import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotBeaconInterface;
import de.hsrm.blaubot.core.connector.IBlaubotConnector;
import de.hsrm.blaubot.core.statemachine.ConnectionStateMachine;

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
	public IBlaubotConnector getConnector();
	public IBlaubotConnectionAcceptor getConnectionAcceptor();
	public IBlaubotBeaconInterface getBeaconInterface();
	
	/**
	 * @return the own {@link IBlaubotDevice} for this adapter interface
	 */
	public IBlaubotDevice getOwnDevice();
	/**
	 * Setter for dependency injecton of the blaubot instance.
	 * @param blaubotInstance
	 */
	public void setBlaubot(Blaubot blaubotInstance);
	
	/**
	 * The adapter specific {@link ConnectionStateMachine} configuration. 
	 * @return the connection state machine configuration specific for this adapter. 
	 */
	public ConnectionStateMachineConfig getConnectionStateMachineConfig();
	
	/**
	 * Configuration object for hardware specific adapter settings.
	 * @return
	 */
	public BlaubotAdapterConfig getBlaubotAdapterConfig();
}

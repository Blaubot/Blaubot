package de.hsrm.blaubot.core.statemachine.states;

import de.hsrm.blaubot.core.IBlaubotConnection;

/**
 * Marks {@link IBlaubotState}s that have a king connection.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public interface IBlaubotSubordinatedState {
	/**
	 * 
	 * @return this subordinate's king uniqueId
	 */
	public String getKingUniqueId();
	
	/**
	 * 
	 * @return the connection to this subordinate's king
	 */
	public IBlaubotConnection getKingConnection();
}

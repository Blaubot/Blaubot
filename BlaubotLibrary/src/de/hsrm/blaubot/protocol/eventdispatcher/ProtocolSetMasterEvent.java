package de.hsrm.blaubot.protocol.eventdispatcher;

import de.hsrm.blaubot.protocol.ProtocolManager;

/**
 * represents the setMaster() event which updates the current master status of
 * the {@link ProtocolManager}
 * 
 * @author manuelpras
 *
 */
public class ProtocolSetMasterEvent extends ProtocolEvent {

	private boolean isMaster;

	public ProtocolSetMasterEvent(boolean isMaster) {
		super(EventType.SET_MASTER);
		this.isMaster = isMaster;
	}

	public boolean isMaster() {
		return isMaster;
	}

}

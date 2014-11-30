package de.hsrm.blaubot.ethernet;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.core.acceptor.discovery.ExchangeStatesTask;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotDiscoveryEventListener;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;

/**
 * This task should be used with {@link EthernetBeaconAcceptThread} as its counterside.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
class EthernetExchangeTask extends ExchangeStatesTask {
	public static final int METADATA_BYTE_LENGTH = 2*4; // 2 Integer
	private final int ourAcceptorPort;
	private final int ourBeaconPort;

	public EthernetExchangeTask(IBlaubotConnection connection, IBlaubotState ourState, int ourAcceptorPort, int ourBeaconPort, IBlaubotDiscoveryEventListener eventListener) {
		super(connection, ourState, eventListener);
		this.ourAcceptorPort = ourAcceptorPort;
		this.ourBeaconPort = ourBeaconPort;
	}

	/**
	 * Utilizes the connection to send our acceptor and beacon port to the beacon to allow
	 * the remote beacon to identify us.
	 * @throws IOException 
	 */
	private void identify() throws IOException {
		ByteBuffer bb = ByteBuffer.allocate(METADATA_BYTE_LENGTH); 
		bb.order(ByteOrder.BIG_ENDIAN);
		bb.putInt(ourAcceptorPort);
		bb.putInt(ourBeaconPort);
		bb.flip();
		connection.write(bb.array());
	}
	
	@Override
	public void run() {
		try {
			identify();
		} catch (IOException e) {
			connection.disconnect();
			return;
		}
		super.run();
	}
}
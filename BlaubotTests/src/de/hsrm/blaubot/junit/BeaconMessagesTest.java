package de.hsrm.blaubot.junit;

import org.junit.Assert;
import org.junit.Test;

import de.hsrm.blaubot.core.State;
import de.hsrm.blaubot.core.acceptor.discovery.BeaconMessage;

/**
 * Tests regarding the {@link BeaconMessage}s and their sending components.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BeaconMessagesTest {

	@Test
	/**
	 * Tests the serialization/deserialization of beacon messages
	 */
	public void testBeaconMessageSerialization() {
		// Test states that don't need the uniqueId
		for (State state : new State[]{State.Stopped, State.Free, State.King}) {
			BeaconMessage bm = new BeaconMessage(state);
			byte[] serialized = bm.toBytes();
			BeaconMessage bm2 = BeaconMessage.fromBytes(serialized);
			Assert.assertEquals(bm, bm2);
		}
		
		// Test states that need to communicate the king's uniqueId
		for (State state : new State[]{State.Prince, State.Peasant}) {
			BeaconMessage bm = new BeaconMessage(state, "someUniqueId");
			byte[] serialized = bm.toBytes();
			BeaconMessage bm2 = BeaconMessage.fromBytes(serialized);
			Assert.assertEquals(bm, bm2);
		}
	}
}

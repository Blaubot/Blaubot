package eu.hgross.blaubot.test;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;

import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.discovery.BeaconMessage;

/**
 * Tests regarding the {@link BeaconMessage}s and their sending components.
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public class BeaconMessagesTest {

	@Test
	/**
	 * Tests the serialization/deserialization of beacon messages
	 */
	public void testBeaconMessageSerialization() {
		// Test states that don't need the king's uniqueId
		for (State state : new State[]{State.Stopped, State.Free, State.King}) {
			BeaconMessage bm = new BeaconMessage("myUniqueId", state, new ArrayList<ConnectionMetaDataDTO>());
			byte[] serialized = bm.toBytes();
			BeaconMessage bm2 = BeaconMessage.fromBytes(serialized);
			Assert.assertEquals(bm, bm2);
		}
		
		// Test states that need to communicate the king's uniqueId
		for (State state : new State[]{State.Prince, State.Peasant}) {
			BeaconMessage bm = new BeaconMessage("myUniqueId", state, new ArrayList<ConnectionMetaDataDTO>(), "someKingUniqueId",
                    new ArrayList<ConnectionMetaDataDTO>());
			byte[] serialized = bm.toBytes();
			BeaconMessage bm2 = BeaconMessage.fromBytes(serialized);
			Assert.assertEquals(bm, bm2);
		}
	}
}

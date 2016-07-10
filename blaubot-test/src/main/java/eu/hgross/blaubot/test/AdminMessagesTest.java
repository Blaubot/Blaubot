package eu.hgross.blaubot.test;

import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.admin.ACKPronouncePrinceAdminMessage;
import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.BowDownToNewKingAdminMessage;
import eu.hgross.blaubot.admin.CensusMessage;
import eu.hgross.blaubot.admin.CloseRelayConnectionAdminMessage;
import eu.hgross.blaubot.admin.DiscoveredDeviceAdminMessage;
import eu.hgross.blaubot.admin.PrinceFoundAKingAdminMessage;
import eu.hgross.blaubot.admin.PronouncePrinceAdminMessage;
import eu.hgross.blaubot.admin.RelayAdminMessage;
import eu.hgross.blaubot.admin.ServerConnectionAvailableAdminMessage;
import eu.hgross.blaubot.admin.ServerConnectionDownAdminMessage;
import eu.hgross.blaubot.messaging.BlaubotMessage;

import static org.junit.Assert.assertTrue;

/**
 * Testing the serialization and deserialization of {@link AbstractAdminMessage}
 * s to and from {@link BlaubotMessage}s.
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class AdminMessagesTest {

	@Test
	public void testPrinceAdminMessage() {
		PronouncePrinceAdminMessage ppam = new PronouncePrinceAdminMessage("test", new ArrayList<ConnectionMetaDataDTO>());
		assertSerialization(ppam, PronouncePrinceAdminMessage.class);
	}

	@Test
	public void testBowDownToNewKingMessage() {
		BowDownToNewKingAdminMessage adminMsg = new BowDownToNewKingAdminMessage("someUniqueDeviceId", new ArrayList<ConnectionMetaDataDTO>());
        assertSerialization(adminMsg, BowDownToNewKingAdminMessage.class);
	}

	@Test
	public void testCensusMessage() {
		Map<String, State> censusMessages = new HashMap<String, State>();
		censusMessages.put("aUniqueId", State.Prince);
		censusMessages.put("aUniqueId2", State.Peasant);
		censusMessages.put("aUniqueId3", State.Free);
		censusMessages.put("aUniqueId4", State.King);
		censusMessages.put("aUniqueId5", State.Stopped);
		CensusMessage adminMsg = new CensusMessage(censusMessages);
        assertSerialization(adminMsg, CensusMessage.class);
	}

	@Test
	public void testPrinceFoundAKingMessage() {
		PrinceFoundAKingAdminMessage adminMsg = new PrinceFoundAKingAdminMessage("foundKingUniqueId", new ArrayList<ConnectionMetaDataDTO>());
        assertSerialization(adminMsg, PrinceFoundAKingAdminMessage.class);
	}

	@Test
	public void testAckPronouncePrinceMessage() {
        ArrayList<ConnectionMetaDataDTO> metaData = new ArrayList<>();
		ACKPronouncePrinceAdminMessage adminMsg = new ACKPronouncePrinceAdminMessage("foundKingUniqueId", metaData);
		assertSerialization(adminMsg, ACKPronouncePrinceAdminMessage.class);
	}

    @Test
    public void testCloseRelayConnectionAdminMessage() {
        CloseRelayConnectionAdminMessage closeRelayConnectionAdminMessage = new CloseRelayConnectionAdminMessage("mediatorId");
        assertSerialization(closeRelayConnectionAdminMessage, CloseRelayConnectionAdminMessage.class);
    }

    @Test
    public void testConnectionUpAdminMessage() {
        ServerConnectionAvailableAdminMessage availableAdminMessage = new ServerConnectionAvailableAdminMessage("mediatorId", "recipientId");
        assertSerialization(availableAdminMessage, ServerConnectionAvailableAdminMessage.class);
    }

    @Test
    public void testConnectionDownAdminMessage() {
        ServerConnectionDownAdminMessage downAdminMessage = new ServerConnectionDownAdminMessage("mediatorId");
        assertSerialization(downAdminMessage, ServerConnectionDownAdminMessage.class);
    }

    @Test
    public void testRelayAdminMessage() {
        BlaubotMessage anyMessage = new ServerConnectionDownAdminMessage("mdiatorId").toBlaubotMessage();
        RelayAdminMessage relayAdminMessage = new RelayAdminMessage(anyMessage);
        assertSerialization(relayAdminMessage, RelayAdminMessage.class);
    }

    @Test
    public void testDiscoveredDeviceAdminMessage() {
        DiscoveredDeviceAdminMessage adminMessage = new DiscoveredDeviceAdminMessage("uniqueDeviceId", State.Free, new ArrayList<ConnectionMetaDataDTO>());
        assertSerialization(adminMessage, DiscoveredDeviceAdminMessage.class);
    }

    private void assertSerialization(AbstractAdminMessage adminMsg, Class type) {
        BlaubotMessage bbm = adminMsg.toBlaubotMessage();
        byte[] payload = bbm.getPayload();
        BlaubotMessage deserialized = new BlaubotMessage();
        deserialized.setPayload(payload);
        AbstractAdminMessage adminMsgDeserialized = null;
        try {
            adminMsgDeserialized = (AbstractAdminMessage) type.getConstructor(BlaubotMessage.class).newInstance(deserialized);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertTrue(adminMsg.equals(adminMsgDeserialized));
    }
}

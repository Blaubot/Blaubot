package de.hsrm.blaubot.junit;

import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

import de.hsrm.blaubot.core.State;
import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.message.admin.ACKPronouncePrinceAdminMessage;
import de.hsrm.blaubot.message.admin.AbstractAdminMessage;
import de.hsrm.blaubot.message.admin.BowDownToNewKingAdminMessage;
import de.hsrm.blaubot.message.admin.CensusMessage;
import de.hsrm.blaubot.message.admin.PrinceFoundAKingAdminMessage;
import de.hsrm.blaubot.message.admin.PronouncePrinceAdminMessage;
import de.hsrm.blaubot.mock.BlaubotDeviceMock;
import de.hsrm.blaubot.protocol.ProtocolManager;

/**
 * Testing the serialization and deserialization of {@link AbstractAdminMessage}
 * s to and from {@link BlaubotMessage}s.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class AdminMessagesTest {

	@Test
	public void testPrinceAdminMessage() {
		PronouncePrinceAdminMessage ppam = new PronouncePrinceAdminMessage("test");
		BlaubotMessage bbm = ppam.toBlaubotMessage();
		byte[] payload = bbm.getPayload();
		BlaubotMessage deserialized = new BlaubotMessage();
		deserialized.setMessageType(bbm.getMessageType());
		deserialized.setProtocolVersion(ProtocolManager.PROTOCOL_VERSION);
		deserialized.setPayload(payload);
		PronouncePrinceAdminMessage ppamDeserialized = new PronouncePrinceAdminMessage(deserialized);
		assertTrue(ppam.equals(ppamDeserialized));
	}

	@Test
	public void testBowDownToNewKingMessage() {
		BowDownToNewKingAdminMessage adminMsg = new BowDownToNewKingAdminMessage(new BlaubotDeviceMock("someUniqueDevice"));
		BlaubotMessage bbm = adminMsg.toBlaubotMessage();
		byte[] payload = bbm.getPayload();
		BlaubotMessage deserialized = new BlaubotMessage();
		deserialized.setMessageType(bbm.getMessageType());
		deserialized.setProtocolVersion(ProtocolManager.PROTOCOL_VERSION);
		deserialized.setPayload(payload);
		AbstractAdminMessage adminMsgDeserialized = new BowDownToNewKingAdminMessage(deserialized);
		assertTrue(adminMsg.equals(adminMsgDeserialized));
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
		BlaubotMessage bbm = adminMsg.toBlaubotMessage();
		byte[] payload = bbm.getPayload();
		BlaubotMessage deserialized = new BlaubotMessage();
		deserialized.setMessageType(bbm.getMessageType());
		deserialized.setProtocolVersion(ProtocolManager.PROTOCOL_VERSION);
		deserialized.setPayload(payload);
		AbstractAdminMessage adminMsgDeserialized = new CensusMessage(deserialized);
		assertTrue(adminMsg.equals(adminMsgDeserialized));
	}

	@Test
	public void testPrinceFoundAKingMessage() {
		PrinceFoundAKingAdminMessage adminMsg = new PrinceFoundAKingAdminMessage("foundKingUniqueId");
		BlaubotMessage bbm = adminMsg.toBlaubotMessage();
		byte[] payload = bbm.getPayload();
		BlaubotMessage deserialized = new BlaubotMessage();
		deserialized.setMessageType(bbm.getMessageType());
		deserialized.setProtocolVersion(ProtocolManager.PROTOCOL_VERSION);
		deserialized.setPayload(payload);
		AbstractAdminMessage adminMsgDeserialized = new PrinceFoundAKingAdminMessage(deserialized);
		assertTrue(adminMsg.equals(adminMsgDeserialized));
	}

	@Test
	public void testAckPronouncePrinceMessage() {
		ACKPronouncePrinceAdminMessage adminMsg = new ACKPronouncePrinceAdminMessage("foundKingUniqueId");
		BlaubotMessage bbm = adminMsg.toBlaubotMessage();
		byte[] payload = bbm.getPayload();
		BlaubotMessage deserialized = new BlaubotMessage();
		deserialized.setMessageType(bbm.getMessageType());
		deserialized.setProtocolVersion(ProtocolManager.PROTOCOL_VERSION);
		deserialized.setPayload(payload);
		AbstractAdminMessage adminMsgDeserialized = new ACKPronouncePrinceAdminMessage(deserialized);
		assertTrue(adminMsg.equals(adminMsgDeserialized));
	}
}

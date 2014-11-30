package de.hsrm.blaubot.junit;

import static org.junit.Assert.assertTrue;

import java.util.BitSet;

import org.junit.Test;

import de.hsrm.blaubot.message.MessageType;
import de.hsrm.blaubot.message.MessageTypeFactory;

public class MessageTypesTest {

	@Test
	public void testBroadCastMessage() {
		MessageType messageType = MessageTypeFactory
				.createKeepAliveMessageType();
		messageType.Broadcast();
		messageType.Payload();
		messageType.Admin();
		assertTrue(messageType.isBroadcastMessage());
		messageType = MessageTypeFactory.createKeepAliveMessageType();
		messageType.Broadcast();
		messageType.NoPayload();
		messageType.Application();
		assertTrue(messageType.isBroadcastMessage());
		messageType.NoBroadcast();
		messageType.NoPayload();
		messageType.Application();
		assertTrue(!messageType.isBroadcastMessage());
	}

	@Test
	public void testKeepAliveType() {
		byte type = MessageTypeFactory.createKeepAliveMessageType().toByte();
		BitSet bs = BitSet.valueOf(new byte[] { type });
		assertTrue(!bs.get(0));
		assertTrue(bs.get(1));
		assertTrue(!bs.get(2));
		assertTrue(!bs.get(15));
	}

	@Test
	public void testToBroadcastMessageAndReverse() {
		MessageType messageType = MessageTypeFactory
				.createBroadcastChannelIDMessageType();
		byte type = messageType.toByte();

		type = messageType.Broadcast().toByte();
		BitSet bs = BitSet.valueOf(new byte[] { type });
		assertTrue(bs.get(2));

		type = messageType.NoBroadcast().toByte();
		bs = BitSet.valueOf(new byte[] { type });
		assertTrue(!bs.get(2));

		type = messageType.Broadcast().toByte();
		bs = BitSet.valueOf(new byte[] { type });
		assertTrue(bs.get(2));
	}

}

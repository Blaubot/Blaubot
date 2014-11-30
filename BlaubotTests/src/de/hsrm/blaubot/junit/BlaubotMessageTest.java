package de.hsrm.blaubot.junit;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;

import org.junit.Test;

import de.hsrm.blaubot.core.BlaubotConstants;
import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.message.MessageType;
import de.hsrm.blaubot.message.MessageTypeFactory;
import de.hsrm.blaubot.mock.BlaubotConnectionQueueMock;
import de.hsrm.blaubot.mock.BlaubotDeviceMock;

public class BlaubotMessageTest {

	@Test(timeout=100)
	public void serializeDeserialize() {
		BlaubotMessage origMsg = new BlaubotMessage();
		
		// create message properties
		final byte protocolVersion = 2;
		final short channelId = -2;
		final short sourceDeviceId = 8;
		final MessageType messageType = MessageTypeFactory.createBroadcastDeviceIDMessageType();
		final byte[] payload = "test".getBytes();

		// set properties
		origMsg.setChannelId(channelId);
		origMsg.setMessageType(messageType);
		origMsg.setPayload(payload);
		origMsg.setProtocolVersion(protocolVersion);
		origMsg.setSourceDeviceId(sourceDeviceId);
		
		// serialize message
		final byte[] serialized = origMsg.toByteArray();
		
		// deserialize message from serialized bytes
		BlaubotMessage deserializedMsg = new BlaubotMessage();
		byte[] headerBytes = new byte[BlaubotMessage.HEADER_BYTE_COUNT];
		ByteBuffer buffer = ByteBuffer.wrap(serialized).order(BlaubotConstants.BYTE_ORDER);
		// get header bytes
		buffer.get(headerBytes);
		deserializedMsg.createHeader(headerBytes);
		
		// get payload bytes
		short payloadLength = buffer.getShort();
		byte[] deserializedPayload = new byte[payloadLength];
		buffer.get(deserializedPayload);
		deserializedMsg.setPayload(deserializedPayload);
		
		System.out.println("orig msg:");
		System.out.println(origMsg.toString());
		
		System.out.println("\ndeserialized msg:");
		System.out.println(deserializedMsg.toString());
		
		assertEquals(origMsg, deserializedMsg);
	}
	
	@Test(timeout=500)
	public void serializeDeserializeWithConnection() throws SocketTimeoutException, IOException {
		// create connection
		final String uniqueId = "remoteDevice";
		BlaubotDeviceMock remoteDevice = new BlaubotDeviceMock(uniqueId);
		BlaubotConnectionQueueMock fromConnection = new BlaubotConnectionQueueMock(remoteDevice);
		
		final String otherSidesDeviceId = "otherSidesDevice";
		BlaubotDeviceMock otherSidesDevice = new BlaubotDeviceMock(otherSidesDeviceId);
		BlaubotConnectionQueueMock toConnection = fromConnection.getOtherEndpointConnection(otherSidesDevice);
		
		BlaubotMessage origMsg = new BlaubotMessage();
		
		// create message properties
		final byte protocolVersion = 2;
		final short channelId = 5;
		final short sourceDeviceId = 8;
		final MessageType messageType = MessageTypeFactory.createBroadcastDeviceIDMessageType();
		final byte[] payload = "test".getBytes();

		// set properties
		origMsg.setChannelId(channelId);
		origMsg.setMessageType(messageType);
		origMsg.setPayload(payload);
		origMsg.setProtocolVersion(protocolVersion);
		origMsg.setSourceDeviceId(sourceDeviceId);
		
		// serialize message
		final byte[] serialized = origMsg.toByteArray();
		
		// write bytes to connection
		fromConnection.write(serialized);
		byte[] readSerialized = new byte[serialized.length];
		
		// read bytes from connection
		toConnection.readFully(readSerialized);
		
		// deserialize message from serialized bytes
		BlaubotMessage deserializedMsg = new BlaubotMessage();
		byte[] headerBytes = new byte[BlaubotMessage.HEADER_BYTE_COUNT];
		ByteBuffer buffer = ByteBuffer.wrap(readSerialized).order(BlaubotConstants.BYTE_ORDER);
		// get header bytes
		buffer.get(headerBytes);
		deserializedMsg.createHeader(headerBytes);
		
		// get payload bytes
		short payloadLength = buffer.getShort();
		byte[] deserializedPayload = new byte[payloadLength];
		buffer.get(deserializedPayload);
		deserializedMsg.setPayload(deserializedPayload);
		
		System.out.println("orig msg:");
		System.out.println(origMsg.toString());
		
		System.out.println("\ndeserialized msg:");
		System.out.println(deserializedMsg.toString());
		
		assertEquals(origMsg, deserializedMsg);
	}

}

package de.hsrm.blaubot.junit;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import org.junit.Ignore;
import org.junit.Test;

import de.hsrm.blaubot.message.MessageType;
import de.hsrm.blaubot.message.MessageTypeFactory;
import de.hsrm.blaubot.protocol.StreamManager;

public class ByteBufferTest {

	private static ByteOrder MY_BYTE_ORDER = StreamManager.MY_BYTE_ORDER;

	final short originSourceDeviceID = 5;
	final String originUniqueDeviceID = "12:as:34:df";
	final byte originVersion = (byte) 1;
	final MessageType originMessageType = MessageTypeFactory.createKeepAliveMessageType();
	final short originChannel_ID = 3;
	byte[] originPayload;
	int readCounter = 0;
	byte[] origin = new byte[0];

	@Test
	@Ignore
	public void test() {
		for (int i = 0; i < 1000; i++) {
			byte[] bytes = write();
			origin = bytes;
			read();
			readCounter = 0;
		}
	}

	/**
	 * schreibt in das zurueckgegebene array den inhalt
	 * 
	 * @return
	 */
	private byte[] write() {
		// broadcast device id to all devices including the new device itself
		// put device id and device's unique id in payload
		int capacity = 2 + originUniqueDeviceID.length();
		originPayload = new byte[capacity];
		ByteBuffer payloadBuffer = ByteBuffer.allocate(capacity).order(MY_BYTE_ORDER).putShort(originSourceDeviceID).put(originUniqueDeviceID.getBytes());
		payloadBuffer.clear();
		payloadBuffer.get(originPayload);

		byte type = originMessageType.toByte();

		final short payloadLength = (short) (originPayload != null ? originPayload.length : 0);
		final int headerLength = 8;
		final int capacity2 = headerLength + payloadLength;
		ByteBuffer writeBuffer = ByteBuffer.allocate(capacity2).order(MY_BYTE_ORDER);
		writeBuffer.put(originVersion);
		writeBuffer.put(type);
		writeBuffer.putShort(originChannel_ID);
		writeBuffer.putShort(originSourceDeviceID);
		writeBuffer.putShort(payloadLength);
		if (originPayload != null) {
			writeBuffer.put(originPayload);
		}
		byte[] bytes = new byte[capacity2];
		writeBuffer.clear();
		writeBuffer.get(bytes);
		return bytes;
	}

	/**
	 * liest per readfully blockweise das geschriebene ein
	 */
	private void read() {
		// 1 byte version, 1 byte type, 2 byte channel id, 2 byte source device
		// id
		byte[] buffer = new byte[6];
		readFully(buffer, 0, 6);
		ByteBuffer byteBuffer = ByteBuffer.wrap(buffer).order(MY_BYTE_ORDER);
		byte version = byteBuffer.get();

		byte type = byteBuffer.get();

		MessageType messageType = MessageType.fromByte(type);

		short channel_ID = byteBuffer.getShort();
		short sourceDevice_ID = byteBuffer.getShort();

		// 2 byte payload length
		buffer = new byte[2];
		readFully(buffer, 0, 2);
		byteBuffer = ByteBuffer.wrap(buffer).order(MY_BYTE_ORDER);
		short length = byteBuffer.getShort();

		// n byte payload
		buffer = new byte[length];
		readFully(buffer, 0, length);
		byteBuffer = ByteBuffer.wrap(buffer).order(MY_BYTE_ORDER);
		byte[] payload = new byte[length];
		byteBuffer.get(payload);

		check(payload, messageType, version, channel_ID, sourceDevice_ID);
	}

	/**
	 * faket ein readfully eines sockets
	 * 
	 * @param buffer
	 * @param offset
	 * @param byteCount
	 */
	private void readFully(byte[] buffer, int offset, int byteCount) {
		for (int i = offset; i < byteCount + offset; i++) {
			buffer[i] = origin[i + readCounter];
		}
		readCounter += byteCount;
	}

	private void check(byte[] payload, MessageType messageType, byte version, short channel_ID, short sourceDevice_ID) {
		assertEquals(originChannel_ID, channel_ID);
		assertEquals(originVersion, version);
		assertEquals(originSourceDeviceID, sourceDevice_ID);
		assertEquals(originMessageType, messageType);
		assertArrayEquals(originPayload, payload);
		assertEquals(readCounter, origin.length);
	}
	
}

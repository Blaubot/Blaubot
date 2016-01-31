package eu.hgross.blaubot.messaging;


import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.util.Log;

/**
 * The message object that is sent through connections
 */
public class BlaubotMessage {
    private static final String LOG_TAG = "BlaubotMessage";

    public final static int VERSION_FIELD_LENGTH = 1;
    public final static int TYPE_FIELD_LENGTH = 1;
    public final static int PRIORITY_FIELD_LENGTH = 1;
    public final static int CHANNEL_FIELD_LENGTH = 2;
    public final static int PAYLOAD_LENGTH_FIELD_LENGTH = 2;
    public final static int CHUNK_ID_FIELD_LENGTH = 2;

    public final static int CHUNK_NO_FIELD_LENGTH = 2;
    public final static int FULL_HEADER_LENGTH = VERSION_FIELD_LENGTH + TYPE_FIELD_LENGTH + PRIORITY_FIELD_LENGTH + CHANNEL_FIELD_LENGTH + CHUNK_ID_FIELD_LENGTH + CHUNK_NO_FIELD_LENGTH + PAYLOAD_LENGTH_FIELD_LENGTH;

    /**
     * Creates chunks of this message containing the given chunkId.
     * The chunk messages are numbered. The numbers can be retrieved via #getChunkNumber().
     * To signal a potential receiver that a chunk message is the last message of a chunkId,
     * a chunk message with less than BlaubotConstants.MAX_PAYLOAD_SIZE payload size is send.
     * If the last message's payload is equal to BlaubotConstants.MAX_PAYLOAD_SIZE the last message
     * will not contain any payload.
     *
     * @param chunkId the chunk id to identify this message
     * @return the ordered list of chunks
     * @throws IllegalArgumentException iff the message contains too much payload to chunk (more than Short.MAX_VALUE resulting chunks)
     */
    public List<BlaubotMessage> createChunks(short chunkId) {
        final int payloadLength = getPayload().length;
        if (!(payloadLength > 0)) {
            throw new IllegalStateException("createChunks() was called for a message without any payload!");
        }
        final int maxChunkSize = BlaubotConstants.MAX_PAYLOAD_SIZE;
        final int numChunks = (payloadLength + maxChunkSize - 1) / maxChunkSize; // rounded up
        if (numChunks > BlaubotConstants.USHORT_MAX_VALUE) { // unsigned short max value
            throw new IllegalArgumentException("The message contains " + payloadLength + "bytes payload which results in " + numChunks + " chunks. The number of chunks exceeds the message header field (short, 2 bytes) and is therefore too big");
        }
        final ByteBuffer byteBuffer = ByteBuffer.wrap(getPayload()).order(BlaubotConstants.BYTE_ORDER);
        final List<BlaubotMessage> chunks = new ArrayList<>();
        for (int chunkNo = 1; chunkNo <= numChunks; chunkNo += 1) {
            // create chunk messages
            final byte[] chunkPayload;
            if (byteBuffer.remaining() >= maxChunkSize) {
                chunkPayload = new byte[maxChunkSize];
            } else {
                chunkPayload = new byte[byteBuffer.remaining()];
            }
            byteBuffer.get(chunkPayload);

            // build the message object
            BlaubotMessage chunk = new BlaubotMessage();
            chunk.setMessageType(BlaubotMessageType.copy(messageType));
            chunk.getMessageType().setIsChunk(true);
            chunk.setChunkId(chunkId);
            chunk.setChunkNo((short) chunkNo);
            chunk.setPriority(priority);
            chunk.channelId = channelId;
            chunk.setPayload(chunkPayload);

            chunks.add(chunk);
        }

        // now we check if the last message equals our maxChunkSize and we therefore have to add an "end marker" message
        BlaubotMessage lastMessage = chunks.get(chunks.size()-1);
        if (lastMessage.getPayload().length == maxChunkSize) {
            // we add an empty message to signal that this is the last chunk
            BlaubotMessage chunk = new BlaubotMessage();
            chunk.setMessageType(BlaubotMessageType.copy(messageType));
            chunk.getMessageType().setIsChunk(true);
            chunk.setChunkId(chunkId);
            chunk.setChunkNo((short) (chunks.size() + 1));
            chunk.setPriority(priority);
            chunk.channelId = channelId;
            chunk.setPayload(new byte[0]);
            chunks.add(chunk);
        }


        return chunks;
    }

    /**
     * Creates a message from multiple chunks
     * @param chunks the complete list of chunks with the same chunkId. Must not be ordered.
     * @return the message
     */
    public static BlaubotMessage fromChunks(List<BlaubotMessage> chunks) {
        // sort by chunkNo
        Collections.sort(chunks, new Comparator<BlaubotMessage>() {
            @Override
            public int compare(BlaubotMessage o1, BlaubotMessage o2) {
                return Short.valueOf(o1.chunkNo).compareTo(Short.valueOf(o2.chunkNo));
            }
        });


        int chunkId = -1;
        int totalSize = 0;
        int i = 0;
        Priority p = null;
        short channelId = -1;
        IBlaubotConnection originator = null;
        for (BlaubotMessage chunk : chunks) {
            // validate id on the run
            if (i++ == 0) {
                // initially set up some vars
                chunkId = chunk.getChunkId();
                channelId = chunk.getChannelId();
                p = chunk.getPriority();
                originator = chunk.getLastOriginatorConnection();
            } else if (chunkId != chunk.getChunkId()) {
                throw new IllegalArgumentException("The list contained chunk messages of multiple chunkIds. ");
            }
            if (chunk.getMessageType().containsPayload()) {
                totalSize += chunk.getPayload().length;
            }
        }

        // create the combined payload byte array
        final ByteBuffer byteBuffer = ByteBuffer.allocate(totalSize).order(BlaubotConstants.BYTE_ORDER);
        for (BlaubotMessage chunk : chunks) {
            if (chunk.getMessageType().containsPayload()) {
                byteBuffer.put(chunk.getPayload());
            }
        }
        byteBuffer.flip();
        final byte[] payload = byteBuffer.array();

        // build the message
        BlaubotMessage out = new BlaubotMessage();
        out.setMessageType(BlaubotMessageType.copy(chunks.get(0).messageType));
        out.getMessageType().setIsChunk(false);
        out.setPriority(p);
        out.setChannelId(channelId);
        out.setLastOriginatorConnection(originator);
        out.setPayload(payload);
        return out;
    }

    /**
     * Priority for BlaubotMessages.
     */
    public enum Priority {
        ADMIN      ((byte) 1),
        ADMIN_LOW  ((byte) 2),
        HIGH       ((byte) 3),
        NORMAL     ((byte) 4),
        LOW        ((byte) 5);

        Priority(byte value) {
            this.value = value;
        }

        public final byte value;

        public static Priority fromByte(byte val) {
            for (Priority p : Priority.values()) {
                if (p.value == val) {
                    return p;
                }
            }
            throw new RuntimeException("Unknown priority: " + val);
        }

    }

    private byte protocolVersion;
    private BlaubotMessageType messageType;
    private Priority priority;
    private short channelId;
    private short chunkId;
    private short chunkNo;
    private byte[] payload;
    /**
     * An attribute that is not sent via the connection
     * Only used to send messages with the same priority in the order they were
     * queued.
     */
    protected int sequenceNumber;

    private IBlaubotConnection lastOriginatorConnection;

    /**
     * Constructs a default message, which sends data on a default channel
     */
    public BlaubotMessage() {
        this.messageType = new BlaubotMessageType();
        this.priority = Priority.NORMAL;
        this.channelId = BlaubotConstants.DEFAULT_CHANNEL_ID;
        this.payload = new byte[0];
    }

    /**
     * Gets the last originators connection. This is the connection over which this message was
     * received.
     * Note that this only represents the origin of the message's last hop and NOT the
     * connection from the original sender.
     * @return the originator connection or null, if not received from a connection
     */
    protected IBlaubotConnection getLastOriginatorConnection() {
        return lastOriginatorConnection;
    }

    protected void setLastOriginatorConnection(IBlaubotConnection originatorConnection) {
        this.lastOriginatorConnection = originatorConnection;
    }

    public byte getProtocolVersion() {
        return protocolVersion;
    }

    protected void setProtocolVersion(byte protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public BlaubotMessageType getMessageType() {
        return messageType;
    }

    protected void setMessageType(BlaubotMessageType messageType) {
        this.messageType = messageType;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    /**
     * Retrieves the channelId for which this message was designated.
     * There are some cases in which no channel is involved like:
     * - AdminMessages
     * - KeepAliveMessages
     * for which this method will return -1;
     *
     * @return channelId or -1, if no channel is involved
     */
    public short getChannelId() {
        return channelId;
    }

    protected void setChannelId(short channelId) {
        // some assertions to avoid mistakes
        if(messageType.isAdminMessage() && channelId >=0) {
            throw new IllegalArgumentException("You are trying something weird. Message is an admin message but you tried to set a channelId ("+channelId+"). AdminMessages don't involve any channels.");
        } else if(messageType.isKeepAliveMessage() && channelId >= 0) {
            throw new IllegalArgumentException("You are trying something weird. Message is a keep alive message but you tried to set a channelId  ("+channelId+"). Keep alives don't involve any channels.");
        }
        this.channelId = channelId;
    }

    /**
     * Retrieve this message's payload
     * @return payload as byte array (max 65535 bytes)
     */
    public byte[] getPayload() {
        return payload;
    }

    /**
     * Set the payload of this message.
     * @param payload the payload bytes
       @throws  java.lang.IllegalArgumentException if the payload length exeeds 65535 bytes
     */
    public void setPayload(byte[] payload) {
        // validate payload length to avoid buffer overflows
//        if(payload.length > BlaubotConstants.MAX_PAYLOAD_SIZE) { // max unsigned short value
//            throw new IllegalArgumentException("Payload is to large. Max size is " + BlaubotConstants.MAX_PAYLOAD_SIZE + ", but got " + payload.length + " bytes. Consider chunking your messages.");
//        } else
        // Messages are now chunked by MessageReceivers and MessageSenders, if too large!
        if(payload != null && payload.length > 0) {
            this.messageType.setContainsPayload(true);
        } else {
            this.messageType.setContainsPayload(false);
        }
        this.payload = payload;
    }

    /**
     * Applies all data from the message schema except the payload, which has to be set afterwards
     * by setPayload(), to this instance.
     * The payload's length is returned.
     *
     * @param headerBytes
     * @return the payloads length in bytes - 0 if no payload at all.
     */
    public int applyBytes(byte[] headerBytes) {
        // VERSION
        ByteBuffer byteBuffer = ByteBuffer.wrap(headerBytes).order(BlaubotConstants.BYTE_ORDER);
        byte version = byteBuffer.get();
        setProtocolVersion(version);

        // MessageType
        byte type = byteBuffer.get();
        BlaubotMessageType messageType = BlaubotMessageType.fromByte(type);
        setMessageType(messageType);

        // PRIORITY
        byte priority = byteBuffer.get();
        setPriority(Priority.fromByte(priority));

        // Now we check if we have to deal with channels
        if (messageType.isAdminMessage() || messageType.isKeepAliveMessage()) {
            // -- no channel needed
            setChannelId((byte) -1);
        } else {
            // CHANNEL ID
            short channelId = byteBuffer.getShort();
            setChannelId(channelId);
        }

        // Now check if this is a chunked message
        if (messageType.isChunk()) {
            // read chunk id
            short chunkId = byteBuffer.getShort();
            // read chunk no
            short chunkNo = byteBuffer.getShort();

            setChunkId(chunkId);
            setChunkNo(chunkNo);
        } else {
            // we ignore this fields
        }

        // Check if there is any payload
        if (messageType.containsPayload()) {
            // PAYLOAD_LENGTH
            short payloadLength = byteBuffer.getShort();
            int unsignedPayloadLength = payloadLength & 0xffff;
            return unsignedPayloadLength;
        } else {
            // no payload
            return 0;
        }
    }

    /**
     * Calculates a messages total header length by a given BlaubotMessageType
     * @param messageType the message type
     * @return the length of all header fields excluding the payload bytes.
     */
    protected static int calculateHeaderLength(BlaubotMessageType messageType) {
        boolean isChannelFieldRelevant = !messageType.isAdminMessage() && !messageType.isKeepAliveMessage();
        boolean containsPayload = messageType.containsPayload();
        boolean isChunkMessage = messageType.isChunk();

        // calculate the total header length needed
        int totalLength = FULL_HEADER_LENGTH;
        if (!containsPayload) {
            totalLength -= PAYLOAD_LENGTH_FIELD_LENGTH;
        }
        if (!isChannelFieldRelevant) {
            totalLength -= CHANNEL_FIELD_LENGTH;
        }
        if (!isChunkMessage) {
            totalLength -= CHUNK_ID_FIELD_LENGTH + CHUNK_NO_FIELD_LENGTH;
        }
        return totalLength;
    }

    /**
     * Serializes the message to a byte array.
     * The resulting bytes contain the header as well as the payload (if any).
     *
     * @return byte array containing the message's header as well as payload (if any)
     */
    public byte[] toBytes() {
        int headerLength = calculateHeaderLength(messageType);
        int totalLength = headerLength + (messageType.containsPayload() ? payload.length : 0);

        // allocate and encode attributes
        ByteBuffer bb = ByteBuffer.allocate(totalLength).order(BlaubotConstants.BYTE_ORDER);

        // encode version, type and priority
        bb.put(protocolVersion);
        bb.put(messageType.toByte());
        bb.put(priority.value);

        // encode channel, if relevant
        final boolean isChannelRelevant = !messageType.isAdminMessage() && !messageType.isKeepAliveMessage();
        if (isChannelRelevant) {
            bb.putShort(channelId);
        }

        // chunked message fields, if relevant
        final boolean isChunkedMessage = messageType.isChunk();
        if (isChunkedMessage) {
            bb.putShort(getChunkId());
            bb.putShort(getChunkNo());
        }

        // append payload, if relevant
        if (messageType.containsPayload()) {
            // note the cast to short which is effectively: (intValue) & 0xffff
            // so the result could be a negative short!
            bb.putShort((short) payload.length);
            bb.put(payload);
        }

        // retrieve byte array from buffer
        byte[] bytes = new byte[totalLength];
        bb.clear();
        bb.get(bytes);
        return bytes;
    }

    /**
     *
     * @param messageBytes byte array containing header and payload
     * @return
     */
    public static BlaubotMessage fromByteArray(byte[] messageBytes) {
        int headerLength = BlaubotMessage.FULL_HEADER_LENGTH;
        byte[] headerBuffer = new byte[headerLength];
        ByteBuffer headerByteBuffer = ByteBuffer.wrap(headerBuffer).order(BlaubotConstants.BYTE_ORDER);
        ByteBuffer messageByteBuffer = ByteBuffer.wrap(messageBytes).order(BlaubotConstants.BYTE_ORDER);

        // Partially read version, type and type, then decide how much bytes we need to read
        int partialHeaderLength = BlaubotMessage.VERSION_FIELD_LENGTH + BlaubotMessage.TYPE_FIELD_LENGTH;
        messageByteBuffer.get(headerBuffer, 0, partialHeaderLength);

        // assert a compatible message schema
        byte messageSchemaVersion = headerByteBuffer.get();
        if (messageSchemaVersion != BlaubotConstants.MESSAGE_SCHEMA_VERSION) {
            // TODO: maybe close connection, see TODO at methods begin
            throw new RuntimeException("Incompatible Blaubot message schema version: " + messageSchemaVersion);
        }

        // pre-create the MessageType to determine, how much header length is left to be read
        byte typeInfo = headerByteBuffer.get();
        BlaubotMessageType messageType = BlaubotMessageType.fromByte(typeInfo);

        // we are now able to calculate, how many bytes of this message's header we need to read.
        // we already read some header bytes, now get the rest needed for this message
        int totalHeaderLength = BlaubotMessage.calculateHeaderLength(messageType);
        int outstandingHeaderBytes = totalHeaderLength - partialHeaderLength;
        messageByteBuffer.get(headerBuffer, partialHeaderLength, outstandingHeaderBytes);

        // construct the message with all header informations
        BlaubotMessage message = new BlaubotMessage();
        int payloadLength = message.applyBytes(headerBuffer);

        // check if there is any payload to retrieve
        if (message.getMessageType().containsPayload()) {
            if (payloadLength > 0) {
                // create buffer - Note: intentionally no reuse of buffers - faster because of javas memory management
                byte[] payloadBuffer = new byte[payloadLength];
                messageByteBuffer.get(payloadBuffer, 0, payloadLength);
                byte[] orderedPayload = new byte[payloadLength]; // byte ordered
                ByteBuffer.wrap(payloadBuffer).order(BlaubotConstants.BYTE_ORDER).get(orderedPayload);
                message.setPayload(payloadBuffer);
            }
        }
        return message;
    }

    /**
     * If this message is a chunk message, returns the chunk number.
     * @return the chunk number
     */
    public short getChunkNo() {
        return chunkNo;
    }

    /**
     * Sets the chunk number
     * @param chunkNo has to be 1-based
     */
    public void setChunkNo(short chunkNo) {
        if (chunkNo == 0) {
            throw new IllegalArgumentException("chunkNo is 1-based");
        }
        this.chunkNo = chunkNo;
    }

    /**
     * If this message is a chunk message, gets the chunk id.
     * @return the chunk id
     */
    public short getChunkId() {
        return chunkId;
    }

    /**
     * sets the chunk id
     * @param chunkId the chunk id
     */
    public void setChunkId(short chunkId) {
        this.chunkId = chunkId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BlaubotMessage that = (BlaubotMessage) o;

        if (channelId != that.channelId) return false;
        if (protocolVersion != that.protocolVersion) return false;
        if (messageType != null ? !messageType.equals(that.messageType) : that.messageType != null)
            return false;
        if (!Arrays.equals(payload, that.payload)) return false;
        if (priority != that.priority) return false;

        return true;
    }


    @Override
    public int hashCode() {
        int result = (int) protocolVersion;
        result = 31 * result + (messageType != null ? messageType.hashCode() : 0);
        result = 31 * result + (priority != null ? priority.hashCode() : 0);
        result = 31 * result + (int) channelId;
        result = 31 * result + (payload != null ? Arrays.hashCode(payload) : 0);
        return result;
    }

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer("BlaubotMessage{");
        sb.append("protocolVersion=").append(protocolVersion);
        sb.append(", messageType=").append(messageType);
        sb.append(", priority=").append(priority);
        sb.append(", channelId=").append(channelId);
        sb.append(", chunkId=").append(chunkId);
        sb.append(", chunkNo=").append(chunkNo);
        sb.append(", payload=");
        if (payload == null) sb.append("null");
        else {
            sb.append(payload.length + " bytes");
        }
        sb.append(", sequenceNumber=").append(sequenceNumber);
        sb.append(", lastOriginatorConnection=").append(lastOriginatorConnection);
        sb.append('}');
        return sb.toString();
    }

    /**
     * Reads a message from a given connection (readFully)
     * @param connection the connection
     * @return the message
     * @throws java.io.IOException if something goes wrong
     */
    public static BlaubotMessage readFromBlaubotConnection(IBlaubotConnection connection) throws IOException {
        byte[] headerBuffer, payloadBuffer;
        headerBuffer = new byte[BlaubotMessage.FULL_HEADER_LENGTH];
        ByteBuffer headerByteBuffer = ByteBuffer.wrap(headerBuffer).order(BlaubotConstants.BYTE_ORDER);
        return readFromBlaubotConnection(connection, headerByteBuffer, headerBuffer);
    }

    /**
     * Reads a message from a given connection and reuses buffers
     * @param blaubotConnection the connection
     * @param headerByteBuffer the byte buffer around header buffer
     * @param headerBuffer the header buffer
     * @return the message
     * @throws IOException if something goes wrong
     */
    public static BlaubotMessage readFromBlaubotConnection(IBlaubotConnection blaubotConnection, ByteBuffer headerByteBuffer, byte[] headerBuffer) throws IOException {
        byte[] payloadBuffer;
        headerByteBuffer.clear();

        // Partially read version and type, then decide how much bytes we need to read
        int partialHeaderLength = BlaubotMessage.VERSION_FIELD_LENGTH + BlaubotMessage.TYPE_FIELD_LENGTH;
        blaubotConnection.readFully(headerBuffer, 0, partialHeaderLength);

        // assert a compatible message schema
        byte messageSchemaVersion = headerByteBuffer.get();
        if (messageSchemaVersion != BlaubotConstants.MESSAGE_SCHEMA_VERSION) {
            // TODO: maybe close connection
            throw new RuntimeException("Incompatible Blaubot message schema version: " + messageSchemaVersion);
        }

        // pre-create the MessageType to determine, how much header length is left to be read
        byte typeInfo = headerByteBuffer.get();
        BlaubotMessageType messageType = BlaubotMessageType.fromByte(typeInfo);

        // we are now able to calculate, how many bytes of this message's header we need to read.
        // we already read some header bytes, now get the rest needed for this message
        int totalHeaderLength = BlaubotMessage.calculateHeaderLength(messageType);
        int outstandingHeaderBytes = totalHeaderLength - partialHeaderLength;
        blaubotConnection.readFully(headerBuffer, partialHeaderLength, outstandingHeaderBytes);

        // construct the message with all header informations
        BlaubotMessage message = new BlaubotMessage();
        int payloadLength = message.applyBytes(headerBuffer);

        // check if there is any payload to retrieve
        if (message.getMessageType().containsPayload()) {
            if (payloadLength > 0) {
                // create buffer - Note: intentionally no reuse of buffers - faster because of javas memory management
                payloadBuffer = new byte[payloadLength];
                blaubotConnection.readFully(payloadBuffer, 0, payloadLength);
                byte[] orderedPayload = new byte[payloadLength]; // byte ordered
                ByteBuffer.wrap(payloadBuffer).order(BlaubotConstants.BYTE_ORDER).get(orderedPayload);
                message.setPayload(payloadBuffer);
            }
        }

        // set the originator connection
        message.setLastOriginatorConnection(blaubotConnection);

        return message;
    }

    public static void main(String[] args) {
        BlaubotMessage msg = new BlaubotMessage();
        msg.setPayload("blabla".getBytes());
        final byte[] bytes = msg.toBytes();
        final byte[] headerBytes = new byte[calculateHeaderLength(msg.getMessageType())];
        ByteBuffer bb = ByteBuffer.wrap(bytes).order(BlaubotConstants.BYTE_ORDER);
        System.out.println(bytes.length);
        bb.get(headerBytes);
        BlaubotMessage msg2 = new BlaubotMessage();
        int payloadLength = msg2.applyBytes(headerBytes);
        byte[] payloadBytes = new byte[payloadLength];
        bb.get(payloadBytes);
        msg2.setPayload(payloadBytes);
        System.out.println("msg, msg2: " + msg + ", " + msg2);
        System.out.println("msg == msg2: " + msg.equals(msg2));

        BlaubotMessage msg3 = fromByteArray(msg2.toBytes());
        System.out.println("msg2, msg3: " + msg2 + ", " + msg3);
        System.out.println("msg2 == msg3: " + msg2.equals(msg3));


        int x = 33000;
        short x1 = (short)x;
        System.out.println(x1);

        int i1 = (int) (x1 & 0xffff);
        System.out.println(i1);
        System.out.println("################################");

        BlaubotMessage bmsg = new BlaubotMessage();
        payloadBytes = new byte[(int) (BlaubotConstants.MAX_PAYLOAD_SIZE * 1.5f)];
        bmsg.setPayload(payloadBytes);


        List<BlaubotMessage> chunks = bmsg.createChunks((short)1);
        System.out.println(bmsg);
        System.out.println(chunks);
        System.out.println(fromChunks(chunks));

    }
}

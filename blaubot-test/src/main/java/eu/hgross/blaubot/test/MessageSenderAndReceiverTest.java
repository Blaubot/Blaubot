package eu.hgross.blaubot.test;

import net.jodah.concurrentunit.Waiter;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.IActionListener;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.AdminMessageFactory;
import eu.hgross.blaubot.admin.RelayAdminMessage;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.BlaubotMessageReceiver;
import eu.hgross.blaubot.messaging.BlaubotMessageSender;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;
import eu.hgross.blaubot.mock.BlaubotConnectionQueueMock;
import eu.hgross.blaubot.mock.BlaubotDeviceMock;

/**
 * Created by henna on 30.01.15.
 *
 * Tests the BlaubotMessageReceiver and Sender objects if they work together.
 */
public class MessageSenderAndReceiverTest {
    private Random random = new Random();
    private IBlaubotConnection connection1;
    private IBlaubotConnection connection2;
    private BlaubotMessageSender conn1_sender;
    private BlaubotMessageReceiver conn1_receiver;
    private BlaubotMessageSender conn2_sender;
    private BlaubotMessageReceiver conn2_receiver;

    @Before
    public void setUp() {
        IBlaubotDevice device = new BlaubotDeviceMock("Device1");
        IBlaubotDevice device2 = new BlaubotDeviceMock("Device2");
        connection1 = new BlaubotConnectionQueueMock(device);
        connection2 = ((BlaubotConnectionQueueMock) connection1).getOtherEndpointConnection(device2);

        conn1_sender = new BlaubotMessageSender(connection1);
        conn1_receiver = new BlaubotMessageReceiver(connection1);
        conn2_sender = new BlaubotMessageSender(connection2);
        conn2_receiver = new BlaubotMessageReceiver(connection2);

        conn1_sender.activate();
        conn2_sender.activate();
        conn1_receiver.activate();
        conn2_receiver.activate();
    }

    @After
    public void cleanUp() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(4);
        IActionListener deactivationListener = new IActionListener() {
            @Override
            public void onFinished() {
                latch.countDown();
            }
        };
        conn1_sender.deactivate(deactivationListener);
        conn2_sender.deactivate(deactivationListener);
        conn1_receiver.deactivate(deactivationListener);
        conn2_receiver.deactivate(deactivationListener);
        
        boolean timedOut = !latch.await(5000, TimeUnit.MILLISECONDS);
        Assert.assertTrue("MessageSender or Receiver deactivation timed out", !timedOut);
        
    }

    private byte[] createRandomPayload() {
        return createRandomPayload(20);
    }

    private byte[] createRandomPayload(int numBytes) {
        byte[] b = new byte[numBytes];
        random.nextBytes(b);
        return b;
    }

    /**
     * Tests deactivation of each receiver/listeners once and asserts the callback invocation
     * @param waiter the waiter to be resolved when finished
     */
    private void deactivateSendersAndReceivers(Waiter waiter) {
        final int LATCH_TIMEOUT = 5000; // ms
        final CountDownLatch receiverLatch = new CountDownLatch(2);
        IActionListener receiverActionListener = new IActionListener() {
            @Override
            public void onFinished() {
                receiverLatch.countDown();
            }
        };

        final CountDownLatch senderLatch = new CountDownLatch(2);
        IActionListener senderActionListener = new IActionListener() {
            @Override
            public void onFinished() {
                senderLatch.countDown();
            }
        };


        conn1_receiver.deactivate(receiverActionListener);
        conn2_receiver.deactivate(receiverActionListener);

        conn1_sender.deactivate(senderActionListener);
        conn2_sender.deactivate(senderActionListener);

        boolean senderLatchTimedOut = false;
        boolean receiverLatchTimedOut = false;
        try {
            senderLatchTimedOut = !senderLatch.await(LATCH_TIMEOUT, TimeUnit.MILLISECONDS);
            receiverLatchTimedOut = !receiverLatch.await(LATCH_TIMEOUT, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            receiverLatchTimedOut = true;
        }
        // assert that the receiver and sender latches were called twice for the initial deactivate call
        if (senderLatchTimedOut) {
            waiter.fail("One of the sender deactivate() listeners were not called in time");
        }
        if (receiverLatchTimedOut) {
            waiter.fail("One of the receiver deactivate() listeners were not called in time");
        }
        waiter.resume();
    }

    /**
     * Helper method to create some traffic originating from both sides.
     * It will send one message per side
     */
    private void sendOneMessagePerSide() {
        BlaubotMessage msg1 = new BlaubotMessage();
        BlaubotMessage msg2 = new BlaubotMessage();
        msg1.setPayload("TestMessageToConn1".getBytes(BlaubotConstants.STRING_CHARSET));
        msg2.setPayload("TestMessageToConn2".getBytes(BlaubotConstants.STRING_CHARSET));
        conn1_sender.sendMessage(msg1);
        conn2_sender.sendMessage(msg2);
    }

    /**
     * 
     * Helper method to send multiple messages per side to create some traffic.
     * @param messagesPerSide number of messages to be sent from each side
     */
    private void sendMultipleMessagesPerSide(int messagesPerSide) {
        for(int i=0; i<messagesPerSide; i++) {
            sendOneMessagePerSide();
        }
    }
    
    @Test(timeout=45000)
    /**
     * Tests if that for each deactivate-call to a message receiver/sender the finished callback is called 
     * after some time.
     */
    public void testDeactivationCallbackIsAlwaysCalled() throws InterruptedException, TimeoutException {
        Waiter waiter = new Waiter();
        
        // test the deactivation's happy path
        deactivateSendersAndReceivers(waiter);
        
        
        // now we test the same for consecutive deactivate calls (idempotence)
        for (int i=0; i < 30; i++) {
            Waiter w = new Waiter();
            deactivateSendersAndReceivers(w);
            w.await();
        }
        
        // now the same with activation beforehand
        for (int i=0; i < 30; i++) {
            conn1_receiver.activate();
            conn2_receiver.activate();
            conn1_sender.activate();
            conn2_sender.activate();
            Waiter w = new Waiter();
            deactivateSendersAndReceivers(w);
            w.await();
        }

        int cnt = 1;
        // now the same with multiple (idempotent) activations beforehand
        for (int i=0; i < cnt; i++) {
            conn1_receiver.activate();
            conn2_receiver.activate();
            conn1_sender.activate();
            conn2_sender.activate();
            conn1_receiver.activate();
            conn2_receiver.activate();
            conn1_sender.activate();
            conn2_sender.activate();
            Waiter w = new Waiter();
            deactivateSendersAndReceivers(w);
            w.await();
        }

        // now multiple deactivations
        for (int i=0; i < 30; i++) {
            conn1_receiver.activate();
            conn2_receiver.activate();
            conn1_sender.activate();
            conn2_sender.activate();
            Waiter w = new Waiter(), w2 = new Waiter(), w3 = new Waiter();
            deactivateSendersAndReceivers(w);
            deactivateSendersAndReceivers(w2);
            deactivateSendersAndReceivers(w3);
            w.await();
            w2.await();
            w3.await();
        }

        // now the same with multiple activations beforehand and everything in parallel
        final Waiter concurrentWaiter = new Waiter();
        for (int i=0; i < cnt; i++) {
            new Thread(new Runnable() {
                @Override
                public void run() {
                    conn1_receiver.activate();
                    conn2_receiver.activate();
                    conn1_sender.activate();
                    conn2_sender.activate();
                    conn1_receiver.activate();
                    conn2_receiver.activate();
                    conn1_sender.activate();
                    conn2_sender.activate();
                    Waiter w = new Waiter();
                    deactivateSendersAndReceivers(w);
                    try {
                        w.await();
                    } catch (TimeoutException e) {
                        concurrentWaiter.fail("Deactivation timed out");
                    }
                    concurrentWaiter.resume();
                }
            }).start();
        }
        concurrentWaiter.await(10000, TimeUnit.MILLISECONDS, cnt);
    }
    
    @Test(timeout=5000)
    public void testSendAndReceiveSequentially() throws InterruptedException {
        for(int i=0; i<100; i++) {
            // we create a random payload, send it over conn1, receive it on the other end, deserialize it, send it back over conn2 and then receive it on conn1
            final CountDownLatch latch = new CountDownLatch(2);
            final byte[] testPayload = createRandomPayload();
            BlaubotMessage testMsg = new BlaubotMessage();
            testMsg.setPayload(testPayload);


            IBlaubotMessageListener conn2_listener = new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage message) {
                    Assert.assertArrayEquals(testPayload, message.getPayload());
                    conn2_sender.sendMessage(message);
                    latch.countDown();
                }
            };

            IBlaubotMessageListener conn1_listener = new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage message) {
                    Assert.assertArrayEquals(testPayload, message.getPayload());
                    latch.countDown();
                }
            };

            conn1_receiver.addMessageListener(conn1_listener);
            conn2_receiver.addMessageListener(conn2_listener);

            // start the ping-pong
            conn1_sender.sendMessage(testMsg);

            // await the receives and asserts on each side
            latch.await();

            //remove the attached listeners
            conn1_receiver.removeMessageListener(conn1_listener);
            conn2_receiver.removeMessageListener(conn2_listener);
        }
    }

    @Test(timeout=45000)
    /**
     * Tests if the chunked message logic works for the bordercase, whereas the chunked size is a
     * multiple of the MAX payload size
     */
    public void testSendAndReceiveChunkedMessagesSequentiallyBorderCase() throws InterruptedException {
        int times = 3;

        // test it with a multiple
        for(int i=0; i<times; i++) {
            // we create a random payload that exceeds the MAX_PAYLOAD size, send it over conn1, receive it on the other end, deserialize it, send it back over conn2 and then receive it on conn1
            final CountDownLatch latch = new CountDownLatch(2);
            final byte[] testPayload = createRandomPayload(BlaubotConstants.MAX_PAYLOAD_SIZE * 5);
            BlaubotMessage testMsg = new BlaubotMessage();
            testMsg.setPayload(testPayload);


            IBlaubotMessageListener conn2_listener = new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage message) {
                    Assert.assertArrayEquals(testPayload, message.getPayload());
                    conn2_sender.sendMessage(message);
                    latch.countDown();
                }
            };

            IBlaubotMessageListener conn1_listener = new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage message) {
                    Assert.assertArrayEquals(testPayload, message.getPayload());
                    latch.countDown();
                }
            };

            conn1_receiver.addMessageListener(conn1_listener);
            conn2_receiver.addMessageListener(conn2_listener);

            // start the ping-pong
            conn1_sender.sendMessage(testMsg);

            // await the receives and asserts on each side
            latch.await();

            //remove the attached listeners
            conn1_receiver.removeMessageListener(conn1_listener);
            conn2_receiver.removeMessageListener(conn2_listener);
        }

        // test it with no multiple MAX_PAYLOAD_SIZE (should not be chunked)
        for(int i=0; i<times; i++) {
            // we create a random payload that exceeds the MAX_PAYLOAD size, send it over conn1, receive it on the other end, deserialize it, send it back over conn2 and then receive it on conn1
            final CountDownLatch latch = new CountDownLatch(2);
            final byte[] testPayload = createRandomPayload(BlaubotConstants.MAX_PAYLOAD_SIZE);
            BlaubotMessage testMsg = new BlaubotMessage();
            testMsg.setPayload(testPayload);


            IBlaubotMessageListener conn2_listener = new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage message) {
                    Assert.assertArrayEquals(testPayload, message.getPayload());
                    conn2_sender.sendMessage(message);
                    latch.countDown();
                }
            };

            IBlaubotMessageListener conn1_listener = new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage message) {
                    Assert.assertArrayEquals(testPayload, message.getPayload());
                    latch.countDown();
                }
            };

            conn1_receiver.addMessageListener(conn1_listener);
            conn2_receiver.addMessageListener(conn2_listener);

            // start the ping-pong
            conn1_sender.sendMessage(testMsg);

            // await the receives and asserts on each side
            latch.await();

            //remove the attached listeners
            conn1_receiver.removeMessageListener(conn1_listener);
            conn2_receiver.removeMessageListener(conn2_listener);
        }

        // test it with 5.5 multiple
        for(int i=0; i<times; i++) {
            // we create a random payload that exceeds the MAX_PAYLOAD size, send it over conn1, receive it on the other end, deserialize it, send it back over conn2 and then receive it on conn1
            final CountDownLatch latch = new CountDownLatch(2);
            final byte[] testPayload = createRandomPayload((int)((float)BlaubotConstants.MAX_PAYLOAD_SIZE * (float)5.5));
            BlaubotMessage testMsg = new BlaubotMessage();
            testMsg.setPayload(testPayload);


            IBlaubotMessageListener conn2_listener = new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage message) {
                    Assert.assertArrayEquals(testPayload, message.getPayload());
                    conn2_sender.sendMessage(message);
                    latch.countDown();
                }
            };

            IBlaubotMessageListener conn1_listener = new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage message) {
                    Assert.assertArrayEquals(testPayload, message.getPayload());
                    latch.countDown();
                }
            };

            conn1_receiver.addMessageListener(conn1_listener);
            conn2_receiver.addMessageListener(conn2_listener);

            // start the ping-pong
            conn1_sender.sendMessage(testMsg);

            // await the receives and asserts on each side
            latch.await();

            //remove the attached listeners
            conn1_receiver.removeMessageListener(conn1_listener);
            conn2_receiver.removeMessageListener(conn2_listener);
        }




    }

    /**
     * Tests the border cases of chunked relay admin messages
     */
    @Test(timeout=25000)
    public void testSendAndReceiveRelayAdminMessageBorderCases() throws InterruptedException {

        // Build payloads
        BlaubotMessage emptyMsg = new BlaubotMessage();
        emptyMsg.setPayload(new byte[0]);

        BlaubotMessage maxMessage = new BlaubotMessage();
        maxMessage.setPayload(createRandomPayload(BlaubotConstants.MAX_PAYLOAD_SIZE));

        BlaubotMessage mediumMessage = new BlaubotMessage();
        mediumMessage.setPayload(createRandomPayload(BlaubotConstants.MAX_PAYLOAD_SIZE/2));

        BlaubotMessage smallMessage1 = new BlaubotMessage();
        smallMessage1.setPayload(createRandomPayload(1));

        BlaubotMessage smallMessage2 = new BlaubotMessage();
        smallMessage2.setPayload(createRandomPayload(2));

        BlaubotMessage chunked1 = new BlaubotMessage();
        chunked1.setPayload(createRandomPayload(BlaubotConstants.MAX_PAYLOAD_SIZE+1));

        BlaubotMessage chunked2 = new BlaubotMessage();
        chunked2.setPayload(createRandomPayload(BlaubotConstants.MAX_PAYLOAD_SIZE * 2));

        BlaubotMessage chunked3 = new BlaubotMessage();
        chunked3.setPayload(createRandomPayload((int)(BlaubotConstants.MAX_PAYLOAD_SIZE * 1.5f)));

        BlaubotMessage chunked4 = new BlaubotMessage();
        chunked4.setPayload(createRandomPayload((int)(BlaubotConstants.MAX_PAYLOAD_SIZE * 2.5f)));

        BlaubotMessage chunked5 = new BlaubotMessage();
        chunked5.setPayload(createRandomPayload(BlaubotConstants.MAX_PAYLOAD_SIZE * 2 + 1));

        BlaubotMessage chunked6 = new BlaubotMessage();
        chunked6.setPayload(createRandomPayload(BlaubotConstants.MAX_PAYLOAD_SIZE * 2 - 1));

        BlaubotMessage chunked7 = new BlaubotMessage();
        chunked7.setPayload(createRandomPayload(BlaubotConstants.MAX_PAYLOAD_SIZE * 6 - 1));


        final List<BlaubotMessage> allMessages = Arrays.asList(emptyMsg, maxMessage, mediumMessage, smallMessage1, smallMessage2, chunked1, chunked2, chunked3, chunked4, chunked5, chunked6, chunked7);
        final List<BlaubotMessage> nonChunkedMessages = Arrays.asList(emptyMsg, maxMessage, mediumMessage, smallMessage1, smallMessage2);
        final List<RelayAdminMessage> relayedMessages = new ArrayList<>();
        for (BlaubotMessage m : nonChunkedMessages) {
            // put each message in a relay message
            RelayAdminMessage relayAdminMessage = new RelayAdminMessage(m.toBytes());
            relayedMessages.add(relayAdminMessage);
        }


        /*
        The actual tests
         */
        for(BlaubotMessage message : allMessages) {
            // we send it over conn1, receive it on the other end, deserialize it, send it back over conn2 and then receive it on conn1
            final CountDownLatch latch = new CountDownLatch(2);
            final byte[] testPayload = message.getPayload();


            IBlaubotMessageListener conn2_listener = new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage message) {
                    Assert.assertArrayEquals(testPayload, message.getPayload());
                    conn2_sender.sendMessage(message);
                    latch.countDown();
                }
            };

            IBlaubotMessageListener conn1_listener = new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage message) {
                    Assert.assertArrayEquals(testPayload, message.getPayload());
                    latch.countDown();
                }
            };

            conn1_receiver.addMessageListener(conn1_listener);
            conn2_receiver.addMessageListener(conn2_listener);

            // start the ping-pong
            conn1_sender.sendMessage(message);

            // await the receives and asserts on each side
            latch.await();

            //remove the attached listeners
            conn1_receiver.removeMessageListener(conn1_listener);
            conn2_receiver.removeMessageListener(conn2_listener);
        }

        // now we repeat that with the relay admin messages
        for(final RelayAdminMessage relayAdminMessage : relayedMessages) {
            // we send it over conn1, receive it on the other end, deserialize it, send it back over conn2 and then receive it on conn1
            final CountDownLatch latch = new CountDownLatch(2);
            BlaubotMessage message = relayAdminMessage.toBlaubotMessage(); // this is the message that will be sent. it contains an AdminMEssage which contains another blaubotmessage
            final byte[] testPayload = message.getPayload();
            final byte[] wrappedPayload = relayAdminMessage.getAsBlaubotMessage().getPayload();


            IBlaubotMessageListener conn2_listener = new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage message) {
                    // check the raw payload
                    Assert.assertArrayEquals(testPayload, message.getPayload());
                    // get the inherited blaubot message and check the payload
                    final AbstractAdminMessage adminMessage = AdminMessageFactory.createAdminMessageFromRawMessage(message);
                    Assert.assertTrue(adminMessage instanceof RelayAdminMessage);
                    final RelayAdminMessage receivedRelayAdminMessage = (RelayAdminMessage) adminMessage;
                    final BlaubotMessage wrappedBlaubotMessage = receivedRelayAdminMessage.getAsBlaubotMessage();
                    Assert.assertArrayEquals("The payload wrapped inside the RelayAdminMessage's BlabotMessage was not received correctly.", wrappedPayload, wrappedBlaubotMessage.getPayload());

                    conn2_sender.sendMessage(message);
                    latch.countDown();
                }
            };

            IBlaubotMessageListener conn1_listener = new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage message) {
                    Assert.assertArrayEquals(testPayload, message.getPayload());
                    // get the inherited blaubot message and check the payload
                    final AbstractAdminMessage adminMessage = AdminMessageFactory.createAdminMessageFromRawMessage(message);
                    Assert.assertTrue(adminMessage instanceof RelayAdminMessage);
                    final RelayAdminMessage receivedRelayAdminMessage = (RelayAdminMessage) adminMessage;
                    final BlaubotMessage wrappedBlaubotMessage = receivedRelayAdminMessage.getAsBlaubotMessage();
                    Assert.assertArrayEquals("The payload wrapped inside the RelayAdminMessage's BlabotMessage was not received correctly.", wrappedPayload, wrappedBlaubotMessage.getPayload());
                    latch.countDown();
                }
            };

            conn1_receiver.addMessageListener(conn1_listener);
            conn2_receiver.addMessageListener(conn2_listener);

            // start the ping-pong
            conn1_sender.sendMessage(message);

            // await the receives and asserts on each side
            latch.await();

            //remove the attached listeners
            conn1_receiver.removeMessageListener(conn1_listener);
            conn2_receiver.removeMessageListener(conn2_listener);
        }

    }


}

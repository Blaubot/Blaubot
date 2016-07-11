package eu.hgross.blaubot.test;

import net.jodah.concurrentunit.Waiter;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.StringAdminMessage;
import eu.hgross.blaubot.core.BlaubotConstants;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.IActionListener;
import eu.hgross.blaubot.messaging.BlaubotChannelManager;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.BlaubotMessageManager;
import eu.hgross.blaubot.messaging.IBlaubotAdminMessageListener;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;
import eu.hgross.blaubot.mock.BlaubotConnectionQueueMock;
import eu.hgross.blaubot.test.mockups.ChannelManagerDeviceMockup;

/**
 * A test for channel managers that can be used with any blaubot implementation by providing a
 * list of BlaubotChannelManager instances for each blaubot instance to the static methods.
 */
public class ChannelManagerTest {
    private static final int NUMBER_OF_CLIENTS = 5;
    /**
     * Subscriptions are async and therefore we need to give them some time to arrive before we post messages.
     * This is the time we wait for subscriptions to be done
     */
    private static final long SUBSCRIPTION_SLEEP_TIME = 1000;
    private static final int ORDER_TEST_NUMBER_OF_MESSAGES = 10;

    private ChannelManagerDeviceMockup master;
    private List<ChannelManagerDeviceMockup> clients;

    @Before
    public void setUp() {
        master = new ChannelManagerDeviceMockup("master");
        clients = new ArrayList<>();
        for(int i=0;i<NUMBER_OF_CLIENTS;i++) {
            clients.add(new ChannelManagerDeviceMockup("client" + (i+1)));
        }
    }

    @After
    public void cleanUp() throws InterruptedException {
        master.channelManager.deactivate();
        for(ChannelManagerDeviceMockup device : clients) {
            device.channelManager.deactivate();
        }
    }

    /**
     * Creates a list of connected ChannelManager instances, where index 0 is the master device.
     *
     * @return list of channel manages representing devices
     */
    protected List<BlaubotChannelManager> connectNetwork() {
        master.channelManager.setMaster(true);
        ArrayList<BlaubotChannelManager> devices = new ArrayList<>();
        devices.add(master.channelManager);
        for(ChannelManagerDeviceMockup client : clients) {
            client.connectToOtherDevice(master);
            devices.add(client.channelManager);
        }
        return devices;
    }

    @Test(timeout = 10000)
    public void testAdminMessageBroadcast() throws InterruptedException {
        // Wire the mockups together
        final List<BlaubotChannelManager> deviceMockups = connectNetwork();

        testAdminBMessageBroadcast(deviceMockups);
    }

    @Test(timeout = 30000)
    /**
     * Tests if the ChannelManager's start/stop methods are idempotent
     */
    public void testStartStopIdempotence() throws InterruptedException {
        BlaubotConnectionQueueMock mockConnnection = new BlaubotConnectionQueueMock(new BlaubotDevice("Device1"));
        BlaubotConnectionQueueMock mockConnectionRemoteEndpoint = mockConnnection.getOtherEndpointConnection(new BlaubotDevice("Dev2"));
        
        final BlaubotMessageManager mm1 = new BlaubotMessageManager(mockConnnection);
        final BlaubotMessageManager mm2 = new BlaubotMessageManager(mockConnectionRemoteEndpoint);

        // send queues
        final String mm1Msg = "mm1Msg", mm2Msg = "mm2Msg";
        final ArrayList<String> mm1ToMm2 = new ArrayList();
        final ArrayList<String> mm2ToMm1 = new ArrayList();
        
        // receive queues
        final BlockingQueue mm1Received = new LinkedBlockingQueue();
        final BlockingQueue mm2Received = new LinkedBlockingQueue();
        
        // fill send queues
        for(int i=0; i<500;i++) {
            mm1ToMm2.add(mm1Msg + i);
            mm2ToMm1.add(mm2Msg + i);
        }

        
        // add listeners to receivers
        final CountDownLatch receivedAllMessagesLatch = new CountDownLatch(mm1ToMm2.size() + mm2ToMm1.size());
        mm1.getMessageReceiver().addMessageListener(new IBlaubotMessageListener() {
            @Override
            public void onMessage(BlaubotMessage blaubotMessage) {
                try {
                    mm1Received.put(new String(blaubotMessage.getPayload(), BlaubotConstants.STRING_CHARSET));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        mm2.getMessageReceiver().addMessageListener(new IBlaubotMessageListener() {
            @Override
            public void onMessage(BlaubotMessage blaubotMessage) {
                try {
                    mm2Received.put(new String(blaubotMessage.getPayload(), BlaubotConstants.STRING_CHARSET));
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        
        // async start sending the messages from each side
        final CountDownLatch sendingFinishedLatch = new CountDownLatch(2);
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (String current : mm1ToMm2) {
                    BlaubotMessage msg = new BlaubotMessage();
                    msg.setPayload(current.getBytes(BlaubotConstants.STRING_CHARSET));
                    mm1.getMessageSender().sendMessage(msg);
                }
                sendingFinishedLatch.countDown();
            }
        }).start();
        new Thread(new Runnable() {
            @Override
            public void run() {
                for (String current : mm2ToMm1) {
                    BlaubotMessage msg = new BlaubotMessage();
                    msg.setPayload(current.getBytes(BlaubotConstants.STRING_CHARSET));
                    mm2.getMessageSender().sendMessage(msg);
                }
                sendingFinishedLatch.countDown();
            }
        }).start();
        
        // start/stop calls
        int iterations = 20;
        // setup callbacks for the deactivations
        final AtomicInteger finishedCountMm1 = new AtomicInteger(0);
        final CountDownLatch deactivationListenerLatch = new CountDownLatch(iterations * 2 * 11);
        final IActionListener actionListener = new IActionListener() {
            @Override
            public void onFinished() {
                finishedCountMm1.incrementAndGet();
                deactivationListenerLatch.countDown();
            }
        };
        final CountDownLatch threadCountDownLatch = new CountDownLatch(iterations * 2); 
        for(int i=0; i < 20; i++) {
            for (final BlaubotMessageManager mm : Arrays.asList(mm1, mm2)) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        mm.activate();
                        mm.deactivate(actionListener);
                        mm.deactivate(actionListener);
                        mm.deactivate(actionListener);
                        mm.activate();
                        mm.activate();
                        mm.activate();
                        mm.deactivate(actionListener);
                        mm.activate();
                        mm.activate();
                        mm.activate();
                        mm.activate();
                        mm.activate();
                        mm.activate();
                        mm.deactivate(actionListener);
                        mm.deactivate(actionListener);
                        mm.activate();
                        mm.activate();
                        mm.activate();
                        mm.deactivate(actionListener);
                        mm.deactivate(actionListener);
                        mm.deactivate(actionListener);
                        mm.deactivate(actionListener);
                        mm.activate();
                        mm.activate();
                        mm.deactivate(actionListener);
                        
                        threadCountDownLatch.countDown();
                    }
                }).start();
            }
        }
        
        // await finish of sending and activate/deactivate calls
        sendingFinishedLatch.await();
        
        threadCountDownLatch.await();
        boolean deactivationTimedOut = !deactivationListenerLatch.await(10000, TimeUnit.MILLISECONDS);
        Assert.assertFalse("Not all deactivation listeners were called", deactivationTimedOut);
        
        // We should have counted 2*11*iterations deactivation callbacks (11 times called deactivate)
        Assert.assertEquals("The deactivation listener were not called", 2 * 11 * iterations, finishedCountMm1.get());
    }
    
    /**
     * Tests the admin broadcast on the given channelManagers.
     *
     * @param deviceMockups the channel managers to test, note that they have to be already connected as one network
     *                      and that the first element in the list has to be the master/king
     * @throws InterruptedException
     */
    public static void testAdminBMessageBroadcast(List<BlaubotChannelManager> deviceMockups) throws InterruptedException {
        // We create some messages to be send by each of the mock instances as broadcast
        final ArrayList<BlaubotMessage> messagesToSend = new ArrayList<>();
        final String prefix = "_unit_test_count";
        for(int i=0; i<10; i++) {
            messagesToSend.add(new StringAdminMessage(prefix + i).toBlaubotMessage());
        }
        final int messageCount = messagesToSend.size() * deviceMockups.size(); // count of messages that should be received by each participant

        // register listeners
        ConcurrentHashMap<BlaubotChannelManager, CountDownLatch> latchesMapping = new ConcurrentHashMap<>();
        ConcurrentHashMap<BlaubotChannelManager, List<AbstractAdminMessage>> receivedMessagesMapping = new ConcurrentHashMap<>();
        for(BlaubotChannelManager deviceChannelManager : deviceMockups) {
            final ArrayList<AbstractAdminMessage> receivedMessages = new ArrayList<>();
            final CountDownLatch latch = new CountDownLatch(messageCount);
            latchesMapping.put(deviceChannelManager, latch);
            receivedMessagesMapping.put(deviceChannelManager, receivedMessages);
            deviceChannelManager.addAdminMessageListener(new IBlaubotAdminMessageListener() {
                @Override
                public void onAdminMessage(AbstractAdminMessage adminMessage) {
                    if(!(adminMessage instanceof StringAdminMessage)) {
                        return;
                    }
                    if (!((StringAdminMessage) adminMessage).getString().startsWith(prefix)) {
                        return;
                    }
                    receivedMessages.add(adminMessage);
                    latch.countDown();
                }
            });
        }

        // for each device, send all messages
        for(BlaubotChannelManager deviceChannelManager: deviceMockups) {
            for(BlaubotMessage blaubotMessage : messagesToSend) {
                deviceChannelManager.broadcastAdminMessage(blaubotMessage);
            }
        }

        // assert results
        for(BlaubotChannelManager deviceChannelManager : deviceMockups) {
            CountDownLatch latch = latchesMapping.get(deviceChannelManager);
            List<AbstractAdminMessage> receivedMessages = receivedMessagesMapping.get(deviceChannelManager);
            latch.await();
            Assert.assertEquals(messageCount, receivedMessages.size());
        }
    }

    @Test(timeout = 10000)
    /**
     *
     */
    public void testSubscriptions() throws InterruptedException {
        final List<BlaubotChannelManager> deviceMockups = connectNetwork();
        testSubscriptions(deviceMockups);
    }

    /**
     * Tests creation, subscribe and unsubscribe
     *
     * @param deviceMockups the channel managers to test, note that they have to be already connected as one network
     *                      and that the first element in the list has to be the master/king
     * @throws InterruptedException
     */
    public static void testSubscriptions(List<BlaubotChannelManager> deviceMockups) throws InterruptedException {
        final short commonChannelNumber = (short) (deviceMockups.size() + 1);

        // for each client subscribe to a own channel
        // master = 0, client1 = 1, client2 = 2, ...
        final Map<Short, IBlaubotChannel> deviceChannelMap = new ConcurrentHashMap<>();
        final List<CountDownLatch> deviceChannelLatches = Collections.synchronizedList(new ArrayList<CountDownLatch>());
        final List<CountDownLatch> commonChannelLatches = Collections.synchronizedList(new ArrayList<CountDownLatch>());
        final List<IBlaubotChannel> allChannels = Collections.synchronizedList(new ArrayList<IBlaubotChannel>());
        short i = 0;
        for(BlaubotChannelManager deviceChannelManager : deviceMockups) {
            // for each device subscribe to the devices channel and a common one
            final IBlaubotChannel deviceChannel = deviceChannelManager.createOrGetChannel(i);
            final IBlaubotChannel commonChannel = deviceChannelManager.createOrGetChannel(commonChannelNumber);
            allChannels.add(deviceChannel);
            allChannels.add(commonChannel);
            deviceChannel.subscribe();
            commonChannel.subscribe();

            // memorize the channel
            deviceChannelMap.put(i, deviceChannel);

            // attach listeners to the channels
            final CountDownLatch deviceChannelLatch = new CountDownLatch(deviceMockups.size());
            final short clientNumber = i;
            deviceChannel.addMessageListener(new IBlaubotMessageListener() {

                @Override
                public void onMessage(BlaubotMessage blaubotMessage) {
                    /*
                     * we will send strings in the format messageForClient1, messageForClient2, ...
                     * to each deviceChannel from each device, so we await sizeof(client) messages,
                     * for which we create a latch and count that down.
                     */
                    String msg = new String(blaubotMessage.getPayload(), BlaubotConstants.STRING_CHARSET);
                    Assert.assertEquals("A message was dispatched to the wrong or not subscribed channel (device channel " + clientNumber + ")", "messageForClient" + clientNumber, msg);
                    deviceChannelLatch.countDown();
                }
            });
            // add latch for this client
            deviceChannelLatches.add(deviceChannelLatch);


            final CountDownLatch commonChannelLatch = new CountDownLatch(deviceMockups.size());
            commonChannel.addMessageListener(new IBlaubotMessageListener() {
                @Override
                public void onMessage(BlaubotMessage blaubotMessage) {
                    /*
                     * we will send strings in the format commonMessage to this channel from each device,
                     * so we await sizeof(client) messages, for which we create a latch and count that down.
                     */
                    String msg = new String(blaubotMessage.getPayload(), BlaubotConstants.STRING_CHARSET);
                    Assert.assertEquals("A message was dispatched to the wrong or not subscribed channel (common channel)", "commonChannelMessage", msg);
                    commonChannelLatch.countDown();
                }
            });
            // add latch
            commonChannelLatches.add(commonChannelLatch);
            i++;
        }

        // wait some time to let the subscription messages arrive
        Thread.sleep(SUBSCRIPTION_SLEEP_TIME);

        // now send the messages from each device to all the available device channels as well as once to the common channel
        for(BlaubotChannelManager fromDevice : deviceMockups) {
            // send to common channel
            final IBlaubotChannel commonChannel = fromDevice.createOrGetChannel(commonChannelNumber);
            final String commonMsg = "commonChannelMessage";
            commonChannel.publish(commonMsg.getBytes(BlaubotConstants.STRING_CHARSET));

            // send to each device
            short receivingDeviceNumber = 0;
            for(BlaubotChannelManager toDevice : deviceMockups) {
                final IBlaubotChannel deviceChannel = fromDevice.createOrGetChannel(receivingDeviceNumber);
                final String deviceMsg = "messageForClient" + receivingDeviceNumber;
                deviceChannel.publish(deviceMsg.getBytes(BlaubotConstants.STRING_CHARSET));
                receivingDeviceNumber++;
            }
        }


        // await latches
        List<CountDownLatch> allLatches = new ArrayList<>();
        allLatches.addAll(commonChannelLatches);
        allLatches.addAll(deviceChannelLatches);

        for(CountDownLatch latch : allLatches) {
            latch.await();
        }

        // if here, we sucessfully received everything as expected after we subscribed.
        // now unsubscribe all channels and check if nothing comes through by sending unexpected messages to device and common channel

        // unsubscribe
        for(IBlaubotChannel channel : allChannels) {
            channel.unsubscribe();
        }

        // wait some time to let the unsubscribe messages arrive
        Thread.sleep(SUBSCRIPTION_SLEEP_TIME);

        final String wrongMsg = "wrongMessageThatShouldNeverReachAListener";
        for(BlaubotChannelManager fromDevice : deviceMockups) {
            // send to common channel
            final IBlaubotChannel commonChannel = fromDevice.createOrGetChannel(commonChannelNumber);
            commonChannel.publish(wrongMsg.getBytes(BlaubotConstants.STRING_CHARSET));

            // send to each device
            short receivingDeviceNumber = 0;
            for(BlaubotChannelManager toDevice : deviceMockups) {
                final IBlaubotChannel deviceChannel = fromDevice.createOrGetChannel(receivingDeviceNumber);
                deviceChannel.publish(wrongMsg.getBytes(BlaubotConstants.STRING_CHARSET));
                receivingDeviceNumber++;
            }
        }

        // if something goes wrong, the listeners asserts will fail ...
        // finished
    }

    @Test(timeout = 10000)
    public void testMessageOrder() throws InterruptedException {
        final List<BlaubotChannelManager> deviceMockups = connectNetwork();
        testMessageOrder(deviceMockups);
    }

    @Test(timeout = 10000)
    public void testExcludeSender() throws InterruptedException, TimeoutException {
        final List<BlaubotChannelManager> deviceMockups = connectNetwork();
        testExcludeSender(deviceMockups);
    }

    /**
     * Tests the message send/and receive order
     *
     * @param channelManagers the channel managers to test, note that they have to be already connected as one network
     *                        and that the first element in the list has to be the master/king
     * @throws InterruptedException
     */
    public static void testMessageOrder(List<BlaubotChannelManager> channelManagers) throws InterruptedException {
        // now we send messages with increasing numbers to check if the order is right
        // every device sends some messages with the format <fromDeviceNumber>;<toDeviceNumber>;<sequenceNumber>
        // to each other device channel
        // the listeners on this device channels asserts that sequenceNumber for fromDeviceNumber is greater than the received before

        // for each client subscribe to a own channel
        final Map<Short, IBlaubotChannel> deviceChannelMap = new ConcurrentHashMap<>();
        final List<CountDownLatch> deviceChannelLatches = Collections.synchronizedList(new ArrayList<CountDownLatch>());
        short deviceId = 0;
        for(BlaubotChannelManager device : channelManagers) {
            // for each device subscribe to the devices channel and a common one
            final IBlaubotChannel deviceChannel = device.createOrGetChannel(deviceId);
            deviceChannel.getChannelConfig().setTransmitReflexiveMessages(true);
            deviceChannel.subscribe();

            // memorize the channel
            deviceChannelMap.put(deviceId, deviceChannel);

            // attach listeners to the channels
            final CountDownLatch deviceChannelLatch = new CountDownLatch(channelManagers.size() * ORDER_TEST_NUMBER_OF_MESSAGES);
            deviceChannel.addMessageListener(new IBlaubotMessageListener() {
                private ConcurrentHashMap<Short, Integer> sequenceCounter = new ConcurrentHashMap<>();

                @Override
                public void onMessage(BlaubotMessage blaubotMessage) {
                    String msg = new String(blaubotMessage.getPayload(), BlaubotConstants.STRING_CHARSET);
                    short senderDeviceNumber = Short.valueOf(msg.split(";")[0]);
                    short receivingDeviceNumber = Short.valueOf(msg.split(";")[1]);
                    int sequenceNumber = Integer.valueOf(msg.split(";")[2]);
                    Integer oldSequenceNumber = sequenceCounter.put(senderDeviceNumber, sequenceNumber);
                    if(oldSequenceNumber != null) {
                        Assert.assertTrue("former sequence number should be smaller than the new sequence number but wasn't: old: " + oldSequenceNumber + " new: " + sequenceNumber + "; message: " + msg, oldSequenceNumber < sequenceNumber);
                    }
                    deviceChannelLatch.countDown();
                }
            });
            // add latch for this client
            deviceChannelLatches.add(deviceChannelLatch);
            deviceId++;
        }

        // wait some time to let the subscription messages arrive
        Thread.sleep(SUBSCRIPTION_SLEEP_TIME);

        // now send the messages from each device to all the available device channels
        short sendingDeviceNumber = 0;
        for(BlaubotChannelManager fromDevice : channelManagers) {
            // send to each device
            short receivingDeviceNumber = 0;
            for(BlaubotChannelManager toDevice : channelManagers) {
                final IBlaubotChannel deviceChannel = fromDevice.createOrGetChannel(receivingDeviceNumber);
                for(int seqNumber=0; seqNumber < ORDER_TEST_NUMBER_OF_MESSAGES; seqNumber += 1) {
                    final String deviceMsg = sendingDeviceNumber + ";" + receivingDeviceNumber + ";" + seqNumber;
                    final boolean published = deviceChannel.publish(deviceMsg.getBytes(BlaubotConstants.STRING_CHARSET), false);
                    Assert.assertTrue(published);
                }
                receivingDeviceNumber += 1;
            }
            sendingDeviceNumber += 1;
        }


        // await latches
        for(CountDownLatch latch : deviceChannelLatches) {
            latch.await();
        }
    }

    /**
     * Tests if the excludeSender option of a channel's publish method is respected for some specific
     * channel settings
     * @param channelManagers the channel managers to test, note that they have to be already connected as one network
     *                        and that the first element in the list has to be the master/king
     */
    public static void testExcludeSender(List<BlaubotChannelManager> channelManagers) throws InterruptedException, TimeoutException {
        /*
        We will build a network of two, subscribe to one channel number on both nodes and check that
        they never receive their own message when the excludeSender option is used with the transmitReflexiveMessagesOption=false
         */

        BlaubotChannelManager king = channelManagers.get(0);
        BlaubotChannelManager anyClient = channelManagers.get(1);

        // define messages to be sent by king and client
        final String kingMessage = "SentByKing";
        final String clientMessage = "SentByClient";

        // we await two messages, so we create a latch for that
        final Waiter noReflexiveWaiter = new Waiter();

        IBlaubotChannel kingChannel = king.createOrGetChannel((short) 1);
        kingChannel.getChannelConfig().setTransmitReflexiveMessages(false);
        IBlaubotMessageListener kingMessageListener = new IBlaubotMessageListener() {
            @Override
            public void onMessage(BlaubotMessage blaubotMessage) {
                // assert that the king never receives the message he sent
                String msgReceived = new String(blaubotMessage.getPayload(), BlaubotConstants.STRING_CHARSET);
                System.out.println("kng" + msgReceived);
                noReflexiveWaiter.assertTrue(!msgReceived.equals(kingMessage));

                // but make sure we receive the client's message
                noReflexiveWaiter.assertEquals(msgReceived, clientMessage);

                noReflexiveWaiter.resume();
            }
        };
        kingChannel.subscribe(kingMessageListener);

        IBlaubotChannel clientChannel = anyClient.createOrGetChannel((short) 1);
        clientChannel.getChannelConfig().setTransmitReflexiveMessages(false);
        IBlaubotMessageListener clientMessageListener = new IBlaubotMessageListener() {
            @Override
            public void onMessage(BlaubotMessage blaubotMessage) {
                // assert that the client never receives the message he sent
                String msgReceived = new String(blaubotMessage.getPayload(), BlaubotConstants.STRING_CHARSET);
                System.out.println("clnt" + msgReceived);
                noReflexiveWaiter.assertTrue(!msgReceived.equals(clientMessage));

                // but make sure we receive the kings message
                noReflexiveWaiter.assertEquals(msgReceived, kingMessage);

                noReflexiveWaiter.resume();
            }
        };
        clientChannel.subscribe(clientMessageListener);

        Thread.sleep(1000); // wait some time for subscriptions to be propagated

        // send with exclude
        clientChannel.publish(clientMessage.getBytes(), true);
        kingChannel.publish(kingMessage.getBytes(), true);

        // await the latch (we await 2 asserted messages)
        noReflexiveWaiter.await(10000, 2);


        // now again, we test it with setTransmitReflexiveMessages set to true
        kingChannel.getChannelConfig().setTransmitReflexiveMessages(true);
        clientChannel.getChannelConfig().setTransmitReflexiveMessages(true);

        // we create new listeners
        kingChannel.removeMessageListener(kingMessageListener);
        clientChannel.removeMessageListener(clientMessageListener);

        final Waiter reflexiveWaiter = new Waiter();
        kingMessageListener = new IBlaubotMessageListener() {
            @Override
            public void onMessage(BlaubotMessage blaubotMessage) {
                // assert that the king never receives the message he sent
                String msgReceived = new String(blaubotMessage.getPayload(), BlaubotConstants.STRING_CHARSET);
                System.out.println("kng" + msgReceived);
                reflexiveWaiter.assertTrue(!msgReceived.equals(kingMessage));

                // but make sure we receive the client's message
                reflexiveWaiter.assertEquals(msgReceived, clientMessage);

                reflexiveWaiter.resume();
            }
        };
        clientMessageListener = new IBlaubotMessageListener() {
            @Override
            public void onMessage(BlaubotMessage blaubotMessage) {
                // assert that the client never receives the message he sent
                String msgReceived = new String(blaubotMessage.getPayload(), BlaubotConstants.STRING_CHARSET);
                System.out.println("clnt" + msgReceived);
                reflexiveWaiter.assertTrue(!msgReceived.equals(clientMessage));

                // but make sure we receive the kings message
                reflexiveWaiter.assertEquals(msgReceived, kingMessage);

                reflexiveWaiter.resume();
            }
        };

        kingChannel.subscribe(kingMessageListener);
        clientChannel.subscribe(clientMessageListener);

        Thread.sleep(300); // wait some time for subscriptions to be propagated/listeners be wired

        // we don't need to wait for subscriptions to come through here
        // send with exclude
        clientChannel.publish(clientMessage.getBytes(), true);
        kingChannel.publish(kingMessage.getBytes(), true);

        // await the latch (we await 2 asserted messages)
        reflexiveWaiter.await(10000, 2);
    }
}


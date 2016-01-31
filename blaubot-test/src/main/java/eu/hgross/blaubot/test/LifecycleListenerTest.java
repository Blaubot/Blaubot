package eu.hgross.blaubot.test;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.LifecycleListenerAdapter;
import eu.hgross.blaubot.util.Log;

/**
 * Tests the lifecycle listener
 */
public class LifecycleListenerTest {
    private static final int CONNECTIVITY_TEST_TIMEOUT = 150000;
    private static final int MAX_STOPPING_TIMEOUT_FOR_ALL_INSTANCES = 30000;
    private static final long WAIT_TIME_BETWEEN_TESTS = 1000; // sleep time between tests to let the os close the sockets
    private static final int NUMBER_OF_BLAUBOT_INSTANCES = 3; // min 3!
    private static final int STARTING_PORT_FOR_BLAUBOT_INSTANCES = 17171;

    private UUID blaubotTestUUID = UUID.fromString("fc7eaac6-ac62-11e4-89d3-123b93f75cba");
    private List<Blaubot> instances;

    @BeforeClass
    public static void setUpClass() {
        Log.LOG_LEVEL = Log.LogLevel.ERRORS;
    }

    @Before
    public void setUp() throws UnknownHostException {
//		HashSet<String> uniqueDeviceIdStrings = BlaubotJunitHelper.createEthernetUniqueDeviceIdStringsFromFirstLocalIpAddress(NUMBER_OF_BLAUBOT_INSTANCES, STARTING_PORT_FOR_BLAUBOT_INSTANCES);
        HashSet<String> uniqueDeviceIdStrings = BlaubotJunitHelper.createEthernetUniqueDeviceIdStringsFromLoopbackInterface(NUMBER_OF_BLAUBOT_INSTANCES, STARTING_PORT_FOR_BLAUBOT_INSTANCES);
        this.instances = BlaubotJunitHelper.setUpEthernetBlaubotInstancesFromUniqueIdSet(uniqueDeviceIdStrings, blaubotTestUUID, BlaubotJunitHelper.EthernetBeaconType.FIXED_DEVICE_SET);
    }

    @After
    public void cleanUp() throws UnknownHostException, InterruptedException {
        BlaubotJunitHelper.stopBlaubotInstances(instances, MAX_STOPPING_TIMEOUT_FOR_ALL_INSTANCES);
        this.instances.clear();
        Thread.sleep(WAIT_TIME_BETWEEN_TESTS);
    }

    @Test(timeout=CONNECTIVITY_TEST_TIMEOUT * 4)
    /**
     * This is what we do:
     * Create 3 blaubot instances and check this states:
     * - State1: start instance1 and instance2; assert that we get 2 onConnected and a onDeviceJoined for both
     * - State2: then start a third and assert that we get 1 onConnected, 2 ondDeviceJoined with the third's uniqueDeviceId and one onDeviceJoined for instance1, and instance2
     * - State3: stop instance3 and assert that we get 2 onDeviceLeft() and one onDisconnected()
     * - State4: stop instance2 and assert that we get 2 onDeviceLeft and two onDisconnected (kingdom is no more after timeout)
     */
    public void testLifeCycle() throws InterruptedException {
        // assert that we have 3 instances
        Assert.assertEquals(3, instances.size());

        final Blaubot device1 = instances.get(0);
        final Blaubot device2 = instances.get(1);
        final Blaubot device3 = instances.get(2);

        class EventCounterLifecycleListener extends  LifecycleListenerAdapter {
            AtomicInteger onDeviceJoinedForDevice1Count = new AtomicInteger(0);
            AtomicInteger onDeviceJoinedForDevice2Count = new AtomicInteger(0);
            AtomicInteger onDeviceJoinedForDevice3Count = new AtomicInteger(0);
            AtomicInteger onDeviceLeftForDevice1Count = new AtomicInteger(0);
            AtomicInteger onDeviceLeftForDevice2Count = new AtomicInteger(0);
            AtomicInteger onDeviceLeftForDevice3Count = new AtomicInteger(0);
            AtomicInteger onConnectedCount = new AtomicInteger(0);
            AtomicInteger onDisconnectedCount = new AtomicInteger(0);


            @Override
            public void onConnected() {
                onConnectedCount.incrementAndGet();
            }

            @Override
            public void onDisconnected() {
                onDisconnectedCount.incrementAndGet();
            }

            @Override
            public void onDeviceJoined(IBlaubotDevice blaubotDevice) {
                if (blaubotDevice.getUniqueDeviceID().equals(device1.getOwnDevice().getUniqueDeviceID())) {
                    onDeviceJoinedForDevice1Count.incrementAndGet();
                } else if (blaubotDevice.getUniqueDeviceID().equals(device2.getOwnDevice().getUniqueDeviceID())) {
                    onDeviceJoinedForDevice2Count.incrementAndGet();
                } else if (blaubotDevice.getUniqueDeviceID().equals(device3.getOwnDevice().getUniqueDeviceID())) {
                    onDeviceJoinedForDevice3Count.incrementAndGet();
                }
            }

            @Override
            public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
                if (blaubotDevice.getUniqueDeviceID().equals(device1.getOwnDevice().getUniqueDeviceID())) {
                    onDeviceLeftForDevice1Count.incrementAndGet();
                } else if (blaubotDevice.getUniqueDeviceID().equals(device2.getOwnDevice().getUniqueDeviceID())) {
                    onDeviceLeftForDevice2Count.incrementAndGet();
                } else if (blaubotDevice.getUniqueDeviceID().equals(device3.getOwnDevice().getUniqueDeviceID())) {
                    onDeviceLeftForDevice3Count.incrementAndGet();
                }
            }

            @Override
            public String toString() {
                final StringBuffer sb = new StringBuffer("EventCounterLifecycleListener{");
                sb.append("onDeviceJoinedForDevice1Count=").append(onDeviceJoinedForDevice1Count);
                sb.append(", onDeviceJoinedForDevice2Count=").append(onDeviceJoinedForDevice2Count);
                sb.append(", onDeviceJoinedForDevice3Count=").append(onDeviceJoinedForDevice3Count);
                sb.append(", onDeviceLeftForDevice1Count=").append(onDeviceLeftForDevice1Count);
                sb.append(", onDeviceLeftForDevice2Count=").append(onDeviceLeftForDevice2Count);
                sb.append(", onDeviceLeftForDevice3Count=").append(onDeviceLeftForDevice3Count);
                sb.append(", onConnectedCount=").append(onConnectedCount);
                sb.append(", onDisconnectedCount=").append(onDisconnectedCount);
                sb.append('}');
                return sb.toString();
            }
        }

        EventCounterLifecycleListener state1 = new EventCounterLifecycleListener();
        EventCounterLifecycleListener state2 = new EventCounterLifecycleListener();
        EventCounterLifecycleListener state3 = new EventCounterLifecycleListener();
        EventCounterLifecycleListener state4 = new EventCounterLifecycleListener();

        // attach listener for state1
        for (Blaubot b : instances) {
            b.addLifecycleListener(state1);
        }

        // start and await that we have a kingdom of two
        Assert.assertTrue("The blaubot instances could not form a kingdom fast enough. States: " + BlaubotJunitHelper.createBlaubotCensusString(device1, device2), BlaubotJunitHelper.blockUntilWeHaveOneKingdom(CONNECTIVITY_TEST_TIMEOUT, device1, device2));

        // give it some time
        Thread.sleep(2000);

        // check condition
        boolean state1Condition =
                        state1.onDeviceJoinedForDevice1Count.get() == 1 &&
                        state1.onDeviceJoinedForDevice2Count.get() == 1 &&
                        state1.onDeviceJoinedForDevice3Count.get() == 0 &&
                        state1.onDeviceLeftForDevice1Count.get() == 0 &&
                        state1.onDeviceLeftForDevice2Count.get() == 0 &&
                        state1.onDeviceLeftForDevice3Count.get() == 0 &&
                        state1.onConnectedCount.get() == 2 &&
                        state1.onDisconnectedCount.get() == 0;
        Assert.assertTrue("State 1 condition not met: " + state1, state1Condition);



        // attach state 2 listener and start instance 3
        for (Blaubot b : instances) {
            b.addLifecycleListener(state2);
        }
        // start instance 3 and await join to kingdom
        Assert.assertTrue("The blaubot instances could not form a kingdom fast enough. States: " + BlaubotJunitHelper.createBlaubotCensusString(device1, device2, device3), BlaubotJunitHelper.blockUntilWeHaveOneKingdom(CONNECTIVITY_TEST_TIMEOUT, device1, device2, device3));
        // give it some time
        Thread.sleep(2000);
        // check condition
        boolean state2Condition =
                        state2.onDeviceJoinedForDevice1Count.get() == 1 &&
                        state2.onDeviceJoinedForDevice2Count.get() == 1 &&
                        state2.onDeviceJoinedForDevice3Count.get() == 2 &&
                        state2.onDeviceLeftForDevice1Count.get() == 0 &&
                        state2.onDeviceLeftForDevice2Count.get() == 0 &&
                        state2.onDeviceLeftForDevice3Count.get() == 0 &&
                        state2.onConnectedCount.get() == 1 &&
                        state2.onDisconnectedCount.get() == 0;
        Assert.assertTrue("State 2 condition not met: " + state2, state2Condition);

        // attach state 3 listener and stop instance 3
        for (Blaubot b : instances) {
            b.addLifecycleListener(state3);
        }
        device3.stopBlaubot();

        // give it some time
        Thread.sleep(2000);

        // check condition
        boolean state3Condition =
                state3.onDeviceJoinedForDevice1Count.get() == 0 &&
                        state3.onDeviceJoinedForDevice2Count.get() == 0 &&
                        state3.onDeviceJoinedForDevice3Count.get() == 0 &&
                        state3.onDeviceLeftForDevice1Count.get() == 1 &&
                        state3.onDeviceLeftForDevice2Count.get() == 1 &&
                        state3.onDeviceLeftForDevice3Count.get() == 2 &&
                        state3.onConnectedCount.get() == 0 &&
                        state3.onDisconnectedCount.get() == 1;
        Assert.assertTrue("State 3 condition not met: " + state3, state3Condition);


//      State4: stop instance2 and assert that we get 2 onDeviceLeft and one 2 onDisconnected
        for (Blaubot b : instances) {
            b.addLifecycleListener(state4);
        }
        device2.stopBlaubot();
        // give it some time
        Thread.sleep((device1.getAdapters().get(0).getConnectionStateMachineConfig().getKingWithoutPeasantsTimeout() * 2));
        // check condition
        boolean state4Condition =
                state4.onDeviceJoinedForDevice1Count.get() == 0 &&
                        state4.onDeviceJoinedForDevice2Count.get() == 0 &&
                        state4.onDeviceJoinedForDevice3Count.get() == 0 &&
                        state4.onDeviceLeftForDevice1Count.get() == 1 && // device 2 should receive this event
                        state4.onDeviceLeftForDevice2Count.get() == 1 && // device 1 should receive this event
                        state4.onDeviceLeftForDevice3Count.get() == 0 &&
                        state4.onConnectedCount.get() == 0 &&
                        state4.onDisconnectedCount.get() == 2; // device 2 should receive this event
        Assert.assertTrue("State 4 condition not met: " + state4, state4Condition);
    }
}

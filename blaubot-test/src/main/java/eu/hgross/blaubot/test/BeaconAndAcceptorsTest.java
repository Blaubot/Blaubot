package eu.hgross.blaubot.test;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.BlaubotFactory;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.ILifecycleListener;
import eu.hgross.blaubot.core.statemachine.BlaubotAdapterHelper;
import eu.hgross.blaubot.core.statemachine.ConnectionStateMachineAdapter;

/**
 * Created by henna on 03.02.15.
 */
public class BeaconAndAcceptorsTest {
    private static final int START_STOP_CYCLES = 100;
    private Blaubot blaubot;

    @Before
    public void setUp() throws UnknownHostException {
        IBlaubotDevice ownDevice = new BlaubotDevice("TestDevice");
        blaubot = BlaubotFactory.createEthernetBlaubotWithFixedDevicesBeacon(UUID.randomUUID(), ownDevice, 9000, 9001, BlaubotFactory.getLocalIpAddress(), new HashSet<String>());
    }

    @After
    public void tearDown() throws IOException, InterruptedException {
        final CountDownLatch latch = new CountDownLatch(1);
        blaubot.getConnectionStateMachine().addConnectionStateMachineListener(new ConnectionStateMachineAdapter() {
            @Override
            public void onStateMachineStopped() {
                latch.countDown();
            }
        });
        blaubot.stopBlaubot();
        boolean timedOut = !latch.await(5000, TimeUnit.MILLISECONDS);
        blaubot.close();
        blaubot = null;
    }


    @Test
    /**
     * Tests fast and many start/stop cycles of  acceptors
     */
    public void testEthernetAcceptorStartStop() throws UnknownHostException {

        for(int i=0; i<START_STOP_CYCLES; i++) {
            BlaubotAdapterHelper.startAcceptors(BlaubotAdapterHelper.getConnectionAcceptors(blaubot.getAdapters()));
            BlaubotAdapterHelper.stopAcceptors(BlaubotAdapterHelper.getConnectionAcceptors(blaubot.getAdapters()));
        }
    }

    @Test
    /**
     * Tests fast and many start,start/stop,stop calls
     */
    public void testEthernetAcceptorStartStopIdempotence() throws UnknownHostException {
        for(int i=0; i<START_STOP_CYCLES; i++) {
            // the acceptors throw their own exceptions if something goes wrong
            BlaubotAdapterHelper.startAcceptors(BlaubotAdapterHelper.getConnectionAcceptors(blaubot.getAdapters()));
            BlaubotAdapterHelper.stopAcceptors(BlaubotAdapterHelper.getConnectionAcceptors(blaubot.getAdapters()));
            BlaubotAdapterHelper.startAcceptors(BlaubotAdapterHelper.getConnectionAcceptors(blaubot.getAdapters()));
            BlaubotAdapterHelper.startAcceptors(BlaubotAdapterHelper.getConnectionAcceptors(blaubot.getAdapters()));
            BlaubotAdapterHelper.stopAcceptors(BlaubotAdapterHelper.getConnectionAcceptors(blaubot.getAdapters()));
            BlaubotAdapterHelper.stopAcceptors(BlaubotAdapterHelper.getConnectionAcceptors(blaubot.getAdapters()));
            BlaubotAdapterHelper.stopAcceptors(BlaubotAdapterHelper.getConnectionAcceptors(blaubot.getAdapters()));
        }
    }

    @Test(timeout = 300000)
    /**
     * Tests fast and many start/stop cycles of ethernet beacons
     */
    public void testEthernetFixedDeviceSetBeaconStartStop() {
        for(int i=0; i<START_STOP_CYCLES; i++) {
            BlaubotAdapterHelper.startBeacons(blaubot.getConnectionStateMachine().getBeaconService());
            BlaubotAdapterHelper.stopBeacons(blaubot.getConnectionStateMachine().getBeaconService());
        }
    }

    @Test(timeout = 30000)
    /**
     * Tests fast and many start,start/stop,stop calls
     */
    public void testEthernetBeaconStartStopIdempotence() {
        for(int i=0; i<START_STOP_CYCLES; i++) {
            // the acceptors throw their own exceptions if something goes wrong
            BlaubotAdapterHelper.startBeacons(blaubot.getConnectionStateMachine().getBeaconService());
            BlaubotAdapterHelper.stopBeacons(blaubot.getConnectionStateMachine().getBeaconService());
            BlaubotAdapterHelper.startBeacons(blaubot.getConnectionStateMachine().getBeaconService());
            BlaubotAdapterHelper.startBeacons(blaubot.getConnectionStateMachine().getBeaconService());
            BlaubotAdapterHelper.stopBeacons(blaubot.getConnectionStateMachine().getBeaconService());
            BlaubotAdapterHelper.stopBeacons(blaubot.getConnectionStateMachine().getBeaconService());
            BlaubotAdapterHelper.stopBeacons(blaubot.getConnectionStateMachine().getBeaconService());
        }
    }
}

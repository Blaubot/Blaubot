package eu.hgross.blaubot.test;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotAdapterConfig;
import eu.hgross.blaubot.core.ConnectionStateMachineConfig;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import eu.hgross.blaubot.core.statemachine.states.FreeState;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.core.statemachine.states.StoppedState;
import eu.hgross.blaubot.ethernet.BlaubotEthernetFixedDeviceSetBeacon;
import eu.hgross.blaubot.messaging.BlaubotChannelManager;
import eu.hgross.blaubot.test.BlaubotJunitHelper.EthernetBeaconType;
import eu.hgross.blaubot.util.Log;
import eu.hgross.blaubot.util.Log.LogLevel;

/**
 * Tests the ethernet {@link Blaubot} instances. 
 * 
 * Note: These tests are based on the {@link BlaubotEthernetFixedDeviceSetBeacon} component without multicast. 
 * 
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 *
 */
public class EthernetBlaubotWithFixedDeviceSetTest {
	private static final int CONNECTIVITY_TEST_RECHECK_TIMEOUT = 10000;
	private static final int STARTING_PORT_FOR_BLAUBOT_INSTANCES = 18171;
	/**
	 * The number of blaubot instances to test with.
	 */
	private static final int NUMBER_OF_BLAUBOT_INSTANCES = 3; // min 3!
	private static final int MAX_STOPPING_TIMEOUT_FOR_ALL_INSTANCES = 30000;
	private static final int MAX_START_TIME_FOR_ALL_INSTANCES = 30000;
	/**
	 * Defines after what amount of time the set of blaubot instances has to be connected to 
	 * one kingdom.
	 */
	private static final int CONNECTIVITY_TEST_TIMEOUT = 150000;
	private static final long WAIT_TIME_BETWEEN_TESTS = 1000; // sleep time between tests to let the os close the sockets
    private static final int START_STOP_CYCLES = 30;
    private UUID blaubotTestUUID = UUID.fromString("b0ae40aa-41c2-49b3-9f7a-7a14ebc0a8e1");
	private List<Blaubot> instances;
	
	@BeforeClass
	public static void setUpClass() {
		// note: output streams are synchronized and therefore a high log level with many blaubot instances (= much output) will be really slow and could lead to timeouts!
		// tl;dr: detailed log level and many instances will make the tests fail
		Log.LOG_LEVEL = LogLevel.ERRORS;
	}
	
	@Before
	public void setUp() throws UnknownHostException {
//		HashSet<String> uniqueDeviceIdStrings = BlaubotJunitHelper.createEthernetUniqueDeviceIdStringsFromFirstLocalIpAddress(NUMBER_OF_BLAUBOT_INSTANCES, STARTING_PORT_FOR_BLAUBOT_INSTANCES);
		HashSet<String> uniqueDeviceIdStrings = BlaubotJunitHelper.createEthernetUniqueDeviceIdStringsFromLoopbackInterface(NUMBER_OF_BLAUBOT_INSTANCES, STARTING_PORT_FOR_BLAUBOT_INSTANCES);
		this.instances = BlaubotJunitHelper.setUpEthernetBlaubotInstancesFromUniqueIdSet(uniqueDeviceIdStrings, blaubotTestUUID, EthernetBeaconType.FIXED_DEVICE_SET);
	}
	
	@After
	public void cleanUp() throws UnknownHostException, InterruptedException {
		boolean stopped = BlaubotJunitHelper.stopBlaubotInstances(instances, MAX_STOPPING_TIMEOUT_FOR_ALL_INSTANCES);
		
		Thread.sleep(WAIT_TIME_BETWEEN_TESTS);
		if (!stopped) {
            // on failure disconnect all connections manually
            for (Blaubot blaubot : this.instances) {
                List<IBlaubotConnection> conns = blaubot.getChannelManager().reset();
                for (IBlaubotConnection conn : conns) {
                    conn.disconnect();
                }
                
                // and use the closeable impl
                try {
                    blaubot.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
			this.instances.clear();
            throw new RuntimeException("Failed to stop blaubot instances");
		}

		this.instances.clear();
	}


    @Test
    /**
     * Tests multiple start/stop cycles of multiple Blaubot instances.
     * @throws InterruptedException
     */
    public void testManyStartStop() throws InterruptedException {
        for(int i=0;i<START_STOP_CYCLES;i++) {
            testStartStop();
        }
    }

	@Test
	/**
	 * Tests the start of multiple Blaubot instances followed by a stop command.
	 * @throws InterruptedException
	 */
	public void testStartStop() throws InterruptedException {
		// check that all instances start and go at least to the FreeState after startBlaubot()
		// and back to StoppedState after stopBlaubot()
		final CountDownLatch freeStateLatch = new CountDownLatch(instances.size());
		final CountDownLatch stoppedStateLatch = new CountDownLatch(instances.size());
		final IBlaubotConnectionStateMachineListener connectionStateMachineListener = new IBlaubotConnectionStateMachineListener() {
			
			@Override 
			public void onStateMachineStopped() {
			}
			
			@Override
			public void onStateMachineStarted() {
			}
			
			@Override
			public void onStateChanged(IBlaubotState oldState, IBlaubotState state) {
				if(state instanceof FreeState) {
					freeStateLatch.countDown();
				} else if(state instanceof StoppedState) {
					stoppedStateLatch.countDown();
				}
			}
		};
		
		// add listener and start
		for(final Blaubot blaubot : instances) {
			blaubot.getConnectionStateMachine().addConnectionStateMachineListener(connectionStateMachineListener);

			new Thread(new Runnable() {
				@Override
				public void run() {
					blaubot.startBlaubot();
				}
			}).start();
		}
		
		// await or fail after MAX_START_TIME_FOR_ALL_INSTANCES
		boolean allGood = freeStateLatch.await(MAX_START_TIME_FOR_ALL_INSTANCES, TimeUnit.MILLISECONDS);
		if(!allGood) {
			Assert.fail();
		}
		
		// now stop and await stoppedState
		for(Blaubot blaubot : instances) {
			blaubot.stopBlaubot();
		}
		
		// await all stoppedStates or fail after 
		allGood = stoppedStateLatch.await(MAX_STOPPING_TIMEOUT_FOR_ALL_INSTANCES, TimeUnit.MILLISECONDS);
		if(!allGood) {
			Assert.fail();
		}
	}
	
	@Test
	/**
	 * Lets the Blaubot instances form a kingdom and validates the participant's states after a defined
	 * time.
	 */
	public void testConnectivity() throws InterruptedException {
		// start the instances
		boolean started = BlaubotJunitHelper.startBlaubotInstances(instances, MAX_START_TIME_FOR_ALL_INSTANCES);
		Assert.assertTrue("Blaubot instances could net bet started.", started);
		
		boolean instancesFormOneKingdom = BlaubotJunitHelper.blockUntilWeHaveOneKingdom(instances, CONNECTIVITY_TEST_TIMEOUT);
		Assert.assertTrue("The blaubot instances do not form one kingdom! " + BlaubotJunitHelper.createBlaubotCensusString(instances), instancesFormOneKingdom);  
		Assert.assertTrue("The blaubot instances do not form one kingdom! " + BlaubotJunitHelper.createBlaubotCensusString(instances), BlaubotJunitHelper.formOneKingdom(instances));
        formOneKingdomOrFail();


        boolean allStopped = BlaubotJunitHelper.stopBlaubotInstances(instances, MAX_STOPPING_TIMEOUT_FOR_ALL_INSTANCES);
		Assert.assertTrue("Blaubot instances could not be stopped.", allStopped);
	}

    /**
     * Utilizes the CONNECTIVITY_TEST_RECHECK_TIMEOUT constant to periodically check if a kingdom was formed by the instances.
     * If after this time no kingdom was formed, the methods assert will fail.
     * @throws InterruptedException
     */
    private void formOneKingdomOrFail() throws InterruptedException {
        // wait some time and re-check if the kingdom is still working
        // we check often if we can proceed, to speed up the test
        final int divisor = 10;
        int i = 0;
        while(!BlaubotJunitHelper.formOneKingdom(instances) && i++<divisor) {
            Thread.sleep(CONNECTIVITY_TEST_RECHECK_TIMEOUT/divisor);
        }
        Assert.assertTrue("The blaubot instances do not form one kingdom! " + BlaubotJunitHelper.createBlaubotCensusString(instances), BlaubotJunitHelper.formOneKingdom(instances));
    }

    @Test(timeout=120000)
	/**
	 * Lets the Blaubot instances form a kingdom and validates the participants states after a defined
	 * time.
	 * Then the prince will be stopped and the kingdom is checked again after another timeout to ensure
	 * another prince was pronounced successfully.
	 */
	public void testPrinceRepronounce() throws InterruptedException {
		// start the instances
		List<Blaubot> currentKingdom = instances;

		// we need at least 3 instances to perform this test
		Assert.assertTrue("Not enough blaubot instances created to perform test." + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), currentKingdom.size() >= 3);
		
		boolean started = BlaubotJunitHelper.startBlaubotInstances(currentKingdom, MAX_START_TIME_FOR_ALL_INSTANCES);
		Assert.assertTrue("Not all blaubot instances could be started."  + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), started);
		
		// Give the instances some time to form the kingdom
		formOneKingdomOrFail();
		
		// check that we have one kingdom
		Assert.assertTrue("The blaubot instances could not form a kingdom fast enough (" + CONNECTIVITY_TEST_TIMEOUT + " ms)." + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), BlaubotJunitHelper.formOneKingdom(currentKingdom));  
		
		// assert we have exactly one prince
		List<Blaubot> princes = BlaubotJunitHelper.filterBlaubotInstancesByState(State.Prince, currentKingdom);
		Assert.assertTrue("We do not have exactly one prince (" + princes.size() + ")"  + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), princes.size() == 1);
		
		// while we have at least 3 instances, we should have a king a prince and a peasant
		// we now remove the prince until we have 2 instances left
		while(currentKingdom.size() >= 3) {
			// stop the kingdoms prince
			boolean princeStopTimedOut = !BlaubotJunitHelper.stopBlaubotInstances(princes, MAX_STOPPING_TIMEOUT_FOR_ALL_INSTANCES);
			Assert.assertFalse("Could not stop the prince's blaubot instance."  + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), princeStopTimedOut);
			
			// remove the prince from the kingdom
			ArrayList<Blaubot> kingdomWithoutFormerPrince = new ArrayList<Blaubot>(currentKingdom);
			kingdomWithoutFormerPrince.remove(princes.get(0));
			currentKingdom = kingdomWithoutFormerPrince;
			
			// wait for the new prince to be pronounced and let him time to send his ACK message
			// wait 2 times as long as the keepAlive and ack timeout for the prince together
			ConnectionStateMachineConfig connectionStateMachineConfig = princes.get(0).getAdapters().get(0).getConnectionStateMachineConfig();
			BlaubotAdapterConfig adapterConfig = princes.get(0).getAdapters().get(0).getBlaubotAdapterConfig();
			int waitTime = (adapterConfig.getKeepAliveInterval() + connectionStateMachineConfig.getPrinceAckTimeout()) * 6;
			Thread.sleep(waitTime);
			
			// now assert we have a new prince and a valid kingdom
			Assert.assertTrue("No valid kingdom after " + waitTime + " milliseconds. Kingdom: " + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), BlaubotJunitHelper.formOneKingdom(kingdomWithoutFormerPrince));
			
			// put the current prince to the princes variable
			princes = BlaubotJunitHelper.filterBlaubotInstancesByState(State.Prince, currentKingdom);
			
			// set the new kingdom
			currentKingdom = kingdomWithoutFormerPrince;
		}
		
		Assert.assertTrue(currentKingdom.size() == 2);
		
		// now we stop the king and check that the prince claims to be king
		List<Blaubot> kingDevice = BlaubotJunitHelper.filterBlaubotInstancesByState(State.King, currentKingdom);
		List<Blaubot> princeDevice = BlaubotJunitHelper.filterBlaubotInstancesByState(State.Prince, currentKingdom);
		Assert.assertTrue("Could not stop remaining prince or king blaubot instaces." + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), BlaubotJunitHelper.stopBlaubotInstances(kingDevice, MAX_STOPPING_TIMEOUT_FOR_ALL_INSTANCES));
		
		// sleep as long as the keepAlive interval and the crowning preparation time together
		ConnectionStateMachineConfig connectionStateMachineConfig = princeDevice.get(0).getAdapters().get(0).getConnectionStateMachineConfig();
		BlaubotAdapterConfig adapterConfig = princeDevice.get(0).getAdapters().get(0).getBlaubotAdapterConfig();
		int sleepTime = (adapterConfig.getKeepAliveInterval() + connectionStateMachineConfig.getCrowningPreparationTimeout());
		
		// assert that we do not wait longer than the no peasants timeout!
		Assert.assertTrue("Bad configuration (should: sleepTime < connectionStateMachineConfig.getKingWithoutPeasantsTimeout()) is: ("+sleepTime+" >= " + connectionStateMachineConfig.getKingWithoutPeasantsTimeout() + ").", sleepTime < connectionStateMachineConfig.getKingWithoutPeasantsTimeout());
		Thread.sleep(sleepTime/2);
		
		// now check that the former prince is king
		Assert.assertTrue("The wrong blaubot instance is king. Expected " + princeDevice  + "; " + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), BlaubotJunitHelper.filterBlaubotInstancesByState(State.King, princeDevice).size() == 1);
		
		// at last stop
		boolean allStopped = BlaubotJunitHelper.stopBlaubotInstances(instances, MAX_STOPPING_TIMEOUT_FOR_ALL_INSTANCES);
		Assert.assertTrue("Could not stop blaubot instances." + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), allStopped);
	}
	
	@Test(timeout=320000)
	/**
	 * Lets the Blaubot instances form a kingdom and validates the participants states after a defined
	 * time.
	 * Then the king will be killed and it will be validate if the former prince is now king and all other
	 * devices joined him.
	 */
	public void testHeirToTheThrone() throws InterruptedException {
		// form a kingdom
		List<Blaubot> currentKingdom = instances;
		Assert.assertTrue("The blaubot instances could not form a kingdom fast enough. States: " + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), BlaubotJunitHelper.blockUntilWeHaveOneKingdom(currentKingdom, CONNECTIVITY_TEST_TIMEOUT));
		
		
		while(currentKingdom.size() >= 3) {
			// find prince and king
			List<Blaubot> kingDevice = BlaubotJunitHelper.filterBlaubotInstancesByState(State.King, currentKingdom);
			List<Blaubot> princeDevice = BlaubotJunitHelper.filterBlaubotInstancesByState(State.Prince, currentKingdom);
			Assert.assertTrue("The kingdom has the wrong number of kings (" + kingDevice.size() + ") or princes ("+ princeDevice.size() +"). " + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), kingDevice.size() == 1 && princeDevice.size() == 1);
			
			// kill the king
			Assert.assertTrue("The (king) blaubot instance did not stop fast enough.", BlaubotJunitHelper.stopBlaubotInstances(kingDevice, MAX_STOPPING_TIMEOUT_FOR_ALL_INSTANCES));
			currentKingdom.remove(kingDevice.get(0));
			
			// sleep a bit to let the prince follow the king to the throne
			ConnectionStateMachineConfig connectionStateMachineConfig = princeDevice.get(0).getAdapters().get(0).getConnectionStateMachineConfig();
			BlaubotAdapterConfig adapterConfig = princeDevice.get(0).getAdapters().get(0).getBlaubotAdapterConfig();
			int sleepTime = (adapterConfig.getKeepAliveInterval() + connectionStateMachineConfig.getCrowningPreparationTimeout()) * 10;
			Thread.sleep(sleepTime);
			
			// check if we have a valid kingdom now
            BlaubotJunitHelper.blockUntilWeHaveOneKingdom(currentKingdom, 60000);
			Assert.assertTrue("The prince did not took over the throne fast enough. Current kingdom: "  + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), BlaubotJunitHelper.formOneKingdom(currentKingdom));
			
			// check if the former prince is now our king
			List<Blaubot> newKing = BlaubotJunitHelper.filterBlaubotInstancesByState(State.King, currentKingdom);
			Assert.assertTrue("The wrong blaubot instance took over the throne. (Expected was the former prince). Expected: " + princeDevice + ", king: " + newKing  + "; Kingdom: " + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), newKing.get(0) == princeDevice.get(0));
		}
		
		// at last stop
		boolean allStopped = BlaubotJunitHelper.stopBlaubotInstances(instances, MAX_STOPPING_TIMEOUT_FOR_ALL_INSTANCES);
		Assert.assertTrue("Failed to stop blaubot instances.", allStopped);
	}


	@Test(timeout=320000)
    public void testSubscriptions() throws InterruptedException {
        // form a kingdom
        List<Blaubot> currentKingdom = instances;
        Assert.assertTrue("The blaubot instances could not form a kingdom fast enough. States: " + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), BlaubotJunitHelper.blockUntilWeHaveOneKingdom(currentKingdom, CONNECTIVITY_TEST_TIMEOUT));

        // get the channel managers for this kingdom
        final List<BlaubotChannelManager> channelManagers = BlaubotJunitHelper.getChannelManagersFromKingdom(currentKingdom);
        ChannelManagerTest.testSubscriptions(channelManagers);
    }

	@Test(timeout=320000)
    public void testAdminBroadcastMessages() throws InterruptedException {
        // form a kingdom
        List<Blaubot> currentKingdom = instances;
        Assert.assertTrue("The blaubot instances could not form a kingdom fast enough. States: " + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), BlaubotJunitHelper.blockUntilWeHaveOneKingdom(currentKingdom, CONNECTIVITY_TEST_TIMEOUT));

        // get the channel managers for this kingdom
        final List<BlaubotChannelManager> channelManagers = BlaubotJunitHelper.getChannelManagersFromKingdom(currentKingdom);
        ChannelManagerTest.testAdminBMessageBroadcast(channelManagers);
    }

	@Test(timeout=320000)
    public void testMessageOrder() throws InterruptedException {
        // form a kingdom
        List<Blaubot> currentKingdom = instances;
        Assert.assertTrue("The blaubot instances could not form a kingdom fast enough. States: " + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), BlaubotJunitHelper.blockUntilWeHaveOneKingdom(currentKingdom, CONNECTIVITY_TEST_TIMEOUT));

        // get the channel managers for this kingdom
        final List<BlaubotChannelManager> channelManagers = BlaubotJunitHelper.getChannelManagersFromKingdom(currentKingdom);
        ChannelManagerTest.testMessageOrder(channelManagers);
    }

	@Test(timeout=320000)
	public void testExcludeSender() throws InterruptedException, TimeoutException {
		// form a kingdom
		List<Blaubot> currentKingdom = instances;
		Assert.assertTrue("The blaubot instances could not form a kingdom fast enough. States: " + BlaubotJunitHelper.createBlaubotCensusString(currentKingdom), BlaubotJunitHelper.blockUntilWeHaveOneKingdom(currentKingdom, CONNECTIVITY_TEST_TIMEOUT));

		// get the channel managers for this kingdom
		final List<BlaubotChannelManager> channelManagers = BlaubotJunitHelper.getChannelManagersFromKingdom(currentKingdom);
		ChannelManagerTest.testExcludeSender(channelManagers);
	}

}


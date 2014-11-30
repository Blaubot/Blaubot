package de.hsrm.blaubot.junit;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import de.hsrm.blaubot.core.Blaubot;
import de.hsrm.blaubot.core.BlaubotFactory;
import de.hsrm.blaubot.core.State;
import de.hsrm.blaubot.core.statemachine.ConnectionStateMachine;
import de.hsrm.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import de.hsrm.blaubot.core.statemachine.states.FreeState;
import de.hsrm.blaubot.core.statemachine.states.IBlaubotState;
import de.hsrm.blaubot.core.statemachine.states.StoppedState;
import de.hsrm.blaubot.ethernet.BlaubotEthernetUtils;
import de.hsrm.blaubot.util.Log;

/**
 * Utility class for some test methods.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class BlaubotJunitHelper {
	public static final String LOG_TAG = "BlaubotJunitHelper";
	
	/**
	 * Blocks until
	 * 
	 * @param instances {@link Blaubot} to start
	 * @param timeout
	 * @return true iff all blaubot instances were started, false if the timeout occured or not all instaces were started
	 */
	public static boolean startBlaubotInstances(List<Blaubot> instances, int timeout) {
		final CountDownLatch freeStateLatch = new CountDownLatch(instances.size());
		final IBlaubotConnectionStateMachineListener connectionStateMachineListener = new IBlaubotConnectionStateMachineListener() {
			@Override
			public void onStateMachineStopped() {
			}

			@Override
			public void onStateMachineStarted() {
			}

			@Override
			public void onStateChanged(IBlaubotState oldState, IBlaubotState state) {
				if (state instanceof FreeState) {
					freeStateLatch.countDown();
				} else if (state instanceof StoppedState) {
				}
			}
		};

		// add listener and start
		for (Blaubot blaubot : instances) {
			blaubot.getConnectionStateMachine().addConnectionStateMachineListener(connectionStateMachineListener);
			if(blaubot.isStarted()) {
				freeStateLatch.countDown();
			} else {
				blaubot.startBlaubot();
			}
		}

		// await or fail after MAX_START_TIME_FOR_ALL_INSTANCES
		try {
			return freeStateLatch.await(timeout, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			return false;
		}
	}

	/**
	 * Blocks until the given list of blaubot instances are stopped or a timeout occured.
	 * 
	 * @param instances the blaubot instances to stop
	 * @param timeout the max time to wait for the instances to stop
	 * @return true if all instances were stopped, false on timeout
	 */
	public static boolean stopBlaubotInstances(List<Blaubot> instances, int timeout) {
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
				if (state instanceof FreeState) {
				} else if (state instanceof StoppedState) {
					stoppedStateLatch.countDown();
				}
			}
		};

		// now stop and await stoppedState
		for (Blaubot blaubot : instances) {
			blaubot.getConnectionStateMachine().addConnectionStateMachineListener(connectionStateMachineListener);
			if(blaubot.isStarted()) {
				blaubot.stopBlaubot();
			} else {
				stoppedStateLatch.countDown();
			}
		}

		// await all stoppedStates or fail after
		try {
			return stoppedStateLatch.await(timeout, TimeUnit.MILLISECONDS);
		} catch (InterruptedException e) {
			return false;
		}
	}

	/**
	 * Creates a set of uniqueDeviceId {@link String}s starting from startPort using two consecutive 
	 * ports for each instance.
	 *   
	 * @param count the number of uniqueId Strings to create
	 * @param startPort the starting port
	 * @param inetAddress the inetAddress to use
	 */
	public static HashSet<String> createEthernetUniqueDeviceIdStrings(int count, int startPort, InetAddress inetAddress) {
		HashSet<String> deviceSet = new HashSet<String>();
		for (int i = 0; i < count; i += 1, startPort += 2) {
			int acceptorPort = startPort;
			int beaconPort = startPort + 1;
			String uniqueIdString = BlaubotEthernetUtils.createUniqueDeviceId(inetAddress, acceptorPort, beaconPort);
			deviceSet.add(uniqueIdString);
		}
		return deviceSet;
	}
	
	/**
	 * Creates a set of uniqueDeviceId {@link String}s starting from startPort using two consecutive 
	 * ports for each instance using the loopback interface (127.0.0.1).
	 * 
	 * @param count the number of uniqueId Strings to create
	 * @param startPort the starting port
	 * @return
	 * @throws UnknownHostException if 127.0.0.1 is not available .. ;-)
	 */
	public static HashSet<String> createEthernetUniqueDeviceIdStringsFromLoopbackInterface(int count, int startPort) throws UnknownHostException {
		return createEthernetUniqueDeviceIdStrings(count, startPort, InetAddress.getByName("127.0.0.1"));
	}
	
	/**
	 * Creates a set of uniqueDeviceId {@link String}s starting from startPort using two consecutive 
	 * ports for each instance using the first found local ip address.
	 * 
	 * Note that this could be anything (vpn, local network, ...). 
	 * It is recommended to use an explicitly chosen {@link InetAddress}.
	 * 
	 * @param count the number of uniqueId Strings to create
	 * @param startPort the starting port
	 * @return
	 */
	public static HashSet<String> createEthernetUniqueDeviceIdStringsFromFirstLocalIpAddress(int count, int startPort) {
		return createEthernetUniqueDeviceIdStrings(count, startPort, BlaubotFactory.getLocalIpAddress());
	}

	public enum EthernetBeaconType {
		FIXED_DEVICE_SET, MULTICAST_BROADCAST
	}
	
	/**
	 * Creates the {@link Blaubot} (ethernet) instances based on the deviceSet of uniqueId strings
	 * 
	 * @param deviceSet set of uniqueIds to create blaubot instances from
	 * @param blaubotUUID the UUID for all the blaubot instances
	 * @param beaconType specifies which beacon should be used for the {@link Blaubot} instances.
	 * @return 
	 * @throws UnknownHostException 
	 */
	public static List<Blaubot> setUpEthernetBlaubotInstancesFromUniqueIdSet(Set<String> deviceSet, UUID blaubotUUID, EthernetBeaconType beaconType) throws UnknownHostException {
		List<Blaubot> instances = new CopyOnWriteArrayList<Blaubot>();
		for(String uniqueDeviceId : deviceSet) {
			int acceptorPort = BlaubotEthernetUtils.getAcceptorPortFromUniqueId(uniqueDeviceId);
			int beaconPort = BlaubotEthernetUtils.getBeaconPortFromUniqueId(uniqueDeviceId);
			InetAddress ownInetAddress = BlaubotEthernetUtils.getInetAddressFromUniqueId(uniqueDeviceId);
			Blaubot blaubotInstance = null;
			if(beaconType.equals(EthernetBeaconType.MULTICAST_BROADCAST)) {
				blaubotInstance = BlaubotFactory.createEthernetBlaubot(blaubotUUID, acceptorPort, beaconPort, beaconPort, ownInetAddress);
			} else if(beaconType.equals(EthernetBeaconType.FIXED_DEVICE_SET)) {
				blaubotInstance = BlaubotFactory.createEthernetBlaubotWithFixedDevices(blaubotUUID, acceptorPort, beaconPort, ownInetAddress, deviceSet);
			} else {
				throw new IllegalArgumentException("Unknown BeaconType.");
			}
			instances.add(blaubotInstance);
		}
		return instances;
	}
	
	/**
	 * 
	 * @param state
	 * @param blaubotInstances
	 * @return a sublist of blaubotInstaces containing only the blaubot instances which {@link ConnectionStateMachine}s current {@link IBlaubotState} matches state.
	 */
	public static List<Blaubot> filterBlaubotInstancesByState(State state, List<Blaubot> blaubotInstances) {
		ArrayList<Blaubot> filtered = new ArrayList<Blaubot>();
		for(Blaubot blaubot : blaubotInstances) {
			State bState = State.getStateByStatemachineClass(blaubot.getConnectionStateMachine().getCurrentState().getClass());
			if(bState.equals(state)) {
				filtered.add(blaubot);
			}
		}
		return filtered;
	}
	
	/**
	 * Starts the blaubot instances and then blocks until one kingdom is formed 
	 * by the given instances or the timeout occured. If a timeout occurs, false 
	 * is returned.
	 * 
	 * @param instances the blaubot instances
	 * @param timeout the max time to wait to form the kingdom
	 * @return true iff all instances build one kingdom
	 */
	public static boolean blockUntilWeHaveOneKingdom(final List<Blaubot> instances, int timeout) {
		final CountDownLatch latch = new CountDownLatch(1);
		IBlaubotConnectionStateMachineListener connectionStateMachineListener = new IBlaubotConnectionStateMachineListener() {
			private void checkKingdomFormed() {
				System.out.println(""+createBlaubotCensusString(instances));
				if(formOneKingdom(instances)) {
					latch.countDown();
				}
			}
			
			@Override
			public void onStateMachineStopped() {
			}
			
			@Override
			public void onStateMachineStarted() {
			}
			
			@Override
			public void onStateChanged(IBlaubotState oldState, IBlaubotState state) {
				checkKingdomFormed();
			}
		};
		
		for(Blaubot b : instances) {
			b.getConnectionStateMachine().addConnectionStateMachineListener(connectionStateMachineListener);
		}
		for(Blaubot b : instances) {
			b.startBlaubot();
		}
		
		if(formOneKingdom(instances)) {
			latch.countDown();
		}
		
		try {
			boolean timedOut = !latch.await(timeout, TimeUnit.MILLISECONDS);
			for(Blaubot b : instances) {
				b.getConnectionStateMachine().removeConnectionStateMachineListener(connectionStateMachineListener);
			}
			
			if(timedOut) {
				Log.e(LOG_TAG, "Could not create a kingdom in time (" + timeout  + " ms) - Kingdom: " + createBlaubotCensusString(instances));
				return false;
			}
			Log.d(LOG_TAG, "Created a kingdom: " + createBlaubotCensusString(instances));
			return true;
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		
	}
	
	/**
	 * True if the given instances form one (and only one) valid kingdom.
	 * 
	 * @param instances the blaubot instances
	 * @return true iff the instances form one kingdom
	 */
	public static boolean formOneKingdom(List<Blaubot> instances) {
		int peasantCount = BlaubotJunitHelper.filterBlaubotInstancesByState(State.Peasant, instances).size();
		int kingCount = BlaubotJunitHelper.filterBlaubotInstancesByState(State.King, instances).size();
		int princeCount = BlaubotJunitHelper.filterBlaubotInstancesByState(State.Prince, instances).size();
		int freeCount = BlaubotJunitHelper.filterBlaubotInstancesByState(State.Free, instances).size();
		if (instances.size() == 1) {
			return (peasantCount == 0 && kingCount == 0 && princeCount == 0 && freeCount == 1) || (peasantCount == 0 && kingCount == 1 && princeCount == 0 && freeCount == 0);
		} else if (instances.size() == 2) {
			return peasantCount == 0 && kingCount == 1 && princeCount == 1 && freeCount == 0;
		} else if (instances.size() >= 3) {
			return peasantCount == instances.size() - 2 && kingCount == 1 && princeCount == 1 && freeCount == 0;
		}
		return false;
	}
	
	/**
	 * Creates a string containing informations about the states of a given set of {@link Blaubot} instances
	 * for logging purposes.
	 * 
	 * @param instances the blaubot instances
	 * @return
	 */
	public static String createBlaubotCensusString(List<Blaubot> instances) {
		int stoppedCount = BlaubotJunitHelper.filterBlaubotInstancesByState(State.Stopped, instances).size();
		int peasantCount = BlaubotJunitHelper.filterBlaubotInstancesByState(State.Peasant, instances).size();
		int kingCount = BlaubotJunitHelper.filterBlaubotInstancesByState(State.King, instances).size();
		int princeCount = BlaubotJunitHelper.filterBlaubotInstancesByState(State.Prince, instances).size();
		int freeCount = BlaubotJunitHelper.filterBlaubotInstancesByState(State.Free, instances).size();	
		StringBuilder sb = new StringBuilder();
		sb.append("Stopped: ").append(stoppedCount).append(", ");
		sb.append("Free: ").append(freeCount).append(", ");
		sb.append("Peasant: ").append(peasantCount).append(", ");
		sb.append("Prince: ").append(princeCount).append(", ");
		sb.append("King: ").append(kingCount);
		return sb.toString();
	}
	
	
}


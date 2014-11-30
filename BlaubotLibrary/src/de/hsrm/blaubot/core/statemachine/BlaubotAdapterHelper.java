package de.hsrm.blaubot.core.statemachine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import de.hsrm.blaubot.core.IBlaubotAdapter;
import de.hsrm.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import de.hsrm.blaubot.core.acceptor.IBlaubotListeningStateListener;
import de.hsrm.blaubot.core.acceptor.discovery.BlaubotBeaconService;
import de.hsrm.blaubot.core.acceptor.discovery.IBlaubotBeaconInterface;
import de.hsrm.blaubot.core.connector.IBlaubotConnector;
import de.hsrm.blaubot.util.Log;

/**
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotAdapterHelper {
	public static final String LOG_TAG = "BlaubotAdapterHelper";
	
	/**
	 * 
	 * @param adapters
	 * @return all beacon intefaces contained by the list of adapters
	 */
	public static List<IBlaubotBeaconInterface> getBeaconInterfaces(List<IBlaubotAdapter> adapters) {
		ArrayList<IBlaubotBeaconInterface> out = new ArrayList<IBlaubotBeaconInterface>();
		for(IBlaubotAdapter a : adapters)
			out.add(a.getBeaconInterface());
		return out;
	}

	/**
	 * 
	 * @param adapters
	 * @return all connection acceptors contained by the list of adapters
	 */
	public static List<IBlaubotConnectionAcceptor> getConnectionAcceptors(List<IBlaubotAdapter> adapters) {
		ArrayList<IBlaubotConnectionAcceptor> out = new ArrayList<IBlaubotConnectionAcceptor>();
		for(IBlaubotAdapter a : adapters)
			out.add(a.getConnectionAcceptor());
		return out;
	}

	/**
	 * 
	 * @param adapters
	 * @return all connectors contained by the list of adapters
	 */
	public static List<IBlaubotConnector> getConnectors(List<IBlaubotAdapter> adapters) {
		ArrayList<IBlaubotConnector> out = new ArrayList<IBlaubotConnector>();
		for(IBlaubotAdapter a : adapters)
			out.add(a.getConnector());
		return out;
	}

	private static int startedCount(List<IBlaubotConnectionAcceptor> acceptors, List<IBlaubotBeaconInterface> beacons) {
		int i = 0;
		if (acceptors != null)
			for (IBlaubotConnectionAcceptor acc : acceptors)
				if (acc.isStarted())
					i++;
		if (beacons != null)
			for (IBlaubotConnectionAcceptor acc : beacons)
				if (acc.isStarted())
					i++;
		return i;
	}
	
	
	/**
	 * Calls setDiscoveryActivated on all beacons
	 * @param beaconService
	 * @param newState
	 */
	public static void setDiscoveryActivated(BlaubotBeaconService beaconService, boolean newState) {
		for(IBlaubotBeaconInterface beacon : beaconService.getBeacons()) {
			beacon.setDiscoveryActivated(newState);
		}
	};

	public static void stopAcceptors(List<IBlaubotConnectionAcceptor> acceptors) {
		int cnt = startedCount(acceptors, null);
		final CountDownLatch countDown = new CountDownLatch(cnt);
		IBlaubotListeningStateListener listeningStateListener = new IBlaubotListeningStateListener() {
			@Override
			public void onListeningStopped(IBlaubotConnectionAcceptor connectionAcceptor) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got listeningStopped from connectionAcceptor: " + connectionAcceptor);
				}
				countDown.countDown();
			}

			@Override
			public void onListeningStarted(IBlaubotConnectionAcceptor connectionAcceptor) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got listeningStarted from connectionAcceptor: " + connectionAcceptor);
				}
				throw new IllegalStateException("Got onListeningStarted while waiting for the acceptors to stop!");
			}
		};
		for (IBlaubotConnectionAcceptor acceptor : acceptors) {
			acceptor.setListeningStateListener(listeningStateListener);
		}
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Stopping blaubot acceptors ...");
		}
		for (IBlaubotConnectionAcceptor acceptor : acceptors) {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "\tStopping acceptor: " + acceptor);
			}
			acceptor.stopListening();
		}

		// TODO use timeout
		try {
			countDown.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		for (IBlaubotConnectionAcceptor acceptor : acceptors) {
			acceptor.setListeningStateListener(null);
		}
	}

	/**
	 * blocking until all acceptors and beacons are stopped (if started)
	 * 
	 * @param acceptors
	 * @param beaconService
	 */
	public static void stopAcceptorsAndBeacons(List<IBlaubotConnectionAcceptor> acceptors, BlaubotBeaconService beaconService) {
		List<IBlaubotBeaconInterface> beacons = beaconService.getBeacons();
		int cnt = startedCount(acceptors, beacons);
		final CountDownLatch countDown = new CountDownLatch(cnt);
		IBlaubotListeningStateListener listeningStateListener = new IBlaubotListeningStateListener() {
			@Override
			public void onListeningStopped(IBlaubotConnectionAcceptor connectionAcceptor) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got listeningStopped from connectionAcceptor: " + connectionAcceptor);
				}
				countDown.countDown();
			}

			@Override
			public void onListeningStarted(IBlaubotConnectionAcceptor connectionAcceptor) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got listeningStarted from connectionAcceptor: " + connectionAcceptor);
				}
				throw new IllegalStateException("Got onListeningStarted while waiting for the acceptors to stop!");
			}
		};
		for (IBlaubotConnectionAcceptor acceptor : acceptors) {
			acceptor.setListeningStateListener(listeningStateListener);
		}
		for (IBlaubotConnectionAcceptor beacon : beacons) {
			beacon.setListeningStateListener(listeningStateListener);
		}

		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Stopping beacon services ... ");
		}
		beaconService.stopBeaconInterfaces();
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Stopping - going through all acceptors ...");
		}
		for (IBlaubotConnectionAcceptor acceptor : acceptors) {
			if(Log.logDebugMessages()) {
				Log.d(LOG_TAG, "\tStopping acceptor: " + acceptor);
			}
			acceptor.stopListening();
		}

		// TODO use timeout
		try {
			countDown.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		for (IBlaubotConnectionAcceptor acceptor : acceptors) {
			acceptor.setListeningStateListener(null);
		}
		for (IBlaubotConnectionAcceptor beacon : beacons) {
			beacon.setListeningStateListener(null);
		}
	}

	public static void startAcceptors(List<IBlaubotConnectionAcceptor> acceptors) {
		int cnt = acceptors.size();
		int startedCnt = startedCount(acceptors, null);
		final CountDownLatch countDown = new CountDownLatch(cnt - startedCnt);
		IBlaubotListeningStateListener listeningStateListener = new IBlaubotListeningStateListener() {
			@Override
			public void onListeningStopped(IBlaubotConnectionAcceptor connectionAcceptor) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got listeningStopped from connectionAcceptor: " + connectionAcceptor);
				}
				throw new IllegalStateException("Got onListeningStopped while waiting for the acceptors to start!");
			}

			@Override
			public void onListeningStarted(IBlaubotConnectionAcceptor connectionAcceptor) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got listeningStarted from connectionAcceptor: " + connectionAcceptor);
				}
				countDown.countDown();
			}
		};
		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Starting acceptors ... ");
		}
		for (IBlaubotConnectionAcceptor acc : acceptors) {
			if(acc.isStarted())
				continue;
			acc.setListeningStateListener(listeningStateListener);
			acc.startListening();
		}


		try {
			// TODO: use a timeout here!
			countDown.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		for (IBlaubotConnectionAcceptor acc : acceptors) {
			acc.setListeningStateListener(null);
		}
	}

	public static void startBeacons(BlaubotBeaconService beaconService) {
		List<IBlaubotBeaconInterface> beacons = beaconService.getBeacons();
		int cnt = beacons.size();
		int startedCnt = startedCount(null, beacons);
		final CountDownLatch countDown = new CountDownLatch(cnt - startedCnt);
		IBlaubotListeningStateListener listeningStateListener = new IBlaubotListeningStateListener() {
			@Override
			public void onListeningStopped(IBlaubotConnectionAcceptor connectionAcceptor) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got listeningStopped from connectionAcceptor: " + connectionAcceptor);
				}
				throw new IllegalStateException("Got onListeningStopped while waiting for the acceptors to start!");
			}

			@Override
			public void onListeningStarted(IBlaubotConnectionAcceptor connectionAcceptor) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got listeningStarted from connectionAcceptor: " + connectionAcceptor);
				}
				countDown.countDown();
			}
		};
		for (IBlaubotConnectionAcceptor beacon : beacons) {
			beacon.setListeningStateListener(listeningStateListener);
		}

		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Starting beacon services ... ");
		}
		beaconService.startBeaconInterfaces();

		try {
			// TODO: use a timeout here!
			countDown.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		for (IBlaubotConnectionAcceptor beacon : beacons) {
			beacon.setListeningStateListener(null);
		}
	}

	public static void startAcceptorsAndBeacons(List<IBlaubotConnectionAcceptor> acceptors, BlaubotBeaconService beaconService) {
		List<IBlaubotBeaconInterface> beacons = beaconService.getBeacons();
		int cnt = acceptors.size() + beacons.size();
		int startedCnt = startedCount(acceptors, beacons);
		final CountDownLatch countDown = new CountDownLatch(cnt - startedCnt);
		IBlaubotListeningStateListener listeningStateListener = new IBlaubotListeningStateListener() {
			@Override
			public void onListeningStopped(IBlaubotConnectionAcceptor connectionAcceptor) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got listeningStopped from connectionAcceptor: " + connectionAcceptor);
				}
				throw new IllegalStateException("Got onListeningStopped while waiting for the acceptors to start!");
			}

			@Override
			public void onListeningStarted(IBlaubotConnectionAcceptor connectionAcceptor) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "Got listeningStarted from connectionAcceptor: " + connectionAcceptor);
				}
				countDown.countDown();
			}
		};
		for (IBlaubotConnectionAcceptor acceptor : acceptors) {
			acceptor.setListeningStateListener(listeningStateListener);
		}
		for (IBlaubotConnectionAcceptor beacon : beacons) {
			beacon.setListeningStateListener(listeningStateListener);
		}

		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Starting beacon services ... ");
		}
		beaconService.startBeaconInterfaces();

		if(Log.logDebugMessages()) {
			Log.d(LOG_TAG, "Starting acceptors ... ");
		}
		for (IBlaubotConnectionAcceptor acceptor : acceptors) {
			if (!acceptor.isStarted()) {
				if(Log.logDebugMessages()) {
					Log.d(LOG_TAG, "\tStarting acceptor: " + acceptor);
				}
				acceptor.startListening();
			}
		}

		try {
			// TODO: use a timeout here!
			countDown.await();
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		for (IBlaubotConnectionAcceptor acceptor : acceptors) {
			acceptor.setListeningStateListener(null);
		}
		for (IBlaubotConnectionAcceptor beacon : beacons) {
			beacon.setListeningStateListener(null);
		}
	}

}

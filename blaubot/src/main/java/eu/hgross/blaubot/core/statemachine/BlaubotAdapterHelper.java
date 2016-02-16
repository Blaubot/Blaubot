package eu.hgross.blaubot.core.statemachine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import eu.hgross.blaubot.core.IBlaubotAdapter;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.core.acceptor.IBlaubotListeningStateListener;
import eu.hgross.blaubot.core.acceptor.discovery.BlaubotBeaconService;
import eu.hgross.blaubot.core.acceptor.discovery.IBlaubotBeacon;
import eu.hgross.blaubot.core.connector.IBlaubotConnector;
import eu.hgross.blaubot.util.Log;

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

	/**
	 * counts the number of started beacons and acceptors
	 * @param acceptors null or list of acceptors
	 * @param beacons null or list of beacons
	 * @return the sum of started acceptors and beacons
	 */
	public static int startedCount(List<IBlaubotConnectionAcceptor> acceptors, List<IBlaubotBeacon> beacons) {
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
		for(IBlaubotBeacon beacon : beaconService.getBeacons()) {
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
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Awaiting onListeningStopped of all acceptors");
            }
            countDown.await();
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "All acceptors are stopped now");
            }
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
		List<IBlaubotBeacon> beacons = beaconService.getBeacons();
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
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Awaiting onListeningStopped of all acceptors and beacons");
            }
            countDown.await();
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "All acceptors and beacons are stopped now");
            }
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

    /**
     * blocking until all beacons are stopped (if started)
     *
     * @param beaconService
     */
    public static void stopBeacons(BlaubotBeaconService beaconService) {
        List<IBlaubotBeacon> beacons = beaconService.getBeacons();
        int cnt = startedCount(null, beacons);
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
        for (IBlaubotConnectionAcceptor beacon : beacons) {
            beacon.setListeningStateListener(listeningStateListener);
        }

        if(Log.logDebugMessages()) {
            Log.d(LOG_TAG, "Stopping beacon services ... ");
        }
        beaconService.stopBeaconInterfaces();

        // TODO use timeout
        try {
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Awaiting onListeningStopped of all acceptors and beacons");
            }
            countDown.await();
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "All acceptors and beacons are stopped now");
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
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
			Log.d(LOG_TAG, "Starting all acceptors ... ");
		}
		for (IBlaubotConnectionAcceptor acc : acceptors) {
			if(acc.isStarted())
				continue;
			acc.setListeningStateListener(listeningStateListener);
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Starting acceptor: " + acc);
            }
			acc.startListening();
		}


		try {
			// TODO: use a timeout here!
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Awaiting onListeningStarted of all acceptors");
            }
            countDown.await();
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "All acceptors are started properly");
            }
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		for (IBlaubotConnectionAcceptor acc : acceptors) {
			acc.setListeningStateListener(null);
		}
	}

	public static void startBeacons(BlaubotBeaconService beaconService) {
		List<IBlaubotBeacon> beacons = beaconService.getBeacons();
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
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Awaiting onListeningStarted of all beacons");
            }
            countDown.await();
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "All beacons are started now");
            }
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}

		for (IBlaubotConnectionAcceptor beacon : beacons) {
			beacon.setListeningStateListener(null);
		}
	}

	public static void startAcceptorsAndBeacons(List<IBlaubotConnectionAcceptor> acceptors, BlaubotBeaconService beaconService) {
		List<IBlaubotBeacon> beacons = beaconService.getBeacons();
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
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Awaiting onListeningStarted of all acceptors and beacons");
            }
            countDown.await();
            if(Log.logDebugMessages()) {
                Log.d(LOG_TAG, "All acceptors and beacons are started now");
            }
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

    /**
     * Takes acceptors and retrieves their connection meta data objects as a list by
     * retrieving the meta data and putting them into a single list.
     *
     * @param acceptors the acceptors to gather the meta data from
     * @return the list of extracted connection meta data objects for the given acceptors
     */
    public static List<ConnectionMetaDataDTO> getConnectionMetaDataList(List<IBlaubotConnectionAcceptor> acceptors) {
        final ArrayList<ConnectionMetaDataDTO> connectionMetaDataDTOs = new ArrayList<>();
        for(IBlaubotConnectionAcceptor acceptor : acceptors) {
            connectionMetaDataDTOs.add(acceptor.getConnectionMetaData());
        }
        return connectionMetaDataDTOs;
    }


    /**
     * Filters a list of connection meta data objects by a list of supported acceptor types (like intersect, but not a set)
     * @param connectionMetaDataList the list of acceptor meta data
     * @param supportedAcceptorTypes the list of supported acceptor types to filter ourAcceptorMetaDataList
     * @return new list containing only the meta data from ourAcceptorMetaDataList which acceptor type matches one of the strings in supportedAcceptorTypes
     */
    public static List<ConnectionMetaDataDTO> filterBySupportedAcceptorTypes(List<ConnectionMetaDataDTO> connectionMetaDataList, List<String> supportedAcceptorTypes) {
        ArrayList<ConnectionMetaDataDTO> filtered = new ArrayList<>();
        for(ConnectionMetaDataDTO metaData : connectionMetaDataList) {
            if(supportedAcceptorTypes.contains(metaData.getConnectionType())) {
                filtered.add(metaData);
            }
        }
        return filtered;
    }

    /**
     * Extracts all supported connection types from a list of connectors
     * @param connectors the connectors
     * @return a list of strings representing the connection types of each of the connectors
     */
    public static List<String> extractSupportedConnectionTypes(List<IBlaubotConnector> connectors) {
        ArrayList<String> supportedConTypes = new ArrayList<>();
        for(IBlaubotConnector connector : connectors) {
            for(String suppportedType : connector.getSupportedAcceptorTypes()) {
                supportedConTypes.add(suppportedType);
            }
        }
        return supportedConTypes;
    }

}

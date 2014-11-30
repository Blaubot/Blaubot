package de.hsrm.blaubot.protocol;

import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

import de.hsrm.blaubot.core.Blaubot;
import de.hsrm.blaubot.protocol.client.channel.ChannelFactory;
import de.hsrm.blaubot.util.Log;

/**
 * holds the current context of the protocol layer. includes the unique device
 * id of this {@link Blaubot} instance and short device id to unique device id
 * mappings for message delivery purposes.
 * 
 * @author manuelpras
 *
 */
public class ProtocolContext {

	private static final String TAG = ProtocolContext.class.getName();
	private final ExecutorService executors;
	private ChannelFactory channelFactory;
	private final String ownUniqueDeviceID;
	// monitors
	private final Object channelSubscriptionsMonitor;
	private final Object channelIdsMonitor;
	private final Object deviceIdsMonitor;
	// mappings
	private ConcurrentHashMap<Short, CopyOnWriteArraySet<Short>> channelSubscriptions;
	private ConcurrentHashMap<String, Short> deviceChannelIDs;
	private ConcurrentHashMap<Short, String> shortToUniqueDeviceIDs;
	private ConcurrentHashMap<String, Short> uniqueToShortDeviceIDs;

	/**
	 * 
	 * @param ownUniqueDeviceID
	 *            the unique device id of the {@link Blaubot} instance (usually
	 *            something like the ip address in the network)
	 */
	public ProtocolContext(String ownUniqueDeviceID) {
		this.ownUniqueDeviceID = ownUniqueDeviceID;
		this.channelSubscriptions = new ConcurrentHashMap<Short, CopyOnWriteArraySet<Short>>();
		this.channelSubscriptionsMonitor = new Object();
		this.deviceChannelIDs = new ConcurrentHashMap<String, Short>();
		this.deviceIdsMonitor = new Object();
		this.channelIdsMonitor = new Object();
		this.shortToUniqueDeviceIDs = new ConcurrentHashMap<Short, String>();
		this.uniqueToShortDeviceIDs = new ConcurrentHashMap<String, Short>();
		this.executors = Executors.newCachedThreadPool();
	}

	/**
	 * 
	 * @return unique device id of the {@link Blaubot} instance
	 */
	public String getOwnUniqueDeviceID() {
		return ownUniqueDeviceID;
	}

	public ChannelFactory getChannelFactory() {
		return channelFactory;
	}

	public void setChannelFactory(ChannelFactory channelFactory) {
		this.channelFactory = channelFactory;
	}

	/**
	 * 
	 * @param channelID
	 * @return all subscriptions for the given channel id
	 */
	public Set<Short> getChannelSubscriptions(short channelID) {
		synchronized (this.channelSubscriptionsMonitor) {
			return this.channelSubscriptions.get(channelID);
		}
	}

	/**
	 * 
	 * @param unqiueDeviceId
	 * @return a {@link FutureTask} which will provide the short device id
	 *         corresponding to the given unique device id if existing.
	 */
	public FutureTask<Short> getShortDeviceId(final String unqiueDeviceId) {
		if (unqiueDeviceId == null) {
			throw new RuntimeException("ProtocolContext::awaitShortDeviceId: key null???");
		}

		FutureTask<Short> futureTask = new FutureTask<Short>(new Callable<Short>() {

			@Override
			public Short call() throws Exception {
				synchronized (deviceIdsMonitor) {
					Short shortDeviceId = uniqueToShortDeviceIDs.get(unqiueDeviceId);
					while (shortDeviceId == null) {
						deviceIdsMonitor.wait();
						shortDeviceId = uniqueToShortDeviceIDs.get(unqiueDeviceId);
					}
					return shortDeviceId;
				}
			}
		});
		this.executors.execute(futureTask);
		return futureTask;
	}

	/**
	 * 
	 * @param shortDeviceId
	 * @return a {@link FutureTask} which will provide the unique device id
	 *         corresponding to the given short device id if existing.
	 */
	public FutureTask<String> getUniqueDeviceId(final Short shortDeviceId) {
		if (shortDeviceId == null) {
			throw new RuntimeException("ProtocolContext::awaitUniqueDeviceId: key null???");
		}

		FutureTask<String> futureTask = new FutureTask<String>(new Callable<String>() {

			@Override
			public String call() throws Exception {
				synchronized (deviceIdsMonitor) {
					String uniqueDeviceId = shortToUniqueDeviceIDs.get(shortDeviceId);
					while (uniqueDeviceId == null) {
						deviceIdsMonitor.wait();
						uniqueDeviceId = shortToUniqueDeviceIDs.get(shortDeviceId);
					}
					return uniqueDeviceId;
				}
			}
		});
		this.executors.execute(futureTask);
		return futureTask;
	}

	/**
	 * await the corresponding device channel id for the given unique device id
	 * to be delivered to the given callback within the given timeout. if the id
	 * couldn't be received within this time frame the callback's onFailure()
	 * method will be called.
	 * 
	 * @param uniqueDeviceId
	 * @param timeout
	 *            in milliseconds
	 * @param callback
	 * @return
	 */
	public FutureTask<Short> getDeviceChannelId(final String uniqueDeviceId) {
		if (uniqueDeviceId == null) {
			throw new RuntimeException("ProtocolContext::awaitDeviceChannelId: key null???");
		}

		FutureTask<Short> futureTask = new FutureTask<Short>(new Callable<Short>() {

			@Override
			public Short call() throws Exception {
				synchronized (channelIdsMonitor) {
					Short deviceChannelID = deviceChannelIDs.get(uniqueDeviceId);
					while (deviceChannelID == null) {
						channelIdsMonitor.wait();
						deviceChannelID = deviceChannelIDs.get(uniqueDeviceId);
					}
					return deviceChannelID;
				}
			}
		});
		this.executors.execute(futureTask);
		return futureTask;
	}

	/**
	 * @return all currently existing short device IDs
	 */
	public Set<Short> getShortDeviceIds() {
		synchronized (deviceIdsMonitor) {
			return shortToUniqueDeviceIDs.keySet();
		}
	}

	/**
	 * @param shortDeviceId
	 * @param uniqueDeviceId
	 * @return true if no previous entry existed for given shortdeviceId and
	 *         uniqueDeviceId, false if already existing
	 */
	public boolean putShortDeviceIdToUnqiueDeviceId(Short shortDeviceId, String uniqueDeviceId) {
		if (shortDeviceId == null) {
			throw new RuntimeException("ProtocolContext::putShortDeviceIdToUnqiueDeviceId: key null???");
		}
		if (uniqueDeviceId == null) {
			throw new RuntimeException("ProtocolContext::putShortDeviceIdToUnqiueDeviceId: value null???");
		}

		synchronized (deviceIdsMonitor) {
			Short uniqueToShort = uniqueToShortDeviceIDs.get(uniqueDeviceId);
			boolean uniqueToShortExists = uniqueToShort != null;
			/*
			 * It is possible that a new connection with the same uniqueDeviceId
			 * connects so the uniqueToShort mapping will already be there. In
			 * this case no new mapping was added an we will return false. This
			 * is because we are called with a higher shortDeviceId than the one
			 * already stored in the uniqueToShort mapping.
			 */
			// cancel if already existing
			if (uniqueToShortExists) {
				return false;
			}
			// only add mapping if not yet existing
			boolean addedUniqueDeviceId = this.shortToUniqueDeviceIDs.put(shortDeviceId, uniqueDeviceId) == null;
			boolean addedShortDeviceId = this.uniqueToShortDeviceIDs.put(uniqueDeviceId, shortDeviceId) == null;
			if (!(addedUniqueDeviceId && addedShortDeviceId)) {
				// either the uniqueDeviceId or shortDevice Id were already
				// mapped
				throw new RuntimeException("Inconsistant state addedUniqueDeviceId: " + addedUniqueDeviceId + ", addedShortDeviceId: " + addedShortDeviceId);
			}
			deviceIdsMonitor.notifyAll();
			return true;
		}
	}

	/**
	 * puts the given unique device id and channel id to the mapping if not yet
	 * existing
	 * 
	 * @param uniqueDeviceId
	 * @param channelId
	 * @return true if the channel already existed, false otherwise
	 */
	public boolean putDeviceChannelIdIfAbsent(String uniqueDeviceId, Short channelId) {
		Short oldChannelId = this.deviceChannelIDs.putIfAbsent(uniqueDeviceId, channelId);
		boolean channelAlreadyExisted = oldChannelId != null;
		if (channelAlreadyExisted && !oldChannelId.equals(channelId)) {
			Log.w(TAG, "device channel already existed, changed from " + oldChannelId + " to " + channelId);
		}
		synchronized (channelIdsMonitor) {
			// notify all threads waiting for this channel
			channelIdsMonitor.notifyAll();
		}
		return channelAlreadyExisted;
	}

	/**
	 * 
	 * @return all current device channel ids as entry sets
	 */
	public Set<Entry<String, Short>> getDeviceChannelIDsEntrySet() {
		synchronized (this.channelIdsMonitor) {
			return this.deviceChannelIDs.entrySet();
		}
	}

	/**
	 * 
	 * @return all current channel subscription as entry sets
	 */
	public Set<Entry<Short, CopyOnWriteArraySet<Short>>> getChannelSubscriptionsEntrySet() {
		synchronized (this.channelSubscriptionsMonitor) {
			return this.channelSubscriptions.entrySet();
		}
	}

	/**
	 * adds the given short device id to the given channel id subscription
	 * mapping.
	 * 
	 * @param channelID
	 * @param shortDeviceID
	 */
	public void addChannelSubscription(Short channelID, short shortDeviceID) {
		synchronized (this.channelSubscriptionsMonitor) {
			// create list if necessary
			this.channelSubscriptions.putIfAbsent(channelID, new CopyOnWriteArraySet<Short>());
			// add subscription to actual list
			Set<Short> subscriptions = this.channelSubscriptions.get(channelID);
			subscriptions.add(shortDeviceID);
		}
	}

	/**
	 * removes the given short device id from the given channel id subscription
	 * mapping.
	 * 
	 * @param channelID
	 * @param shortDeviceID
	 */
	public void removeChannelSubscription(Short channelID, short shortDeviceID) {
		synchronized (this.channelSubscriptionsMonitor) {
			Set<Short> subscriptions = this.channelSubscriptions.get(channelID);
			if (subscriptions != null) {
				subscriptions.remove(shortDeviceID);
			}
		}
	}

	/**
	 * create channel id for given short device id if not existing, finally
	 * return corresponding channel id
	 * 
	 * @param uniqueDeviceID
	 *            id corresponding to the short device id
	 * @param shortDeviceID
	 *            id corresponding to the unique device id
	 * @return corresponding channel id
	 */
	public Short createChannelId(String uniqueDeviceID, short shortDeviceID) {
		// broadcast channel uses Short.MIN_VALUE as channel id
		// -> protocol device IDs start with 1
		if (shortDeviceID < 1) {
			throw new RuntimeException("shortDeviceID has to be greater 0!");
		}
		short channelID = (short) (Short.MIN_VALUE + shortDeviceID);
		boolean channelWasNewlyCreated = !putDeviceChannelIdIfAbsent(uniqueDeviceID, channelID);
		if (Log.logDebugMessages()) {
			if(channelWasNewlyCreated) {
				Log.d(TAG, String.format("Created channel id %d for unique device id %s", channelID, uniqueDeviceID));
			}
		}
		try {
			return getDeviceChannelId(uniqueDeviceID).get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public String toString() {
		return "ProtocolContext [ownUniqueDeviceID=" + ownUniqueDeviceID + ", channelSubscriptions=" + channelSubscriptions + ", adminDeviceChannelIDs=" + deviceChannelIDs + ", shortToUniqueDeviceIDs=" + shortToUniqueDeviceIDs + ", uniqueToShortDeviceIDs=" + uniqueToShortDeviceIDs + "]";
	}

	/**
	 * resets all values in this context (clear mappings etc.)
	 */
	public void reset() {
		synchronized (channelIdsMonitor) {
			this.deviceChannelIDs.clear();
		}
		synchronized (deviceIdsMonitor) {
			this.shortToUniqueDeviceIDs.clear();
			this.uniqueToShortDeviceIDs.clear();
		}
	}

}

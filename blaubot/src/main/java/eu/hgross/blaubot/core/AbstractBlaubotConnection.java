package eu.hgross.blaubot.core;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionListener;

/**
 * Abstract {@link IBlaubotConnection} implemeneting only the listener code.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public abstract class AbstractBlaubotConnection implements IBlaubotConnection {
	protected List<IBlaubotConnectionListener> connectionListeners;
	private UUID id = UUID.randomUUID();
	
	public AbstractBlaubotConnection() {
		this.connectionListeners = new CopyOnWriteArrayList<IBlaubotConnectionListener>();
	}
	
	@Override
	public void addConnectionListener(IBlaubotConnectionListener listener) {
		this.connectionListeners.add(listener);
	}

	@Override
	public void removeConnectionListener(IBlaubotConnectionListener listener) {
		this.connectionListeners.remove(listener);
	}
	
	/**
	 * Notifies all registered listeners that this connection is now disconnected
	 */
	protected void notifyDisconnected() {
		for(final IBlaubotConnectionListener listener: this.connectionListeners){
            listener.onConnectionClosed(this);
        }
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (obj == null) {
			return false;
		}
		if (getClass() != obj.getClass()) {
			return false;
		}
		AbstractBlaubotConnection other = (AbstractBlaubotConnection) obj;
		if (id == null) {
			if (other.id != null) {
				return false;
			}
		} else if (!id.equals(other.id)) {
			return false;
		}
		return true;
	}
	
	
	
}

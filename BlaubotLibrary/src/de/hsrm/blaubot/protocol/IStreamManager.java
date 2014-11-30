package de.hsrm.blaubot.protocol;

import de.hsrm.blaubot.core.IBlaubotConnection;
import de.hsrm.blaubot.message.BlaubotMessage;

/**
 * wrapper around an abstract {@link IBlaubotConnection}. once started, this
 * implementation will start reading form the corresponding
 * {@link IBlaubotConnection} and notify attached {@link IStreamListener}s when
 * there are new {@link BlaubotMessage}s.
 * 
 * @author manuelpras
 *
 */
public interface IStreamManager {

	/**
	 * cancel stream manager by not reading from connection anymore and release
	 * resources
	 */
	public void cancel();

	/**
	 * start reading from the given connection
	 */
	public void start();

	/**
	 * @return true if previously canceled via cancel(), false otherwise
	 */
	public boolean isCanceled();

}

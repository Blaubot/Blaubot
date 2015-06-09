package eu.hgross.blaubot.core;

import java.util.Timer;
import java.util.TimerTask;

import eu.hgross.blaubot.messaging.BlaubotChannelManager;
import eu.hgross.blaubot.messaging.BlaubotMessage;

/**
 * Helper object managing the keep alive message delivery at a fixed rate.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class KeepAliveSender {
	protected static final String LOG_TAG = "KeepAliveSender";
	private final int keepAliveInterval;
	private final TimerTask timerTask;
	private Timer timer;

	public KeepAliveSender(final IBlaubotDevice device, final BlaubotChannelManager channelManager, int keepAliveInterval) {
        this.keepAliveInterval = keepAliveInterval;
		this.timerTask = new TimerTask() {
			@Override
			public void run() {
				final BlaubotMessage keepAliveMsg = new BlaubotMessage();
                keepAliveMsg.getMessageType().setIsAdminMessage(false).setIsKeepAliveMessage(true).setContainsPayload(false).setIsFirstHop(false);
                channelManager.publishToSingleDevice(keepAliveMsg, device.getUniqueDeviceID());
			}
		};
	}

	public void stop() {
		if (this.timer != null) {
			this.timer.cancel();
			this.timer = null;
		}
	}

	public void start() {
		if (this.timer != null) {
			stop();
		}
		this.timer = new Timer();
		this.timer.scheduleAtFixedRate(timerTask, keepAliveInterval, keepAliveInterval);
	}

}

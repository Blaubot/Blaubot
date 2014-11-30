package de.hsrm.blaubot.core;

import java.util.Timer;
import java.util.TimerTask;

import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.message.MessageType;
import de.hsrm.blaubot.message.MessageTypeFactory;
import de.hsrm.blaubot.protocol.IProtocolManager;
import de.hsrm.blaubot.protocol.ProtocolManager;
import de.hsrm.blaubot.protocol.client.channel.Channel;

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

	public KeepAliveSender(final IBlaubotDevice device, final IProtocolManager protocolManager) {
		this.keepAliveInterval = device.getAdapter().getBlaubotAdapterConfig().getKeepAliveInterval();
		this.timerTask = new TimerTask() {
			@Override
			public void run() {
				final MessageType type = MessageTypeFactory.createKeepAliveMessageType();
				final BlaubotMessage keepAliveMessage = new BlaubotMessage();
				keepAliveMessage.setProtocolVersion(ProtocolManager.PROTOCOL_VERSION);
				keepAliveMessage.setMessageType(type);
				Channel channel = protocolManager.getChannelFactory().getAdminDeviceChannel(device);
				channel.post(keepAliveMessage);
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

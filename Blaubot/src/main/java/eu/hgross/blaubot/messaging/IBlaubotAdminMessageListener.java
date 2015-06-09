package eu.hgross.blaubot.messaging;

import eu.hgross.blaubot.admin.AbstractAdminMessage;

/**
 * Created by henna on 30.01.15.
 */
public interface IBlaubotAdminMessageListener {

    /**
     * Called when an admin message was received
     * @param adminMessage
     */
    public void onAdminMessage(AbstractAdminMessage adminMessage);
}

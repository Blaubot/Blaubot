package eu.hgross.blaubot.ui;

import eu.hgross.blaubot.core.Blaubot;

/**
 * A debug view is meant to show some internal mechanics of Blaubot to interested
 * developers. The Blaubot instance should be injected via registerBlaubotInstance(..)
 * and has to be detached via unregisterBlaubotInstance(..), if not needed anymore.
 * Note that the register and unregister methods are not designed to be thread safe.
 */
public interface IBlaubotDebugView {
    /**
     * Registers the blaubot instance to a view
     * @param blaubot the instance to be introspected
     */
    void registerBlaubotInstance(Blaubot blaubot);

    /**
     * Unregisters the associated blaubot instance from a view (if any)
     */
    void unregisterBlaubotInstance();
}

package eu.hgross.blaubot.ui;

import eu.hgross.blaubot.core.BlaubotKingdom;

/**
 * A debug view is meant to show some internal mechanics of BlaubotKingdom to interested
 * developers. The BlaubotKingdom instance should be injected via registerBlaubotKingdomInstance(..)
 * and has to be detached via unregisterBlaubotKingdomInstance(..), if not needed anymore.
 * Note that the register and unregister methods are not designed to be thread safe.
 */
public interface IBlaubotKingdomDebugView {
    /**
     * Registers the BlaubotKingdom instance to a view
     * @param BlaubotKingdom the instance to be introspected
     */
    void registerBlaubotKingdomInstance(BlaubotKingdom BlaubotKingdom);

    /**
     * Unregisters the associated blaubot instance from a view (if any)
     */
    void unregisterBlaubotKingdomInstance();
}

package eu.hgross.blaubot.core;

/**
 * A generic listener for thready things.
 */
public interface IActionListener {
    /**
     * Called when the thready thing finished/stopped/closed/started
     */
    void onFinished();
}

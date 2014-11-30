package de.hsrm.blaubot.protocol;

/**
 * listener for join and disjoint events. join means that the app joined a
 * network of other devices / apps. disjoint means that it left the network it
 * was previously connected to
 */
public interface JoinListener {

	/**
	 * gets called as soon as the app joined a new network
	 */
	public void onJoined();

	/**
	 * called as soon as the app left the previously joined network
	 */
	public void onDisjointed();

}

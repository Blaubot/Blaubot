package eu.hgross.blaubot.core;

import eu.hgross.blaubot.core.statemachine.ConnectionStateMachine;
import eu.hgross.blaubot.core.statemachine.states.FreeState;
import eu.hgross.blaubot.core.statemachine.states.KingState;
import eu.hgross.blaubot.core.statemachine.states.PeasantState;
import eu.hgross.blaubot.core.statemachine.states.PrinceState;
import eu.hgross.blaubot.admin.ACKPronouncePrinceAdminMessage;
import eu.hgross.blaubot.admin.PronouncePrinceAdminMessage;

/**
 * Configuration object specific to a given {@link IBlaubotAdapter} implementation.
 * Contains adapter specific constants.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class ConnectionStateMachineConfig {
	private int crowningPreparationTimeout = 850;
	private int kingWithoutPeasantsTimeout = 10000;
	private int kingdomMergeOldKingBowDownTimeout = 400;
	private int princeAckTimeout = 800;

	public ConnectionStateMachineConfig() {
	}
	
	public int getCrowningPreparationTimeout() {
		return crowningPreparationTimeout;
	}
	/**
	 * The preparation time for the prince to be crowned.
	 * == the time all peasants will wait before connecting to the
	 * prince, if the king connection gets lost.
	 * 
	 * Only relevant in {@link PeasantState} and {@link PrinceState}.
	 * @param crowningPreparationTimeout
	 */
	public void setCrowningPreparationTimeout(int crowningPreparationTimeout) {
		this.crowningPreparationTimeout = crowningPreparationTimeout;
	}

	public int getKingWithoutPeasantsTimeout() {
		return kingWithoutPeasantsTimeout;
	}
	
	/**
	 * The timeout after the {@link ConnectionStateMachine} will go to {@link FreeState},
	 * if no peasants are connected.
	 * 
	 * Only relevant in {@link KingState}
	 * @param kingWithoutPeasantsTimeout
	 */
	public void setKingWithoutPeasantsTimeout(int kingWithoutPeasantsTimeout) {
		this.kingWithoutPeasantsTimeout = kingWithoutPeasantsTimeout;
	}
	
	public static void validateTimeouts(ConnectionStateMachineConfig cfg, BlaubotAdapterConfig adapterConfig) {
		if(cfg.getCrowningPreparationTimeout() < adapterConfig.getKeepAliveInterval()) {
			throw new IllegalArgumentException("The crowningPreparationTimeout should be (significantly) greater than the keep-alive interval.");
		}
		if(cfg.getKingWithoutPeasantsTimeout() < cfg.getCrowningPreparationTimeout()) {
			throw new IllegalArgumentException("The kingWithoutPeasantsTimeout should be (significantly) greater than crowningPreparationTimeout.");
		}
	}

	public int getKingdomMergeOldKingBowDownTimeout() {
		return kingdomMergeOldKingBowDownTimeout;
	}

	/**
	 * This timeout is used when a king gets informed about another king and decides to bow his kingdom
	 * down to the other king.
	 * To do that, the king informs all peasants and the prince to join the new king.
	 * Then he waits kingdomMergeOldKingBowDownTimeout ms before he disconnects all remaining connections
	 * of his former peasants and prince.
	 * After that he joins the new king himself.
	 * 
	 * @param kingdomMergeOldKingBowDownTimeout the timeout to wait before the king cuts his peasant connections on a kingdom merger
	 */
	public void setKingdomMergeOldKingBowDownTimeout(int kingdomMergeOldKingBowDownTimeout) {
		this.kingdomMergeOldKingBowDownTimeout = kingdomMergeOldKingBowDownTimeout;
	}

	public int getPrinceAckTimeout() {
		return princeAckTimeout;
	}

	/**
	 * After the prince gets pronounced via a {@link PronouncePrinceAdminMessage}, the pronounced device has to
	 * respond within this timeouts time interval with an {@link ACKPronouncePrinceAdminMessage} to accept
	 * his new position. If he does not respond fast enough, the pronouncing process is retriggered.
	 * @param princeAckTimeout
	 */
	public void setPrinceAckTimeout(int princeAckTimeout) {
		this.princeAckTimeout = princeAckTimeout;
	}
	
	
}

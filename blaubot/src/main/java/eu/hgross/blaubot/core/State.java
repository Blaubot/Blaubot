package eu.hgross.blaubot.core;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.statemachine.events.AbstractBlaubotDeviceDiscoveryEvent;
import eu.hgross.blaubot.core.statemachine.events.DiscoveredFreeEvent;
import eu.hgross.blaubot.core.statemachine.events.DiscoveredKingEvent;
import eu.hgross.blaubot.core.statemachine.events.DiscoveredPeasantEvent;
import eu.hgross.blaubot.core.statemachine.events.DiscoveredPrinceEvent;
import eu.hgross.blaubot.core.statemachine.events.DiscoveredStoppedEvent;
import eu.hgross.blaubot.core.statemachine.states.FreeState;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.core.statemachine.states.KingState;
import eu.hgross.blaubot.core.statemachine.states.PeasantState;
import eu.hgross.blaubot.core.statemachine.states.PrinceState;
import eu.hgross.blaubot.core.statemachine.states.StoppedState;

/**
 * Enum constants wiring together some classes related to device states.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public enum State {
	Free (FreeState.class, DiscoveredFreeEvent.class),
	Peasant (PeasantState.class, DiscoveredPeasantEvent.class),
	Prince (PrinceState.class, DiscoveredPrinceEvent.class),
	King (KingState.class, DiscoveredKingEvent.class),
	Stopped (StoppedState.class, DiscoveredStoppedEvent.class);
	
	/**
	 * Maps all allowed state changes
	 */
	private static Map<State, Set<State>> ALLOWED_STATE_CHANGES = new HashMap<State, Set<State>>();
	static {
		for(State s : State.values()) {
			ALLOWED_STATE_CHANGES.put(s, new HashSet<State>());
		}
		ALLOWED_STATE_CHANGES.get(Free).addAll(Arrays.asList(Stopped, Peasant, King));
		ALLOWED_STATE_CHANGES.get(Peasant).addAll(Arrays.asList(Stopped, Peasant, Free, Prince));
		ALLOWED_STATE_CHANGES.get(Prince).addAll(Arrays.asList(Stopped, Peasant, Free, King));
		ALLOWED_STATE_CHANGES.get(King).addAll(Arrays.asList(Stopped, Free, Peasant));
		ALLOWED_STATE_CHANGES.get(Stopped).addAll(Arrays.asList(Stopped, Free));
	}
	
	private Class<? extends IBlaubotState> stateClass;
	private Class<? extends AbstractBlaubotDeviceDiscoveryEvent> discoveryEventClass;
	
	State(Class<? extends IBlaubotState> stateClass, Class<? extends AbstractBlaubotDeviceDiscoveryEvent> discoveryEventClass) {
		this.stateClass = stateClass;
		this.discoveryEventClass = discoveryEventClass;
	}

	public Class<? extends IBlaubotState> getStateClass() {
		return stateClass;
	}

	private Class<? extends AbstractBlaubotDeviceDiscoveryEvent> getDiscoveryEventClass() {
		return discoveryEventClass;
	}
	
	/**
	 * @param stateClass a class that is used by sthe state machine
	 * @return state enum or null, if no state is mapped to this stateClass
	 */
	public static State getStateByStatemachineClass(Class<? extends IBlaubotState> stateClass) {
		for(State s : State.values()) {
			if (s.stateClass == stateClass)
				return s;
		}
		return null;
	}
	
	/**
	 * Creates the appropriate discovery event from the given State 
	 * @param device the device associated with this state
     * @param connectionMetaDataList the meta data for the acceptors of `device`
	 * @return
	 */
	public AbstractBlaubotDeviceDiscoveryEvent createDiscoveryEventForDevice (IBlaubotDevice device, List<ConnectionMetaDataDTO> connectionMetaDataList) {
		Constructor<? extends AbstractBlaubotDeviceDiscoveryEvent> constructor;
		try {
			constructor = getDiscoveryEventClass().getConstructor(IBlaubotDevice.class, List.class);
			AbstractBlaubotDeviceDiscoveryEvent event = constructor.newInstance(device, connectionMetaDataList);
			return event;
		} catch(Exception e) {
			throw new RuntimeException("Could not create AbstractBlaubotDeviceDiscoveryEvent from State " + this, e);
		}
	}
	
	/**
	 * @param toState
	 * @return true iff a state change from 'this' state to 'toState' is allowed.
	 */
	public boolean isStateChangeAllowed(State toState) {
		return ALLOWED_STATE_CHANGES.get(this).contains(toState);
	}
}
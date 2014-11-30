package de.hsrm.blaubot.junit;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolActivateEvent;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolDeactivateEvent;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolEvent;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolEvent.EventType;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolEventDispatcher;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolEventListener;
import de.hsrm.blaubot.protocol.eventdispatcher.ProtocolSetMasterEvent;

public class ProtocolEventDispatcherTest {

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test(timeout = 1000)
	public void test() throws InterruptedException {
		LinkedBlockingQueue<ProtocolEvent> eventQueue = new LinkedBlockingQueue<ProtocolEvent>();
		ProtocolEventDispatcher eventDispatcher = new ProtocolEventDispatcher(eventQueue);
		final ArrayList<ProtocolEvent> originalEvents = new ArrayList<ProtocolEvent>();
		originalEvents.add(new ProtocolSetMasterEvent(true));
		originalEvents.add(new ProtocolActivateEvent());
		originalEvents.add(new ProtocolDeactivateEvent());
		final ArrayList<ProtocolEvent> incomingEvents = new ArrayList<ProtocolEvent>();
		final CountDownLatch latch = new CountDownLatch(originalEvents.size());

		ProtocolEventListener listener = new ProtocolEventListener() {

			@Override
			public void onProtocolEvent(ProtocolEvent event) {
				incomingEvents.add(event);
				latch.countDown();
			}
		};

		eventDispatcher.addListener(listener);
		eventDispatcher.activate();

		for (ProtocolEvent event : originalEvents) {
			eventQueue.add(event);
		}

		latch.await();

		for (int i = 0; i < originalEvents.size(); i++) {
			ProtocolEvent original = originalEvents.get(i);
			ProtocolEvent incoming = incomingEvents.get(i);
			EventType originalEventType = original.getEventType();
			EventType incomingEventType = incoming.getEventType();
			assertEquals(originalEventType, incomingEventType);
			if (originalEventType == EventType.SET_MASTER) {
				boolean originalIsMaster = ((ProtocolSetMasterEvent) original).isMaster();
				boolean incomingIsMaster = ((ProtocolSetMasterEvent) incoming).isMaster();
				assertEquals(originalIsMaster, incomingIsMaster);
			}
		}
	}

}

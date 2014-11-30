package de.hsrm.blaubot.junit;

import static org.junit.Assert.*;

import java.util.concurrent.PriorityBlockingQueue;

import org.junit.Test;

import de.hsrm.blaubot.message.BlaubotMessage;
import de.hsrm.blaubot.message.BlaubotMessagePriorityComparator;

public class BlaubotMessagePriorityComparatorTest {

	/**
	 * tests the correct ordering of the priority comparator class in priority
	 * blocking queue
	 */
	@Test
	public void test() {
		final PriorityBlockingQueue<BlaubotMessage> queue = new PriorityBlockingQueue<BlaubotMessage>(5, new BlaubotMessagePriorityComparator());

		BlaubotMessage m1 = new BlaubotMessage();
		final int priority1 = -1;
		m1.setPriority(priority1);
		BlaubotMessage m2 = new BlaubotMessage();
		final int priority2 = 2;
		m2.setPriority(priority2);
		BlaubotMessage m3 = new BlaubotMessage();
		final int priority3 = -3;
		m3.setPriority(priority3);
		BlaubotMessage m4 = new BlaubotMessage();
		final int priority4 = 4;
		m4.setPriority(priority4);
		BlaubotMessage m5 = new BlaubotMessage();
		final int priority5 = -5;
		m5.setPriority(priority5);

		queue.add(m1);
		queue.add(m2);
		queue.add(m3);
		queue.add(m4);
		queue.add(m5);

		BlaubotMessage poll1 = queue.poll();
		BlaubotMessage poll2 = queue.poll();
		BlaubotMessage poll3 = queue.poll();
		BlaubotMessage poll4 = queue.poll();
		BlaubotMessage poll5 = queue.poll();

		assertEquals(priority5, poll1.getPriority());
		assertEquals(priority3, poll2.getPriority());
		assertEquals(priority1, poll3.getPriority());
		assertEquals(priority2, poll4.getPriority());
		assertEquals(priority4, poll5.getPriority());
	}

}

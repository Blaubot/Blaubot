package de.hsrm.blaubot.message;

import java.util.Comparator;

/**
 * comparator for comparing two {@link BlaubotMessage}s by their priority. uses
 * natural ordering of numbers
 * 
 * @author manuelpras
 * 
 */
public class BlaubotMessagePriorityComparator implements Comparator<BlaubotMessage> {

	@Override
	public int compare(BlaubotMessage o1, BlaubotMessage o2) {
		Integer priority1 = new Integer(o1.getPriority());
		Integer priority2 = new Integer(o2.getPriority());
		return Integer.compare(priority1, priority2);
	}

}

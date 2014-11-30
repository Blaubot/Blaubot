package de.hsrm.blaubot.junit;

import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import de.hsrm.blaubot.core.Blaubot;
import de.hsrm.blaubot.junit.BlaubotJunitHelper.EthernetBeaconType;

/**
 * Creates a Blaubot Kingdom and runs indefinitely.
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class CreateKingdomMain {
	private static final int STARTING_PORT_FOR_BLAUBOT_INSTANCES = 19171;
	private static final int NUMBER_OF_BLAUBOT_INSTANCES = 10;

	public static void main(String[] args) throws UnknownHostException {
		UUID appUUid = UUID.fromString("a2d00ba0-f920-11e3-a3ac-0800200c9a66");
		HashSet<String> uniqueDeviceIdStrings = BlaubotJunitHelper.createEthernetUniqueDeviceIdStringsFromFirstLocalIpAddress(NUMBER_OF_BLAUBOT_INSTANCES, STARTING_PORT_FOR_BLAUBOT_INSTANCES);
		List<Blaubot> instances = BlaubotJunitHelper.setUpEthernetBlaubotInstancesFromUniqueIdSet(uniqueDeviceIdStrings, appUUid, EthernetBeaconType.FIXED_DEVICE_SET);
		
		BlaubotJunitHelper.blockUntilWeHaveOneKingdom(instances, 60000);
		System.out.println("We have a kingdom.");
		
	}
}

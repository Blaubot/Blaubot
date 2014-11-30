package de.hsrm.blaubot.junit;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({ AdminMessagesTest.class, 
	BeaconMessagesTest.class,
	BitManipTest.class,
	ByteBufferTest.class,
	MessageTypesTest.class,
	MockObjectsTest.class,
	ProtocolHandshakeTest.class,
	ProtocolTest.class,
	FingerTrackingTest.class,
	EthernetBlaubotWithFixedDeviceSetTest.class,
	StatisticsUtilTest.class,
	ProtocolContextTest.class,
	BlaubotMessagePriorityComparatorTest.class
	})
public class AllSuite {

}

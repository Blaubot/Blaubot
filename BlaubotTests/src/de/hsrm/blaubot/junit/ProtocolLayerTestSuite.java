package de.hsrm.blaubot.junit;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({ BitManipTest.class, BlaubotMessagePriorityComparatorTest.class, BlaubotMessageTest.class, ByteBufferTest.class, ChannelFactoryTest.class, MessageTypesTest.class, MockObjectsTest.class, ProtocolContextTest.class, ProtocolEventDispatcherTest.class, ProtocolHandshakeTest.class,
		ProtocolTestHelperTest.class, ProtocolTest.class })
public class ProtocolLayerTestSuite {

}

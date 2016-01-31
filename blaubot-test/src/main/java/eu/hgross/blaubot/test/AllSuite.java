package eu.hgross.blaubot.test;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.junit.runners.Suite.SuiteClasses;

@RunWith(Suite.class)
@SuiteClasses({
        AdminMessagesTest.class,
        BeaconAndAcceptorsTest.class,
	    BeaconMessagesTest.class,
	    BitManipTest.class,
        ChannelManagerTest.class,
        EthernetBlaubotWithFixedDeviceSetTest.class,
        LifecycleListenerTest.class,
        MessageSenderAndReceiverTest.class,
        MockObjectsTest.class,
        BlaubotFactoryTest.class
	})
public class AllSuite {

}

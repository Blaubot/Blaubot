package de.hsrm.blaubot.core;

import java.nio.ByteOrder;
import java.nio.charset.Charset;

/**
 * Blaubot wide constants.
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 *
 */
public class BlaubotConstants {
	/**
	 * Charset used for Strings
	 */
	public static final Charset STRING_CHARSET = Charset.forName("UTF-8");
	public static final ByteOrder BYTE_ORDER = ByteOrder.BIG_ENDIAN;
}

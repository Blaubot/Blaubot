package eu.hgross.blaubot.util;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A log wrapper for blaubot
 * TODO mpras: use slf4j for build AND check out how to configure it on android 
 * 
 * @author Henning Gross <mail.to@henning-gross.de>
 * 
 */
public class Log {
	public enum LogLevel {
		NONE,
		ERRORS,
		WARNINGS,
		DEBUG,
		INFOS
	}
	private static final Class<?> androidLog;
	private static Logger logger = Logger.getLogger("Blaubot Library");
	
	public static LogLevel LOG_LEVEL;
	
	static {
		LOG_LEVEL = LogLevel.ERRORS;
//		LOG_LEVEL = LogLevel.WARNINGS;
		Package androidUtil = Package.getPackage("android.util");
		Class<?> aLog = null;
		if(androidUtil != null) {
			try {
				aLog = Class.forName("android.util.Log");
			} catch (ClassNotFoundException e) {
				aLog = null;
			}
		} 
		if (aLog == null) {
			FileInputStream fis = null;
			try {
				fis = new FileInputStream("mylogging.properties");
				java.util.logging.LogManager.getLogManager().readConfiguration(fis);
			} catch (SecurityException e1) {
				//e1.printStackTrace();
			} catch (FileNotFoundException e1) {
				//e1.printStackTrace();
			} catch (IOException e1) {
				//e1.printStackTrace();
			} finally {
				try {
					if (fis != null) {
						fis.close();
					}
				} catch (Exception ignore) {					
				}
			}
		}
		androidLog = aLog;
	}

	/**
	 * @return true if warning messages should be printed
	 */
	public static boolean logWarningMessages() {
		return LOG_LEVEL.ordinal() >= LogLevel.WARNINGS.ordinal();
	}

	/**
	 * @return true if error messages should be printed
	 */
	public static boolean logErrorMessages() {
		return LOG_LEVEL.ordinal() >= LogLevel.ERRORS.ordinal();
	}
	
	/**
	 * @return true if info messages should be printed
	 */
	public static boolean logInfoMessages() {
		return LOG_LEVEL.ordinal() >= LogLevel.INFOS.ordinal();
	}
	
	/**
	 * @return true if debug messages should be printed
	 */
	public static boolean logDebugMessages() {
        return LOG_LEVEL.ordinal() >= LogLevel.DEBUG.ordinal();
	}
	
	/**
	 * small number (hopefully unique) representing threadId
	 * @return long, approximation of threadId 
	 */
	private static int getThreadId() {
		long threadId = Thread.currentThread().getId();
		return ((int) threadId % 1000);
	}
	private static String getSThreadId() {
			return String.format(" {%03d} ", getThreadId());
	}
	private final static boolean SHOW_THREADID = true;
	private static String formatTag(String tag) {
		String parts[] = tag.split("\\.");
		return String.format("[%30s] ", parts[parts.length-1]);
	}
	private static String formatLog(String message, String tag) {
		if (SHOW_THREADID) {
			return getSThreadId() + formatTag(tag) + message;
		} else {
			return formatTag(tag) + message;			
		}
	}
	private static String formatMessage(String message) {
		if (SHOW_THREADID) {
			return getSThreadId() + message;
		} else {
			return message;
		}
	}
	
	public static void d(String tag, String message) {
		try {
			if(androidLog != null) {
				Method m = androidLog.getMethod("d", String.class, String.class);
				m.invoke(androidLog, tag, formatMessage(message));
			} else {
				logger.log(Level.INFO, formatLog(message, tag));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void d(String tag, String message, Throwable t) {
		try {
			if(androidLog != null) {
				Method m = androidLog.getMethod("d", String.class, String.class, Throwable.class);
				m.invoke(androidLog, tag, formatMessage(message), t);
			} else {
				logger.log(Level.INFO, formatLog(message, tag));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void w(String tag, String message) {
		try {
			if(androidLog != null) {
				Method m = androidLog.getMethod("w", String.class, String.class);
				m.invoke(androidLog, tag, formatMessage(message));
			} else {
				logger.log(Level.WARNING, formatLog(message, tag));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void w(String tag, String message, Throwable t) {
		try {
			if(androidLog != null) {
				Method m = androidLog.getMethod("w", String.class, String.class, Throwable.class);
				m.invoke(androidLog, tag, formatMessage(message), t);
			} else {
				logger.log(Level.WARNING, formatLog(message, tag));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void w(String tag, Throwable t) {
		try {
			if(androidLog != null) {
				Method m = androidLog.getMethod("w", String.class, Throwable.class);
				m.invoke(androidLog, tag, t);
			} else {
				logger.log(Level.WARNING, formatLog(" ", tag));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void e(String tag, String message) {
		try {
			if(androidLog != null) {
				Method m = androidLog.getMethod("e", String.class, String.class);
				m.invoke(androidLog, tag, formatMessage(message));
			} else {
				logger.log(Level.SEVERE, formatLog(message, tag));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void e(String tag, String message, Throwable t) {
		try {
			if(androidLog != null) {
				Method m = androidLog.getMethod("e", String.class, String.class, Throwable.class);
				m.invoke(androidLog, tag, formatMessage(message), t);
			} else {
				logger.log(Level.SEVERE, formatLog(message, tag));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}


	public static void wtf(String tag, String message) {
		try {
			if(androidLog != null) {
				Method m = androidLog.getMethod("wtf", String.class, String.class);
				m.invoke(androidLog, tag, formatMessage(message));
			} else {
				logger.log(Level.SEVERE, formatLog(message, tag));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void wtf(String tag, String message, Throwable t) {
		try {
			if(androidLog != null) {
				Method m = androidLog.getMethod("wtf", String.class, String.class, Throwable.class);
				m.invoke(androidLog, tag, formatMessage(message), t);
			} else {
				logger.log(Level.SEVERE, formatLog(message, tag));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void wtf(String tag, Throwable t) {
		try {
			if(androidLog != null) {
				Method m = androidLog.getMethod("wtf", String.class, Throwable.class);
				m.invoke(androidLog, tag, t);
			} else {
				logger.log(Level.SEVERE, formatLog(" ", tag));
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public static String getStackTraceString(Throwable tr) {
		try {
			if(androidLog != null) {
				Method m = androidLog.getMethod("getStackTraceString", Throwable.class);
				return (String) m.invoke(androidLog, tr);
			} else {
				StringBuilder sb = new StringBuilder();
				for (StackTraceElement ste : tr.getStackTrace()) {
					sb.append(ste.toString() + "\n");
				}
				return sb.toString();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return "";
	}

}

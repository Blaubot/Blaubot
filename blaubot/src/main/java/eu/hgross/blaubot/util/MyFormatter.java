package eu.hgross.blaubot.util;

import java.util.Date;
import java.util.logging.Formatter;
import java.util.logging.LogRecord;

public class MyFormatter extends Formatter {
	 
    @Override
    public String format(LogRecord record) {
        return "Thread#"+record.getThreadID()+"::"
                +new Date(record.getMillis())+"::"
                +record.getMessage()+"\n";
    }
 
}
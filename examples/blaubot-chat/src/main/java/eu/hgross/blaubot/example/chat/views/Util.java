package eu.hgross.blaubot.example.chat.views;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import eu.hgross.blaubot.example.chat.R;

/**
 */
public class Util {
    private static final DateTimeFormatter FORMATTER = DateTimeFormat.forPattern("HH:mm:ss");

    /**
     * Creates a generic message view to show a simple text and a timestamp
     *
     * @param context   the context
     * @param timestamp the timestamp in milliseconds
     * @param text      the text to view
     * @return the view
     */
    public static View createGenericChatMessageView(Context context, long timestamp, String text) {
        LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View root = inflater.inflate(R.layout.generic_message, null);
        TextView textView = (TextView) root.findViewById(R.id.generic_message_text);
        TextView timestampView = (TextView) root.findViewById(R.id.generic_message_timestamp);
        textView.setText(text);
        timestampView.setText(FORMATTER.print(timestamp));
        return root;
    }
}

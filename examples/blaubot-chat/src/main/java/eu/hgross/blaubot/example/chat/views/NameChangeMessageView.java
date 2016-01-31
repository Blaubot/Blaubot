package eu.hgross.blaubot.example.chat.views;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;

import eu.hgross.blaubot.example.chat.R;
import eu.hgross.blaubot.example.chat.messages.ChatMessage;
import eu.hgross.blaubot.example.chat.messages.NameChangeMessage;

/**
 * Visualizes a NameChangeMessage
 */
public class NameChangeMessageView extends FrameLayout {
    private Handler mUiHandler;
    private View mMainView;
    private DateTimeFormatter mDateTimeFormatter;

    public NameChangeMessageView(Context context) {
        super(context);
        initView();
    }

    public NameChangeMessageView(Context context, NameChangeMessage msg) {
        super(context);
        initView();
        setNameChangeMessage(msg);
    }

    private void initView() {
        mMainView = inflate(getContext(), R.layout.name_change_message, null);
        addView(mMainView);
        mUiHandler = new Handler(Looper.getMainLooper());
        mDateTimeFormatter = DateTimeFormat.forPattern("HH:mm:ss");
    }

    /**
     * Sets the message to be displayed
     *
     * @param nameChangeMessage the message to be visualized
     */
    public void setNameChangeMessage(final NameChangeMessage nameChangeMessage) {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                TextView timestamp = (TextView) mMainView.findViewById(R.id.name_change_message_timestamp);
                TextView oldName = (TextView) mMainView.findViewById(R.id.name_change_message_oldName);
                TextView newName = (TextView) mMainView.findViewById(R.id.name_change_message_newName);
                DateTime dateTime = new DateTime(nameChangeMessage.getSendTimestamp());
                timestamp.setText(mDateTimeFormatter.print(dateTime));
                oldName.setText(nameChangeMessage.getPreviousName());
                newName.setText(nameChangeMessage.getNewName());
            }
        });
    }
}

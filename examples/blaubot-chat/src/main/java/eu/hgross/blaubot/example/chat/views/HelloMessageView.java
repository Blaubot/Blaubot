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
import eu.hgross.blaubot.example.chat.messages.HelloMessage;

/**
 * Visualizes a hello message
 */
public class HelloMessageView extends FrameLayout {
    private Handler mUiHandler;
    private View mMainView;
    private DateTimeFormatter mDateTimeFormatter;

    public HelloMessageView(Context context) {
        super(context);
        initView();
    }

    public HelloMessageView(Context context, HelloMessage msg) {
        super(context);
        initView();
        setHelloMessage(msg);
    }

    private void initView() {
        mMainView = inflate(getContext(), R.layout.generic_message, null);
        addView(mMainView);
        mUiHandler = new Handler(Looper.getMainLooper());
        mDateTimeFormatter = DateTimeFormat.forPattern("HH:mm:ss");
    }

    /**
     * Sets the message to be displayed
     *
     * @param helloMessage the message to be visualized
     */
    public void setHelloMessage(final HelloMessage helloMessage) {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                TextView timestamp = (TextView) mMainView.findViewById(R.id.generic_message_timestamp);
                TextView text = (TextView) mMainView.findViewById(R.id.generic_message_text);
                DateTime dateTime = new DateTime(helloMessage.getSendTimestamp());
                timestamp.setText(mDateTimeFormatter.print(dateTime));
                text.setText(helloMessage.getOriginator().getUserName() + " " + getContext().getResources().getString(R.string.joined_the_chatroom));
            }
        });
    }
}

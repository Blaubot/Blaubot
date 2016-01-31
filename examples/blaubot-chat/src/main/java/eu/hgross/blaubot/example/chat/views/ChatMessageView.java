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

/**
 * Visualizes a ChatMessage
 */
public class ChatMessageView extends FrameLayout {
    private Handler mUiHandler;
    private View mMainView;
    private ChatMessage mChatMessage;
    private DateTimeFormatter mDateTimeFormatter;

    public ChatMessageView(Context context) {
        super(context);
        initView();
    }

    public ChatMessageView(Context context, ChatMessage chatMessage) {
        super(context);
        initView();
        setChatMessage(chatMessage);
    }

    private void initView() {
        mMainView = inflate(getContext(), R.layout.chat_message, null);
        addView(mMainView);
        mUiHandler = new Handler(Looper.getMainLooper());
        mDateTimeFormatter = DateTimeFormat.forPattern("HH:mm:ss");
    }

    /**
     * Sets the message to be displayed
     *
     * @param chatMessage the message to be visualized
     */
    public void setChatMessage(final ChatMessage chatMessage) {
        mChatMessage = chatMessage;
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                TextView timestamp = (TextView) mMainView.findViewById(R.id.chat_msg_timestamp);
                TextView originatorName = (TextView) mMainView.findViewById(R.id.chat_msg_originatorName);
                TextView message = (TextView) mMainView.findViewById(R.id.chat_msg_message);
                DateTime dateTime = new DateTime(chatMessage.getSendTimestamp());
                timestamp.setText(mDateTimeFormatter.print(dateTime));
                originatorName.setText(chatMessage.getOriginator().getUserName());
                message.setText(chatMessage.getMessage());
            }
        });
    }
}

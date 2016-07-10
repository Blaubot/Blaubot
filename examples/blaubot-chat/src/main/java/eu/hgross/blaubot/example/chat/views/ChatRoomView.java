package eu.hgross.blaubot.example.chat.views;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.core.ILifecycleListener;
import eu.hgross.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import eu.hgross.blaubot.core.statemachine.states.FreeState;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.example.chat.messages.ChatUser;
import eu.hgross.blaubot.example.chat.R;
import eu.hgross.blaubot.example.chat.messages.ChatMessage;
import eu.hgross.blaubot.example.chat.messages.HelloMessage;
import eu.hgross.blaubot.example.chat.messages.NameChangeMessage;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;

/**
 * A Chat view displaying ChatMessages send through a channel.
 * Utilizes channels for different purposes like
 * <ul>
 * <li>HelloMessages, that are send when a device enters a ChatRoom</li>
 * <li>ChatMessages, that are the actual chat messages</li>
 * <li>NameChangeMessages, which announce a nickname change</li>
 * </ul>
 * 
 * Additionally listens to Blaubot's lifecycle to get to know when devices are
 * not part of the blaubot network anymore.
 */
public class ChatRoomView extends ScrollView implements ILifecycleListener, IBlaubotConnectionStateMachineListener {
    /**
     * Max number of chat messages to be displayed. Older ones will be discarded.
     */
    private static final int DEFAULT_MAX_MESSAGES = 150;
    private IBlaubotChannel mMessageChannel;
    private IBlaubotChannel mNameChangeChannel;
    private IBlaubotChannel mHelloMessageChannel;

    private int maxMessages = DEFAULT_MAX_MESSAGES;
    private Handler mUiHandler;
    private List<View> mMessageViews;
    /**
     * Maps unique device ids to nicknames.
     */
    private ConcurrentHashMap<String, String> mDeviceToNicknameMapping;
    private Blaubot mBlaubot;
    private ChatUser mChatUser;
    private LinearLayout mMainView;

    public ChatRoomView(Context context, AttributeSet attributeSet) {
        super(context, attributeSet);
        initView(context, attributeSet);
    }

    private void initView(Context context, AttributeSet attributeSet) {
        mMessageViews = new LinkedList<>();
        mUiHandler = new Handler(Looper.getMainLooper());
        mDeviceToNicknameMapping = new ConcurrentHashMap<>();
//        setOrientation(VERTICAL);
        mMainView = new LinearLayout(context, attributeSet);
        mMainView.setOrientation(LinearLayout.VERTICAL);
        addView(mMainView);
    }


    /**
     * Adds views to the main panel and maintains the max number of views defined
     *
     * @param v the view to add
     */
    private void addViewToChatRoom(View v) {
        mMessageViews.add(v);
        mMainView.addView(v);
        if (mMessageViews.size() > maxMessages) {
            // remove head
            View removedView = mMessageViews.remove(0);
            mMainView.removeView(removedView);
        }
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                // scroll down
                fullScroll(View.FOCUS_DOWN);
            }
        });
    }

    /**
     * Updates our udid to nickname mapping
     *
     * @param deviceUuid the udid
     * @param nickName   the nickname
     */
    private void maintainUniqueDeviceIdToNicknameMapping(String deviceUuid, String nickName) {
        mDeviceToNicknameMapping.put(deviceUuid, nickName);
    }

    /**
     * Handles incoming chat messages
     */
    private IBlaubotMessageListener mMessageListener = new IBlaubotMessageListener() {
        @Override
        public void onMessage(BlaubotMessage blaubotMessage) {
            final ChatMessage chatMessage = ChatMessage.fromBytes(blaubotMessage.getPayload());
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    ChatMessageView chatMessageView = new ChatMessageView(getContext(), chatMessage);
                    addViewToChatRoom(chatMessageView);

                    ChatUser originator = chatMessage.getOriginator();
                    maintainUniqueDeviceIdToNicknameMapping(originator.getDeviceUuid(), originator.getUserName());
                }
            });
        }
    };


    /**
     * Handles incoming NameChangeMessages
     */
    private IBlaubotMessageListener mNameChangeMessageListener = new IBlaubotMessageListener() {
        @Override
        public void onMessage(BlaubotMessage blaubotMessage) {
            final NameChangeMessage nameChangeMessage = NameChangeMessage.fromBytes(blaubotMessage.getPayload());
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    NameChangeMessageView view = new NameChangeMessageView(getContext(), nameChangeMessage);
                    addViewToChatRoom(view);

                    maintainUniqueDeviceIdToNicknameMapping(nameChangeMessage.getDeviceUuid(), nameChangeMessage.getNewName());
                }
            });
        }
    };

    /**
     * Handles incoming hello messages
     */
    private IBlaubotMessageListener mHelloMessageMessageListener = new IBlaubotMessageListener() {
        @Override
        public void onMessage(BlaubotMessage blaubotMessage) {
            final HelloMessage helloMessage = HelloMessage.fromBytes(blaubotMessage.getPayload());
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    HelloMessageView helloMessageView = new HelloMessageView(getContext(), helloMessage);
                    addViewToChatRoom(helloMessageView);

                    maintainUniqueDeviceIdToNicknameMapping(helloMessage.getOriginator().getDeviceUuid(), helloMessage.getOriginator().getUserName());
                }
            });
        }
    };


    /**
     * Registers the channel on which the messages are received
     *
     * @param blaubotChannel the channel to register to
     */
    private void registerMessageChannel(IBlaubotChannel blaubotChannel) {
        mMessageChannel = blaubotChannel;
        mMessageChannel.subscribe(mMessageListener);
    }

    /**
     * Registers the channel on which name changes are received
     *
     * @param blaubotChannel the channel to register to
     */
    private void registerNameChangeChannel(IBlaubotChannel blaubotChannel) {
        mNameChangeChannel = blaubotChannel;
        mNameChangeChannel.subscribe(mNameChangeMessageListener);
    }

    /**
     * Registers the channel on which new users say hello on entry
     *
     * @param blaubotChannel the channel to register to
     */
    private void registerHelloMessageChannel(IBlaubotChannel blaubotChannel) {
        mHelloMessageChannel = blaubotChannel;
        mHelloMessageChannel.subscribe(mHelloMessageMessageListener);
    }

    /**
     * Unregisters from all channels, if any channel is regsitered.
     */
    private void unregisterChannels() {
        if (mMessageChannel != null) {
            mMessageChannel.removeMessageListener(mMessageListener);
        }
        if (mNameChangeChannel != null) {
            mNameChangeChannel.removeMessageListener(mNameChangeMessageListener);
        }
        if (mHelloMessageChannel != null) {
            mHelloMessageChannel.removeMessageListener(mHelloMessageMessageListener);
        }
    }

    /**
     * Registers this instance with blaubot
     *
     * @param blaubot             the blaubot instance
     * @param messageChannelId    the channel id to use for sending and receiving chat messages
     * @param helloChannelId      the channel id to use for sending and receiving hello announcements
     * @param nameChangeChannelId the channel id to use for sending and receiving name changes
     */
    public void registerBlaubot(Blaubot blaubot, short messageChannelId, short helloChannelId, short nameChangeChannelId) {
        if (mBlaubot != null) {
            unregisterBlaubot();
        }

        mBlaubot = blaubot;
        mBlaubot.addLifecycleListener(this);
        mBlaubot.getConnectionStateMachine().addConnectionStateMachineListener(this);
        mChatUser = new ChatUser();
        mChatUser.setDeviceUuid(mBlaubot.getOwnDevice().getUniqueDeviceID());
        mChatUser.setUserName(android.os.Build.MODEL + "");

        // Create a channel for each of our messages
        mMessageChannel = mBlaubot.createChannel(messageChannelId);
        mNameChangeChannel = mBlaubot.createChannel(nameChangeChannelId);
        mHelloMessageChannel = mBlaubot.createChannel(helloChannelId);

        // register them with the chatroom
        registerMessageChannel(mMessageChannel);
        registerNameChangeChannel(mNameChangeChannel);
        registerHelloMessageChannel(mHelloMessageChannel);
    }

    /**
     * Unregister this instance from blaubot
     */
    public void unregisterBlaubot() {
        unregisterChannels();
        if (mBlaubot != null) {
            mBlaubot.removeLifecycleListener(this);
            mBlaubot.getConnectionStateMachine().removeConnectionStateMachineListener(this);
            mBlaubot = null;
        }
    }


    @Override
    public void onConnected() {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                // add left message
                String message = getResources().getString(R.string.connected_to_chatroom);
                View view = Util.createGenericChatMessageView(getContext(), System.currentTimeMillis(), message);
                addViewToChatRoom(view);
            }
        });
        sendHello();
    }

    private void sendHello() {
        // say hello
        HelloMessage helloMessage = new HelloMessage();
        helloMessage.setOriginator(mChatUser);
        mHelloMessageChannel.publish(helloMessage.toBytes());
    }

    @Override
    public void onDisconnected() {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                // add left message
                String message = getResources().getString(R.string.disconnected_from_chatroom);
                View view = Util.createGenericChatMessageView(getContext(), System.currentTimeMillis(), message);
                addViewToChatRoom(view);
            }
        });
    }

    @Override
    public void onDeviceJoined(IBlaubotDevice blaubotDevice) {
        // handled by hello messages
    }

    @Override
    public void onDeviceLeft(IBlaubotDevice blaubotDevice) {
        final String nickname = resolveNickname(blaubotDevice.getUniqueDeviceID());
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                // add left message
                String message = nickname + " " + getResources().getString(R.string.left_chatroom_message);
                View view = Util.createGenericChatMessageView(getContext(), System.currentTimeMillis(), message);
                addViewToChatRoom(view);
            }
        });
    }

    @Override
    public void onPrinceDeviceChanged(IBlaubotDevice oldPrince, IBlaubotDevice newPrince) {

    }

    @Override
    public void onKingDeviceChanged(IBlaubotDevice oldKing, IBlaubotDevice newKing) {
        if (oldKing == null) {
            // if we were not part of a network before, we did not merge but connected initially
            return;
        }
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                // add left message
                String message = getResources().getString(R.string.merged_with_nearby_chatroom);
                View view = Util.createGenericChatMessageView(getContext(), System.currentTimeMillis(), message);
                addViewToChatRoom(view);
            }
        });
        sendHello();
    }

    @Override
    public void onStateChanged(IBlaubotState oldState, IBlaubotState newState) {
        if (newState instanceof FreeState) {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    // add left message
                    String message = getResources().getString(R.string.searching_chatroom);
                    View view = Util.createGenericChatMessageView(getContext(), System.currentTimeMillis(), message);
                    addViewToChatRoom(view);
                }
            });
            mMessageChannel.clearMessageQueue();
            mHelloMessageChannel.clearMessageQueue();
            mNameChangeChannel.clearMessageQueue();
        }
    }

    @Override
    public void onStateMachineStopped() {
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                // add left message
                String message = getResources().getString(R.string.stopped);
                View view = Util.createGenericChatMessageView(getContext(), System.currentTimeMillis(), message);
                addViewToChatRoom(view);
            }
        });
    }

    @Override
    public void onStateMachineStarted() {

    }


    /**
     * Tries to resolve the nickname for a unique device id
     *
     * @param uniqueDeviceId the unique device id
     * @return the nickanme or the unique device id
     */
    private String resolveNickname(String uniqueDeviceId) {
        // try to retrieve nickname
        String nickname = mDeviceToNicknameMapping.get(uniqueDeviceId);
        final String fnickname = nickname != null && !nickname.isEmpty() ? nickname : uniqueDeviceId;
        return fnickname;
    }

    /**
     * Creates and shows the dialog to change the nickname.
     */
    public void showChangeNicknameDialog() {
        View editView = inflate(getContext(), R.layout.change_username_editview, null);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle(R.string.change_nickname_dialog_title).setView(editView);
        final AlertDialog dialog = builder.create();

        final Button doChangeButton = (Button) editView.findViewById(R.id.doChangeButton);
        final Button cancelButton = (Button) editView.findViewById(R.id.doCancelButton);
        final EditText nickNameEditText = (EditText) editView.findViewById(R.id.username);
        nickNameEditText.setText(mChatUser.getUserName());

        nickNameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                boolean valid = validateUsername(s.toString());
                if (!valid) {
                    nickNameEditText.setError(getResources().getString(R.string.invalid_username));
                } else {
                    nickNameEditText.setError(null);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {

            }

        });

        doChangeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String newUsername = nickNameEditText.getText().toString();
                // validate username
                if (!validateUsername(newUsername)) {
                    return;
                }
                // preserver old name
                String oldName = mChatUser.getUserName();
                // set username
                mChatUser.setUserName(newUsername);

                // inform others
                NameChangeMessage nameChangeMessage = new NameChangeMessage();
                nameChangeMessage.setDeviceUuid(mBlaubot.getOwnDevice().getUniqueDeviceID());
                nameChangeMessage.setPreviousName(oldName);
                nameChangeMessage.setNewName(newUsername);
                mNameChangeChannel.publish(nameChangeMessage.toBytes());

                // close dialog
                dialog.dismiss();
            }
        });

        cancelButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dialog.dismiss();
            }
        });

        dialog.show();
    }


    /**
     * Validates a username to be non empty
     *
     * @param username the username to check
     * @return true, if valid username
     */
    private boolean validateUsername(String username) {
        return username != null && !username.isEmpty();
    }

    /**
     * Sends a chat message
     *
     * @param text the text
     * @return true, iff message was queued to send
     */
    public boolean sendChatMessage(String text) {
        ChatMessage message = new ChatMessage();
        message.setOriginator(mChatUser);
        message.setMessage(text);

        boolean queued = mMessageChannel.publish(message.toBytes());
        return queued;
    }

}

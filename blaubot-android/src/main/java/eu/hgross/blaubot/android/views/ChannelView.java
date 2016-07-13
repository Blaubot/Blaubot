package eu.hgross.blaubot.android.views;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.TypedArray;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.Html;
import android.text.Spanned;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.ToggleButton;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.messaging.BlaubotChannel;
import eu.hgross.blaubot.messaging.BlaubotChannelConfig;
import eu.hgross.blaubot.messaging.ChannelInfo;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.util.Log;

/**
 * Represents a Channel that has to be injected via setChannel(ChannelInfo)
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class ChannelView extends FrameLayout {
    private static final String LOG_TAG = "ChannelView";
    private Handler mUiHandler;
    private View mMainView;
    private ChannelInfo mChannelInfo;
    private ToggleButton mSubscribeButton;
    private Button mEditChannelConfigButton;
    private boolean mShowEditButton = true;
    private Button mShowSubscriberButton;

    public ChannelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    public ChannelView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attrs) {
        if (attrs != null) {
            TypedArray a = context.getTheme().obtainStyledAttributes(
                    attrs,
                    R.styleable.ChannelView,
                    0, 0);
            try {
                mShowEditButton = a.getBoolean(R.styleable.ChannelView_showEditButton, mShowEditButton);
            } finally {
                a.recycle();
            }
        }

        this.mMainView = inflate(getContext(), R.layout.blaubot_channel_view, null);
        mUiHandler = new Handler(Looper.getMainLooper());
        addView(this.mMainView);

        this.mEditChannelConfigButton = (Button) this.mMainView.findViewById(R.id.editChannelConfigButton);
        this.mSubscribeButton = (ToggleButton) this.mMainView.findViewById(R.id.channelInfoSubscribeButton);
        this.mSubscribeButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final boolean subscriberToChannel = mChannelInfo.isOwnDeviceSubscriberToChannel();
                final BlaubotChannel channel = mChannelInfo.getChannel();
                if (subscriberToChannel) {
                    channel.unsubscribe();
                } else {
                    channel.subscribe();
                }
                setEnabled(false);
            }
        });
        this.mShowSubscriberButton = (Button) this.mMainView.findViewById(R.id.showSubscribersButton);

        this.mEditChannelConfigButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mChannelInfo == null) {
                    if (Log.logWarningMessages()) {
                        Log.w(LOG_TAG, "The edit channel config button was pressed but i have no channel info object.");
                    }
                    return;
                }
                final Dialog channelEditDialog = createChannelEditDialog(getContext(), mChannelInfo.getChannel());
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "Showing edit channel config dialog.");
                }
                channelEditDialog.show();
            }
        });
    }

    private void updateUI() {
        // append the views
        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                final boolean subscriberToChannel = mChannelInfo.isOwnDeviceSubscriberToChannel();
                final short channelId = mChannelInfo.getChannelConfig().getChannelId();
                final int queueCapacity = mChannelInfo.getQueueCapacity();
                final int queueSize = mChannelInfo.getQueueSize();
                final long receivedBytes = mChannelInfo.getReceivedBytes();
                final long receivedMessages = mChannelInfo.getReceivedMessages();
                final long sentBytes = mChannelInfo.getSentBytes();
                final long sentMessages = mChannelInfo.getSentMessages();
                final ConcurrentSkipListSet<String> subscriptions = mChannelInfo.getSubscriptions();

                final String htmlStr = "<br><b>Channel #" + channelId + "</b><br>" +
                        "<span style=\"width:100px\">MessageQueue:</span> " + queueSize + "/" + queueCapacity + "<br>" +
                        "Subscriptions: \t" + subscriptions.size() + "<br>" +
                        "Rx/Tx bytes: \t" + ViewUtils.humanReadableByteCount(receivedBytes, false) + "/" + ViewUtils.humanReadableByteCount(sentBytes, false) + "<br>" +
                        "Rx/Tx messages: \t" + receivedMessages + "/" + sentMessages + "<br>" +
                        "";
                final Spanned html = Html.fromHtml(htmlStr);

                mSubscribeButton.setTextOff(html);
                mSubscribeButton.setTextOn(html);
                mSubscribeButton.setChecked(subscriberToChannel);
                mSubscribeButton.setEnabled(true);
                mShowSubscriberButton.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // custom view
                        SubscribersView subscribersView = new SubscribersView(getContext());
                        subscribersView.setPingMeasureResult(mChannelInfo.getSubscriptions());

                        // show dialog
                        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                        builder.setView(subscribersView);
                        builder.setTitle("Subscribers of channel #" + channelId);
                        builder.setPositiveButton("close", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        });
                        final AlertDialog alertDialog = builder.create();

                        alertDialog.show();
                    }
                });
                mEditChannelConfigButton.setVisibility(mShowEditButton ? VISIBLE : GONE);
            }
        });
    }

    /**
     * Sets the channel to be displayed
     *
     * @param channelInfo the channel info model that is to be rendered
     */
    public void setChannelInfo(ChannelInfo channelInfo) {
        this.mChannelInfo = channelInfo;
        updateUI();
    }

    /**
     * Creates a dialog which lets a user change the properties of the channel's config.
     *
     * @param context the current android context
     * @param blaubotChannel the blaubot channel to edit
     * @return the dialog
     */
    public static Dialog createChannelEditDialog(Context context, final IBlaubotChannel blaubotChannel) {
        /*
        TODO: NTH: put this stuff into a own view and use the observer functionality of BlaubotChannelConfig to update on external changes
         */
        final int MAX_MESSAGE_RATE = 10000;

        final View view = inflate(context, R.layout.blaubot_channel_config_edit_view, null);
        final SeekBar messageRateSeekBar = (SeekBar) view.findViewById(R.id.messageRateSeekBar);
        final TextView messageRateSeekBarValueTextView = (TextView) view.findViewById(R.id.messageRateSeekBarValueTextView);
        final Spinner pickingStrategySpinner = (Spinner) view.findViewById(R.id.pickingStrategySpinner);
        final EditText messageQueueCapacityEditText = (EditText) view.findViewById(R.id.messageQueueCapacityEditText);
        final ToggleButton transmitReflexiveMessagesToggleButton = (ToggleButton) view.findViewById(R.id.transmitReflexivChannelMessagesToggleButton);
        final ToggleButton transmitIfNoSubscribersToggleButton = (ToggleButton) view.findViewById(R.id.transmitIfNoSubscribersToggleButton);
        
        
        /*
            SeekBar stuff
         */
        messageRateSeekBar.setMax(MAX_MESSAGE_RATE);
        messageRateSeekBar.setProgress(blaubotChannel.getChannelConfig().getMinMessageRateDelay());
        messageRateSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int progress = messageRateSeekBar.getProgress();

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                this.progress = progress;
                updateMessageRateSeekbarTextView(progress);
            }

            private void updateMessageRateSeekbarTextView(int progress) {
                messageRateSeekBarValueTextView.setText(progress + " ms");
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                blaubotChannel.getChannelConfig().setMessageRateLimit(progress);
            }
        });
        messageRateSeekBarValueTextView.setText(messageRateSeekBar.getProgress() + " ms");



        /*
            Picker strategy stuff
         */
        List<BlaubotChannelConfig.MessagePickerStrategy> pickerStrategies = Arrays.asList(BlaubotChannelConfig.MessagePickerStrategy.values());
        final PickerStrategyArrayAdapter pickerStrategyArrayAdapter = new PickerStrategyArrayAdapter(context, pickerStrategies);
        pickingStrategySpinner.setAdapter(pickerStrategyArrayAdapter);
        pickingStrategySpinner.setSelection(pickerStrategies.indexOf(blaubotChannel.getChannelConfig().getPickerStrategy()));
        pickingStrategySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                BlaubotChannelConfig.MessagePickerStrategy strategy = pickerStrategyArrayAdapter.getItem(position);
                blaubotChannel.getChannelConfig().setMessagePickerStrategy(strategy);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });

        /*
            Queue capacity stuff
         */
        messageQueueCapacityEditText.setText(blaubotChannel.getChannelConfig().getQueueCapacity() + "");
        messageQueueCapacityEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                // validate
                Integer number = null;
                try {
                    number = Integer.parseInt(messageQueueCapacityEditText.getText().toString());
                } catch (NumberFormatException e) {
                }

                if (number == null) {
                    messageQueueCapacityEditText.setError("Not a number");
                    return;
                } else if (number <= 0) {
                    messageQueueCapacityEditText.setError("Has to be greater than zero.");
                    return;
                }

                messageQueueCapacityEditText.setError(null);

                // set
                blaubotChannel.getChannelConfig().setQueueCapacity(number);
            }
        });

        // reflexive message transmission
        transmitReflexiveMessagesToggleButton.setChecked(blaubotChannel.getChannelConfig().isTransmitReflexiveMessages());
        transmitReflexiveMessagesToggleButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final boolean currentState = blaubotChannel.getChannelConfig().isTransmitReflexiveMessages();
                blaubotChannel.getChannelConfig().setTransmitReflexiveMessages(!currentState);
            }
        });

        transmitIfNoSubscribersToggleButton.setChecked(blaubotChannel.getChannelConfig().isTransmitIfNoSubscribers());
        transmitIfNoSubscribersToggleButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final boolean transmitIfNoSubscribers = blaubotChannel.getChannelConfig().isTransmitIfNoSubscribers();
                blaubotChannel.getChannelConfig().setTransmitIfNoSubscribers(!transmitIfNoSubscribers);
            }
        });


        // build the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(view)
                .setPositiveButton("Close", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setTitle("Channel configuration");
        return builder.create();
    }


    private static class PickerStrategyArrayAdapter extends ArrayAdapter<BlaubotChannelConfig.MessagePickerStrategy> {
        public PickerStrategyArrayAdapter(Context context, List<BlaubotChannelConfig.MessagePickerStrategy> strategies) {
            super(context, R.layout.blaubot_picker_strategy_list_item, R.id.name, strategies);
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            return getCustomView(position, convertView, parent);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            return getCustomView(position, convertView, parent);
        }

        private View getCustomView(int position, View convertView, ViewGroup parent) {
            final BlaubotChannelConfig.MessagePickerStrategy strategy = getItem(position);
            final View view = convertView != null ? convertView : View.inflate(this.getContext(), R.layout.blaubot_picker_strategy_list_item, null);
            final TextView nameTextView = (TextView) view.findViewById(R.id.name);
            final TextView descriptionTextView = (TextView) view.findViewById(R.id.description);
//            ImageView iconImageView = (ImageView) view.findViewById(R.id.icon);
//            iconImageView.setImageDrawable(getResources().getDrawable(R.drawable.ic_merge));
            nameTextView.setText(strategy.name() + "");
            descriptionTextView.setText(getDescriptionForPickerStrategy(strategy));
            return view;
        }
    }

    /**
     * Returns a string describing the given strategy
     *
     * @param strategy the strategy
     * @return a description for the given strategy
     */
    public static Spanned getDescriptionForPickerStrategy(BlaubotChannelConfig.MessagePickerStrategy strategy) {

        if (strategy.equals(BlaubotChannelConfig.MessagePickerStrategy.PROCESS_ALL)) {
            return Html.fromHtml("<html>All messages added to the message queue are processed sequentially.</html>");
        } else if (strategy.equals(BlaubotChannelConfig.MessagePickerStrategy.DISCARD_NEW)) {
            return Html.fromHtml("<html>On each pick, only the <b>oldest</b> message is picked from the message queue and all newer messages will be discarded.</html>");
        } else if (strategy.equals(BlaubotChannelConfig.MessagePickerStrategy.DISCARD_OLD)) {
            return Html.fromHtml("<html>On each pick, only the <b>newest</b> message is picked from the message queue and all older messages will be discarded.</html>");
        } else {
            throw new RuntimeException("unknown strategy");
        }
    }

}
package eu.hgross.blaubotcam;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.Date;
import java.util.Observable;
import java.util.Observer;
import java.util.UUID;

import eu.hgross.blaubot.android.BlaubotAndroid;
import eu.hgross.blaubot.android.BlaubotAndroidFactory;
import eu.hgross.blaubot.android.views.DebugView;
import eu.hgross.blaubot.android.views.StateView;
import eu.hgross.blaubot.android.views.edit.WebsocketServerConnectorEditView;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.LifecycleListenerAdapter;
import eu.hgross.blaubot.core.statemachine.ConnectionStateMachineAdapter;
import eu.hgross.blaubot.core.statemachine.states.FreeState;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotSubordinatedState;
import eu.hgross.blaubot.core.statemachine.states.KingState;
import eu.hgross.blaubot.core.statemachine.states.StoppedState;
import eu.hgross.blaubot.messaging.BlaubotChannelConfig;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.util.Log;
import eu.hgross.blaubot.util.SubscriptionWatcher;
import eu.hgross.blaubotcam.audio.BlaubotWalkieTalkie;
import eu.hgross.blaubotcam.audio.IRecordingListener;
import eu.hgross.blaubotcam.video.CameraReader;
import eu.hgross.blaubotcam.video.ImageMessage;
import eu.hgross.blaubotcam.views.AudioViewer;
import eu.hgross.blaubotcam.views.VideoViewer;

/**
 * A simple video cam app to show the m:n communication of blaubot with bigger byte messages
 */
public class BlaubotCamMainActivity extends Activity {
    private static final String LOG_TAG = "BlaubotCamMainActivity";
    private static final UUID APP_UUID = UUID.fromString("cc3aee81-2187-4324-91ac-db9b0697e61c");
    private static final int ACCEPTOR_PORT = 17171;
    private static final int BEACON_PORT = 17172;
    private static final int BEACON_BROADCAST_PORT = 17173;
    /**
     * Iff true, blaubot will be auto-started
     */
    private static final boolean AUTOSTART = false;
    /**
     * The channel id over which we send our pictures
     */
    private static final short VIDEO_CHANNEL_ID = 1;
    /**
     * The channel id used to send audio messages
     */
    private static final short AUDIO_CHANNEL_ID = 2;
    /**
     * The max settable delay in ms between the publish of two pictures
     */
    private static final int MAX_FEED_DELAY = 5000;
    /**
     * Never hold more than 10 messages in the queue
     */
    private static final int PICTURE_QUEUE_SIZE = 10;
    /**
     * The default unique device id of the server. Has to match the unique device on the server side.
     */
    private static final String SERVER_DEFAULT_UNIQUE_DEVICE_ID = "BlaubotCamServer";
    /**
     * The default uri path to the websocket endpoint.
     */
    private static final String SERVER_DEFAULT_WEBSOCKET_URL_PATH = "/blaubot";
    /**
     * The default server port of the websocket endpoint
     */
    private static final int SERVER_DEFAULT_PORT = 8080;
    /**
     * The default hostname of the server
     */
    private static final String SERVER_DEFAULT_HOST = "192.168.168.2";
    private static final String DEBUG_VIEW_TAB_INDICATOR_TEXT = "DebugView";


    private Blaubot mBlaubot;
    private VideoViewer mVideoViewer;
    private AudioViewer mAudioViewer;
    private CameraReader mCameraReader;
    private BlaubotWalkieTalkie mWalkieTalkie;
    private Handler mUiHandler;
    private Button mToggleVideoButton;
    private Button mCreateServerConnectorButton;
    private Button mWalkieTalkieRecordButton;
    private ScrollView mPreviewTabScrollView;
    private View mConnectionStatusIndicatorView;

    private SeekBar mVideoChannelMessageRateSeekBar;
    private TextView mSeekBarValueTextView;

    private SeekBar mJpegQualitySeekBar;
    private TextView mJpegQualitySeekBarValueTextView;
    /**
     * Start/Stop buttons and state visualization for each tab
     */
    private StateView mStateView2, mStateView3;

    /**
     * The tab tag for the config view
     */
    private static final String TAB_PREVIEW = "Preview";
    /**
     * The tab tag for the debug view
     */
    private static final String TAB_DEBUG_VIEW = "DebugView";
    /**
     * The tab tag for the picture feed
     */
    private static final String TAB_FEED = "Feed";
    private DebugView mDebugView;
    private TabHost mTabHost;


    private ToggleButton mToggleReceiveImagesButton;
    private ToggleButton mToggleReceiveAudioButton;
    private LinearLayout mSurfaceViewContainer;
    private IBlaubotChannel mVideoChannel;
    private IBlaubotChannel mAudioChannel;
    private boolean mConnected;
    /**
     * If the creation of blaubot fails due to an exception (no wifi or whatever)
     * the exception is stored here to be displayed to the user.
     */
    private Exception mFailException;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blaubot_cam_main);
        mUiHandler = new Handler(Looper.getMainLooper());
        mCameraReader = new CameraReader(this);

        this.mDebugView = (DebugView) findViewById(R.id.debugView);
        this.mStateView2 = (StateView) findViewById(R.id.stateView2);
        this.mStateView3 = (StateView) findViewById(R.id.stateView3);
        this.mTabHost = (TabHost) findViewById(R.id.tabHost);
        this.mTabHost.setup();

        // create tabspecs
        TabHost.TabSpec previewTabSpec = this.mTabHost.newTabSpec(TAB_PREVIEW);
        previewTabSpec.setContent(R.id.previewContainer);
        previewTabSpec.setIndicator("Preview");

        TabHost.TabSpec feedTabSpec = this.mTabHost.newTabSpec(TAB_FEED);
        feedTabSpec.setContent(R.id.feedContainer);
        feedTabSpec.setIndicator("Feed");

        final TabHost.TabSpec debugViewTabSpec = this.mTabHost.newTabSpec(TAB_DEBUG_VIEW);
        debugViewTabSpec.setContent(R.id.debugViewContainer);
        debugViewTabSpec.setIndicator(DEBUG_VIEW_TAB_INDICATOR_TEXT);


        mTabHost.addTab(previewTabSpec);
        mTabHost.addTab(feedTabSpec);
        mTabHost.addTab(debugViewTabSpec);
        mTabHost.setCurrentTabByTag(TAB_DEBUG_VIEW);

        mConnectionStatusIndicatorView = findViewById(R.id.connectionStatusIndicator);
        mPreviewTabScrollView = (ScrollView) findViewById(R.id.previewTabScrollView);
        mWalkieTalkieRecordButton = (Button) findViewById(R.id.walkieTalkieButton);
        mToggleVideoButton = (Button) findViewById(R.id.toggleVideoButton);
        mToggleVideoButton.setSelected(false);
        mVideoChannelMessageRateSeekBar = (SeekBar) findViewById(R.id.videoChannelMessageRateSeekBar);
        mJpegQualitySeekBar = (SeekBar) findViewById(R.id.jpegQualitySeekBar);
        mSeekBarValueTextView = (TextView) findViewById(R.id.seekbarValueTextView);
        mJpegQualitySeekBarValueTextView = (TextView) findViewById(R.id.jpegQualitySeekBarValueTextView);
        mSurfaceViewContainer = (LinearLayout) findViewById(R.id.liveVideoSurfaceViewContainer);
        mCreateServerConnectorButton = (Button) findViewById(R.id.createServerConnectorButton);
        mVideoViewer = (VideoViewer) findViewById(R.id.videoViewer);
        mAudioViewer = (AudioViewer) findViewById(R.id.audioViewer);

        mToggleReceiveAudioButton = (ToggleButton) findViewById(R.id.toggleReceiveAudioButton);
        mToggleReceiveAudioButton.setChecked(true);
        mToggleReceiveAudioButton.setEnabled(false);
        mToggleReceiveImagesButton = (ToggleButton) findViewById(R.id.toggleReceiveVideoButton);
        mToggleReceiveImagesButton.setChecked(false);
        mToggleReceiveImagesButton.setEnabled(false);


        // Try to create blaubot or save the exception
        try {
//            mBlaubot = BlaubotAndroidFactory.createBluetoothBlaubot(APP_UUID);
//            mBlaubot = BlaubotAndroidFactory.createBluetoothBlaubotWithNFCBeacon(APP_UUID);
//            mBlaubot = BlaubotAndroidFactory.createBluetoothBlaubotWithMulticastBeacon(APP_UUID, BEACON_PORT, BEACON_BROADCAST_PORT);

            mBlaubot = BlaubotAndroidFactory.createEthernetBlaubot(APP_UUID);
//            mBlaubot = BlaubotAndroidFactory.createEthernetBlaubot(APP_UUID, ACCEPTOR_PORT, BEACON_PORT, BEACON_BROADCAST_PORT, BlaubotAndroidFactory.getLocalIpAddress());
//            mBlaubot = BlaubotAndroidFactory.createEthernetBlaubotWithNFCBeacon(APP_UUID, ACCEPTOR_PORT, BlaubotAndroidFactory.getLocalIpAddress());
//            mBlaubot = BlaubotAndroidFactory.createEthernetBlaubotWithBonjourBeacon(APP_UUID, ACCEPTOR_PORT, BEACON_PORT, BlaubotAndroidFactory.getLocalIpAddress());

//            mBlaubot = BlaubotAndroidFactory.createWebSocketBlaubotWithMulticastBeacon(APP_UUID, ACCEPTOR_PORT, BEACON_PORT, BEACON_BROADCAST_PORT, BlaubotAndroidFactory.getLocalIpAddress());
//            mBlaubot = BlaubotAndroidFactory.createWebSocketBlaubotWithBonjourBeacon(APP_UUID, ACCEPTOR_PORT, BEACON_PORT, BlaubotAndroidFactory.getLocalIpAddress());

//            mBlaubot = BlaubotAndroidFactory.createEthernetBlaubotWithBluetoothBeacon(APP_UUID, ACCEPTOR_PORT, BlaubotAndroidFactory.getLocalIpAddress());
//            mBlaubot = BlaubotAndroidFactory.createWifiApWithBluetoothBeaconBlaubot(APP_UUID, (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE), (WifiManager) getSystemService(WIFI_SERVICE), ACCEPTOR_PORT);
//            mBlaubot = BlaubotAndroidFactory.createWifiApWithNfcBeaconBlaubot(APP_UUID, (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE), (WifiManager) getSystemService(WIFI_SERVICE), ACCEPTOR_PORT);
        } catch (Exception e) {
            mFailException = e;
            e.printStackTrace();
            return;
        }

        // wire everything
        mSurfaceViewContainer.addView(mCameraReader.getSurfaceView());
        mToggleVideoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleVideo();
            }
        });
        mCameraReader.addObserver(mNewImageObserver);
        mDebugView.registerBlaubotInstance(mBlaubot);
        mStateView2.registerBlaubotInstance(mBlaubot);
        mStateView3.registerBlaubotInstance(mBlaubot);


        mBlaubot.addLifecycleListener(new LifecycleListenerAdapter() {
            @Override
            public void onConnected() {
                mConnected = true;
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mToggleReceiveImagesButton.setEnabled(true);
                        mToggleReceiveAudioButton.setEnabled(true);
                    }
                });
            }

            @Override
            public void onDisconnected() {
                mConnected = false;
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mToggleReceiveImagesButton.setEnabled(false);
                        mToggleReceiveAudioButton.setEnabled(false);
                    }
                });

            }
        });


        // create configure and add a listener to the audio channel
        mAudioChannel = mBlaubot.createChannel(AUDIO_CHANNEL_ID);
        mWalkieTalkie = new BlaubotWalkieTalkie(this, mAudioChannel, mBlaubot.getOwnDevice());

        // create, configure and add a listener to the video channel
        mVideoChannel = mBlaubot.createChannel(VIDEO_CHANNEL_ID);
        mVideoChannel.getChannelConfig().setMessageRateLimit(500).setMessagePickerStrategy(BlaubotChannelConfig.MessagePickerStrategy.DISCARD_OLD);
        mVideoChannel.getChannelConfig().setPriority(BlaubotMessage.Priority.LOW);
        mVideoChannel.getChannelConfig().setQueueCapacity(PICTURE_QUEUE_SIZE);
        mVideoChannel.addMessageListener(mVideoViewer);
        mBlaubot.getChannelManager().addAdminMessageListener(mVideoViewer);


        // set up the WalkieTalkie UI, the subscription toggle buttons for audio and video and the 
        // seekbars to configure message rate and jpeg quality        
        setUpWalkieTalkieUi();
        setUpSubscriptionButtons();
        setUpSeekBars();

        // wire the create server connector button to an edit view
        mCreateServerConnectorButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                WebsocketServerConnectorEditView.createAsDialog(BlaubotCamMainActivity.this, mBlaubot, SERVER_DEFAULT_HOST, SERVER_DEFAULT_PORT, SERVER_DEFAULT_WEBSOCKET_URL_PATH, SERVER_DEFAULT_UNIQUE_DEVICE_ID).show();
            }
        });

        // maintain the connection indicator color
        final ConnectionStatusIndicatorUpdater indicatorUpdater = new ConnectionStatusIndicatorUpdater();
        mBlaubot.getConnectionStateMachine().addConnectionStateMachineListener(indicatorUpdater);
        indicatorUpdater.setIndicatorFromState(mBlaubot.getConnectionStateMachine().getCurrentState());

        // Autostart switch
        if (AUTOSTART) {
            this.mBlaubot.startBlaubot();
        }
    }

    /**
     * Creates the WalkieTalkie send button and some logic to avoid scrolling while it is pressed.
     */
    private void setUpWalkieTalkieUi() {
        mAudioViewer.setWalkieTalkie(mWalkieTalkie);

        /**
         * a touch listener that triggers audio recording and prevents scrolling when we record audio
         * by blocking the wrapping scroll view
         */
        final View.OnTouchListener recordingTouchListener = new View.OnTouchListener() {
            /**
             * if true, the scrollview in the preview tab will not scroll and we are recording
             */
            private boolean mNoScrollingInPreviewTab = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (v == mWalkieTalkieRecordButton && event.getAction() == MotionEvent.ACTION_DOWN) {
                    // prevent scroll view scrolling when pressed
                    mNoScrollingInPreviewTab = true;

                    // start recording audio
                    mWalkieTalkie.startRecording();
                } else if (event.getAction() == MotionEvent.ACTION_UP) {// || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (mNoScrollingInPreviewTab) {
                        mWalkieTalkie.stopRecording();
                        // enable scrolling
                        mNoScrollingInPreviewTab = false;
                    }
                }
                return mNoScrollingInPreviewTab;
            }
        };

        // Button to record audio
        mWalkieTalkieRecordButton.setOnTouchListener(recordingTouchListener);
        mPreviewTabScrollView.setOnTouchListener(recordingTouchListener);


        // add listener to maintain button state
        mWalkieTalkie.addRecordingListener(new IRecordingListener() {
            @Override
            public void onRecordingStarted() {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mWalkieTalkieRecordButton.setText("RECORDING ...");
                    }
                });
            }

            @Override
            public void onRecordingStopped() {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mWalkieTalkieRecordButton.setText("record and send audio");
                    }
                });

            }
        });
    }

    /**
     * Sets up the seekbars to change the message rate and image quality.
     */
    private void setUpSeekBars() {
        // Configure the message rate seek bar
        mVideoChannelMessageRateSeekBar.setMax(MAX_FEED_DELAY);
        mVideoChannelMessageRateSeekBar.setProgress(mVideoChannel.getChannelConfig().getMinMessageRateDelay());
        mVideoChannelMessageRateSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int progress = mVideoChannelMessageRateSeekBar.getProgress();

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress <= 0) {
                    progress = 1;
                }
                this.progress = progress;
                updateMessageRateSeekbarTextView(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mVideoChannel.getChannelConfig().setMessageRateLimit(progress);
            }
        });


        // configure the jpeg quality seekbar
        mJpegQualitySeekBar.setMax(100);
        mJpegQualitySeekBar.setProgress(mCameraReader.getJpegQuality());
        mJpegQualitySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            private int progress = mJpegQualitySeekBar.getProgress();

            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (progress <= 0) {
                    progress = 1;
                }
                this.progress = progress;
                updateJpegQualitySeekbarTextView(progress);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                mCameraReader.setJpegQuality(progress);
            }
        });

        // we attach an observer to the channel config to get informed about changes
        Observer configChangeObserver = new Observer() {
            @Override
            public void update(Observable observable, Object data) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        final int minMessageRateDelay = mVideoChannel.getChannelConfig().getMinMessageRateDelay();
                        updateMessageRateSeekbarTextView(minMessageRateDelay);
                        mVideoChannelMessageRateSeekBar.setProgress(minMessageRateDelay);
                    }
                });
            }
        };
        mVideoChannel.getChannelConfig().addObserver(configChangeObserver);


        // update the views initially
        updateMessageRateSeekbarTextView(mVideoChannel.getChannelConfig().getMinMessageRateDelay());
        updateJpegQualitySeekbarTextView(mCameraReader.getJpegQuality());
    }

    /**
     * Wires the subscription toggle buttons for the audio and video channel with some listeners
     */
    private void setUpSubscriptionButtons() {
        /*
            Toggle buttons for subscriptions
         */
        // using a watcher to maintain the toggle state of the receive images button
        final SubscriptionWatcher videoChannelSubscriptionWatcher = new SubscriptionWatcher(VIDEO_CHANNEL_ID) {
            private void setChecked(final boolean checked) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mToggleReceiveImagesButton.setChecked(checked);
                    }
                });
            }

            @Override
            public void onUnsubscribed(short channelId) {
                setChecked(false);
            }

            @Override
            public void onSubscribed(short channelId) {
                setChecked(true);
            }
        };
        videoChannelSubscriptionWatcher.registerWithBlaubot(mBlaubot);

        // Button to toggle the subscription to the video channel
        mToggleReceiveImagesButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mConnected) {
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(BlaubotCamMainActivity.this, "Not connected to blaubot network.", Toast.LENGTH_SHORT).show();
                            mToggleReceiveImagesButton.setChecked(videoChannelSubscriptionWatcher.isSubscribed());
                        }
                    });
                    return;
                }
                if (!videoChannelSubscriptionWatcher.isSubscribed()) {
                    mVideoChannel.subscribe();
                } else {
                    mVideoChannel.unsubscribe();
                }
            }
        });


        // using a watcher to maintain the toggle state of the receive images button
        final SubscriptionWatcher audioChannelSubscriptionWatcher = new SubscriptionWatcher(AUDIO_CHANNEL_ID) {
            private void setChecked(final boolean checked) {
                mUiHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mToggleReceiveAudioButton.setChecked(checked);
                    }
                });
            }

            @Override
            public void onUnsubscribed(short channelId) {
                setChecked(false);
            }

            @Override
            public void onSubscribed(short channelId) {
                setChecked(true);
            }
        };

        // Button to toggle the audio receiving
        mToggleReceiveAudioButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mConnected) {
                    mUiHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(BlaubotCamMainActivity.this, "Not connected to blaubot network.", Toast.LENGTH_SHORT).show();
                            mToggleReceiveAudioButton.setChecked(audioChannelSubscriptionWatcher.isSubscribed());
                        }
                    });
                    return;
                }
                if (!audioChannelSubscriptionWatcher.isSubscribed()) {
                    mAudioChannel.subscribe();
                } else {
                    mAudioChannel.unsubscribe();
                }
            }
        });
    }

    private void updateMessageRateSeekbarTextView(int value) {
        mSeekBarValueTextView.setText(value + " ms");
    }

    private void updateJpegQualitySeekbarTextView(int value) {
        mJpegQualitySeekBarValueTextView.setText(value + "%");
    }

    /**
     * Listens to new images received from the camera
     */
    private Observer mNewImageObserver = new Observer() {
        // TODO: CHUNKING NECESSARY!
        @Override
        public void update(Observable observable, Object data) {
            final byte[] jpegData = (byte[]) data;
            if (mConnected && mVideoChannel != null) {
                // construct a message containing the image and our id
                final String myUniqueDeviceId = mBlaubot.getOwnDevice().getUniqueDeviceID();
                final ImageMessage msg = new ImageMessage(myUniqueDeviceId, jpegData, new Date());
                final boolean addedToQueue = mVideoChannel.publish(msg.toBytes(), 150);
                if (!addedToQueue) {
                    // message was not added to queue and will be skipped
                }
            }
        }
    };


    private Object videoToggleLock = new Object();
    private boolean videoActive = false;

    private void toggleVideo() {
        synchronized (videoToggleLock) {
            mCameraReader.toggleVideoStream();
            videoActive = !videoActive;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_blaubot_cam_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }


    @Override
    protected void onNewIntent(Intent intent) {
        if (mBlaubot instanceof BlaubotAndroid) {
            final BlaubotAndroid blaubotAndroid = (BlaubotAndroid) mBlaubot;
            blaubotAndroid.onNewIntent(intent);
        }
        super.onNewIntent(intent);
    }

    @Override
    protected void onResume() {
        Log.d(LOG_TAG, "LifeCycle.onResume");
        if (this.mBlaubot == null) {
            // stop
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setIcon(R.drawable.ic_stopped);
            builder.setTitle("Creation error");
            builder.setMessage("Could not create Blaubot. Is Wi-Fi/Bluetooth/... turned on? Message: " + mFailException.getMessage())
                    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            BlaubotCamMainActivity.this.finish();
                        }
                    });
            builder.create().show();
            super.onResume();
            return;
        }


        if (mBlaubot instanceof BlaubotAndroid) {
            final BlaubotAndroid blaubotAndroid = (BlaubotAndroid) mBlaubot;
            blaubotAndroid.setContext(this);
            blaubotAndroid.registerReceivers(this);
            blaubotAndroid.onResume(this);
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBlaubot instanceof BlaubotAndroid) {
            final BlaubotAndroid blaubotAndroid = (BlaubotAndroid) mBlaubot;
            blaubotAndroid.unregisterReceivers(this);
            blaubotAndroid.onResume(this);
        }
    }

    @Override
    protected void onStop() {
        if (mBlaubot == null) {
            super.onStop();
            return;
        }

        mWalkieTalkie.release();
        if (mBlaubot instanceof BlaubotAndroid) {
            final BlaubotAndroid blaubotAndroid = (BlaubotAndroid) mBlaubot;
            blaubotAndroid.stopBlaubot();
        }
        super.onStop();
    }

    /**
     * Maintains the indicator color to show if we are connected, stopped or searching.
     */
    private class ConnectionStatusIndicatorUpdater extends ConnectionStateMachineAdapter {
        private static final int CONNECTED_COLOR = R.color.Green;
        private static final int STOPPED_COLOR = R.color.Maroon;
        private static final int SEARCHING_COLOR = R.color.DarkGoldenrod;


        private void setColor(final int color) {
            mUiHandler.post(new Runnable() {
                @Override
                public void run() {
                    mConnectionStatusIndicatorView.setBackgroundResource(color);
                }
            });
        }

        @Override
        public void onStateChanged(IBlaubotState oldState, IBlaubotState newState) {
            setIndicatorFromState(newState);
        }

        public void setIndicatorFromState(IBlaubotState state) {
            if (state instanceof FreeState) {
                setColor(SEARCHING_COLOR);
            } else if (state instanceof IBlaubotSubordinatedState || state instanceof KingState) {
                setColor(CONNECTED_COLOR);
            } else if (state instanceof StoppedState) {
                setColor(STOPPED_COLOR);
            }
        }
    }

}

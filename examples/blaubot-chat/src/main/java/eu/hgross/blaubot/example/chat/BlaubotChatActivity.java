package eu.hgross.blaubot.example.chat;

import android.bluetooth.BluetoothAdapter;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import java.util.UUID;

import eu.hgross.blaubot.android.BlaubotAndroid;
import eu.hgross.blaubot.android.BlaubotAndroidFactory;
import eu.hgross.blaubot.android.views.DebugView;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.LifecycleListenerAdapter;
import eu.hgross.blaubot.example.chat.navigation.NavigationDrawerCallbacks;
import eu.hgross.blaubot.example.chat.navigation.NavigationDrawerFragment;
import eu.hgross.blaubot.example.chat.views.ChatRoomView;
import eu.hgross.blaubot.util.Log;

/**
 * A simple chat app example to demonstrate blaubot.
 */
public class BlaubotChatActivity extends ActionBarActivity implements NavigationDrawerCallbacks {
    private static final UUID APP_UUID = UUID.fromString("DE292C5B-34FB-4738-8C7E-D0291389DEC9");
    private static final short CHAT_CHANNEL_ID = 1;
    private static final short NAMECHANGE_CHANNEL_ID = 2;
    private static final short HELLO_CHANNEL_ID = 3;
    private Blaubot mBlaubot;
    private DebugView mDebugView;

    /**
     * Fragment managing the behaviors, interactions and presentation of the navigation drawer.
     */
    private NavigationDrawerFragment mNavigationDrawerFragment;
    private Toolbar mToolbar;
    private ChatRoomView mChatView;
    private View mChatViewContainer;
    private View mDebugViewContainer;
    private Button mSendButton;
    private EditText mTextInput;

    static {
        Log.LOG_LEVEL = Log.LogLevel.DEBUG;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_blaubot_chat);
        mToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);

        // find our views
        DrawerLayout drawerLayout = (DrawerLayout) findViewById(R.id.drawer);
        mDebugView = (DebugView) findViewById(R.id.debugView);
        mChatView = (ChatRoomView) findViewById(R.id.mainChatView);
        mChatViewContainer = findViewById(R.id.chatViewContainer);
        mDebugViewContainer = findViewById(R.id.debugViewScrollContainer);
        mSendButton = (Button) findViewById(R.id.sendButton);
        mSendButton.setEnabled(false);
        mTextInput = (EditText) findViewById(R.id.chatMessageInput);

        // Set up the drawer.
        mNavigationDrawerFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentById(R.id.fragment_drawer);
        mNavigationDrawerFragment.setup(R.id.fragment_drawer, drawerLayout, mToolbar);

        // enable bt
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            bluetoothAdapter.enable();
        }

        // set up blaubot
        mBlaubot = BlaubotAndroidFactory.createEthernetBlaubot(APP_UUID);
//        mBlaubot = BlaubotAndroidFactory.createBluetoothBlaubotWithNFCBeacon(APP_UUID);
//        mBlaubot = BlaubotAndroidFactory.createEthernetBlaubotWithNFCBeacon(APP_UUID, 17171, BlaubotAndroidFactory.getLocalIpAddress());
        mDebugView.registerBlaubotInstance(mBlaubot);

        // wire it with the chatview
        mChatView.registerBlaubot(mBlaubot, CHAT_CHANNEL_ID, HELLO_CHANNEL_ID, NAMECHANGE_CHANNEL_ID);

        // set up the send ui
        setUpSendButton();

        // select the chat on start
        mNavigationDrawerFragment.onNavigationDrawerItemSelected(NavigationDrawerFragment.CHAT_INDEX);

        // add a listener that informs us, if we are connected to a network
        mBlaubot.addLifecycleListener(new LifecycleListenerAdapter() {
            @Override
            public void onConnected() {
                setEnabled(true);
            }

            @Override
            public void onDisconnected() {
                setEnabled(false);
            }

            private void setEnabled(final boolean enabled) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mSendButton.setEnabled(enabled);
                    }
                });
            }
        });
    }

    @Override
    protected void onStop() {
        mBlaubot.stopBlaubot();
        super.onStop();
    }

    /**
     * Sets up the send button and the textfield to send messages over the chat channel
     */
    private void setUpSendButton() {
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String toSend = mTextInput.getText().toString();
                boolean queued = mChatView.sendChatMessage(toSend);
                if (!queued) {
                    Toast.makeText(BlaubotChatActivity.this, "Could not add message to queue (full)", Toast.LENGTH_LONG);
                } else {
                    mTextInput.setText("");
                }
            }
        });
    }


    @Override
    public void onNavigationDrawerItemSelected(int position) {
        // update the main content by replacing fragments
        View[] all = new View[]{mChatViewContainer, mDebugViewContainer};
        View active = null;
        if (position == NavigationDrawerFragment.CHAT_INDEX) {
            active = mChatViewContainer;
        } else if (position == NavigationDrawerFragment.DEBUGVIEW_INDEX) {
            active = mDebugViewContainer;
            hideKeyboard();
        } else {
            return;
        }

        for (View v : all) {
            if (v == active || v == null) {

                continue;
            }
            v.setVisibility(View.GONE);
        }
        if (active != null) {
            active.setVisibility(View.VISIBLE);
        }
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
        mBlaubot.startBlaubot();

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
        mTextInput.clearFocus();
        if (mBlaubot instanceof BlaubotAndroid) {
            final BlaubotAndroid blaubotAndroid = (BlaubotAndroid) mBlaubot;
            blaubotAndroid.unregisterReceivers(this);
            blaubotAndroid.onResume(this);
        }
    }


    @Override
    public void onBackPressed() {
        if (mNavigationDrawerFragment.isDrawerOpen()) {
            mNavigationDrawerFragment.closeDrawer();
        } else {
            super.onBackPressed();
        }
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // Only show items in the action bar relevant to this screen
            // if the drawer is not showing. Otherwise, let the drawer
            // decide what to show in the action bar.
            getMenuInflater().inflate(R.menu.blaubot_chat, menu);
            return true;
        }
        return super.onCreateOptionsMenu(menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_change_username) {
            if (mChatView != null) {
                mChatView.showChangeNicknameDialog();
            }
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * Hides the keyboard
     */
    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager inputManager = (InputMethodManager) this.getSystemService(INPUT_METHOD_SERVICE);
            inputManager.hideSoftInputFromWindow(view.getWindowToken(), InputMethodManager.HIDE_NOT_ALWAYS);
        }
    }


}

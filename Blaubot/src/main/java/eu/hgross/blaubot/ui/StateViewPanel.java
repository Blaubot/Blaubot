package eu.hgross.blaubot.ui;

import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.IBlaubotConnection;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import eu.hgross.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.util.Log;

/**
 * Created by henna on 30.04.15.
 */
public class StateViewPanel extends JPanel implements IBlaubotDebugView {
    public static final String LOG_TAG = "StatusViewPanel";

    private final JLabel mCurrentStateTextView;
    private final JLabel mConnectedDevicesCountTextView;
    private final JLabel mOwnUniqueDeviceIdLabel;
    private final JButton mStartStopButton;
    private Blaubot mBlaubot;

    public StateViewPanel() {
        super();
        setLayout(new FlowLayout());
        mCurrentStateTextView = new JLabel("");
        mConnectedDevicesCountTextView = new JLabel("");
        mOwnUniqueDeviceIdLabel = new JLabel("");
        mStartStopButton = new JButton("Start");
        mStartStopButton.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                if (mBlaubot == null) {
                    if (Log.logWarningMessages()) {
                        Log.w(LOG_TAG, "Blaubot not set - ignoring onClick.");
                    }
                    return;
                }
                mStartStopButton.setEnabled(false);
                if (mBlaubot.isStarted()) {
                    mBlaubot.stopBlaubot();
                } else {
                    mBlaubot.startBlaubot();
                }
            }
        });

        add(mCurrentStateTextView);
        add(mStartStopButton);
        add(mConnectedDevicesCountTextView);
        add(mOwnUniqueDeviceIdLabel);
    }

    private IBlaubotConnectionStateMachineListener mBlaubotConnectionStateMachineListener = new IBlaubotConnectionStateMachineListener() {
        public IBlaubotState mCurrentBlaubotState;

        private void updateCurrentStateTextView() {
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    if (mCurrentBlaubotState != null) {
                        mCurrentStateTextView.setText(mCurrentBlaubotState.toString());
                    } else {
                        mCurrentStateTextView.setText("no registered instance");
                    }
                }
            });
        }

        @Override
        public void onStateChanged(IBlaubotState oldState, final IBlaubotState state) {
            mCurrentBlaubotState = state;
            updateCurrentStateTextView();
        }

        @Override
        public void onStateMachineStopped() {
            updateCurrentStateTextView();
            setButtonText();
        }

        @Override
        public void onStateMachineStarted() {
            updateCurrentStateTextView();
            setButtonText();
        }
    };


    private IBlaubotConnectionManagerListener mConnectionManagerListener = new IBlaubotConnectionManagerListener() {
        private void updateUI() {
            final int num;
            if (mBlaubot != null) {
                num = mBlaubot.getConnectionManager().getAllConnections().size();
            } else {
                num = 0;
            }
            SwingUtilities.invokeLater(new Runnable() {
                @Override
                public void run() {
                    mConnectedDevicesCountTextView.setText("Connected devices to ConnectionManager: " + num);
                }
            });
        }

        @Override
        public void onConnectionClosed(IBlaubotConnection connection) {
            updateUI();
        }

        @Override
        public void onConnectionEstablished(IBlaubotConnection connection) {
            updateUI();
        }
    };


    private void setButtonText() {
        final boolean started = mBlaubot.isStarted();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                // await either started or stopped
                if (started) {
                    mStartStopButton.setText("Stop");
                } else {
                    mStartStopButton.setText("Start");
                }
                mStartStopButton.setEnabled(true);
            }
        });
    }

    private void updateOwnUniqueDeviceId() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (mBlaubot != null) {
                    String uniqueDeviceID = mBlaubot.getOwnDevice().getUniqueDeviceID();
                    mOwnUniqueDeviceIdLabel.setText(uniqueDeviceID);
                } else {
                    mOwnUniqueDeviceIdLabel.setText("");
                }
            }
        });
    }


    @Override
    public void registerBlaubotInstance(Blaubot blaubot) {
        if (this.mBlaubot != null) {
            unregisterBlaubotInstance();
        }
        this.mBlaubot = blaubot;
        blaubot.getConnectionStateMachine().addConnectionStateMachineListener(mBlaubotConnectionStateMachineListener);
        blaubot.getConnectionManager().addConnectionListener(mConnectionManagerListener);

        setButtonText();
        updateOwnUniqueDeviceId();

    }

    @Override
    public void unregisterBlaubotInstance() {
        if (this.mBlaubot != null) {
            mBlaubot.getConnectionStateMachine().removeConnectionStateMachineListener(mBlaubotConnectionStateMachineListener);
            mBlaubot.getConnectionManager().removeConnectionListener(mConnectionManagerListener);
        }
        this.mBlaubot = null;
    }
}

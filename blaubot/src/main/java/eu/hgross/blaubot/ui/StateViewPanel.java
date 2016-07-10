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
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionManagerListener;
import eu.hgross.blaubot.core.statemachine.IBlaubotConnectionStateMachineListener;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;
import eu.hgross.blaubot.util.Log;

/**
 * Visualizes the current state and provides a start/stop button for the registered instance.
 */
public class StateViewPanel extends JPanel implements IBlaubotDebugView {
    public static final String LOG_TAG = "StatusViewPanel";

    private JPanel mCurrentStateIconContainer;
    private final JLabel mCurrentStateTextView;
    private final JLabel mConnectedDevicesCountTextView;
    private final JButton mStartStopButton;
    private Blaubot mBlaubot;

    public StateViewPanel() {
        super();
        setLayout(new FlowLayout());
        mCurrentStateTextView = new JLabel("");
        mCurrentStateIconContainer = new JPanel();
        mConnectedDevicesCountTextView = new JLabel("");
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

        add(mCurrentStateIconContainer);
        add(mCurrentStateTextView);
        add(mStartStopButton);
        add(mConnectedDevicesCountTextView);
    }
    
    public IBlaubotState mCurrentBlaubotState;
    
    private void updateStateIconAndText() {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                // clear current icon state
                mCurrentStateIconContainer.removeAll();

                if (mCurrentBlaubotState != null) {
                    State state = State.getStateByStatemachineClass(mCurrentBlaubotState.getClass());
                    String uniqueDeviceId = mBlaubot.getOwnDevice().getUniqueDeviceID();
                    JPanel icon = Util.createIcon(state, uniqueDeviceId);
                    mCurrentStateIconContainer.add(icon);
                    mCurrentStateIconContainer.setSize(icon.getSize());
                    mCurrentStateTextView.setText("");
                } else {
                    mCurrentStateTextView.setText("no instance registered to view!");
                }
            }
        });
    }
    
    private IBlaubotConnectionStateMachineListener mBlaubotConnectionStateMachineListener = new IBlaubotConnectionStateMachineListener() {
        

        @Override
        public void onStateChanged(IBlaubotState oldState, final IBlaubotState state) {
            mCurrentBlaubotState = state;
            updateStateIconAndText();
        }

        @Override
        public void onStateMachineStopped() {
            updateStateIconAndText();
            setButtonText();
        }

        @Override
        public void onStateMachineStarted() {
            updateStateIconAndText();
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
                    mConnectedDevicesCountTextView.setText(num + " P2P connections.");
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

    @Override
    public void registerBlaubotInstance(Blaubot blaubot) {
        if (this.mBlaubot != null) {
            unregisterBlaubotInstance();
        }
        this.mBlaubot = blaubot;
        this.mCurrentBlaubotState = blaubot.getConnectionStateMachine().getCurrentState();
        blaubot.getConnectionStateMachine().addConnectionStateMachineListener(mBlaubotConnectionStateMachineListener);
        blaubot.getConnectionManager().addConnectionListener(mConnectionManagerListener);
        
        setButtonText();
        updateStateIconAndText();
    }

    @Override
    public void unregisterBlaubotInstance() {
        if (this.mBlaubot != null) {
            mBlaubot.getConnectionStateMachine().removeConnectionStateMachineListener(mBlaubotConnectionStateMachineListener);
            mBlaubot.getConnectionManager().removeConnectionListener(mConnectionManagerListener);
        }
        this.mBlaubot = null;
        this.mCurrentBlaubotState = null;
    }
}

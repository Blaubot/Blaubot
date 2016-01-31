package eu.hgross.blaubot.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;

import eu.hgross.blaubot.core.BlaubotDevice;
import eu.hgross.blaubot.core.BlaubotFactory;
import eu.hgross.blaubot.core.BlaubotKingdom;
import eu.hgross.blaubot.core.BlaubotServer;
import eu.hgross.blaubot.core.IBlaubotServerLifeCycleListener;
import eu.hgross.blaubot.core.acceptor.ConnectionMetaDataDTO;
import eu.hgross.blaubot.core.acceptor.IBlaubotConnectionAcceptor;
import eu.hgross.blaubot.util.Log;

/**
 * Shows the server state and connected kingdoms.
 */
public class BlaubotServerPanel extends JPanel {
    private static final String LOG_TAG = "StatusViewPanel";
    private static final long UPDATE_INTERVAL = 350;
    private BlaubotServer mBlaubotServer;

    /**
     * Starts/Stops the whole server (if registered)
     */
    private final JButton mStartStopButton;
    /**
     * The container which holds the selected kingdom's view
     */
    private final JPanel mBlaubotKingdomViewContainer;
    /**
     * The listview of kindomgs of the split pane
     */
    private final JList<String> mKingdomListView;
    /**
     * the model for the kindom list
     */
    private final DefaultListModel<String> mKingdomListViewModel;

    /**
     * Shows informations about the server (acceptor, ip, ...)
     */
    private final JLabel mServerInfoLabel;

    /**
     * Holds connected BlaubotKingdoms and their views.
     */
    private final ConcurrentHashMap<BlaubotKingdom, BlaubotKingdomView> mBlaubotKingdomViews;

    /**
     * Schedules the updateUiTask for the start/stop button state.
     * Yeah it's hacky and should be refactored.
     */
    private ScheduledExecutorService scheduledThreadPoolExecutor;

    /**
     * updates the start/stop state
     * TODO add listeners for start/stop to the server instead of polling
     */
    private Runnable updateUiTask = new Runnable() {
        @Override
        public void run() {
            setButtonText();
        }
    };

    public BlaubotServerPanel() {
        super();
        this.scheduledThreadPoolExecutor = Executors.newSingleThreadScheduledExecutor();
        scheduledThreadPoolExecutor.scheduleAtFixedRate(updateUiTask, (long) 0, UPDATE_INTERVAL, TimeUnit.MILLISECONDS);

        setLayout(new GridBagLayout());


        this.mBlaubotKingdomViews = new ConcurrentHashMap<>();
        this.mKingdomListViewModel = new DefaultListModel<>();
        this.mKingdomListView = new JList<>(mKingdomListViewModel);
        this.mKingdomListView.setMinimumSize(new Dimension(100, 200));
        this.mKingdomListView.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.mKingdomListView.addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                final String selectedKingdomUniqueDeviceId = mKingdomListView.getSelectedValue();
                if (selectedKingdomUniqueDeviceId == null) {
                    return;
                }
                // find blaubot kingdom
                BlaubotKingdom kingdom = null;
                for (BlaubotKingdom k : mBlaubotKingdomViews.keySet()) {
                    if (k.getKingDevice().getUniqueDeviceID().equals(selectedKingdomUniqueDeviceId)) {
                        kingdom = k;
                    }
                }
                if (kingdom == null) {
                    return;
                }
                final BlaubotKingdomView blaubotKingdomView = mBlaubotKingdomViews.get(kingdom);
                mBlaubotKingdomViewContainer.removeAll();
                mBlaubotKingdomViewContainer.add(blaubotKingdomView);
                updateUI();
            }
        });

        this.mBlaubotKingdomViewContainer = new JPanel();

        this.mStartStopButton = new JButton();
        this.mStartStopButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (mBlaubotServer == null) {
                    if (Log.logWarningMessages()) {
                        Log.w(LOG_TAG, "Blaubot not set - ignoring onClick.");
                    }
                    return;
                }
                mStartStopButton.setEnabled(false);
                if (mBlaubotServer.isStarted()) {
                    mBlaubotServer.stopBlaubotServer();
                } else {
                    mBlaubotServer.startBlaubotServer();
                }
            }
        });

        this.mServerInfoLabel = new JLabel();

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(2, 2, 2, 2);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 0;
        c.gridheight = 1;
        c.gridwidth = 2;
        add(mStartStopButton, c);

        c.gridy++;
        add(mServerInfoLabel, c);

        c.gridwidth = 1;
        c.gridx = 0;
        c.gridy++;
        add(new JLabel("Connected kingdoms:"), c);

        c.gridx = 1;
        add(new JLabel("Details:"), c);

        c.fill = GridBagConstraints.BOTH;
        c.gridx = 0;
        c.gridy++;
        add(mKingdomListView, c);

        c.gridx = 1;
        add(mBlaubotKingdomViewContainer, c);
    }


    private IBlaubotServerLifeCycleListener mServerLifeCycleListener = new IBlaubotServerLifeCycleListener() {
        @Override
        public void onKingdomConnected(BlaubotKingdom kingdom) {
            BlaubotKingdomView bkv = new BlaubotKingdomView();
            bkv.registerBlaubotKingdomInstance(kingdom);
            Component prev = mBlaubotKingdomViews.put(kingdom, bkv);
            if (prev != null) {
                removeView((BlaubotKingdomView) prev, kingdom);
            }
            mKingdomListViewModel.addElement(kingdom.getKingDevice().getUniqueDeviceID());
            boolean nothingSelected = mKingdomListView.getSelectedIndex() == -1;
            if (nothingSelected) {
                mKingdomListView.setSelectedIndex(0);
            }
            updateUI();
        }

        @Override
        public void onKingdomDisconnected(BlaubotKingdom kingdom) {
            System.out.println("disc: " + kingdom);
            Component remove = mBlaubotKingdomViews.remove(kingdom);
            if (remove != null) {
                removeView((BlaubotKingdomView) remove, kingdom);
            }
            updateUI();
        }

        private void removeView(BlaubotKingdomView view, BlaubotKingdom kingdom) {
            view.unregisterBlaubotKingdomInstance();
            mKingdomListViewModel.removeElement(kingdom.getKingDevice().getUniqueDeviceID());
            // remove from container, if set
            mBlaubotKingdomViewContainer.remove(view);
        }
    };


    private void setButtonText() {
        if (mBlaubotServer == null) {
            return;
        }
        final boolean started = mBlaubotServer.isStarted();
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                // await either started or stopped
                if (started) {
                    mStartStopButton.setText("Stop BlaubotServer");
                } else {
                    mStartStopButton.setText("Start BlaubotServer");
                }
                mStartStopButton.setEnabled(true);
            }
        });
    }


    public void registerBlaubotServerInstance(BlaubotServer blaubotServer) {
        if (this.mBlaubotServer != null) {
            unregisterBlaubotServerInstance();
        }
        this.mBlaubotServer = blaubotServer;
        blaubotServer.addServerLifeCycleListener(mServerLifeCycleListener);
        setUpInfoLabel();
        setButtonText();
    }

    public void unregisterBlaubotServerInstance() {
        if (this.mBlaubotServer != null) {
            mBlaubotServer.removeServerLifeCycleListener(mServerLifeCycleListener);

        }
        this.mBlaubotServer = null;
    }

    /**
     * Updates the general server information textview.
     */
    private void setUpInfoLabel() {
        final String labelTemplate = "" +
                "<html>" +
                "   <table>" +
                "       <tr>" +
                "           <th>Acceptors: </th>" +
                "           <td>%s</td>" +
                "       </tr>" +
                "       <tr>" +
                "           <th>ConnectionMetaData: </th>" +
                "           <td>%s</td>" +
                "       </tr>" +
                "   </table>" +
                "</html>";
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                if (mBlaubotServer == null) {
                    mServerInfoLabel.setText("No server instance registered to GUI.");
                } else {
                    final List<IBlaubotConnectionAcceptor> acceptors = mBlaubotServer.getAcceptors();
                    final ArrayList<ConnectionMetaDataDTO> connectionMetaData = new ArrayList<>();

                    for (IBlaubotConnectionAcceptor acceptor : acceptors) {
                        connectionMetaData.add(acceptor.getConnectionMetaData());
                    }

                    mServerInfoLabel.setText(String.format(labelTemplate, acceptors, connectionMetaData));
                }
            }
        });
    }

    /**
     * Creates the gui for the server and displays the returned JFrame in a new Thread.
     *
     * @param blaubotServer the server instance
     * @return the JFrame that is shown.
     */
    public static JFrame createAndshowGui(final BlaubotServer blaubotServer) {
        FutureTask<JFrame> futureTask = new FutureTask<JFrame>(new Callable<JFrame>() {
            @Override
            public JFrame call() throws Exception {
                BlaubotServerPanel serverPanel = new BlaubotServerPanel();
                serverPanel.registerBlaubotServerInstance(blaubotServer);

                JFrame frame = new JFrame();
                frame.getContentPane().setLayout(new FlowLayout(FlowLayout.CENTER, 0, 0));
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setMinimumSize(new Dimension(900, 500));
                frame.add(serverPanel);
                frame.pack();
                frame.setVisible(true);
                frame.setTitle("Blaubot Server");
                return frame;
            }
        });
        new Thread(futureTask).start();
        try {
            return futureTask.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws ClassNotFoundException {
        final BlaubotServer websocketServer = BlaubotFactory.createBlaubotWebsocketServer(new BlaubotDevice("Server1"));
        BlaubotServerPanel.createAndshowGui(websocketServer);
    }
}

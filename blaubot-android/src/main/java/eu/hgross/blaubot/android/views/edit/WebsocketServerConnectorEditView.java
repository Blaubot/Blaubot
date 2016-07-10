package eu.hgross.blaubot.android.views.edit;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.android.views.ViewUtils;
import eu.hgross.blaubot.core.Blaubot;
import eu.hgross.blaubot.core.BlaubotFactory;
import eu.hgross.blaubot.core.BlaubotServerConnector;
import eu.hgross.blaubot.core.IBlaubotDevice;

/**
 * Provides a user interface to create a websocket server connector.
 * Can be reused inside a dialog to create websocket server conncetors when needed.
 * USAGE:
 *  WebsocketServerConnectorEditView.createAsDialog(myContext, myBlaubot).show();
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class WebsocketServerConnectorEditView extends FrameLayout {
    private static final String LOG_TAG = "WebsocketServerConnectorEditView";
    private Handler mUiHandler;
    private View mMainView;

    private EditText mHostnameEditText;
    private EditText mPathEditText;
    private EditText mPortEditText;
    private EditText mServerUniqueDeviceId;

    public WebsocketServerConnectorEditView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView(context, attrs);
    }

    public WebsocketServerConnectorEditView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView(context, attrs);
    }

    private void initView(Context context, AttributeSet attrs) {
        this.mMainView = inflate(getContext(), R.layout.blaubot_websocket_serverconnector_editview, null);
        this.mHostnameEditText = (EditText) mMainView.findViewById(R.id.hostnameOrIpEditText);
        this.mPortEditText = (EditText) mMainView.findViewById(R.id.port);
        this.mPathEditText = (EditText) mMainView.findViewById(R.id.path);
        this.mServerUniqueDeviceId = (EditText) mMainView.findViewById(R.id.serverUniqueDeviceId);
        mUiHandler = new Handler(Looper.getMainLooper());
        addView(this.mMainView);

        this.mHostnameEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mHostnameEditText.setError(isHostOrIpValid() ? null : "Not a valid hostname or IP-Address.");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        this.mPortEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mPortEditText.setError(isPortValid() ? null : "Port is not valid. Should be in [1025, 65535]");
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        this.mPathEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mPathEditText.setError(isPathSegmentValid() ? null : "Not a valid path.");
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });

        this.mServerUniqueDeviceId.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                mServerUniqueDeviceId.setError(isServerUniqueDeviceIdValid() ? null : "Not a valid unique device id.");
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
    }

    private boolean isHostOrIpValid() {
        final String val = mHostnameEditText.getText().toString();
        final boolean valid = ViewUtils.isValidHostname(val) || ViewUtils.isValidIpAddress(val);
        return valid;
    }

    private boolean isPortValid() {
        final String val = mPortEditText.getText().toString();
        try {
            int number = Integer.valueOf(val);
            return number > 1024 || number < 65535;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private boolean isPathSegmentValid() {
        final String val = mPathEditText.getText().toString();
        return !val.isEmpty() && val.startsWith("/") && ViewUtils.validateURLPathSegment(val);
    }

    private boolean isServerUniqueDeviceIdValid() {
        final String serverUniqueDeviceId = mServerUniqueDeviceId.getText().toString();
        // TODO: there are plans in my mind to limit the length of unique device ids ... has to be reflected here
        return !serverUniqueDeviceId.isEmpty();
    }

    private boolean isInputValid() {
        return isHostOrIpValid() && isPortValid() && isPathSegmentValid() && isServerUniqueDeviceIdValid();
    }


    /**
     * Tries to construct the server connector from the view's inputs.
     *
     * @param ownDevice the own device of the connecting blaubot instance
     * @return the server connector
     * @throws IllegalArgumentException if the user input was not valid
     * @throws ClassNotFoundException if the blaubot-websocket jar is not in the classpath
     */
    public BlaubotServerConnector createConnector(IBlaubotDevice ownDevice) throws IllegalArgumentException, ClassNotFoundException {
        if (!isInputValid()) {
            throw new IllegalArgumentException();
        }
        int port = Integer.valueOf(mPortEditText.getText().toString());
        String path = mPathEditText.getText().toString();
        String hostOrIp = mHostnameEditText.getText().toString();
        String serverUniqueDeviceId = mServerUniqueDeviceId.getText().toString();

        return BlaubotFactory.createWebSocketServerConnector(hostOrIp, port, path, ownDevice, serverUniqueDeviceId);
    }


    /**
     * Creates this view as a dialog that allows the user to create a server connector which is then
     * attached to the given blaubot instance.
     *
     * @param context the context
     * @param blaubot the blaubot instance to create the connector for
     * @param presetHostname the hostname with which the form is initially filled 
     * @param presetPort the port with which the form is initially filled 
     * @param presetPath  the path with which the form is initially filled
     * @param presetServerUniqueDeviceId  the server's unique device id with which the form is initially filled
     * @return the dialog. Call dialog.show() to use it.
     */
    public static Dialog createAsDialog(Context context, final Blaubot blaubot, String presetHostname, int presetPort, String presetPath, String presetServerUniqueDeviceId) {
        final WebsocketServerConnectorEditView view = new WebsocketServerConnectorEditView(context, null);

        // set the preset fields
        view.mHostnameEditText.setText(presetHostname);
        view.mServerUniqueDeviceId.setText(presetServerUniqueDeviceId);
        view.mPortEditText.setText(presetPort+"");
        view.mPathEditText.setText(presetPath);

        // build the dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(view)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        final BlaubotServerConnector connector;
                        try {
                            connector = view.createConnector(blaubot.getOwnDevice());
                            blaubot.setServerConnector(connector);
                        } catch (ClassNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setTitle("Create server connector");
        return builder.create();
    }


}
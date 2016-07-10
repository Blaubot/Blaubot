package eu.hgross.blaubot.android.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.Arrays;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.util.Log;

/**
 * Allows to set the LogLevel at runtime.
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 * 
 */
public class LogLevelView extends FrameLayout {
    private static final String LOG_TAG = "LogLevelView";
    private Spinner mLogLevelSpinner;
    private LinearLayout mMainView;

    public LogLevelView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public LogLevelView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    private void initView() {
        this.mMainView = (LinearLayout) inflate(getContext(), R.layout.blaubot_log_level_view, null);
        this.mLogLevelSpinner = (Spinner) mMainView.findViewById(R.id.logLevelSpinner);
        final LogLevelArrayAdapter adapter = new LogLevelArrayAdapter(getContext());
        this.mLogLevelSpinner.setAdapter(adapter);
        this.mLogLevelSpinner.setSelection(adapter.getPosition(Log.LOG_LEVEL));
        this.mLogLevelSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                final Log.LogLevel item = adapter.getItem(position);
                if (Log.logDebugMessages()) {
                    Log.d(LOG_TAG, "setting log level to: " + item.name());
                }
                Log.LOG_LEVEL = item;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {

            }
        });
        addView(mMainView);
    }

    private static class LogLevelArrayAdapter extends ArrayAdapter<Log.LogLevel> {
        public LogLevelArrayAdapter(Context context) {
            super(context, R.layout.blaubot_log_level_list_item, R.id.name, Arrays.asList(Log.LogLevel.values()));
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
            final Log.LogLevel logLevel = getItem(position);
            final View view = convertView != null ? convertView : View.inflate(this.getContext(), R.layout.blaubot_log_level_list_item, null);
            final TextView nameTextView = (TextView) view.findViewById(R.id.name);
            final TextView descriptionTextView = (TextView) view.findViewById(R.id.description);

            nameTextView.setText(logLevel.name() + "");
//            descriptionTextView.setText(getDescriptionForPickerStrategy(strategy));
            descriptionTextView.setVisibility(GONE);

            return view;
        }
    }
}
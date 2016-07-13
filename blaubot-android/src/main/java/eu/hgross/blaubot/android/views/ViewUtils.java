package eu.hgross.blaubot.android.views;

import android.graphics.drawable.Drawable;

import eu.hgross.blaubot.android.R;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.core.statemachine.states.IBlaubotState;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;

import java.util.regex.Pattern;


/**
 * Helper for some commonly used methods around the debug views.
 */
public class ViewUtils {
    /**
     * Retrieve the corresponding drawable by the blaubot state
     *
     * @param ctx   the context
     * @param state the blaubot state to retrieve the drawable for
     * @return the drawable
     * @throws java.lang.IllegalStateException if the state is not mapped to an icon
     */
    public static Drawable getDrawableForBlaubotState(Context ctx, IBlaubotState state) {
        final State stateEnum = State.getStateByStatemachineClass(state.getClass());
        return getDrawableForBlaubotState(ctx, stateEnum);
    }

    /**
     * Retrieve the corresponding drawable by the blaubot state
     *
     * @param ctx       the context
     * @param stateEnum the blaubot state to retrieve the drawable for
     * @return the drawable
     * @throws java.lang.IllegalStateException if the state is not mapped to an icon
     */
    public static Drawable getDrawableForBlaubotState(Context ctx, State stateEnum) {
        final int resId;
        if (stateEnum.equals(State.Stopped)) {
            resId = R.drawable.ic_stopped;
        } else if (stateEnum.equals(State.Free)) {
            resId = R.drawable.ic_free;
        } else if (stateEnum.equals(State.Peasant)) {
            resId = R.drawable.ic_peasant;
        } else if (stateEnum.equals(State.Prince)) {
            resId = R.drawable.ic_prince;
        } else if (stateEnum.equals(State.King)) {
            resId = R.drawable.ic_king;
        } else {
            throw new IllegalStateException("State " + stateEnum + " unknown.");
        }
        return ctx.getResources().getDrawable(resId);
    }

    /**
     * {@link TextWatcher} adapter class bound to a {@link View}
     */
    public abstract class SettingsTextWatcher implements TextWatcher {
        protected View view;

        public SettingsTextWatcher(View view) {
            this.view = view;
        }

        @Override
        public void afterTextChanged(Editable s) {
            if (validate(s.toString())) {
                ((EditText) view).setError(null);
                onSettingChanged(s.toString());
            } else {
                ((EditText) view).setError("Invalid value");
            }
        }

        /**
         * Called iff the text was changed and validation was successful.
         *
         * @param text the new text
         */
        public abstract void onSettingChanged(String text);

        /**
         * @param val the current edittext's state
         * @return true if val is a valid input, false otherwise
         */
        protected abstract boolean validate(String val);

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    /**
     * A Regex based SettingsTextWatcher to simplify validation
     */
    public abstract class RegexSettingsTextWatcher extends SettingsTextWatcher {
        private final Pattern pattern;

        public RegexSettingsTextWatcher(View view, String pattern) {
            super(view);
            this.pattern = Pattern.compile(pattern);
        }

        protected boolean validate(String val) {
            return pattern.matcher(val).matches();
        };

    }

    private static final Pattern URL_PATTERN = Pattern.compile("^(([^:/?#]+):)?(//([^/?#]*))?([^?#]*)(\\?([^#]*))?(#(.*))?");
    /**
     * Validates url path segements.
     * 
     * @param val the url path segment to be validated
     * @return true iff val is a valid URL path (without protocol, host, port)
     */
    public static boolean validateURLPathSegment(String val) {
        val = "http://example.com" + val;
        return URL_PATTERN.matcher(val).matches();
    }

    private static final Pattern validIpAddressRegex = Pattern.compile("^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$");
    private static final Pattern validHostnameRegex = Pattern.compile("^(([a-zA-Z0-9]|[a-zA-Z0-9][a-zA-Z0-9\\-]*[a-zA-Z0-9])\\.)*([A-Za-z0-9]|[A-Za-z0-9][A-Za-z0-9\\-]*[A-Za-z0-9])$");

    /**
     * Validates a hostname
     * 
     * @param hostname the hostname to be validated
     * @return true, iff hostname is a valid hostname (not an ip)
     */
    public static boolean isValidHostname(String hostname) {
        return validHostnameRegex.matcher(hostname).matches();
    }

    /**
     * Checks wheter an ipv4 address is valid
     * 
     * @param ip the ip to be checked
     * @return true, iff ip is a valid ip address
     */
    public static boolean isValidIpAddress(String ip) {
        return validHostnameRegex.matcher(ip).matches();
    }

    /**
     * Converts a byte size into a human readable sting
     * @param bytes the number of bytes
     * @param si if true, the output will be in si units
     * @return the human readable string
     */
    public static String humanReadableByteCount(final long bytes, final boolean si) {
        final int unit = si ? 1000 : 1024;
        if (bytes < unit) return bytes + " B";
        final int exp = (int) (Math.log(bytes) / Math.log(unit));
        final String pre = (si ? "kMGTPE" : "KMGTPE").charAt(exp-1) + (si ? "" : "i");
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}

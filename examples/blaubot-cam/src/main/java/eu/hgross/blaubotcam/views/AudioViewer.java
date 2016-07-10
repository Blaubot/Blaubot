package eu.hgross.blaubotcam.views;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import eu.hgross.blaubot.android.views.ViewUtils;
import eu.hgross.blaubotcam.R;
import eu.hgross.blaubotcam.audio.BlaubotWalkieTalkie;
import eu.hgross.blaubotcam.audio.IPlaybackListener;
import eu.hgross.blaubotcam.audio.WalkieTalkieMessage;

/**
 * Displays status of the walkietalkie playback on received messagees.
 * Attach it to a BlaubotWalkieTalkie.
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class AudioViewer extends FrameLayout implements IPlaybackListener {
    private static final String LOG_TAG = "AudioViewer";
    private Handler mUiHandler;
    /**
     * Maps the unique device id to the number of current playbacks for this device
     */
    private ConcurrentHashMap<String, AtomicInteger> mPlaybacks;
    /**
     * Maps the unique device id to the view, displaying the last image
     */
    private ConcurrentHashMap<String, View> mViews;
    /**
     * The walkie talkie instance to be visualized
     */
    private BlaubotWalkieTalkie mWalkieTalkie;
    /**
     * The main view containing the audio indicator icon
     */
    private View mMainView;
    /**
     * Indicates if there is any playback at the moment
     */
    private ImageView mAudioIndicatorIcon;
    /**
     * Indicates the number of current playbakcs
     */
    private TextView mNumberOfPlaybacksTextView;
    /**
     * Is GONE by default and can be made visible by clicking on the indicator.
     * Shows a list of all current playbacks.
     */
    private LinearLayout mCurrentPlaybacksContainer;
    private Drawable mTalkingPeasantsDrawable;
    private Drawable mNoOneTalkingDrawable;
    

    public AudioViewer(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public AudioViewer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    private void initView() {
        mViews = new ConcurrentHashMap<>();
        mPlaybacks = new ConcurrentHashMap<>();
        mUiHandler = new Handler(Looper.getMainLooper());
        mMainView = inflate(getContext(), R.layout.audio_viewer, null);
        mAudioIndicatorIcon = (ImageView) mMainView.findViewById(R.id.audioIndicatorIcon);
        mNumberOfPlaybacksTextView = (TextView) mMainView.findViewById(R.id.numberOfPlaybacksTextView);
        mCurrentPlaybacksContainer = (LinearLayout) mMainView.findViewById(R.id.currentPlaybacksContainer);
        mTalkingPeasantsDrawable = getResources().getDrawable(R.drawable.ic_peasant_talking);
        mNoOneTalkingDrawable = getResources().getDrawable(R.drawable.ic_stopped);
        mAudioIndicatorIcon.setImageDrawable(mNoOneTalkingDrawable);
        
        // add onclick to indicator to toggle the container
        mMainView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                final boolean isVisible = mCurrentPlaybacksContainer.getVisibility() == VISIBLE;
                mCurrentPlaybacksContainer.setVisibility(isVisible ? GONE : VISIBLE);
            }
        });
        mCurrentPlaybacksContainer.setVisibility(VISIBLE);
        addView(mMainView);
    }

    public void setWalkieTalkie(BlaubotWalkieTalkie walkieTalkie) {
        this.mWalkieTalkie = walkieTalkie;
        this.mWalkieTalkie.addPlaybackListener(this);
        updateUI(null);
    }

    /**
     * updates the state images for all clients and the image only for the given uniqueDeviceId
     *
     * @param message the image message that needs to be rendered
     */
    private void updateUI(final WalkieTalkieMessage message) {
        // find out if we have any current playback
        int sum = 0;
        for (AtomicInteger i : mPlaybacks.values()) {
            sum += i.get();
        }
        
        final int numberOfCurrentPlaybacks = sum;
        final int numberOfPlaybacksByCurrentMessageSender = message == null ? 0 : mPlaybacks.get(message.getSenderUniqueDeviceId()).get();


        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                // maintain main View
                mNumberOfPlaybacksTextView.setText(numberOfCurrentPlaybacks == 0 ? "Not playing any audio" : "Playing " + numberOfCurrentPlaybacks + " audio messages.");
                mAudioIndicatorIcon.setImageDrawable(numberOfCurrentPlaybacks > 0 ? mTalkingPeasantsDrawable : mNoOneTalkingDrawable);

                // maintain the view for the current message's sender (if any message)
                if (message != null) {
                    final String senderUniqueDeviceId = message.getSenderUniqueDeviceId();

                    // add the sender's data
                    final String numBytes = ViewUtils.humanReadableByteCount(message.getAudioBytes().length, true);
                    View playbackListItem = createPlaybackListItemNameElement(getContext(), mTalkingPeasantsDrawable, senderUniqueDeviceId, numBytes);
                    final boolean added = mViews.putIfAbsent(senderUniqueDeviceId, playbackListItem) == null;
                    if (!added) {
                        playbackListItem = mViews.get(senderUniqueDeviceId);

                        // just update
                        TextView titleTextView = (TextView) playbackListItem.findViewById(R.id.text);
                        titleTextView.setText(numBytes);
                    } else {
                        // only add, if view was newly created (not reused)
                        mCurrentPlaybacksContainer.addView(playbackListItem);
                    } 
                    
                    if (numberOfPlaybacksByCurrentMessageSender <= 0) {
                        // remove view, if no playbacks anymore
                        mCurrentPlaybacksContainer.removeView(playbackListItem);
                        mViews.remove(senderUniqueDeviceId);
                    }
                }
                System.out.println("numViews:" + mCurrentPlaybacksContainer.getChildCount());
            }
        });
    }

    /**
     * Creates a list item 
     * @param context the context
     * @param uniqueDeviceId the unique device id
     * @param title the title text to be displayed instead of the state name
     * @return the view
     */
    private static View createPlaybackListItemNameElement(Context context, Drawable icon, String uniqueDeviceId, String title) {
        View item = inflate(context, R.layout.audio_viewer_playback_list_item, null);
        TextView uniqueDeviceIdTextView = (TextView) item.findViewById(R.id.uniqueDeviceIdLabel);
        TextView titleTextView = (TextView) item.findViewById(R.id.text);
        ImageView iconImageView = (ImageView) item.findViewById(R.id.icon);
        iconImageView.setImageDrawable(icon);
        uniqueDeviceIdTextView.setText(uniqueDeviceId);
        titleTextView.setText(title);
        return item;
    }
    
    @Override
    public void beforePlayback(WalkieTalkieMessage walkieTalkieMessage) {
        mPlaybacks.putIfAbsent(walkieTalkieMessage.getSenderUniqueDeviceId(), new AtomicInteger(0));
        final AtomicInteger counter = mPlaybacks.get(walkieTalkieMessage.getSenderUniqueDeviceId());
        counter.incrementAndGet();
        updateUI(walkieTalkieMessage);
    }

    @Override
    public void afterPlayback(WalkieTalkieMessage walkieTalkieMessage) {
        final AtomicInteger counter = mPlaybacks.get(walkieTalkieMessage.getSenderUniqueDeviceId());
        counter.decrementAndGet();
        updateUI(walkieTalkieMessage);
    }
}
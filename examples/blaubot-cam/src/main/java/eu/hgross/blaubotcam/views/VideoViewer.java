package eu.hgross.blaubotcam.views;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

import eu.hgross.blaubot.android.views.KingdomView;
import eu.hgross.blaubot.core.State;
import eu.hgross.blaubot.admin.AbstractAdminMessage;
import eu.hgross.blaubot.admin.CensusMessage;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.IBlaubotAdminMessageListener;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;
import eu.hgross.blaubotcam.video.ImageMessage;
import eu.hgross.blaubotcam.R;

/**
 * Displays ImageMessages.
 * Attach it to the channel from where you receive the ImageMessages.
 *
 * @author Henning Gross {@literal (mail.to@henning-gross.de)}
 */
public class VideoViewer extends LinearLayout implements IBlaubotMessageListener, IBlaubotAdminMessageListener {
    private static final String LOG_TAG = "VideoViewer";
    private Handler mUiHandler;
    /**
     * Maps the unique device id to the view, displaying the last image
     */
    private ConcurrentHashMap<String, View> mViews;
    /**
     * The last census message. May be null.
     */
    private CensusMessage mLastCensusMessage;
    /**
     * A cache for bitmaps filled when ImageMessages arrive
     * uniquedeviceid- > bitmap
     */
    private ConcurrentHashMap<String, Bitmap> mBitmapCache;

    public VideoViewer(Context context, AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public VideoViewer(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initView();
    }

    private void initView() {
        mViews = new ConcurrentHashMap<>();
        mBitmapCache = new ConcurrentHashMap<>();
        mUiHandler = new Handler(Looper.getMainLooper());
    }


    /**
     * updates the state images for all clients and the image only for the given uniqueDeviceId
     *
     * @param message the image message that needs to be rendered
     */
    private void updateUI(ImageMessage message) {
        final String uniqueDeviceId = message.getUniqueDeviceId();
        final Date time = message.getTime();
        // pre-process the bitmap conversion to not do this on the ui thread.
        mBitmapCache.put(uniqueDeviceId, message.toBitmap());

        mUiHandler.post(new Runnable() {
            @Override
            public void run() {
                final String senderUniqueDeviceId = uniqueDeviceId;
                View item = inflate(getContext(), R.layout.video_viewer_item, null);
                final boolean added = mViews.putIfAbsent(senderUniqueDeviceId, item) == null;
                item = mViews.get(senderUniqueDeviceId);
                FrameLayout nameContainer = (FrameLayout) item.findViewById(R.id.nameContainer);
                TextView timestampTextView = (TextView) item.findViewById(R.id.timestampTextView);
                ImageView imageView = (ImageView) item.findViewById(R.id.imageView);

                // set the timestamp
                timestampTextView.setText(time.toString());

                // set the new picture for the uniqueDeviceId, for which this was requested
                Bitmap bm = mBitmapCache.get(uniqueDeviceId);
                if (bm == null) {
                    // something went sideways decoding a bitmap!
                } else {
                    imageView.setImageBitmap(bm);
                    // set the name container
                    final int width = bm.getWidth();
                    final int height = bm.getHeight();
                    nameContainer.removeAllViews();
                    nameContainer.setLayoutParams(new RelativeLayout.LayoutParams(width, RelativeLayout.LayoutParams.WRAP_CONTENT));

                    // set the wrapping containers dimensions to the pictures dimensions
                    item.setLayoutParams(new LinearLayout.LayoutParams(width, height));
                }

                // add the sender's data
                View kingdomViewListItem = null;
                if (mLastCensusMessage != null) {
                    final State state = mLastCensusMessage.getDeviceStates().get(senderUniqueDeviceId);
                    if (state != null) {
                        kingdomViewListItem = KingdomView.createKingdomViewListItem(getContext(), state, senderUniqueDeviceId);
                    }
                }

                // if we don't have the state in the last message, we know the instance is not part of the network
                // we display the stopped icon assumg it is stopped, dead or ...
                if (kingdomViewListItem == null) {
                    kingdomViewListItem = KingdomView.createKingdomViewListItem(getContext(), State.Stopped, senderUniqueDeviceId);
                }

                // add the namecontainer's content
                nameContainer.addView(kingdomViewListItem);

                // only add, if view was newly created (not reused)
                if (added) {
                    addView(item);
                }
            }
        });
    }


    @Override
    public void onMessage(BlaubotMessage blaubotMessage) {
        final ImageMessage imageMessage = new ImageMessage(blaubotMessage.getPayload());
        updateUI(imageMessage);
    }

    @Override
    public void onAdminMessage(AbstractAdminMessage adminMessage) {
        if (adminMessage instanceof CensusMessage) {
            mLastCensusMessage = (CensusMessage) adminMessage;
        }
    }
}
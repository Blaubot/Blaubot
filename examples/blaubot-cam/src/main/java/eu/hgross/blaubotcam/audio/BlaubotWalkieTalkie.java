package eu.hgross.blaubotcam.audio;

import android.content.Context;
import android.media.MediaRecorder;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import eu.hgross.blaubot.core.IBlaubotDevice;
import eu.hgross.blaubot.messaging.BlaubotMessage;
import eu.hgross.blaubot.messaging.IBlaubotChannel;
import eu.hgross.blaubot.messaging.IBlaubotMessageListener;
import eu.hgross.blaubot.util.Log;

/**
 * Uses the microphone to record and send data through a BlaubotChannel.
 */
public class BlaubotWalkieTalkie {
    private static final String LOG_TAG = BlaubotWalkieTalkie.class.getSimpleName();

    private final IBlaubotDevice mOwnDevice;
    private final IBlaubotChannel mBlaubotChannel;
    private final Context mContext;

    private MediaRecorder mMediaRecorder;
    private Object mRecorderLock = new Object();
    private CopyOnWriteArrayList<IRecordingListener> mRecordingListeners;
    private CopyOnWriteArrayList<IPlaybackListener> mPlaybackListeners;
    private ExecutorService mExecutorService = Executors.newCachedThreadPool();
    private boolean mReceiveOwnMessages;

    public BlaubotWalkieTalkie(Context context, IBlaubotChannel channel, IBlaubotDevice ownDevice) {
        this.mContext = context;
        this.mOwnDevice = ownDevice;
        this.mBlaubotChannel = channel;
        this.mRecordingListeners = new CopyOnWriteArrayList<>();
        this.mPlaybackListeners = new CopyOnWriteArrayList<>();
        this.mReceiveOwnMessages = false;
        channel.subscribe();
        channel.addMessageListener(messageListener);
    }


    /**
     * Receives audio data via a Blaubot channel
     */
    private final IBlaubotMessageListener messageListener = new IBlaubotMessageListener() {
        @Override
        public void onMessage(final BlaubotMessage blaubotMessage) {
            mExecutorService.execute(new Runnable() {
                @Override
                public void run() {
                    WalkieTalkieMessage walkieTalkieMessage = WalkieTalkieMessage.fromBytes(blaubotMessage.getPayload());
                    Log.d(LOG_TAG, "Received " + blaubotMessage.getPayload().length + " bytes");
                    // play
                    try {
                        notifyPlaybackStarted(walkieTalkieMessage);
                        walkieTalkieMessage.play(mContext);
                    } catch (IOException e) {
                        if (Log.logErrorMessages()) {
                            Log.e(LOG_TAG, "Could not play received audio data: " + e.getMessage(), e);
                        }
                    }
                    notifyPlaybackFinished(walkieTalkieMessage);
                }
            });
        }
    };

    private File mRecordFile;

    /**
     * Starts recording
     */
    public void startRecording() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "startRecording()");
        }
        synchronized (mRecorderLock) {
            if (mRecordFile != null) {
                return;
            }
            try {
                mRecordFile = File.createTempFile("BlaubotTempAudioRecording", "3gp", mContext.getCacheDir());
            } catch (IOException e) {
                if (Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "Could not create TempFile: " + e.getMessage(), e);
                }
                return;
            }
            mMediaRecorder = new MediaRecorder();
            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            mMediaRecorder.setAudioChannels(1);
            mMediaRecorder.setAudioEncodingBitRate(48000);
            mMediaRecorder.setOutputFile(mRecordFile.getAbsolutePath());
            try {
                mMediaRecorder.prepare();
            } catch (IOException e) {
                if (Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "prepare() failed: " + e.getMessage());
                }
            }

            mMediaRecorder.start();
        }
        notifyRecordingStarted();
    }

    /**
     * Stops recording (if any)
     */
    public void stopRecording() {
        if (Log.logDebugMessages()) {
            Log.d(LOG_TAG, "stopRecording()");
        }
        synchronized (mRecorderLock) {
            if (mMediaRecorder == null) {
                return;
            }
            try {
                mMediaRecorder.stop();
                mMediaRecorder.reset();
            } catch (RuntimeException e) {
                // we have to catch it, see stop() JavaDoc
                if (Log.logErrorMessages()) {
                    Log.e(LOG_TAG, "Could not stop or reset media recorder: " + e.getMessage(), e);
                }
                mMediaRecorder = null;
            }
            final String filePath = mRecordFile.getAbsolutePath();
            onRecordingStopped(filePath);
            mRecordFile.delete();
            mRecordFile = null;
        }
        notifyRecordingStopped();
    }

    /**
     * Releases all resources
     */
    public void release() {
        synchronized (mRecorderLock) {
            try {
                if (mMediaRecorder != null) {
                    mMediaRecorder.stop();
                }
            } catch (RuntimeException e) {
                // dont care
            }
            if (mRecordFile != null) {
                mRecordFile.delete();
            }
            try {
                if (mMediaRecorder != null) {
                    mMediaRecorder.release();
                }
            } catch (RuntimeException e) {
                // dont care
            }
        }
    }

    /**
     * Called whenever a audio recording was made
     *
     * @param dataFile the path of the file holding the audio data
     */
    private void onRecordingStopped(String dataFile) {
        // get the bytes
        try {
            byte[] audioBytes = IOUtils.toByteArray(new FileInputStream(new File(dataFile)));
            if (Log.logDebugMessages()) {
                Log.d(LOG_TAG, "Recorded " + audioBytes.length + " bytes");
            }
            WalkieTalkieMessage walkieTalkieMessage = new WalkieTalkieMessage(audioBytes, mOwnDevice.getUniqueDeviceID());
            mBlaubotChannel.publish(walkieTalkieMessage.toBytes(), !mReceiveOwnMessages);
        } catch (IOException e) {
            if (Log.logErrorMessages()) {
                Log.e(LOG_TAG, "Could not read bytes from file: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Set if the walkie talkie should receiv it's own messages (audio sent by this instance)
     *
     * @param receiveOwnMessages if true, messages sent by this walkie talkie will also be received by this walkie talkie
     */
    public void setReceiveOwnMessages(boolean receiveOwnMessages) {
        this.mReceiveOwnMessages = receiveOwnMessages;
    }

    /**
     * Returns the receive own messages flag.
     *
     * @return if true, messages sent by this walkie talkie will also be received by this walkie talkie
     */
    public boolean isReceiveOwnMessages() {
        return mReceiveOwnMessages;
    }

    
    /*
        Listener implementation from here
     */

    /**
     * Notifies the listeners, that the recording started
     */
    private void notifyRecordingStarted() {
        for (IRecordingListener listener : mRecordingListeners) {
            listener.onRecordingStarted();
        }
    }

    /**
     * Notifies the listeners, that the recording stopped
     */
    private void notifyRecordingStopped() {
        for (IRecordingListener listener : mRecordingListeners) {
            listener.onRecordingStopped();
        }
    }

    /**
     * Adds a recording listener to this walkietalkie
     *
     * @param recordingListener the listener
     */
    public void addRecordingListener(IRecordingListener recordingListener) {
        mRecordingListeners.add(recordingListener);
    }

    /**
     * Removes a recording listener from this walkietalkie
     *
     * @param recordingListener the listener to be removed
     */
    public void removeRecordingListener(IRecordingListener recordingListener) {
        mRecordingListeners.remove(recordingListener);
    }

    /**
     * Notifies the listeners, that the plaback started
     *
     * @param msg the message
     */
    private void notifyPlaybackStarted(WalkieTalkieMessage msg) {
        for (IPlaybackListener listener : mPlaybackListeners) {
            listener.beforePlayback(msg);
        }
    }

    /**
     * Notifies the listeners, that the playback has finished started
     *
     * @param msg the message
     */
    private void notifyPlaybackFinished(WalkieTalkieMessage msg) {
        for (IPlaybackListener listener : mPlaybackListeners) {
            listener.afterPlayback(msg);
        }
    }

    /**
     * Adds a playback listener to this walkietalkie
     *
     * @param playbackListener the listener
     */
    public void addPlaybackListener(IPlaybackListener playbackListener) {
        mPlaybackListeners.add(playbackListener);
    }

    /**
     * Removes a playback listener from this walkietalkie
     *
     * @param playbackListener the listener to be removed
     */
    public void removePlaybackListener(IPlaybackListener playbackListener) {
        mPlaybackListeners.remove(playbackListener);
    }
}

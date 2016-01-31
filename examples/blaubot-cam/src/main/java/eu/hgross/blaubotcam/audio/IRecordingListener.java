package eu.hgross.blaubotcam.audio;

/**
 * Listener that informs interested components about the start/stop of WalkieTalkie recordings.
 */
public interface IRecordingListener {
    /**
     * Called when the recording is started.
     */
    void onRecordingStarted();

    /**
     * Called after the recording stopped.
     */
    void onRecordingStopped();
}

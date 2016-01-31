package eu.hgross.blaubotcam.audio;

/**
 * Gets called before and after a received audio message is played
 */
public interface IPlaybackListener {
    /**
     * Gets called before playback is started.
     * @param walkieTalkieMessage the message
     */
    void beforePlayback(WalkieTalkieMessage walkieTalkieMessage);

    /**
     * Gets called after the playback of a walkie talkie message finished
     * @param walkieTalkieMessage the message
     */
    void afterPlayback(WalkieTalkieMessage walkieTalkieMessage);
}

package chirp.me.in.utils;

import android.content.Context;
import chirp.me.in.base.OnSuccessCallback;

/**
 * Helper for starting and stopping recorder, from WavMaker
 */
public class RecordingHelper {
    /**
     * calling context
     */
    private final Context context;
    /**
     * for recording audio
     */
    private WavMaker recorder;

    /**
     * default constructor
     * @param context - set calling context
     */
    public RecordingHelper(final Context context) {
        this.context = context;
    }

    /**
     * start recording, then perform callback (save to filename)
     * @param callback - to perform on success
     * @param filename - where to save file
     */
    public void startRecording(final OnSuccessCallback callback, final String filename) {
        this.recorder = new WavMaker(context.getExternalCacheDir().getAbsolutePath(), filename, context);
        recorder.startRecording();
        callback.OnSuccess();   // call on success callback
    }

    /**
     * stop recording, then perform callback
     * @param onSuccess - to perform on success
     */
    public void stopRecording(final OnSuccessCallback onSuccess) {
        if (recorder != null) {
            recorder.stopRecording();
            onSuccess.OnSuccess();
        }
    }
}

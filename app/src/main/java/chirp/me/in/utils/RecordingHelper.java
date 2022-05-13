package chirp.me.in.utils;

import android.content.Context;
import chirp.me.in.base.OnSuccessCallback;

public class RecordingHelper {
    /**
     * calling context
     */
    private final Context context;
    /**
     * for recording audio
     */
    private wavClass recorder;

    public RecordingHelper(final Context context) {
        this.context = context;
    }

    public void startRecording(final OnSuccessCallback callback, final String filename) {
        this.recorder = new wavClass(context.getExternalCacheDir().getAbsolutePath(), filename, context);
        recorder.startRecording();
        callback.OnSuccess();   // call on success callback
    }

    public void stopRecording(final OnSuccessCallback onSuccess) {
        if (recorder != null) {
            recorder.stopRecording();
            onSuccess.OnSuccess();
        }
    }
}

package chirp.me.in.utils;

import android.content.Context;
import android.media.MediaRecorder;
import android.util.Log;

import java.io.IOException;

import chirp.me.in.base.OnSuccessCallback;

public class RecordingHelper {
    /**
     * calling context
     */
    private final Context context;
    /**
     * for recording audio
     */
    private MediaRecorder recorder;

    public RecordingHelper(final Context context) {
        this.context = context;
    }

    public void startRecording(final OnSuccessCallback callback) {
        this.recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setOutputFile(context.getExternalCacheDir().getAbsolutePath() + "/recording.3gp");
        try {
            recorder.prepare();
        } catch (IOException e) {
            Log.e("MY_RECORDER", "Recording prepare() failed");
        }
        recorder.start();
        callback.OnSuccess();   // call on success callback
    }

    public void stopRecording(final OnSuccessCallback callback) {
        if (recorder != null) {
            recorder.release();
            recorder = null;
            callback.OnSuccess();   // call on success callback
        }
    }
}

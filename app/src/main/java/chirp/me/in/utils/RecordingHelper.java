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
    //private MediaRecorder recorder;
    private wavClass recorder;

    public RecordingHelper(final Context context) {
        this.context = context;
    }

    public void startRecording(final OnSuccessCallback callback, final String filename) {
        this.recorder = new wavClass(context.getExternalCacheDir().getAbsolutePath(), filename, context);
//        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
//        recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
//        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
//        recorder.setOutputFile(context.getExternalCacheDir().getAbsolutePath() + "/" + filename);
//        try {
//            recorder.prepare();
//        } catch (IOException e) {
//            Log.e("MY_RECORDER", "Recording prepare() failed");
//        }
//        recorder.start();
        recorder.startRecording();
        callback.OnSuccess();   // call on success callback
    }

    public void stopRecording(final OnSuccessCallback onSuccess) {
        if (recorder != null) {
//            recorder.release();
//            recorder = null;
            recorder.stopRecording();

//            AndroidAudioConverter.load(context, new ILoadCallback() {
//                @Override
//                public void onSuccess() {
//                    // library loaded
//                    File aacFile = new File(context.getExternalCacheDir().getAbsolutePath(), "recording.aac");
//                    IConvertCallback callback = new IConvertCallback() {
//                        @Override
//                        public void onSuccess(File convertedFile) {
//                            // So fast? Love it!
//                            onSuccess.OnSuccess();
//                        }
//                        @Override
//                        public void onFailure(Exception error) {
//                            // Oops! Something went wrong
//                            Log.d("MY_CONVERT", "File conversion went wrong");
//                        }
//                    };
//                    AndroidAudioConverter.with(context)
//                            // Your current audio file
//                            .setFile(aacFile)
//
//                            // Your desired audio format
//                            .setFormat(AudioFormat.MP3)
//
//                            // An callback to know when conversion is finished
//                            .setCallback(callback)
//
//                            // Start conversion
//                            .convert();
//                }
//                @Override
//                public void onFailure(Exception error) {
//                    // FFmpeg is not supported by device
//                }
//            });
            onSuccess.OnSuccess();
        }
    }
}

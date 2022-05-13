package chirp.me.in.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import chirp.me.in.R;
import chirp.me.in.base.OnSuccessCallback;

public class FirebaseHelper {
    // consts imported from integers.xml
    private final int IDLE;
    private final int WAKE_UP;
    private final int RECORDING_STARTED;
    private final int PLAYBACK_STARTED;
    private final int PLAYBACK_STOPPED;
    private final int PERFORMING_ANALYSIS;
    private final int METRICS_UPLOADED;

    /**
     * filename used for .wav file
     */
    private final String filename = "recording.wav";
    /**
     * for storing file absolute path
     */
    private final String absoluteFilePath;
    /**
     * initial timestamp when flag is locally set to RECORDING_STARTED (in ms)
     */
    private long timeStamp1 = -1;
    /**
     * second timestamp when flag is remotely set to PLAYBACK_STARTED (in ms)
     */
    private long timeStamp2 = -1;
    /**
     * helper for recording
     */
    private RecordingHelper recordingHelper;
    /**
     * for processing wav file and signal processing
     */
    private SoundProcessor soundProcessor;
    /**
     * latency of transmissions
     */
    private long latencyMS = 0;

    /**
     * init consts from context
     * @param context - application context
     */
    public FirebaseHelper(final Context context) {
        IDLE = context.getResources().getInteger(R.integer.IDLE);
        WAKE_UP = context.getResources().getInteger(R.integer.WAKE_UP);
        RECORDING_STARTED = context.getResources().getInteger(R.integer.RECORDING_STARTED);
        PLAYBACK_STARTED = context.getResources().getInteger(R.integer.PLAYBACK_STARTED);
        PLAYBACK_STOPPED = context.getResources().getInteger(R.integer.PLAYBACK_STOPPED);
        PERFORMING_ANALYSIS = context.getResources().getInteger(R.integer.PERFORMING_ANALYSIS);
        METRICS_UPLOADED = context.getResources().getInteger(R.integer.METRICS_UPLOADED);

        absoluteFilePath = context.getExternalCacheDir().getAbsolutePath() + "/" + filename;

        recordingHelper = new RecordingHelper(context); // init recording helper
        soundProcessor = new SoundProcessor(absoluteFilePath, context);
    }

    /**
     * push firebase user to db, merging with existing user if already in db
     * @param user
     * @param onSuccessCallback
     */
    public void pushUser(final FirebaseUser user, final OnSuccessCallback onSuccessCallback) {
        Map<String, Object> fbUser = new HashMap<>();
        fbUser.put("name", user.getDisplayName());
        fbUser.put("email", user.getEmail());
        fbUser.put("id", user.getUid());

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        db.collection("users").document(user.getUid())
            .set(fbUser, SetOptions.merge())
            .addOnSuccessListener(aVoid -> onSuccessCallback.OnSuccess())
            .addOnFailureListener(e -> Log.w("MY_AUTH", "Error writing document", e)
        );
    }

    /**
     * update user's flag in firebase
     * @param user - firebase user
     * @param onSuccessCallback - to call on success
     * @param flag - represent system state, see integers.xml for explanation
     */
    public void updateFlag(final FirebaseUser user, final OnSuccessCallback onSuccessCallback, int flag) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userDoc = db.collection("users").document(user.getUid());

        // set the 'flag' field to flag
        userDoc.update("flag", flag)
                .addOnSuccessListener(unused -> onSuccessCallback.OnSuccess())
                .addOnFailureListener(e ->
                        Log.d("MY_FIREBASE", "Failed to update user: " + user.getUid() +
                        "to flag: " + flag));
    }

    /**
     * update user's metrics for detected sound in firebase
     * @param user - firebase user
     * @param onSuccessCallback - to call on success
     * @param slope - slope of chirp in frequency domain
     * @param r2 - r^2 of that regression result
     */
    public void updateMetrics(final FirebaseUser user, final OnSuccessCallback onSuccessCallback, double slope, double r2) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userDoc = db.collection("users").document(user.getUid());

        // set the slope field to slope
        userDoc.update("slope", slope)
                .addOnSuccessListener(unused ->
                        // set the r2 field to r2
                        userDoc.update("r2", r2)
                                .addOnSuccessListener(unused1 -> onSuccessCallback.OnSuccess())
                                .addOnFailureListener(e -> Log.d("MY_FIREBASE", "Failed to update user: " + user.getUid() +
                                " r2 to: " + r2)))
                .addOnFailureListener(e -> Log.d("MY_FIREBASE", "Failed to update user: " + user.getUid() +
                                " slope to: " + slope));
    }

//    /**
//     * update user's latency on firebase
//     * @param user - firebase user
//     * @param onSuccessCallback - to call on success
//     * @param latency - initial latency between two communications
//     */
//    public void updateLatency(final FirebaseUser user, final OnSuccessCallback onSuccessCallback, long latency) {
//        FirebaseFirestore db = FirebaseFirestore.getInstance();
//        DocumentReference userDoc = db.collection("users").document(user.getUid());
//
//        // set the 'latency' field to latency
//        userDoc.update("latencyMS", latency)
//                .addOnSuccessListener(unused -> onSuccessCallback.OnSuccess())
//                .addOnFailureListener(e ->
//                        Log.d("MY_FIREBASE", "Failed to update user: " + user.getUid() +
//                                "to flag: " + latency));
//    }

    /**
     * add a snapshot listener to a user's document in the firestore, performs callback when it is invoked
     * @param user
     * @param onUpdate
     */
    private void addSnapshotListener(final FirebaseUser user, final EventListener<DocumentSnapshot> onUpdate) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userDoc = db.collection("users").document(user.getUid());

        userDoc.addSnapshotListener(onUpdate);
    }

    /**
     * push user's profile, update user flag to idle, add snapshot listener to user document
     * @param user
     * @param context
     */
    public void initPollingProcedure(final FirebaseUser user, final Context context) {
        // pusher user profile to db and toast on success
        pushUser(user, () -> Log.d("MY_FIREBASE","Pushed user profile to db"));

        // set flag to idle
        updateFlag(
                user,
                () -> Log.d("MY_FIREBASE", "Updated flag to polling"),
                context.getResources().getInteger(R.integer.IDLE)
        );

        // add snapshot listener for flag
        addSnapshotListener(user, (snapshot, e) -> {
            if (e != null) {
                Log.w("MY_SNAPSHOT", "Listen failed.", e);
            }

            if (snapshot != null && snapshot.exists()) {
                Log.d("MY_SNAPSHOT", "Current data: " + snapshot.getData());

                // extract flag value, assert != null
                int flag;
                Object o = snapshot.get("flag", Integer.TYPE);
                assert o != null;
                flag = (int) o;

                // to perform on IDLE
                if(flag == IDLE) {  // flag is usually IDLE as system is idle
                    // do nothing
                } else if(flag == WAKE_UP) {    // computer tells phone to wake up, start record
                    // begin recording
                    recordingHelper.startRecording(
                            () -> {
                                // note the time
                                timeStamp1 = System.currentTimeMillis();
                                // update flag
                                updateFlag(user, () -> Log.d("MY_FIREBASE", "Flag set to RECORDING_STARTED"), RECORDING_STARTED);
                            },
                            filename
                    );

                } else if(flag == RECORDING_STARTED) {  // phone has begun recording
                    // do nothing
                } else if(flag == PLAYBACK_STARTED) {   // computer has begun playback
                    // note the time
                    timeStamp2 = System.currentTimeMillis();
                    // calculate latency in ms
                    latencyMS = timeStamp2 - timeStamp1;
                } else if(flag == PLAYBACK_STOPPED) {   // playback stopped, process signal
//                    AtomicReference<Double> slope = new AtomicReference<>((double) 0);
//                    recordingHelper.stopRecording(
//                            () -> {
//                                slope.set(soundProcessor.getDominantSlope(latencyMS));
//                            }
//                      );
                    final LinearRegression[] regression = new LinearRegression[1];
                    // stop recording audio then perform linear regression on FFT data
                    recordingHelper.stopRecording(() -> {
                        regression[0] = soundProcessor.getLinearRegression(latencyMS);
                        // set flag to PERFORMING_ANALYSIS to avoid firebase looping on snapshot listener
                        updateFlag(
                                user,
                                // on update flag success, update slope and r^2 from regression
                                () -> {
                                    Log.d("MY_FIREBASE", "Flag set to PERFORMING_ANALYSIS");
                                    // update metric in firebase then set flag to METRICS_UPLOADED
                                    updateMetrics(user,
                                            () -> Log.d("MY_FIREBASE", "Regression metrics uploaded to user profile"),
                                            regression[0].slope(),
                                            regression[0].R2()
                                    );
                                },
                                PERFORMING_ANALYSIS);
                    });
                } else if(flag == PERFORMING_ANALYSIS) {
                    // do nothing
                }
            } else {
                Log.d("MY_SNAPSHOT", "Current data: null");
            }
        });
    }

//    /**
//     * upload recording "recording.mp3" from local file to storage "recordings/[UID]/recording.mp3"
//     * @param user - firebase user
//     * @param context - app context
//     */
//    private void uploadFile(final FirebaseUser user, final Context context) {
//        //  upload file to storage, if successful, update flag
//        // get base storage reference
//        FirebaseStorage storage = FirebaseStorage.getInstance();
//        // get my cloud storage reference
//        StorageReference recordingRef = storage.getReference().child("recordings/" + user.getUid() + "/" + filename);;
//
//        // get local storage ref
//        Uri recordingFile = Uri.fromFile(new File(context.getExternalCacheDir().getAbsolutePath() + "/" + filename));
//        // create upload task
//        UploadTask uploadTask = recordingRef.putFile(recordingFile);
//        // add failure listener
//        // add success listener to update flag
//        uploadTask.addOnFailureListener(exception -> {
//            // Handle unsuccessful uploads
//            Log.d("MY_STORAGE", "Failed to upload file");
//        }).addOnSuccessListener(taskSnapshot -> updateFlag(
//                user,
//                () -> Log.d("MY_FLAG", "Flag updated to: " + FILE_UPLOADED),
//                FILE_UPLOADED
//        ));
//    }
}

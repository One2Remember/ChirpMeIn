package chirp.me.in.utils;

import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

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

import chirp.me.in.*;
import chirp.me.in.base.OnSuccessCallback;

/**
 * Used as a helper for all firebase interactions:
 *  Authentication
 *  Communication protocol between Android and Web Applications
 */
public class FirebaseHelper {
    /**
     * constant imported from integers.xml
     *  represents system state: Idle, nothing is happening, default state
     */
    private final int IDLE;
    /**
     * constant imported from integers.xml
     *  represents system state: Web-app has informed phone to start recording
     */
    private final int WAKE_UP;
    /**
     * constant imported from integers.xml
     *  represents system state: Phone has informed web-app it has started recording
     */
    private final int RECORDING_STARTED;
    /**
     * constant imported from integers.xml
     *  represents system state: Web-app has informed phone it has begun playback (used in latency
     *  calculation on Android application)
     */
    private final int PLAYBACK_STARTED;
    /**
     * constant imported from integers.xml
     *  represents system state: Web-app has informed phone it is done playing back sound
     */
    private final int PLAYBACK_STOPPED;
    /**
     * constant imported from integers.xml
     *  represents system state: Phone has informed web-app it is now performing analysis
     */
    private final int PERFORMING_ANALYSIS;
    /**
     * constant imported from integers.xml
     *  represents system state: Phone has informed web-app it has uploaded metrics (Deprecated -
     *  this flag represented an earlier state that was necessary when the web-app was ultimately
     *  making the decision to succeed or fail)
     */
    private final int METRICS_UPLOADED;
    /**
     * constant imported from integers.xml
     *  represents system state: Phone has informed web-app 2FA was successful
     */
    private final int AUTH_SUCCESS;
    /**
     * constant imported from integers.xml
     *  represents system state: Phone has informed web-app 2FA was not successful
     */
    private final int AUTH_FAILURE;
    /**
     * constant imported from bools.xml
     *  represents whether system is running in debug mode
     */
    private final boolean DEBUG;
    /**
     * greatest percent deviation we allow between true slope and regression calculated slope
     */
    private final double DELTA = 0.05;
    /**
     * r^2 threshold we must pass to allow for auth success
     */
    private final double TAU = 0.95;
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
     * Construct a firebase helper, initializing constants, filepaths, and recording helper
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
        AUTH_SUCCESS = context.getResources().getInteger(R.integer.AUTH_SUCCESS);
        AUTH_FAILURE = context.getResources().getInteger(R.integer.AUTH_FAILURE);
        DEBUG = context.getResources().getBoolean(R.bool.DEBUG);

        // set absolute filepath for recording.wav
        absoluteFilePath = context.getExternalCacheDir().getAbsolutePath() + "/" + filename;

        recordingHelper = new RecordingHelper(context); // init recording helper
    }

    /**
     * push firebase user to db, merging with existing user if already in db
     * @param user - firebase user
     * @param onSuccessCallback - callback to perform on successful update
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
     * update user's metrics for detected sound in firebase (both slope and r^2), primarily
     * used for debugging, as current protocol does not require user to upload the recorded
     * sound regression metrics
     * @param user - firebase user
     * @param onSuccessCallback - to call on success
     * @param slope - slope of chirp in frequency domain
     * @param r2 - r^2 of that regression result
     */
    public void updateMetrics(final FirebaseUser user, final OnSuccessCallback onSuccessCallback,
                              double slope, double r2) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userDoc = db.collection("users").document(user.getUid());

        // set the slope field to slope
        userDoc.update("calculatedSlope", slope)
            .addOnSuccessListener(unused ->
                // set the r2 field to r2
                userDoc.update("r2", r2)
                    .addOnSuccessListener(unused1 -> onSuccessCallback.OnSuccess())
                    .addOnFailureListener(e -> Log.d("MY_FIREBASE",
                            "Failed to update user: " + user.getUid() + " r2 to: " + r2)))
            .addOnFailureListener(e -> Log.d("MY_FIREBASE", "Failed to update user: "
                    + user.getUid() + " slope to: " + slope));
    }

    /**
     * update user's latency on firebase - DEPRECATED - latency calculation is now only needed
     * locally, as mobile app is doing the sound recognition protocol
     * @param user - firebase user
     * @param onSuccessCallback - to call on success
     * @param latency - initial latency between two communications
     */
    public void updateLatency(final FirebaseUser user, final OnSuccessCallback onSuccessCallback, long latency) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userDoc = db.collection("users").document(user.getUid());

        // set the 'latency' field to latency
        userDoc.update("latencyMS", latency)
                .addOnSuccessListener(unused -> onSuccessCallback.OnSuccess())
                .addOnFailureListener(e ->
                        Log.d("MY_FIREBASE", "Failed to update user: " + user.getUid() +
                                "to flag: " + latency));
    }

    /**
     * add a snapshot listener to a user's document in the firestore, performs callback when
     * the user's document is updated. This snapshot listener is what allows the application
     * to know when various milestones occur within the authentication protocol
     * @param user - the firebase user to query
     * @param onUpdate - the callback to perform on document update
     */
    private void addSnapshotListener(final FirebaseUser user, final EventListener<DocumentSnapshot> onUpdate) {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        DocumentReference userDoc = db.collection("users").document(user.getUid());

        userDoc.addSnapshotListener(onUpdate);
    }

    /**
     * Initializes the snapshot listener by first pushing the current user profile to the database,
     * setting the user's flag to "idle", then adding a snapshot listener to the user's document
     * within the database. Snapshot listener is defined as anonymous class within this method
     * @param user - the firebase user to query
     * @param context - the calling application context
     */
    public void initPollingProcedure(final FirebaseUser user, final Context context) {
        // pushe user profile to db and toast on success
        pushUser(user, () -> Log.d("MY_FIREBASE","Pushed user profile to db"));

        // set flag to idle
        updateFlag(
                user,
                () -> Log.d("MY_FIREBASE", "Updated flag to polling"),
                context.getResources().getInteger(R.integer.IDLE)
        );

        /*
        Adds snapshot listener for flag
            Defines behavior for phone on various flag updates
            (e.g. flag==WAKE_UP tells the phone to start recording and then set the flag to
            RECORDING_STARTED so that the web-app can begin playback)
         */
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
                    // set flag to PERFORMING_ANALYSIS to avoid firebase looping on snapshot listener
                    updateFlag(
                        user,
                        // on update flag success, update slope and r^2 from regression
                        () -> {
                            Log.d("MY_FIREBASE", "Flag set to PERFORMING_ANALYSIS");
                            final LinearRegression[] regression = new LinearRegression[1];
                            // stop recording audio then perform linear regression on FFT data
                            recordingHelper.stopRecording(() -> {
                                soundProcessor = new SoundProcessor(absoluteFilePath, context);
                                // get linear regression (slope and r^2) on data
                                regression[0] = soundProcessor.getLinearRegression(latencyMS);

                                Log.d("MY_REGRESSION", "Slope: " + regression[0].slope()
                                        + ", R^2: " + regression[0].R2());

                                // get true slope value
                                double trueSlope;
                                Object s = snapshot.get("slope", Double.TYPE);
                                assert s != null;
                                trueSlope = (double) s;

                                // calculate percent difference as abs((true-measured)/true)
                                double percentDifference = Math.abs((trueSlope - regression[0].slope()) / trueSlope);
                                // succeed if r^2 > 0.95 and 1 - ratio < 0.05
                                int authResult =
                                        regression[0].R2() > TAU && 1 - percentDifference < DELTA ?
                                                AUTH_SUCCESS : AUTH_FAILURE;
                                // upload recording.wav and spectrogram.png files to firebase
                                if(DEBUG){
                                    uploadFile(
                                            user,
                                            "recordings",
                                            "recording.wav",
                                            context.getExternalCacheDir()
                                                    .getAbsolutePath()
                                    );
                                    uploadFile(
                                            user,
                                            "recordings",
                                            "spectrogram.png",
                                            Environment.getExternalStorageDirectory()
                                                    .getAbsolutePath()
                                    );
                                }
                                // update flag based on authResult
                                updateFlag(
                                    user,
                                    () -> Log.d("MY_REGRESSION", "Flag set to " +
                                            (authResult == AUTH_FAILURE ?
                                                    "AUTH_FAILURE" : "AUTH_SUCCESS")),
                                    authResult
                                );
                            });
                        },
                        PERFORMING_ANALYSIS
                    );
                } else if(flag == PERFORMING_ANALYSIS) {
                    // do nothing
                }
            } else {
                Log.d("MY_SNAPSHOT", "Current data: null");
            }
        });
    }

    /**
     * upload file [filename] from local file to storage "[bucketName]/[UID]/[filename]"
     * @param user - firebase user to update
     * @param bucketName - storage bucket name in Google firebase
     * @param filename - name of file
     * @param localPath - localPath to file (does not include filename)
     */
    private void uploadFile(final FirebaseUser user, final String bucketName,
                            final String filename, final String localPath) {
        //  upload file to storage, if successful, update flag
        // get base storage reference
        FirebaseStorage storage = FirebaseStorage.getInstance();
        // get my cloud storage reference
        StorageReference recordingRef =
                storage.getReference().child(bucketName + "/" + user.getUid() + "/" + filename);

        // get local storage ref
        Uri recordingFile = Uri.fromFile(new File(localPath + "/" + filename));
        // create upload task
        UploadTask uploadTask = recordingRef.putFile(recordingFile);
        // add failure listener
        // add success listener to update flag
        uploadTask.addOnFailureListener(exception -> {
            // Handle unsuccessful uploads
            Log.d("MY_STORAGE", "Failed to upload file");
        }).addOnSuccessListener(taskSnapshot -> Log.d("MY_UPLOAD", "Successful"));
    }
}

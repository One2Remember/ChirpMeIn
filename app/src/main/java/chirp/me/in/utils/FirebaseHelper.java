package chirp.me.in.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import com.google.firebase.storage.UploadTask;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import chirp.me.in.R;
import chirp.me.in.base.OnSuccessCallback;

public class FirebaseHelper {
    // consts imported from integers.xml
    private final int IDLE;
    private final int WAKE_UP;
    private final int RECORDING_STARTED;
    private final int PLAYBACK_STARTED;
    private final int PLAYBACK_STOPPED;
    private final int FILE_UPLOADED;

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
     * init consts from context
     * @param context - application context
     */
    public FirebaseHelper(final Context context) {
        IDLE = context.getResources().getInteger(R.integer.IDLE);
        WAKE_UP = context.getResources().getInteger(R.integer.WAKE_UP);
        RECORDING_STARTED = context.getResources().getInteger(R.integer.RECORDING_STARTED);
        PLAYBACK_STARTED = context.getResources().getInteger(R.integer.PLAYBACK_STARTED);
        PLAYBACK_STOPPED = context.getResources().getInteger(R.integer.PLAYBACK_STOPPED);
        FILE_UPLOADED = context.getResources().getInteger(R.integer.FILE_UPLOADED);

        recordingHelper = new RecordingHelper(context); // init recording helper
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
     * update user's latency on firebase
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
        addSnapshotListener(user, new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot snapshot, @Nullable FirebaseFirestoreException e) {
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
                                () -> Log.d("MY_RECORD", "Beginning to record")
                        );
                        // note the time
                        timeStamp1 = System.currentTimeMillis();
                        // update flag
                        updateFlag(user, () -> Log.d("MY_FIREBASE", "Flag set to RECORDING_STARTED"), RECORDING_STARTED);
                    } else if(flag == RECORDING_STARTED) {  // phone has begun recording
                        // do nothing
                    } else if(flag == PLAYBACK_STARTED) {   // computer has begun playback
                        // note the time
                        timeStamp2 = System.currentTimeMillis();
                        // calculate latency in ms
                        long latencyMS = timeStamp2 - timeStamp1;
                        // TODO: dont let this run infinitely update latency in db
//                        updateLatency(
//                                user,
//                                () -> Log.d("MY_LATENCY", "Latency set to: " + latencyMS + "ms"),
//                                latencyMS
//                        );
                    } else if(flag == PLAYBACK_STOPPED) {   // playback stopped, upload file
                        recordingHelper.stopRecording(() -> uploadFile(user, context));
                    } else if(flag == FILE_UPLOADED) {
                        // do nothing
                    }
                } else {
                    Log.d("MY_SNAPSHOT", "Current data: null");
                }
            }
        });
    }

    /**
     * upload recording "recording.3gp" from local file to storage "recordings/[UID]/recording.3gp"
     * @param user - firebase user
     * @param context - app context
     */
    private void uploadFile(final FirebaseUser user, final Context context) {
        //  upload file to storage, if successful, update flag
        // get base storage reference
        FirebaseStorage storage = FirebaseStorage.getInstance();
        // get my cloud storage reference
        StorageReference recordingRef = storage.getReference().child("recordings/" + user.getUid() + "recording.3gp");;

        // get local storage ref
        Uri recordingFile = Uri.fromFile(new File(context.getExternalCacheDir().getAbsolutePath() + "/recording.3gp"));
        // create upload task
        UploadTask uploadTask = recordingRef.putFile(recordingFile);
        // add failure listener
        // add success listener to update flag
        uploadTask.addOnFailureListener(exception -> {
            // Handle unsuccessful uploads
            Log.d("MY_STORAGE", "Failed to upload file");
        }).addOnSuccessListener(taskSnapshot -> updateFlag(
                user,
                () -> Log.d("MY_FLAG", "Flag updated to: " + FILE_UPLOADED),
                FILE_UPLOADED
        ));
    }

//    /**
//     * TODO: send token to user profile
//     * @param token - to send
//     */
//    public void sendRegistrationTokenToServer(final String token) {
//
//    }
//
//    /**
//     * Gets firebase messaging token
//     */
//    public void initToken() {
//        FirebaseMessaging.getInstance().getToken()
//                .addOnCompleteListener(task -> {
//                    if (!task.isSuccessful()) {
//                        Log.w("MY_TOKEN", "Fetching FCM registration token failed", task.getException());
//                        return;
//                    }
//
//                    // Get new FCM registration token
//                    String token = task.getResult();
//
//                    // Log token
//                    Log.d("MY_TOKEN", "Token: " + token);
//
//                    // send token to user profile
//                    sendRegistrationTokenToServer(token);
//                });
//    }
}

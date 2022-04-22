package chirp.me.in.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

import chirp.me.in.base.OnSuccessCallback;

public class FirebaseHelper {
    /**
     * TODO: send token to user profile
     * @param token - to send
     */
    public void sendRegistrationTokenToServer(final String token) {

    }

    /**
     * Gets firebase messaging token
     */
    public void initToken() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (!task.isSuccessful()) {
                        Log.w("MY_TOKEN", "Fetching FCM registration token failed", task.getException());
                        return;
                    }

                    // Get new FCM registration token
                    String token = task.getResult();

                    // Log token
                    Log.d("MY_TOKEN", "Token: " + token);

                    // send token to user profile
                    sendRegistrationTokenToServer(token);
                });
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
}

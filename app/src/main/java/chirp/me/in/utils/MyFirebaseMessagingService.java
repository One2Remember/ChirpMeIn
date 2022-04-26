package chirp.me.in.utils;

import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.messaging.FirebaseMessagingService;

public class MyFirebaseMessagingService extends FirebaseMessagingService {
//    /**
//     * for firebase comms
//     */
//    final FirebaseHelper myFirebaseHelper;
//
//    /**
//     * default constructor inits new firebase helper
//     */
//    public MyFirebaseMessagingService() {
//        myFirebaseHelper = new FirebaseHelper();
//    }
//
//    /**
//     * There are two scenarios when onNewToken is called:
//     * 1) When a new token is generated on initial app startup
//     * 2) Whenever an existing token is changed
//     * Under #2, there are three scenarios when the existing token is changed:
//     * A) App is restored to a new device
//     * B) User uninstalls/reinstalls the app
//     * C) User clears app data
//     */
//    @Override
//    public void onNewToken(@NonNull String token) {
//        Log.d("MY_TOKEN", "Refreshed token: " + token);
//
//        // If you want to send messages to this application instance or
//        // manage this apps subscriptions on the server side, send the
//        // FCM registration token to your app server.
//        myFirebaseHelper.sendRegistrationTokenToServer(token);
//    }
}

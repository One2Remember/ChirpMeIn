package chirp.me.in.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;


import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult;
import com.google.android.gms.common.SignInButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Arrays;
import java.util.List;

import chirp.me.in.*;
import chirp.me.in.base.OnSuccessCallback;
import chirp.me.in.utils.FirebaseHelper;

/**
 * Primary Activity - allows for user sign in / sign out and deploys the user's document
 * snapshot listener on successful login
 */
public class LoginActivity extends AppCompatActivity {
    /**
     * signInLauncher, must be declared before view is created
     */
    private ActivityResultLauncher<Intent> signInLauncher;
    /**
     * google sign in button
     */
    private SignInButton gsiButton;
    /**
     * sign out button
     */
    private Button soButton;
    /**
     * for firebase comms
     */
    private FirebaseHelper myFirebaseHelper;
    /**
     * for record audio permission
     */
    private final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    /**
     * for write external storage permission
     */
    private final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION = 2;
    /**
     * for record audio permission
     */
    private final String[] permissions = {Manifest.permission.RECORD_AUDIO};

    @SuppressLint("SourceLockedOrientationActivity")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        // lock in portrait mode
        this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        // initialize application base state
        initHelpers();
        initHooks();
        initListeners();
        initView();
        initSignIn();
        // request storage permissions
        requestStoragePermissions();
        // request audio recording permission
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);

    }

    /**
     * performed when user has returned from permission request prompt
    */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean audioRecordingPermissionGranted = false;
        boolean storagePermissionGranted = false;

        if(requestCode == REQUEST_RECORD_AUDIO_PERMISSION && grantResults.length > 0) {
            audioRecordingPermissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if(!audioRecordingPermissionGranted) {
                toast("Recording permission is required for ChirpMeIn to operate", true);
                finish();
            }
        }
        else if(requestCode == REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION && grantResults.length > 0) {
            storagePermissionGranted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if(!storagePermissionGranted) {
                toast("External storage permission is required for ChirpMeIn to operate", true);
                finish();
            }
        }
    }

    /**
     * request storage permission (for saving wav files and png files)
     */
    public void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED) {
                Log.d("MY_STORAGE","Permission is granted");

            } else {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        },
                        REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION
                );
                Log.d("MY_STORAGE","Permission is revoked");
            }
        }
        else { //permission is automatically granted on sdk<23 upon installation
            Log.d("MY_STORAGE","Permission is granted");

        }
    }

    /**
     * init firebase helper class
     */
    private void initHelpers() {
        myFirebaseHelper = new FirebaseHelper(getApplicationContext());
    }

    /**
     * initialize 'hooks' to all manipulated views on screen
     */
    protected void initHooks() {
        gsiButton = findViewById(R.id.sign_in_button);
        soButton = findViewById(R.id.sign_out_button);
    }
    /**
     * initialize any UI listeners, such as onClick listeners
     */
    protected void initListeners() {
        gsiButton.setOnClickListener(v-> launchFirebaseUIAuth());

        soButton.setOnClickListener(v-> signOut(() -> {
            toast("You have been signed out.", false);
            initView();
        }));
    }
    /**
     * initialize the base view state (login/out buttons based on login status)
     */
    protected void initView() {
        gsiButton.setVisibility(View.GONE);
        soButton.setVisibility(View.GONE);

        if(FirebaseAuth.getInstance().getCurrentUser() == null) {
            gsiButton.setVisibility(View.VISIBLE);
        } else {
            soButton.setVisibility(View.VISIBLE);
        }
    }

    /**
     * register sign in launcher (must be done in on create or as member variable)
     */
    private void initSignIn() {
        signInLauncher= registerForActivityResult(
                new FirebaseAuthUIActivityResultContract(),
                this::onSignInResult
        );
    }

    /**
     * currently supports sign in with google services only, can easily be extended
     */
    private void launchFirebaseUIAuth() {
        // Choose authentication providers
        List<AuthUI.IdpConfig> providers = Arrays.asList(
                //new AuthUI.IdpConfig.EmailBuilder().build(),
                //new AuthUI.IdpConfig.PhoneBuilder().build(),
                new AuthUI.IdpConfig.GoogleBuilder().build()
                //new AuthUI.IdpConfig.FacebookBuilder().build()
                //new AuthUI.IdpConfig.TwitterBuilder().build()*/
        );

        // Create and launch sign-in intent
        Intent signInIntent = AuthUI.getInstance()
                .createSignInIntentBuilder()
                .setAvailableProviders(providers)
                .setLogo(R.drawable.chirp_me_in)
                .setTheme(R.style.Theme_MyApplication)
                .build();
        signInLauncher.launch(signInIntent);
    }


    /**
     * to be performed when returning from sign in activity
     *  pushes profile to db, setting flag to idle
     *  add a snapshot listener for any changes to document
     * @param result - result of sign in activity
     */
    private void onSignInResult(FirebaseAuthUIAuthenticationResult result) {
        if (result.getResultCode() == RESULT_OK) {
            // redraw the activity (show/hide buttons)
            initView();

            // toast successful sign in
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            assert user != null;
            toast("Welcome, " + user.getDisplayName(), false);

            // init polling procedure
            myFirebaseHelper.initPollingProcedure(user, LoginActivity.this);
        } else {
            // Sign in failed. If response is null the user canceled the
            // sign-in flow using the back button. Otherwise check
            // response.getError().getErrorCode() and handle the error.
            toast("Sign in failed", true);
        }
    }

    /**
     * toast message to screen
     * @param toastMsg - message to print
     * @param isLong - whether toast is long
     */
    protected void toast(final String toastMsg, boolean isLong) {
        Toast.makeText(this, toastMsg, isLong ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG)
                .show();
    }

    /**
     * sign user out of firebase auth then perform callback when successful
     * @param onSuccessCallback - to perform on sign out success
     */
    protected void signOut(OnSuccessCallback onSuccessCallback) {
        AuthUI.getInstance()
                .signOut(this)
                .addOnCompleteListener(task -> {
                    // perform callback on successful sign out
                    onSuccessCallback.OnSuccess();
                });
    }

}
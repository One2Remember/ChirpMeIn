package chirp.me.in;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;


import androidx.activity.result.ActivityResultLauncher;

import com.firebase.ui.auth.AuthUI;
import com.firebase.ui.auth.FirebaseAuthUIActivityResultContract;
import com.firebase.ui.auth.IdpResponse;
import com.firebase.ui.auth.data.model.FirebaseAuthUIAuthenticationResult;
import com.google.android.gms.common.SignInButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.util.Arrays;
import java.util.List;

import chirp.me.in.base.BaseActivity;
import chirp.me.in.base.OnSuccessCallback;

public class LoginActivity extends BaseActivity {
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        initHooks();
        initListeners();
        initView();
        initSignIn();
    }

    @Override
    protected void initHooks() {
        gsiButton = findViewById(R.id.sign_in_button);
        soButton = findViewById(R.id.sign_out_button);
    }

    @Override
    protected void initListeners() {
        gsiButton.setOnClickListener(v-> launchFirebaseUIAuth());

        soButton.setOnClickListener(v-> signOut(() -> {
            toast("You have been signed out.", false);
            initView();
        }));
    }

    @Override
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
                this::onSignInResult);
    }

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
     * @param result - result of sign in activity
     */
    private void onSignInResult(FirebaseAuthUIAuthenticationResult result) {
        if (result.getResultCode() == RESULT_OK) {
            // redraw the activity (show/hide buttons)
            initView();

            // toast successful sign in
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            assert user != null;
            toast("Welcome, " + user.getDisplayName(), true);
        } else {
            // Sign in failed. If response is null the user canceled the
            // sign-in flow using the back button. Otherwise check
            // response.getError().getErrorCode() and handle the error.
            // ...
            // TODO: real failed sign-in response
            toast("Sign in failed", true);
        }
    }



}
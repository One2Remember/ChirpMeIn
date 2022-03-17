package chirp.me.in.base;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import chirp.me.in.R;

/**
 * abstract base activity class for providing convenient methods to all other app activities
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base);
    }

    protected abstract void initHooks();

    protected abstract void initListeners();

    protected abstract void initView();

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
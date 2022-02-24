package chirp.me.in.base;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import chirp.me.in.R;

public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_base);
    }

    // print toast to screen
    protected void toast(final String toastMsg, boolean isLong) {
        Toast.makeText(this, toastMsg, isLong ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG)
                .show();
    }
}
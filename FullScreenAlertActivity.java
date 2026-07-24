package plugins;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Build;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.TextView;

import youssef.yaqeen.R;

/**
 * شاشة كاملة بتصميم يقين، بتظهر حتى لو الموبايل مقفول.
 * بتتفتح عن طريق Intent فيه extras: title, message
 */
public class FullScreenAlertActivity extends Activity {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MESSAGE = "message";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyLockScreenFlags();
        setContentView(R.layout.activity_full_screen_alert);

        String title = getIntent().getStringExtra(EXTRA_TITLE);
        String message = getIntent().getStringExtra(EXTRA_MESSAGE);

        TextView titleView = findViewById(R.id.fsTitle);
        TextView messageView = findViewById(R.id.fsMessage);
        TextView dismissBtn = findViewById(R.id.fsDismissBtn);

        titleView.setText(title != null ? title : "يقين");
        messageView.setText(message != null ? message : "");

        dismissBtn.setOnClickListener(v -> finish());
    }

    private void applyLockScreenFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
            KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
            if (km != null) {
                km.requestDismissKeyguard(this, null);
            }
        } else {
            getWindow().addFlags(
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                            | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                            | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                            | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            );
        }
    }

    @Override
    public void onBackPressed() {
        // نمنع الرجوع بزرار الـ Back عشان المستخدم يقفل التنبيه بوعي (زرار الحمد لله)
        // ممكن نلغي التعليق ده لو عايز تسمح بالرجوع العادي
    }
}

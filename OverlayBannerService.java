package plugins;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.Nullable;

import youssef.yaqeen.MainActivity;
import youssef.yaqeen.R;

/**
 * خدمة مسؤولة عن رسم Banner صغير فوق أي تطبيق شغال، باستخدام صلاحية SYSTEM_ALERT_WINDOW.
 *
 * مهم جدًا: شكل الـ Banner (اللي بيظهر على الشاشة) بيختفي تلقائيًا بعد فترة قصيرة،
 * لكن الصوت (زي الأذان الكامل) مستقل تمامًا وبيكمل لحد ما يخلص لوحده مهما كان طوله.
 * الصوت بيتقفل بس لو: (أ) خلص لوحده طبيعي، أو (ب) المستخدم دوس على الـ Banner أو زرار الإغلاق.
 */
public class OverlayBannerService extends Service {

    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_MESSAGE = "message";
    public static final String EXTRA_AUTO_DISMISS_SECONDS = "autoDismissSeconds";
    public static final String EXTRA_SOUND_TYPE = "soundType";

    private WindowManager windowManager;
    private View bannerView;
    private MediaPlayer mediaPlayer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Runnable autoDismissRunnable;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        removeBannerViewOnly();
        stopAudioOnly();

        String title = intent != null ? intent.getStringExtra(EXTRA_TITLE) : "يقين";
        String message = intent != null ? intent.getStringExtra(EXTRA_MESSAGE) : "";
        String soundType = intent != null ? intent.getStringExtra(EXTRA_SOUND_TYPE) : null;
        int autoDismiss = intent != null ? intent.getIntExtra(EXTRA_AUTO_DISMISS_SECONDS, 8) : 8;

        showBanner(title, message, autoDismiss);
        playSoundForType(soundType);
        return START_NOT_STICKY;
    }

    private void showBanner(String title, String message, int autoDismissSeconds) {
        windowManager = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        bannerView = LayoutInflater.from(this).inflate(R.layout.view_overlay_banner, null);

        TextView titleView = bannerView.findViewById(R.id.bannerTitle);
        TextView messageView = bannerView.findViewById(R.id.bannerMessage);
        TextView closeView = bannerView.findViewById(R.id.bannerClose);

        titleView.setText(title != null ? title : "يقين");
        messageView.setText(message != null ? message : "");

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                overlayType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP;
        params.y = 40;

        windowManager.addView(bannerView, params);

        // الضغط على الـ Banner نفسه = المستخدم شافه فعلاً، فنوقف الصوت ونفتح التطبيق
        bannerView.setOnClickListener(v -> {
            Intent openApp = new Intent(this, MainActivity.class);
            openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(openApp);
            removeBannerViewOnly();
            stopAudioAndSelf();
        });

        // زرار الإغلاق = قرار صريح من المستخدم إنه عايز يوقف كل حاجة
        closeView.setOnClickListener(v -> {
            removeBannerViewOnly();
            stopAudioAndSelf();
        });

        // ده بيشيل شكل الـ Banner الظاهر بس بعد المدة دي — الصوت مايتأثرش خالص ويكمل لوحده
        autoDismissRunnable = this::removeBannerViewOnly;
        handler.postDelayed(autoDismissRunnable, autoDismissSeconds * 1000L);
    }

    private void playSoundForType(String soundType) {
        if (soundType == null) {
            playDefaultNotificationSound();
            return;
        }
        String rawName;
        if ("adhan_fajr".equals(soundType)) rawName = "adhan_fajr_sound";
        else if ("adhan".equals(soundType)) rawName = "adhan_sound";
        else if ("salawat".equals(soundType)) rawName = "salawat_sound";
        else if ("azkar".equals(soundType)) rawName = "azkar_allah_sound";
        else { playDefaultNotificationSound(); return; }

        int resId = getResources().getIdentifier(rawName, "raw", getPackageName());
        if (resId == 0) {
            playDefaultNotificationSound(); // الملف لسه متحطش، نستخدم صوت الإشعار العادي بتاع الموبايل
            return;
        }

        try {
            mediaPlayer = MediaPlayer.create(this, resId);
            if (mediaPlayer != null) {
                // لما الصوت يخلص طبيعي لوحده، نقفل الخدمة
                mediaPlayer.setOnCompletionListener(mp -> stopAudioAndSelf());
                mediaPlayer.start();
            } else {
                stopSelf();
            }
        } catch (Exception ignored) {
            stopSelf();
        }
    }

    /** يشغّل صوت الإشعار الافتراضي بتاع نظام الموبايل نفسه */
    private void playDefaultNotificationSound() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            Ringtone ringtone = RingtoneManager.getRingtone(this, uri);
            if (ringtone != null) ringtone.play();
        } catch (Exception ignored) {
        }
        // نغمة النظام قصيرة جدًا، فنقفل الخدمة بعد شوية ثواني بس
        handler.postDelayed(this::stopSelf, 3000);
    }

    /** يشيل شكل الـ Banner الظاهر بس، من غير ما يلمس الصوت خالص */
    private void removeBannerViewOnly() {
        if (autoDismissRunnable != null) {
            handler.removeCallbacks(autoDismissRunnable);
        }
        if (windowManager != null && bannerView != null) {
            try {
                windowManager.removeView(bannerView);
            } catch (Exception ignored) {
            }
            bannerView = null;
        }
    }

    /** يوقف الصوت لو لسه شغال */
    private void stopAudioOnly() {
        if (mediaPlayer != null) {
            try {
                if (mediaPlayer.isPlaying()) mediaPlayer.stop();
                mediaPlayer.release();
            } catch (Exception ignored) {
            }
            mediaPlayer = null;
        }
    }

    private void stopAudioAndSelf() {
        stopAudioOnly();
        stopSelf();
    }

    @Override
    public void onDestroy() {
        removeBannerViewOnly();
        stopAudioOnly();
        super.onDestroy();
    }
}

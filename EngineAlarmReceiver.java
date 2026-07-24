package plugins;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import youssef.yaqeen.MainActivity;
import youssef.yaqeen.R;

/**
 * ده قلب محرك التنبيهات: بيتفعّل بالظبط في وقت كل حدث (أذان، دعاء، رسالة لقلبك، إلخ).
 *
 * القرار وقت التنفيذ:
 * - الشاشة شغالة (المستخدم فاتح موبايله، حتى لو التطبيق نفسه مقفول) → يظهر Banner
 * - الشاشة مقفولة/مطفية → يظهر إشعار نظام عادي بسيط
 *
 * كل حدث بييجي معاه soundType بيحدد الصوت المناسب:
 * "adhan" | "adhan_fajr" | "salawat" | "azkar" | (فاضي = الصوت الافتراضي بدون تخصيص)
 */
public class EngineAlarmReceiver extends BroadcastReceiver {

    public static final String CHANNEL_DEFAULT = "yaqeen_engine_channel";
    public static final String CHANNEL_ADHAN = "yaqeen_adhan_channel_v3";
    public static final String CHANNEL_ADHAN_FAJR = "yaqeen_adhan_fajr_channel_v3";
    public static final String CHANNEL_SALAWAT = "yaqeen_salawat_channel_v3";
    public static final String CHANNEL_AZKAR = "yaqeen_azkar_channel_v3";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            String title = intent.getStringExtra("title");
            String message = intent.getStringExtra("message");
            String soundType = intent.getStringExtra("soundType");
            int id = intent.getIntExtra("id", (int) System.currentTimeMillis());

            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            boolean screenOn = pm != null && pm.isInteractive();

            if (screenOn) {
                showBanner(context, title, message, soundType);
            } else {
                showPlainNotification(context, id, title, message, soundType);
            }
        } catch (Exception e) {
            // أي خطأ غير متوقع (زي مشكلة في ملف صوت) ما ينفعش يوقف الإشعار خالص
            // نحاول نظهر إشعار بسيط بالصوت الافتراضي كحل أخير
            try {
                String title = intent.getStringExtra("title");
                String message = intent.getStringExtra("message");
                int id = intent.getIntExtra("id", (int) System.currentTimeMillis());
                showPlainNotification(context, id, title, message, null);
            } catch (Exception ignored) {
            }
        }
    }

    private void showBanner(Context context, String title, String message, String soundType) {
        Intent bannerIntent = new Intent(context, OverlayBannerService.class);
        bannerIntent.putExtra(OverlayBannerService.EXTRA_TITLE, title);
        bannerIntent.putExtra(OverlayBannerService.EXTRA_MESSAGE, message);
        bannerIntent.putExtra(OverlayBannerService.EXTRA_AUTO_DISMISS_SECONDS, 10);
        bannerIntent.putExtra(OverlayBannerService.EXTRA_SOUND_TYPE, soundType);
        context.startService(bannerIntent);
    }

    private void showPlainNotification(Context context, int id, String title, String message, String soundType) {
        String channelId = getOrCreateChannel(context, soundType);

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context, id, openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title != null ? title : "يقين")
                .setContentText(message != null ? message : "")
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message != null ? message : ""))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        NotificationManagerCompat.from(context).notify(id, builder.build());
    }

    /**
     * كل نوع صوت له قناة (Channel) منفصلة، لأن أندرويد 8+ بيربط الصوت بالقناة نفسها
     * مش بكل إشعار لوحده، والقناة لما تتعمل مرة، صوتها مايتغيرش تاني.
     */
    private String getOrCreateChannel(Context context, String soundType) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return CHANNEL_DEFAULT;
        }
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return CHANNEL_DEFAULT;

        String channelId;
        int soundResId;
        String channelName;

        if ("adhan_fajr".equals(soundType)) {
            channelId = CHANNEL_ADHAN_FAJR;
            soundResId = getRawId(context, "adhan_fajr_sound");
            channelName = "أذان الفجر";
        } else if ("adhan".equals(soundType)) {
            channelId = CHANNEL_ADHAN;
            soundResId = getRawId(context, "adhan_sound");
            channelName = "الأذان";
        } else if ("salawat".equals(soundType)) {
            channelId = CHANNEL_SALAWAT;
            soundResId = getRawId(context, "salawat_sound");
            channelName = "الصلاة على النبي";
        } else if ("azkar".equals(soundType)) {
            channelId = CHANNEL_AZKAR;
            soundResId = getRawId(context, "azkar_allah_sound");
            channelName = "اذكر الله";
        } else {
            createDefaultChannelIfNeeded(nm);
            return CHANNEL_DEFAULT;
        }

        if (nm.getNotificationChannel(channelId) == null) {
            try {
                NotificationChannel channel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
                channel.setDescription("تنبيهات " + channelName);

                if (soundResId != 0) {
                    try {
                        Uri soundUri = Uri.parse("android.resource://" + context.getPackageName() + "/" + soundResId);
                        AudioAttributes attrs = new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                                .build();
                        channel.setSound(soundUri, attrs);
                    } catch (Exception soundError) {
                        // لو الصوت فيه مشكلة (ملف تالف أو صيغة غير مدعومة)، نكمل من غير صوت مخصص
                        // بدل ما نوقف الإشعار كله
                    }
                }
                nm.createNotificationChannel(channel);
            } catch (Exception channelError) {
                // فشل إنشاء القناة تمامًا (نادر جدًا) -> نرجع للقناة الافتراضية عشان الإشعار يظهر برضو
                createDefaultChannelIfNeeded(nm);
                return CHANNEL_DEFAULT;
            }
        }
        return channelId;
    }

    private void createDefaultChannelIfNeeded(NotificationManager nm) {
        if (nm.getNotificationChannel(CHANNEL_DEFAULT) == null) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_DEFAULT, "تنبيهات يقين", NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("تنبيهات عامة");
            nm.createNotificationChannel(channel);
        }
    }

    /** بيدور على ملف الصوت جوه res/raw بالاسم، ولو مش لاقيه بيرجع 0 (يعني هيستخدم الصوت الافتراضي) */
    private int getRawId(Context context, String rawName) {
        return context.getResources().getIdentifier(rawName, "raw", context.getPackageName());
    }
}

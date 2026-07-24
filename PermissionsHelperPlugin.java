package plugins;

import android.app.AlarmManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import androidx.core.app.NotificationManagerCompat;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Plugin خاص بمشروع يقين (مش جزء من مكتبات Capacitor الرسمية).
 * وظيفته: التحقق من الصلاحيات الخاصة وفتح شاشات النظام المناسبة لتفعيلها.
 * - Overlay (الظهور فوق التطبيقات)
 * - Ignore Battery Optimization
 * - Schedule Exact Alarm (أندرويد 12+)
 * - حالة إشعارات النظام
 *
 * ملاحظة: الصلاحيات دي بتتفعل من شاشة إعدادات خارجية (مفيش Dialog داخلي)،
 * فبعد رجوع المستخدم للتطبيق لازم نعمل checkStatus() تاني عشان نعرف هو فعّل ولا لأ.
 */
@CapacitorPlugin(name = "PermissionsHelper")
public class PermissionsHelperPlugin extends Plugin {

    // ══════════════════ فحص حالة كل الصلاحيات دفعة واحدة ══════════════════
    @PluginMethod
    public void checkStatus(PluginCall call) {
        Context ctx = getContext();
        JSObject result = new JSObject();

        result.put("overlay", canDrawOverlays(ctx));
        result.put("batteryOptimizationIgnored", isIgnoringBatteryOptimizations(ctx));
        result.put("exactAlarm", canScheduleExactAlarms(ctx));
        result.put("notificationsEnabled", NotificationManagerCompat.from(ctx).areNotificationsEnabled());

        call.resolve(result);
    }

    // ══════════════════ Overlay (SYSTEM_ALERT_WINDOW) ══════════════════
    @PluginMethod
    public void requestOverlayPermission(PluginCall call) {
        Context ctx = getContext();
        if (canDrawOverlays(ctx)) {
            call.resolve(new JSObject().put("opened", false).put("alreadyGranted", true));
            return;
        }
        try {
            Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + ctx.getPackageName())
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(intent);
            call.resolve(new JSObject().put("opened", true).put("alreadyGranted", false));
        } catch (Exception e) {
            call.reject("تعذر فتح شاشة صلاحية الظهور فوق التطبيقات", e);
        }
    }

    // ══════════════════ Ignore Battery Optimization ══════════════════
    @PluginMethod
    public void requestBatteryOptimization(PluginCall call) {
        Context ctx = getContext();
        if (isIgnoringBatteryOptimizations(ctx)) {
            call.resolve(new JSObject().put("opened", false).put("alreadyGranted", true));
            return;
        }
        try {
            Intent intent = new Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + ctx.getPackageName())
            );
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(intent);
            call.resolve(new JSObject().put("opened", true).put("alreadyGranted", false));
        } catch (Exception e) {
            // فallback: فتح شاشة إعدادات البطارية العامة لو الجهاز مش داعم الإنتنت المباشر
            try {
                Intent fallback = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                fallback.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getActivity().startActivity(fallback);
                call.resolve(new JSObject().put("opened", true).put("alreadyGranted", false));
            } catch (Exception e2) {
                call.reject("تعذر فتح شاشة صلاحية البطارية", e2);
            }
        }
    }

    // ══════════════════ Schedule Exact Alarm (أندرويد 12+) ══════════════════
    @PluginMethod
    public void requestExactAlarmPermission(PluginCall call) {
        Context ctx = getContext();
        if (canScheduleExactAlarms(ctx)) {
            call.resolve(new JSObject().put("opened", false).put("alreadyGranted", true));
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Intent intent = new Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.parse("package:" + ctx.getPackageName())
                );
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getActivity().startActivity(intent);
                call.resolve(new JSObject().put("opened", true).put("alreadyGranted", false));
            } else {
                // قبل أندرويد 12 الصلاحية دي ممنوحة تلقائيًا
                call.resolve(new JSObject().put("opened", false).put("alreadyGranted", true));
            }
        } catch (Exception e) {
            call.reject("تعذر فتح شاشة صلاحية المواعيد الدقيقة", e);
        }
    }

    // ══════════════════ فتح شاشة إعدادات إشعارات التطبيق ══════════════════
    @PluginMethod
    public void openNotificationSettings(PluginCall call) {
        Context ctx = getContext();
        try {
            Intent intent = new Intent();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                intent.setAction(Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                intent.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.getPackageName());
            } else {
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.parse("package:" + ctx.getPackageName()));
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(intent);
            call.resolve(new JSObject().put("opened", true));
        } catch (Exception e) {
            call.reject("تعذر فتح شاشة إعدادات الإشعارات", e);
        }
    }

    // ══════════════════ دوال مساعدة داخلية ══════════════════
    private boolean canDrawOverlays(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(ctx);
        }
        return true;
    }

    private boolean isIgnoringBatteryOptimizations(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) ctx.getSystemService(Context.POWER_SERVICE);
            return pm != null && pm.isIgnoringBatteryOptimizations(ctx.getPackageName());
        }
        return true;
    }

    private boolean canScheduleExactAlarms(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            return am != null && am.canScheduleExactAlarms();
        }
        return true;
    }
}

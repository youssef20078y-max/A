package plugins;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * محرك التنبيهات: الـ JS بيبعت قايمة أحداث (id, title, message, timestamp)،
 * والـ Plugin ده بيجدولهم فعليًا كـ Alarms حقيقية في أندرويد.
 * كل مرة بتتنده فيها scheduleEvents، بنلغي القايمة القديمة ونستبدلها بالكامل بالجديدة
 * (عشان كده الـ JS لازم يبعت القايمة الكاملة مش بس الجديد).
 */
@CapacitorPlugin(name = "NotificationEngine")
public class NotificationEnginePlugin extends Plugin {

    public static final String PREFS_NAME = "yaqeen_engine_prefs";
    public static final String EVENTS_KEY = "scheduled_events";

    @PluginMethod
    public void scheduleEvents(PluginCall call) {
        JSArray events = call.getArray("events");
        if (events == null) {
            call.reject("محتاج قائمة events");
            return;
        }

        Context ctx = getContext();
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        long now = System.currentTimeMillis();

        // نلغي كل الأحداث المجدولة قبل كده الأول
        cancelStoredEvents(ctx, am);

        JSONArray toStore = new JSONArray();

        try {
            for (int i = 0; i < events.length(); i++) {
                JSONObject ev = events.getJSONObject(i);
                int id = ev.optInt("id", 9000 + i);
                String title = ev.optString("title", "يقين");
                String message = ev.optString("message", "");
                String soundType = ev.optString("soundType", null);
                long timestamp = ev.optLong("timestamp", 0);

                if (timestamp <= now) continue; // نتجاهل أي وقت فات بالفعل

                Intent alarmIntent = new Intent(ctx, EngineAlarmReceiver.class);
                alarmIntent.putExtra("id", id);
                alarmIntent.putExtra("title", title);
                alarmIntent.putExtra("message", message);
                alarmIntent.putExtra("soundType", soundType);

                PendingIntent pi = PendingIntent.getBroadcast(
                        ctx, id, alarmIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                if (am != null) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pi);
                }

                JSONObject storedEv = new JSONObject();
                storedEv.put("id", id);
                storedEv.put("title", title);
                storedEv.put("message", message);
                storedEv.put("timestamp", timestamp);
                if (soundType != null) storedEv.put("soundType", soundType);
                toStore.put(storedEv);
            }

            SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(EVENTS_KEY, toStore.toString()).apply();

            call.resolve(new JSObject().put("scheduled", toStore.length()));
        } catch (Exception e) {
            call.reject("خطأ في جدولة الأحداث: " + e.getMessage(), e);
        }
    }

    @PluginMethod
    public void cancelAllEvents(PluginCall call) {
        Context ctx = getContext();
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        cancelStoredEvents(ctx, am);
        call.resolve();
    }

    private void cancelStoredEvents(Context ctx, AlarmManager am) {
        SharedPreferences prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String json = prefs.getString(EVENTS_KEY, null);
        if (json == null) return;

        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject ev = arr.getJSONObject(i);
                int id = ev.optInt("id", i);

                Intent alarmIntent = new Intent(ctx, EngineAlarmReceiver.class);
                PendingIntent pi = PendingIntent.getBroadcast(
                        ctx, id, alarmIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );
                if (am != null) am.cancel(pi);
            }
        } catch (Exception ignored) {
        }

        prefs.edit().remove(EVENTS_KEY).apply();
    }
}

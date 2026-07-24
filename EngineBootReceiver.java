package plugins;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * لما الموبايل يتقفل ويترستارت، أندرويد بيمسح كل الـ Alarms المجدولة.
 * الـ Receiver ده بيرجع يجدول تاني أي حدث لسه ماحصلش من القايمة المحفوظة.
 */
public class EngineBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;

        SharedPreferences prefs = context.getSharedPreferences(
                NotificationEnginePlugin.PREFS_NAME, Context.MODE_PRIVATE
        );
        String json = prefs.getString(NotificationEnginePlugin.EVENTS_KEY, null);
        if (json == null) return;

        try {
            JSONArray arr = new JSONArray(json);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            long now = System.currentTimeMillis();

            for (int i = 0; i < arr.length(); i++) {
                JSONObject ev = arr.getJSONObject(i);
                long timestamp = ev.optLong("timestamp", 0);
                if (timestamp <= now) continue;

                int id = ev.optInt("id", i);
                String title = ev.optString("title", "يقين");
                String message = ev.optString("message", "");
                String soundType = ev.optString("soundType", null);

                Intent alarmIntent = new Intent(context, EngineAlarmReceiver.class);
                alarmIntent.putExtra("id", id);
                alarmIntent.putExtra("title", title);
                alarmIntent.putExtra("message", message);
                alarmIntent.putExtra("soundType", soundType);

                PendingIntent pi = PendingIntent.getBroadcast(
                        context, id, alarmIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                );

                if (am != null) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pi);
                }
            }
        } catch (Exception ignored) {
        }
    }
}

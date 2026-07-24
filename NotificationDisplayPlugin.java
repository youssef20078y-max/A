package plugins;

import android.content.Intent;
import android.provider.Settings;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

/**
 * Plugin لاختبار وتشغيل نظامي العرض الجديدين: Banner و FullScreen.
 * ده Plugin تجريبي في المرحلة دي بس، هيتربط بعدين بمحرك التنبيهات الأساسي.
 */
@CapacitorPlugin(name = "NotificationDisplay")
public class NotificationDisplayPlugin extends Plugin {

    @PluginMethod
    public void showBanner(PluginCall call) {
        if (!Settings.canDrawOverlays(getContext())) {
            call.reject("لازم تفعّل صلاحية الظهور فوق التطبيقات الأول");
            return;
        }
        String title = call.getString("title", "يقين");
        String message = call.getString("message", "");
        String soundType = call.getString("soundType", null);
        int autoDismiss = call.getInt("autoDismissSeconds", 8);

        Intent intent = new Intent(getContext(), OverlayBannerService.class);
        intent.putExtra(OverlayBannerService.EXTRA_SOUND_TYPE, soundType);
        intent.putExtra(OverlayBannerService.EXTRA_TITLE, title);
        intent.putExtra(OverlayBannerService.EXTRA_MESSAGE, message);
        intent.putExtra(OverlayBannerService.EXTRA_AUTO_DISMISS_SECONDS, autoDismiss);
        getContext().startService(intent);

        call.resolve(new JSObject().put("shown", true));
    }

    @PluginMethod
    public void showFullScreen(PluginCall call) {
        String title = call.getString("title", "يقين");
        String message = call.getString("message", "");

        Intent intent = new Intent(getContext(), FullScreenAlertActivity.class);
        intent.putExtra(FullScreenAlertActivity.EXTRA_TITLE, title);
        intent.putExtra(FullScreenAlertActivity.EXTRA_MESSAGE, message);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getContext().startActivity(intent);

        call.resolve(new JSObject().put("shown", true));
    }
}

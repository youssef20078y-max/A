package youssef.yaqeen;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

import plugins.PermissionsHelperPlugin;
import plugins.NotificationDisplayPlugin;
import plugins.NotificationEnginePlugin;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PermissionsHelperPlugin.class);
        registerPlugin(NotificationDisplayPlugin.class);
        registerPlugin(NotificationEnginePlugin.class);
        super.onCreate(savedInstanceState);
    }
}

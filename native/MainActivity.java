package de.taxiruf25500.funk;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override public void onCreate(Bundle savedInstanceState) {
        registerPlugin(CallWatcherPlugin.class);
        super.onCreate(savedInstanceState);
    }
}

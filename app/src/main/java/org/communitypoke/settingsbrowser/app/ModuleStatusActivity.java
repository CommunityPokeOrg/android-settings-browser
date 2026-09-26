package org.communitypoke.settingsbrowser.app;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * Standalone launcher activity for the module APK. The real functionality lives
 * inside the Settings app (injected by the Xposed hook); this screen only
 * explains how to enable it. Management of extensions happens under
 * Settings > System > Browser extensions.
 */
public class ModuleStatusActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(24);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("Settings Browser");
        title.setTextSize(24);
        title.setTextColor(Color.BLACK);
        root.addView(title);

        TextView body = new TextView(this);
        body.setTextSize(15);
        body.setTextColor(Color.DKGRAY);
        body.setLineSpacing(0, 1.2f);
        body.setText(
                "\nThis is a rooted-device system module (Xposed / LSPosed, modern API).\n\n"
                + "Setup:\n"
                + "  1. Enable this module in LSPosed.\n"
                + "  2. Set the scope to the Settings app (com.android.settings).\n"
                + "  3. Force-stop and reopen Settings.\n\n"
                + "Inside Settings you get:\n"
                + "  • \"Browser\" — a WebView browser embedded in Settings\n"
                + "  • System → \"Browser extensions\" — the extension manager\n\n"
                + "Extensions are userscript-style .js files with @match/@run-at\n"
                + "metadata and can call the privileged SettingsBrowser JS bridge\n"
                + "(openSetting, getSetting/putSetting, getProp, exec/execRoot,\n"
                + "kvGet/kvSet) — everything runs with Settings' system uid.\n");
        root.addView(body);

        TextView ver = new TextView(this);
        ver.setTextSize(12);
        ver.setTextColor(Color.GRAY);
        ver.setGravity(Gravity.BOTTOM);
        try {
            String v = getPackageManager()
                    .getPackageInfo(getPackageName(), 0).versionName;
            ver.setText("\nversion " + v);
        } catch (Throwable ignored) {
        }
        root.addView(ver);

        setContentView(scroll);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}

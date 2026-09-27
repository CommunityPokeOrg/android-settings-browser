package org.communitypoke.settingsbrowser.app;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import org.communitypoke.settingsbrowser.ui.BrowserFragment;
import org.communitypoke.settingsbrowser.ui.ExtensionsFragment;

/**
 * Launcher activity of the standalone Settings Browser app. Hosts the embedded
 * WebView browser directly; the extensions manager is reachable from the
 * browser toolbar, the quick-settings tile, or the "extensions" intent extra.
 *
 * Replaces the old Xposed injection into com.android.settings: without LSPosed
 * nothing can live inside the Settings process, so the browser runs here with
 * root privileges provided by RootDaemon instead.
 */
public class MainActivity extends FragmentActivity {

    /** Optional intent extra: "extensions" opens the extension manager. */
    public static final String EXTRA_SCREEN =
            "org.communitypoke.settingsbrowser.SCREEN";
    public static final String SCREEN_EXTENSIONS = "extensions";

    private static final String BACK_STACK_NAME = "settings_browser";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(android.R.id.content, new BrowserFragment())
                    .commit();
        }
        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null
                || !SCREEN_EXTENSIONS.equals(intent.getStringExtra(EXTRA_SCREEN))) {
            return;
        }
        openScreen(new ExtensionsFragment());
    }

    private void openScreen(Fragment target) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(android.R.id.content, target)
                .addToBackStack(BACK_STACK_NAME)
                .commit();
    }
}

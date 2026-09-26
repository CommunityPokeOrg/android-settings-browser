package org.communitypoke.settingsbrowser.ui;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

/**
 * Swaps a Settings screen for one of our fragments inside the Settings process.
 * The Settings homepage keeps its preference fragment in {@code R.id.main_content};
 * {@code android.R.id.content} is the fallback for OEM forks that renamed it.
 */
public final class Navigation {

    private static final String BACK_STACK_NAME = "settings_browser";

    private Navigation() {
    }

    public static void open(Fragment host, Fragment target) {
        FragmentActivity activity = host.requireActivity();
        int containerId = activity.getResources().getIdentifier(
                "main_content", "id", activity.getPackageName());
        if (containerId == 0) {
            containerId = android.R.id.content;
        }
        host.getParentFragmentManager()
                .beginTransaction()
                .replace(containerId, target, "settings_browser_screen")
                .addToBackStack(BACK_STACK_NAME)
                .commit();
    }
}

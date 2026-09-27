package org.communitypoke.settingsbrowser.ui;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

/**
 * Swaps the visible screen for one of our fragments inside the app's own
 * activity. {@code android.R.id.content} is the fragment container.
 */
public final class Navigation {

    private static final String BACK_STACK_NAME = "settings_browser";

    private Navigation() {
    }

    public static void open(Fragment host, Fragment target) {
        FragmentActivity activity = host.requireActivity();
        int containerId = android.R.id.content;
        host.getParentFragmentManager()
                .beginTransaction()
                .replace(containerId, target, "settings_browser_screen")
                .addToBackStack(BACK_STACK_NAME)
                .commit();
    }
}

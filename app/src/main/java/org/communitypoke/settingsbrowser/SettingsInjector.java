package org.communitypoke.settingsbrowser;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import org.communitypoke.settingsbrowser.ui.BrowserFragment;
import org.communitypoke.settingsbrowser.ui.ExtensionsFragment;
import org.communitypoke.settingsbrowser.ui.Navigation;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

import io.github.libxposed.api.XposedModule;

/**
 * Injects our "Browser" and "Browser extensions" entries into Settings'
 * preference screens.
 *
 * Layout in AOSP Settings:
 *   - Home page fragment: TopLevelSettings (12+) / DashboardSummary (9-11),
 *     both subclasses of DashboardFragment. Entries are flat Preference items
 *     keyed "top_level_*" (top_level_system is the "System" entry).
 *   - Settings > System screen: SystemDashboardFragment
 *     (com.android.settings.system.* on 12+, com.android.settings.* before).
 *     Entries live inside the System dashboard; this is where the extensions
 *     manager is attached, as requested.
 *
 * We hook onCreatePreferences (and onResume as a retry point) on the
 * DashboardFragment base class plus the concrete subclasses, because a hook
 * only fires when that exact method implementation is invoked.
 */
final class SettingsInjector {

    private static final String KEY_BROWSER = "settings_browser_open";
    private static final String KEY_EXTENSIONS = "settings_browser_extensions";

    /** Fragment instances we have already injected into. */
    private static final Set<Object> INJECTED =
            Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

    private static final String[] HOOK_TARGETS = {
            "com.android.settings.dashboard.DashboardFragment",
            "com.android.settings.homepage.TopLevelSettings",
            "com.android.settings.dashboard.DashboardSummary",
            "com.android.settings.system.SystemDashboardFragment",
            "com.android.settings.SystemDashboardFragment",
    };

    private SettingsInjector() {
    }

    static void install(ModuleMain module, ClassLoader classLoader) {
        for (String className : HOOK_TARGETS) {
            Class<?> fragmentClass = findClass(classLoader, className);
            if (fragmentClass == null) {
                continue;
            }
            hookLifecycle(module, fragmentClass);
        }
    }

    private static void hookLifecycle(ModuleMain module, Class<?> fragmentClass) {
        Method onCreatePreferences = findMethod(fragmentClass,
                "onCreatePreferences", Bundle.class, String.class);
        if (onCreatePreferences != null) {
            hookAfter(module, onCreatePreferences);
        }
        // Some builds bind preferences late; onResume is a safe retry point.
        Method onResume = findMethod(fragmentClass, "onResume");
        if (onResume != null) {
            hookAfter(module, onResume);
        }
    }

    private static void hookAfter(final ModuleMain module, final Method method) {
        try {
            module.hook(method).intercept(chain -> {
                Object result = chain.proceed();
                try {
                    injectIfNeeded(chain.getThisObject());
                } catch (Throwable t) {
                    module.log(Log.ERROR, ModuleMain.TAG,
                            "injection failed in " + method.getName(), t);
                }
                return result;
            });
        } catch (Throwable t) {
            module.log(Log.WARN, ModuleMain.TAG,
                    "could not hook " + method.getDeclaringClass().getName()
                            + "#" + method.getName(), t);
        }
    }

    private static void injectIfNeeded(Object fragment) {
        if (INJECTED.contains(fragment)) {
            return;
        }
        if (dispatch(fragment)) {
            INJECTED.add(fragment);
        }
    }

    /** @return true once injection succeeded (or is not applicable). */
    private static boolean dispatch(Object fragment) {
        String name = fragment.getClass().getName();
        if (isSystemDashboard(name)) {
            return injectSystemEntries(fragment);
        }
        if (isTopLevel(name)) {
            return injectTopLevelEntry(fragment);
        }
        // Other dashboards (Network, Connected devices, ...) get nothing.
        return false;
    }

    private static boolean isTopLevel(String className) {
        return className.contains("TopLevelSettings")
                || className.endsWith("DashboardSummary");
    }

    private static boolean isSystemDashboard(String className) {
        return className.endsWith("SystemDashboardFragment");
    }

    // --- injection -------------------------------------------------------

    private static boolean injectTopLevelEntry(Object fragment) {
        PreferenceScreen screen = screenOf(fragment);
        if (screen == null || screen.findPreference(KEY_BROWSER) != null) {
            return screen != null;
        }
        Context ctx = ((PreferenceFragmentCompat) fragment).requireContext();

        Preference browser = new Preference(ctx);
        browser.setKey(KEY_BROWSER);
        browser.setTitle("Browser");
        browser.setSummary("Embedded web browser");
        browser.setIcon(android.R.drawable.ic_menu_compass);
        browser.setOnPreferenceClickListener(pref -> {
            Navigation.open((Fragment) fragment, new BrowserFragment());
            return true;
        });
        orderAfter(screen, browser, "top_level_system");
        screen.addPreference(browser);
        return true;
    }

    private static boolean injectSystemEntries(Object fragment) {
        PreferenceScreen screen = screenOf(fragment);
        if (screen == null) {
            return false;
        }
        Context ctx = ((PreferenceFragmentCompat) fragment).requireContext();

        if (screen.findPreference(KEY_EXTENSIONS) == null) {
            Preference extensions = new Preference(ctx);
            extensions.setKey(KEY_EXTENSIONS);
            extensions.setTitle("Browser extensions");
            extensions.setSummary("Manage extensions for the embedded browser");
            extensions.setIcon(android.R.drawable.ic_menu_manage);
            extensions.setOnPreferenceClickListener(pref -> {
                Navigation.open((Fragment) fragment, new ExtensionsFragment());
                return true;
            });
            screen.addPreference(extensions);
        }
        if (screen.findPreference(KEY_BROWSER) == null) {
            Preference browser = new Preference(ctx);
            browser.setKey(KEY_BROWSER);
            browser.setTitle("Browser");
            browser.setSummary("Open the embedded web browser");
            browser.setIcon(android.R.drawable.ic_menu_compass);
            browser.setOnPreferenceClickListener(pref -> {
                Navigation.open((Fragment) fragment, new BrowserFragment());
                return true;
            });
            screen.addPreference(browser);
        }
        return true;
    }

    private static PreferenceScreen screenOf(Object fragment) {
        if (!(fragment instanceof PreferenceFragmentCompat)) {
            return null;
        }
        return ((PreferenceFragmentCompat) fragment).getPreferenceScreen();
    }

    private static void orderAfter(PreferenceScreen screen, Preference pref, String key) {
        Preference anchor = screen.findPreference(key);
        if (anchor != null) {
            pref.setOrder(anchor.getOrder() + 1);
        }
    }

    // --- reflection helpers ----------------------------------------------

    private static Class<?> findClass(ClassLoader classLoader, String name) {
        try {
            return Class.forName(name, false, classLoader);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Method findMethod(Class<?> klass, String name, Class<?>... params) {
        for (Class<?> c = klass; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name, params);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}

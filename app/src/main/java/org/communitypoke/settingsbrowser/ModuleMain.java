package org.communitypoke.settingsbrowser;

import android.util.Log;

import io.github.libxposed.api.XposedModule;

/**
 * Module entry point (see META-INF/xposed/java_init.list).
 *
 * We hook com.android.settings at package-ready time: at that point the app's
 * classloader can resolve both Settings' own classes and this module's classes,
 * which lets us instantiate our fragments inside the Settings process.
 */
public class ModuleMain extends XposedModule {

    static final String TAG = "SettingsBrowser";
    static final String SETTINGS_PACKAGE = "com.android.settings";

    private static ModuleMain instance;

    static ModuleMain get() {
        return instance;
    }

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        instance = this;
        log(Log.INFO, TAG, "module loaded in process " + param.getProcessName()
                + " (framework " + getFrameworkName() + " "
                + getFrameworkVersion() + ", api " + getApiVersion() + ")");
    }

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!SETTINGS_PACKAGE.equals(param.getPackageName())) {
            return;
        }
        if (!param.isFirstPackage()) {
            return;
        }
        SettingsInjector.install(this, param.getClassLoader());
    }
}

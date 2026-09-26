package org.communitypoke.settingsbrowser.api;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import org.communitypoke.settingsbrowser.ext.Extension;
import org.communitypoke.settingsbrowser.ext.ExtensionStore;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * JavaScript bridge exposed to every page and every extension as
 * {@code window.SettingsBrowser}. Because the browser runs inside the Settings
 * process, this bridge can use Settings' privileged permissions (e.g.
 * WRITE_SECURE_SETTINGS) and launch any settings screen.
 *
 * All @JavascriptInterface methods run on a background binder thread.
 */
public final class SettingsBrowserApi {

    private static final String TAG = "SettingsBrowserApi";
    private static final String API_VERSION = "1";

    private final Context context;
    private final WebView webView;
    private final ExtensionStore store;

    public SettingsBrowserApi(Context context, WebView webView, ExtensionStore store) {
        this.context = context.getApplicationContext();
        this.webView = webView;
        this.store = store;
    }

    @JavascriptInterface
    public String apiVersion() {
        return API_VERSION;
    }

    @JavascriptInterface
    public String deviceInfo() {
        JSONObject o = new JSONObject();
        try {
            o.put("manufacturer", Build.MANUFACTURER);
            o.put("model", Build.MODEL);
            o.put("device", Build.DEVICE);
            o.put("sdk", Build.VERSION.SDK_INT);
            o.put("release", Build.VERSION.RELEASE);
        } catch (Throwable ignored) {
        }
        return o.toString();
    }

    // --- navigation / settings --------------------------------------------

    @JavascriptInterface
    public void navigate(final String url) {
        webView.post(() -> webView.loadUrl(url));
    }

    /** Launch an Android settings screen, e.g. "android.settings.WIFI_SETTINGS". */
    @JavascriptInterface
    public void openSetting(String action) {
        try {
            Intent intent = new Intent(action);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        } catch (Throwable t) {
            Log.w(TAG, "openSetting failed: " + action, t);
        }
    }

    /** Read a Settings.System/Secure/Global value. namespace: system|secure|global. */
    @JavascriptInterface
    public String getSetting(String namespace, String key) {
        try {
            switch (namespace == null ? "" : namespace) {
                case "system":
                    return Settings.System.getString(context.getContentResolver(), key);
                case "secure":
                    return Settings.Secure.getString(context.getContentResolver(), key);
                case "global":
                    return Settings.Global.getString(context.getContentResolver(), key);
                default:
                    return null;
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Write a Settings.System/Secure/Global value. The Settings app holds
     * WRITE_SETTINGS/WRITE_SECURE_SETTINGS, so this normally succeeds.
     */
    @JavascriptInterface
    public boolean putSetting(String namespace, String key, String value) {
        try {
            switch (namespace == null ? "" : namespace) {
                case "system":
                    return Settings.System.putString(context.getContentResolver(), key, value);
                case "secure":
                    return Settings.Secure.putString(context.getContentResolver(), key, value);
                case "global":
                    return Settings.Global.putString(context.getContentResolver(), key, value);
                default:
                    return false;
            }
        } catch (Throwable t) {
            Log.w(TAG, "putSetting failed", t);
            return false;
        }
    }

    /** Read a build.prop-style system property via android.os.SystemProperties. */
    @JavascriptInterface
    public String getProp(String key) {
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getDeclaredMethod("get", String.class);
            return (String) get.invoke(null, key);
        } catch (Throwable t) {
            return null;
        }
    }

    // --- shell ------------------------------------------------------------

    /** Run "sh -c <cmd>" as the Settings (system) user. Returns JSON result. */
    @JavascriptInterface
    public String exec(String cmd) {
        return runShell(cmd, false);
    }

    /** Run "su -c <cmd>" for rooted devices (requires su access for Settings). */
    @JavascriptInterface
    public String execRoot(String cmd) {
        return runShell(cmd, true);
    }

    private String runShell(String cmd, boolean su) {
        JSONObject out = new JSONObject();
        Process process = null;
        try {
            String[] argv = su
                    ? new String[]{"su", "-c", cmd}
                    : new String[]{"sh", "-c", cmd};
            process = Runtime.getRuntime().exec(argv);
            boolean done = process.waitFor(15, TimeUnit.SECONDS);
            String stdout = readStream(process.getInputStream());
            String stderr = readStream(process.getErrorStream());
            out.put("ok", done && process.exitValue() == 0);
            out.put("code", done ? process.exitValue() : -1);
            out.put("stdout", stdout);
            out.put("stderr", stderr);
        } catch (Throwable t) {
            try {
                out.put("ok", false);
                out.put("code", -1);
                out.put("error", String.valueOf(t));
            } catch (Throwable ignored) {
            }
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return out.toString();
    }

    private static String readStream(java.io.InputStream in) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        } catch (Throwable ignored) {
        }
        return sb.toString();
    }

    // --- extensions --------------------------------------------------------

    @JavascriptInterface
    public String listExtensions() {
        JSONArray arr = new JSONArray();
        for (Extension e : store.list()) {
            try {
                arr.put(e.publicJson());
            } catch (Throwable ignored) {
            }
        }
        return arr.toString();
    }

    /** Per-extension persistent key/value storage. */
    @JavascriptInterface
    public String kvGet(String extId, String key) {
        if (TextUtils.isEmpty(extId)) {
            return null;
        }
        return store.kvGet(extId, key);
    }

    @JavascriptInterface
    public void kvSet(String extId, String key, String value) {
        if (TextUtils.isEmpty(extId)) {
            return;
        }
        store.kvSet(extId, key, value);
    }
}

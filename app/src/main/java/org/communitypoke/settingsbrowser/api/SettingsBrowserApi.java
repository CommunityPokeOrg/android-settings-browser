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
import org.communitypoke.settingsbrowser.root.RootDaemon;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * JavaScript bridge exposed to every page and every extension as
 * {@code window.SettingsBrowser}. The browser now runs in this app's own
 * process; privileged work is delegated to the persistent root shell
 * (RootDaemon) instead of borrowing the Settings app's system uid.
 *
 * All @JavascriptInterface methods run on a background binder thread.
 */
public final class SettingsBrowserApi {

    private static final String TAG = "SettingsBrowserApi";
    private static final String API_VERSION = "2";

    private final Context context;
    private final WebView webView;
    private final ExtensionStore store;
    private final RootDaemon root;

    public SettingsBrowserApi(Context context, WebView webView, ExtensionStore store) {
        this.context = context.getApplicationContext();
        this.webView = webView;
        this.store = store;
        this.root = RootDaemon.get();
    }

    @JavascriptInterface
    public String apiVersion() {
        return API_VERSION;
    }

    /** Whether the persistent root shell (su) is available and granted. */
    @JavascriptInterface
    public boolean rootAvailable() {
        return root.isAvailable();
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
            o.put("rooted", root.isAvailable());
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

    /**
     * Read a Settings.System/Secure/Global value. namespace: system|secure|global.
     * Tries the content-resolver API first (works for many readable keys), then
     * falls back to `settings get` through the root daemon.
     */
    @JavascriptInterface
    public String getSetting(String namespace, String key) {
        String ns = normalizeNamespace(namespace);
        if (ns == null || key == null) {
            return null;
        }
        try {
            String value;
            switch (ns) {
                case "system":
                    value = Settings.System.getString(context.getContentResolver(), key);
                    break;
                case "secure":
                    value = Settings.Secure.getString(context.getContentResolver(), key);
                    break;
                default:
                    value = Settings.Global.getString(context.getContentResolver(), key);
                    break;
            }
            if (value != null) {
                return value;
            }
        } catch (Throwable ignored) {
        }
        RootDaemon.Result r = root.exec(
                "settings get " + ns + " " + RootDaemon.shellQuote(key));
        if (r.code == 0) {
            String out = r.stdout;
            return out.endsWith("\n") ? out.substring(0, out.length() - 1) : out;
        }
        return null;
    }

    /**
     * Write a Settings.System/Secure/Global value. A regular app cannot hold
     * WRITE_SECURE_SETTINGS, so this goes through the root daemon's
     * `settings put` (with a direct-API attempt first for the rare privileged
     * context, e.g. a system-signed build).
     */
    @JavascriptInterface
    public boolean putSetting(String namespace, String key, String value) {
        String ns = normalizeNamespace(namespace);
        if (ns == null || key == null) {
            return false;
        }
        try {
            boolean ok;
            switch (ns) {
                case "system":
                    ok = Settings.System.putString(context.getContentResolver(), key, value);
                    break;
                case "secure":
                    ok = Settings.Secure.putString(context.getContentResolver(), key, value);
                    break;
                default:
                    ok = Settings.Global.putString(context.getContentResolver(), key, value);
                    break;
            }
            if (ok) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        RootDaemon.Result r = root.exec("settings put " + ns + " "
                + RootDaemon.shellQuote(key) + " " + RootDaemon.shellQuote(value));
        return r.code == 0;
    }

    private static String normalizeNamespace(String namespace) {
        if ("system".equals(namespace) || "secure".equals(namespace)
                || "global".equals(namespace)) {
            return namespace;
        }
        return null;
    }

    /** Read a build.prop-style system property (getprop / SystemProperties). */
    @JavascriptInterface
    public String getProp(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }
        try {
            Class<?> sp = Class.forName("android.os.SystemProperties");
            Method get = sp.getDeclaredMethod("get", String.class);
            String value = (String) get.invoke(null, key);
            if (value != null && !value.isEmpty()) {
                return value;
            }
        } catch (Throwable ignored) {
        }
        // Hidden-API blocked (API 28+) or unset: fall back to the getprop binary.
        RootDaemon.Result r = root.isAvailable()
                ? root.exec("getprop " + RootDaemon.shellQuote(key))
                : null;
        if (r != null && r.code == 0) {
            String out = r.stdout;
            return out.endsWith("\n") ? out.substring(0, out.length() - 1) : out;
        }
        return null;
    }

    // --- shell ------------------------------------------------------------

    /** Run "sh -c <cmd>" as the app user. Returns JSON result. */
    @JavascriptInterface
    public String exec(String cmd) {
        return runShell(cmd, false);
    }

    /** Run a command in the persistent root shell (needs su granted to the app). */
    @JavascriptInterface
    public String execRoot(String cmd) {
        return runShell(cmd, true);
    }

    private String runShell(String cmd, boolean su) {
        if (su) {
            RootDaemon.Result r = root.exec(cmd);
            JSONObject out = new JSONObject();
            try {
                out.put("ok", r.code == 0 && !r.timedOut);
                out.put("code", r.code);
                out.put("stdout", r.stdout);
                out.put("stderr", r.stderr);
                if (r.error != null) {
                    out.put("error", r.error);
                }
            } catch (Throwable ignored) {
            }
            return out.toString();
        }
        JSONObject out = new JSONObject();
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
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

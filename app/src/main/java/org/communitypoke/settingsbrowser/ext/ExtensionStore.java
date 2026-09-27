package org.communitypoke.settingsbrowser.ext;

import android.content.Context;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistence for extensions. Everything lives in the app's private storage
 * under files/settings_browser/:
 *   index.json        extension metadata array
 *   exts/<id>.js      extension source
 *   kv/<id>.json      per-extension key/value storage exposed to page scripts
 */
public final class ExtensionStore {

    private static final String TAG = "SettingsBrowserStore";

    private final File root;
    private final File extDir;
    private final File kvDir;
    private final File indexFile;

    public ExtensionStore(Context context) {
        root = new File(context.getFilesDir(), "settings_browser");
        extDir = new File(root, "exts");
        kvDir = new File(root, "kv");
        indexFile = new File(root, "index.json");
    }

    public synchronized List<Extension> list() {
        List<Extension> out = new ArrayList<>();
        if (!indexFile.exists()) {
            return out;
        }
        try {
            JSONArray arr = new JSONObject(readFile(indexFile)).optJSONArray("extensions");
            if (arr == null) {
                return out;
            }
            for (int i = 0; i < arr.length(); i++) {
                Extension e = Extension.fromJson(arr.getJSONObject(i));
                e.source = readSource(e.id);
                out.add(e);
            }
        } catch (Throwable t) {
            Log.w(TAG, "failed to read extension index", t);
        }
        return out;
    }

    public synchronized List<Extension> enabledFor(String url) {
        List<Extension> out = new ArrayList<>();
        for (Extension e : list()) {
            if (e.enabled && e.shouldRunOn(url)) {
                out.add(e);
            }
        }
        return out;
    }

    public synchronized Extension add(Extension e) throws IOException {
        if (!extDir.exists() && !extDir.mkdirs()) {
            throw new IOException("cannot create " + extDir);
        }
        List<Extension> all = list();
        writeText(sourceFile(e.id), e.source);
        all.add(e);
        saveIndex(all);
        return e;
    }

    public synchronized void remove(String id) throws IOException {
        List<Extension> all = list();
        all.removeIf(e -> e.id.equals(id));
        File src = sourceFile(id);
        if (src.exists() && !src.delete()) {
            throw new IOException("cannot delete " + src);
        }
        saveIndex(all);
    }

    public synchronized void setEnabled(String id, boolean enabled) throws IOException {
        List<Extension> all = list();
        for (Extension e : all) {
            if (e.id.equals(id)) {
                e.enabled = enabled;
            }
        }
        saveIndex(all);
    }

    // --- per-extension key/value storage ----------------------------------

    public synchronized String kvGet(String extId, String key) {
        JSONObject kv = readKv(extId);
        return kv.optString(key, null);
    }

    public synchronized void kvSet(String extId, String key, String value) {
        JSONObject kv = readKv(extId);
        try {
            kv.put(key, value);
            File f = kvFile(extId);
            if (!kvDir.exists() && !kvDir.mkdirs()) {
                return;
            }
            writeText(f, kv.toString());
        } catch (Throwable t) {
            Log.w(TAG, "kv write failed", t);
        }
    }

    private JSONObject readKv(String extId) {
        File f = kvFile(extId);
        if (!f.exists()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(readFile(f));
        } catch (Throwable t) {
            return new JSONObject();
        }
    }

    // --- files -------------------------------------------------------------

    private File sourceFile(String id) {
        return new File(extDir, sanitize(id) + ".js");
    }

    private File kvFile(String id) {
        return new File(kvDir, sanitize(id) + ".json");
    }

    private static String sanitize(String id) {
        return id.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private String readSource(String id) {
        File f = sourceFile(id);
        if (!f.exists()) {
            return "";
        }
        try {
            return readFile(f);
        } catch (IOException e) {
            return "";
        }
    }

    private void saveIndex(List<Extension> all) throws IOException {
        JSONArray arr = new JSONArray();
        for (Extension e : all) {
            try {
                arr.put(e.toJson());
            } catch (Throwable ignored) {
            }
        }
        if (!root.exists() && !root.mkdirs()) {
            throw new IOException("cannot create " + root);
        }
        try {
            writeText(indexFile, new JSONObject().put("extensions", arr).toString());
        } catch (org.json.JSONException e) {
            throw new IOException("failed to serialize index", e);
        }
    }

    private static String readFile(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) != -1) {
                sb.append(buf, 0, n);
            }
        }
        return sb.toString();
    }

    private static void writeText(File f, String content) throws IOException {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            w.write(content);
        }
    }
}

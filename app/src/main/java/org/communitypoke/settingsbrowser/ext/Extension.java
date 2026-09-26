package org.communitypoke.settingsbrowser.ext;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One browser extension: a named JavaScript payload plus the match patterns
 * that decide which pages it runs on.
 */
public final class Extension {

    public static final String RUN_AT_START = "document-start";
    public static final String RUN_AT_END = "document-end";

    public String id;
    public String name;
    public String version;
    public String description;
    /** Match patterns, e.g. "*://*.example.com/*" or "<all_urls>". */
    public final List<String> matches = new ArrayList<>();
    /** Optional regex include list (checked in addition to matches). */
    public final List<String> includes = new ArrayList<>();
    public String runAt = RUN_AT_END;
    public boolean enabled = true;
    /** Raw JavaScript source. */
    public String source = "";

    public Extension() {
        this.id = UUID.randomUUID().toString();
    }

    boolean shouldRunOn(String url) {
        if (url == null) {
            return false;
        }
        for (String pattern : matches) {
            if (MatchPattern.matches(pattern, url)) {
                return true;
            }
        }
        for (String include : includes) {
            try {
                if (url.matches(include)) {
                    return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("version", version);
        o.put("description", description);
        o.put("runAt", runAt);
        o.put("enabled", enabled);
        o.put("matches", new JSONArray(matches));
        o.put("includes", new JSONArray(includes));
        return o;
    }

    /** Summary JSON for the JS bridge (no source). */
    public JSONObject publicJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("version", version);
        o.put("enabled", enabled);
        return o;
    }

    static Extension fromJson(JSONObject o) {
        Extension e = new Extension();
        e.id = o.optString("id", e.id);
        e.name = o.optString("name", "Unnamed");
        e.version = o.optString("version", "");
        e.description = o.optString("description", "");
        e.runAt = o.optString("runAt", RUN_AT_END);
        e.enabled = o.optBoolean("enabled", true);
        JSONArray m = o.optJSONArray("matches");
        if (m != null) {
            for (int i = 0; i < m.length(); i++) {
                e.matches.add(m.optString(i));
            }
        }
        JSONArray inc = o.optJSONArray("includes");
        if (inc != null) {
            for (int i = 0; i < inc.length(); i++) {
                e.includes.add(inc.optString(i));
            }
        }
        return e;
    }
}

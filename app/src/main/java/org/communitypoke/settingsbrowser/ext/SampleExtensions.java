package org.communitypoke.settingsbrowser.ext;

import java.util.Arrays;
import java.util.List;

/** Built-in demo extensions the user can install from the manager page. */
public final class SampleExtensions {

    private SampleExtensions() {
    }

    public static List<Extension> samples() {
        return Arrays.asList(darkMode(), settingsBanner());
    }

    /** Inverts page colors as a document-end stylesheet injection. */
    public static Extension darkMode() {
        Extension e = new Extension();
        e.name = "Dark Mode";
        e.version = "1.0";
        e.description = "Inverts page colors for a rough dark theme.";
        e.matches.add("*://*/*");
        e.runAt = Extension.RUN_AT_END;
        e.source = "(function(){\n"
                + "  var s = document.createElement('style');\n"
                + "  s.textContent = 'html{filter:invert(1) hue-rotate(180deg)!important;}"
                + " img,video{filter:invert(1) hue-rotate(180deg)!important;}';\n"
                + "  (document.head || document.documentElement).appendChild(s);\n"
                + "})();\n";
        return e;
    }

    /** Shows a banner demonstrating the SettingsBrowser bridge API. */
    public static Extension settingsBanner() {
        Extension e = new Extension();
        e.name = "Settings banner";
        e.version = "1.0";
        e.description = "Adds a floating panel with device info and quick settings links.";
        e.matches.add("*://*/*");
        e.runAt = Extension.RUN_AT_END;
        e.source = "(function(){\n"
                + "  var api = window.SettingsBrowser; if (!api) return;\n"
                + "  var info = {}; try { info = JSON.parse(api.deviceInfo()); } catch(e){}\n"
                + "  var d = document.createElement('div');\n"
                + "  d.style.cssText = 'position:fixed;bottom:12px;right:12px;z-index:2147483647;'\n"
                + "    + 'background:#222;color:#fff;padding:10px 14px;border-radius:10px;'\n"
                + "    + 'font:13px sans-serif;box-shadow:0 2px 10px rgba(0,0,0,.4);opacity:.92';\n"
                + "  d.innerHTML = '<b>' + (info.model || 'device') + '</b>'\n"
                + "    + '<br>SDK ' + (info.sdk || '?') + ' · <a id=sbx-wifi style=\"color:#8ab4f8\">Wi-Fi settings</a>';\n"
                + "  d.onclick = function(ev){\n"
                + "    if (ev.target && ev.target.id === 'sbx-wifi'){\n"
                + "      api.openSetting('android.settings.WIFI_SETTINGS');\n"
                + "    }\n"
                + "  };\n"
                + "  (document.body || document.documentElement).appendChild(d);\n"
                + "})();\n";
        return e;
    }
}

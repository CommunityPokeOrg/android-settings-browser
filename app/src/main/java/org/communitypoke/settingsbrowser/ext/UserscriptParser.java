package org.communitypoke.settingsbrowser.ext;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a Greasemonkey/SettingsBrowser metadata block:
 *
 *   // ==SettingsBrowser==
 *   // @name        My extension
 *   // @match       *://*.example.com/*
 *   // @include     ^https?://...
 *   // @run-at      document-start|document-end
 *   // ==/SettingsBrowser==
 *
 * The legacy ==UserScript== block markers are accepted too.
 */
public final class UserscriptParser {

    private static final Pattern BLOCK =
            Pattern.compile("//\\s*==\\s*(?:SettingsBrowser|UserScript)\\s*==(.*?)//\\s*==/\\s*(?:SettingsBrowser|UserScript)\\s*==",
                    Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
    private static final Pattern DIRECTIVE =
            Pattern.compile("^\\s*//\\s*@([\\w-]+)\\s+(.+?)\\s*$", Pattern.MULTILINE);

    private UserscriptParser() {
    }

    public static Extension parse(String source, String fallbackName) {
        Extension e = new Extension();
        e.source = source;
        e.name = fallbackName != null ? fallbackName : "Imported extension";

        Matcher block = BLOCK.matcher(source);
        if (!block.find()) {
            e.matches.add("*://*/*");
            return e;
        }
        Matcher directive = DIRECTIVE.matcher(block.group(1));
        while (directive.find()) {
            String key = directive.group(1).toLowerCase();
            String value = directive.group(2).trim();
            switch (key) {
                case "name":
                    e.name = value;
                    break;
                case "version":
                    e.version = value;
                    break;
                case "description":
                    e.description = value;
                    break;
                case "match":
                    e.matches.add(value);
                    break;
                case "include":
                    e.includes.add(value);
                    break;
                case "run-at":
                case "runat":
                    if (value.equals(Extension.RUN_AT_START)
                            || value.equals(Extension.RUN_AT_END)) {
                        e.runAt = value;
                    }
                    break;
                default:
                    break;
            }
        }
        if (e.matches.isEmpty() && e.includes.isEmpty()) {
            e.matches.add("*://*/*");
        }
        return e;
    }
}

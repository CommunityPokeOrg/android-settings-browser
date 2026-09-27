package org.communitypoke.settingsbrowser.ext;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class UserscriptParserTest {

    @Test
    public void parsesMetadataBlock() {
        String src = "// ==SettingsBrowser==\n"
                + "// @name        My extension\n"
                + "// @version     1.2\n"
                + "// @description Does a thing\n"
                + "// @match       *://*.example.com/*\n"
                + "// @run-at      document-start\n"
                + "// ==/SettingsBrowser==\n"
                + "console.log('hi');\n";
        Extension e = UserscriptParser.parse(src, "fallback");
        assertEquals("My extension", e.name);
        assertEquals("1.2", e.version);
        assertEquals("Does a thing", e.description);
        assertEquals(Extension.RUN_AT_START, e.runAt);
        assertEquals(1, e.matches.size());
        assertEquals("*://*.example.com/*", e.matches.get(0));
        assertEquals(src, e.source);
    }

    @Test
    public void legacyUserScriptMarkers_accepted() {
        String src = "// ==UserScript==\n// @name Old\n// ==/UserScript==\nx();\n";
        assertEquals("Old", UserscriptParser.parse(src, null).name);
    }

    @Test
    public void noBlock_defaultsToAllUrlsAndFallbackName() {
        Extension e = UserscriptParser.parse("x();\n", "plain.js");
        assertEquals("plain.js", e.name);
        assertEquals(Extension.RUN_AT_END, e.runAt);
        assertTrue(e.matches.contains("*://*/*"));
    }

    @Test
    public void includeRegex_isCollected() {
        String src = "// ==SettingsBrowser==\n"
                + "// @include ^https?://example\\.net/\n"
                + "// ==/SettingsBrowser==\n";
        Extension e = UserscriptParser.parse(src, null);
        assertEquals(1, e.includes.size());
    }
}

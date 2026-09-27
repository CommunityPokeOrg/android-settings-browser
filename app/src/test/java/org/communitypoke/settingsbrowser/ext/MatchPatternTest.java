package org.communitypoke.settingsbrowser.ext;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MatchPatternTest {

    @Test
    public void allUrls_matchesHttpAndHttps() {
        assertTrue(MatchPattern.matches("<all_urls>", "http://a.b/c"));
        assertTrue(MatchPattern.matches("<all_urls>", "https://a.b/c"));
        assertFalse(MatchPattern.matches("<all_urls>", "ftp://a.b/c"));
    }

    @Test
    public void schemeWildcard_matchesBothSchemes() {
        assertTrue(MatchPattern.matches("*://example.com/*", "http://example.com/x"));
        assertTrue(MatchPattern.matches("*://example.com/*", "https://example.com/x"));
        assertFalse(MatchPattern.matches("http://example.com/*", "https://example.com/x"));
    }

    @Test
    public void subdomainWildcard_coversApexAndSubdomains() {
        assertTrue(MatchPattern.matches("*://*.example.com/*", "https://www.example.com/x"));
        assertTrue(MatchPattern.matches("*://*.example.com/*", "https://example.com/x"));
        assertFalse(MatchPattern.matches("*://*.example.com/*", "https://notexample.com/x"));
    }

    @Test
    public void pathGlob_matchesSuffixes() {
        assertTrue(MatchPattern.matches("*://example.com/a/*", "https://example.com/a/b/c"));
        assertFalse(MatchPattern.matches("*://example.com/a/*", "https://example.com/b"));
    }

    @Test
    public void invalidPattern_neverMatches() {
        assertFalse(MatchPattern.matches("not-a-pattern", "https://example.com/"));
        assertFalse(MatchPattern.matches(null, "https://example.com/"));
    }
}

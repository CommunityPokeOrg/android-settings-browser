package org.communitypoke.settingsbrowser.ext;

import java.util.regex.Pattern;

/**
 * Chrome-style match patterns: {@code scheme://host/path}.
 * scheme: "*", "http", "https". host: "*", "*.domain", "domain".
 * path: glob where * matches any sequence. "<all_urls>" matches http(s) pages.
 */
public final class MatchPattern {

    private MatchPattern() {
    }

    public static boolean matches(String pattern, String url) {
        Pattern compiled = compile(pattern);
        return compiled != null && compiled.matcher(url).matches();
    }

    static Pattern compile(String pattern) {
        if (pattern == null) {
            return null;
        }
        pattern = pattern.trim();
        if (pattern.equals("<all_urls>")) {
            return Pattern.compile("^https?://.*");
        }
        int schemeEnd = pattern.indexOf("://");
        if (schemeEnd <= 0) {
            return null;
        }
        String scheme = pattern.substring(0, schemeEnd);
        String rest = pattern.substring(schemeEnd + 3);
        int slash = rest.indexOf('/');
        String host = slash < 0 ? rest : rest.substring(0, slash);
        String path = slash < 0 ? "/*" : rest.substring(slash);

        StringBuilder regex = new StringBuilder("^");
        switch (scheme) {
            case "*":
                regex.append("https?");
                break;
            case "http":
            case "https":
                regex.append(scheme);
                break;
            default:
                regex.append(Pattern.quote(scheme));
                break;
        }
        regex.append("://");

        if (host.equals("*") || host.isEmpty()) {
            regex.append("[^/]+");
        } else if (host.startsWith("*.")) {
            regex.append("([^/]*\\.)?").append(Pattern.quote(host.substring(2)));
        } else {
            regex.append(Pattern.quote(host));
        }
        regex.append(glob(path)).append('$');
        try {
            return Pattern.compile(regex.toString(), Pattern.CASE_INSENSITIVE);
        } catch (Throwable t) {
            return null;
        }
    }

    private static String glob(String glob) {
        StringBuilder out = new StringBuilder();
        for (char c : glob.toCharArray()) {
            switch (c) {
                case '*':
                    out.append(".*");
                    break;
                case '?':
                    out.append('.');
                    break;
                default:
                    out.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return out.toString();
    }
}

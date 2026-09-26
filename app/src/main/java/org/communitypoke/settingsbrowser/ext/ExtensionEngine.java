package org.communitypoke.settingsbrowser.ext;

import android.util.Log;
import android.webkit.WebView;

/**
 * Runs enabled extensions in the WebView. document-start scripts are injected
 * from WebViewClient.onPageStarted (best-effort: WebView offers no true
 * main-world document-start), document-end from onPageFinished.
 *
 * Inside each script {@code __EXT_ID} is bound to the extension id and
 * {@code SettingsBrowser} is the Java bridge object (see SettingsBrowserApi).
 */
public final class ExtensionEngine {

    private static final String TAG = "SettingsBrowserEngine";

    private final ExtensionStore store;

    public ExtensionEngine(ExtensionStore store) {
        this.store = store;
    }

    public void injectAt(WebView view, String url, String runAt) {
        if (view == null || url == null || url.startsWith("about:")) {
            return;
        }
        for (Extension e : store.enabledFor(url)) {
            if (!e.runAt.equals(runAt)) {
                continue;
            }
            view.evaluateJavascript(wrap(e), null);
        }
    }

    private static String wrap(Extension e) {
        StringBuilder js = new StringBuilder(e.source.length() + 256);
        js.append(";try{var __EXT_ID=").append(jsString(e.id))
                .append(";var __EXT_NAME=").append(jsString(e.name)).append(";\n")
                .append(e.source)
                .append("\n}catch(__e){console.error('[SettingsBrowser:' + __EXT_NAME + '] ' + __e);}");
        return js.toString();
    }

    private static String jsString(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder b = new StringBuilder(s.length() + 2).append('\'');
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\'':
                    b.append("\\'");
                    break;
                case '\\':
                    b.append("\\\\");
                    break;
                case '\n':
                    b.append("\\n");
                    break;
                case '\r':
                    b.append("\\r");
                    break;
                default:
                    b.append(c);
            }
        }
        return b.append('\'').toString();
    }
}

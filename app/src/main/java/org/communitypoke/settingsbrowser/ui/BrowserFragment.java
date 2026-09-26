package org.communitypoke.settingsbrowser.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.communitypoke.settingsbrowser.api.SettingsBrowserApi;
import org.communitypoke.settingsbrowser.ext.Extension;
import org.communitypoke.settingsbrowser.ext.ExtensionEngine;
import org.communitypoke.settingsbrowser.ext.ExtensionStore;

import java.net.URLEncoder;
import java.util.List;

/**
 * The embedded browser screen. Lives inside com.android.settings, so its
 * WebView shares the Settings app's process, permissions and storage.
 */
public class BrowserFragment extends Fragment {

    private WebView webView;
    private EditText urlField;
    private ProgressBar progress;
    private Button backButton;
    private Button forwardButton;
    private ExtensionEngine engine;
    private ExtensionStore store;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ExtensionStore(requireContext());
        engine = new ExtensionEngine(store);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.addView(buildToolbar(ctx));
        progress = new ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        progress.setVisibility(View.GONE);
        root.addView(progress, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 3)));
        webView = buildWebView(ctx);
        root.addView(webView, Ui.weighted());
        loadHomePage();
        return root;
    }

    private View buildToolbar(Context ctx) {
        LinearLayout bar = new LinearLayout(ctx);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 4), Ui.dp(ctx, 4));

        backButton = new Button(ctx);
        backButton.setText("◀");
        backButton.setOnClickListener(v -> {
            if (webView != null && webView.canGoBack()) {
                webView.goBack();
            } else if (getParentFragmentManager().getBackStackEntryCount() > 0) {
                getParentFragmentManager().popBackStack();
            }
        });

        forwardButton = new Button(ctx);
        forwardButton.setText("▶");
        forwardButton.setOnClickListener(v -> {
            if (webView != null && webView.canGoForward()) {
                webView.goForward();
            }
        });

        Button reload = new Button(ctx);
        reload.setText("⟳");
        reload.setOnClickListener(v -> {
            if (webView != null) {
                webView.reload();
            }
        });

        urlField = new EditText(ctx);
        urlField.setSingleLine(true);
        urlField.setHint("Search or enter URL");
        urlField.setImeOptions(EditorInfo.IME_ACTION_GO);
        urlField.setOnEditorActionListener((v, actionId, event) -> {
            go();
            return true;
        });

        Button go = new Button(ctx);
        go.setText("Go");
        go.setOnClickListener(v -> go());

        Button ext = new Button(ctx);
        ext.setText("Ext");
        ext.setOnClickListener(v -> Navigation.open(this, new ExtensionsFragment()));

        LinearLayout.LayoutParams wrap = Ui.linearParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        LinearLayout.LayoutParams grow = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);

        bar.addView(backButton, wrap);
        bar.addView(forwardButton, wrap);
        bar.addView(reload, wrap);
        bar.addView(urlField, grow);
        bar.addView(go, wrap);
        bar.addView(ext, wrap);
        return bar;
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private WebView buildWebView(Context ctx) {
        WebView view = new WebView(ctx);
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setDomStorageEnabled(true);
        view.getSettings().setDatabaseEnabled(true);
        view.getSettings().setBuiltInZoomControls(true);
        view.getSettings().setDisplayZoomControls(false);
        view.getSettings().setLoadWithOverviewMode(true);
        view.getSettings().setUseWideViewPort(true);
        view.getSettings().setMediaPlaybackRequiresUserGesture(true);
        view.addJavascriptInterface(
                new SettingsBrowserApi(ctx, view, store), "SettingsBrowser");

        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
                engine.injectAt(v, url, Extension.RUN_AT_START);
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                progress.setVisibility(View.GONE);
                engine.injectAt(v, url, Extension.RUN_AT_END);
                urlField.setText(url);
                backButton.setEnabled(v.canGoBack());
                forwardButton.setEnabled(v.canGoForward());
            }
        });
        view.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int newProgress) {
                progress.setProgress(newProgress);
            }
        });
        return view;
    }

    private void go() {
        if (webView == null) {
            return;
        }
        String input = urlField.getText().toString().trim();
        if (input.isEmpty()) {
            return;
        }
        String url;
        if (input.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*$")) {
            url = input;
        } else if (input.contains(".") && !input.contains(" ")) {
            url = "https://" + input;
        } else {
            url = "https://duckduckgo.com/?q=" + URLEncoder.encode(input);
        }
        webView.loadUrl(url);
    }

    /** Internal start page listing installed extensions and the bridge API. */
    private void loadHomePage() {
        StringBuilder rows = new StringBuilder();
        List<Extension> all = store.list();
        if (all.isEmpty()) {
            rows.append("<p class=dim>No extensions installed. Manage them under "
                    + "Settings &rarr; System &rarr; Browser extensions.</p>");
        }
        for (Extension e : all) {
            rows.append("<li>")
                    .append(escape(e.name)).append(' ')
                    .append(e.enabled
                            ? "<span class=on>enabled</span>"
                            : "<span class=off>disabled</span>")
                    .append("</li>");
        }
        String html = "<!doctype html><html><head><meta name=viewport "
                + "content='width=device-width,initial-scale=1'>"
                + "<style>body{font-family:sans-serif;margin:24px;background:#1b1b1f;"
                + "color:#e3e3e3}h1{font-size:20px}.dim{color:#9a9aa0}"
                + ".on{color:#7fdb9a}.off{color:#e0816f}code{background:#2a2a30;"
                + "padding:1px 5px;border-radius:4px}</style></head><body>"
                + "<h1>Settings Browser</h1>"
                + "<p class=dim>This WebView runs inside the Settings process. "
                + "Page scripts and extensions can call "
                + "<code>SettingsBrowser.*</code> (openSetting, getSetting, "
                + "putSetting, getProp, exec, kvGet/kvSet).</p>"
                + "<h2>Extensions</h2><ul>" + rows + "</ul>"
                + "</body></html>";
        webView.loadDataWithBaseURL("https://settings.browser/", html,
                "text/html", "utf-8", null);
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @Override
    public void onDestroyView() {
        if (webView != null) {
            webView.destroy();
            webView = null;
        }
        super.onDestroyView();
    }
}

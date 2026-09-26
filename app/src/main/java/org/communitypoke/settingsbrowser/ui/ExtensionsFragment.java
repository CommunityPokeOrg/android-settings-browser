package org.communitypoke.settingsbrowser.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import org.communitypoke.settingsbrowser.ext.Extension;
import org.communitypoke.settingsbrowser.ext.ExtensionStore;
import org.communitypoke.settingsbrowser.ext.SampleExtensions;
import org.communitypoke.settingsbrowser.ext.UserscriptParser;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Extension management screen, placed under Settings &gt; System.
 * Lists installed extensions with enable toggles, supports importing
 * userscript-style .js files via the system picker, deleting, and installing
 * the bundled samples.
 */
public class ExtensionsFragment extends Fragment {

    private static final int REQ_PICK_SCRIPT = 41;

    private ExtensionStore store;
    private final List<Extension> items = new ArrayList<>();
    private BaseAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new ExtensionStore(requireContext());
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        Context ctx = requireContext();
        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView title = new TextView(ctx);
        title.setText("Browser extensions");
        title.setTextSize(20);
        title.setTextColor(Ui.themedTextColor(ctx));
        int pad = Ui.dp(ctx, 16);
        title.setPadding(pad, pad, pad, Ui.dp(ctx, 4));

        TextView subtitle = new TextView(ctx);
        subtitle.setText("JavaScript extensions for the embedded browser. "
                + "Page scripts can reach the SettingsBrowser bridge API.");
        subtitle.setPadding(pad, 0, pad, pad);

        LinearLayout actions = new LinearLayout(ctx);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(pad, 0, pad, Ui.dp(ctx, 8));
        Button importBtn = new Button(ctx);
        importBtn.setText("Import .js");
        importBtn.setOnClickListener(v -> pickScript());
        Button samplesBtn = new Button(ctx);
        samplesBtn.setText("Add samples");
        samplesBtn.setOnClickListener(v -> installSamples());
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        btnLp.setMarginEnd(Ui.dp(ctx, 8));
        actions.addView(importBtn, btnLp);
        actions.addView(samplesBtn, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ListView list = new ListView(ctx);
        adapter = new ExtAdapter();
        list.setAdapter(adapter);

        root.addView(title);
        root.addView(subtitle);
        root.addView(actions);
        root.addView(list, Ui.weighted());

        reload();
        return root;
    }

    private void pickScript() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*")
                .putExtra(Intent.EXTRA_MIME_TYPES,
                        new String[]{"text/javascript", "application/javascript", "text/plain", "*/*"});
        try {
            startActivityForResult(intent, REQ_PICK_SCRIPT);
        } catch (Throwable t) {
            toast("No file picker available");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_PICK_SCRIPT || resultCode != Activity.RESULT_OK
                || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        try {
            String source = readUri(uri);
            Extension e = UserscriptParser.parse(source, displayName(uri));
            store.add(e);
            toast("Installed " + e.name);
            reload();
        } catch (Throwable t) {
            toast("Import failed: " + t.getMessage());
        }
    }

    private void installSamples() {
        int added = 0;
        try {
            List<Extension> existing = store.list();
            for (Extension sample : SampleExtensions.samples()) {
                boolean present = false;
                for (Extension e : existing) {
                    if (sample.name.equals(e.name)) {
                        present = true;
                        break;
                    }
                }
                if (!present) {
                    store.add(sample);
                    added++;
                }
            }
            toast(added > 0 ? "Installed " + added + " sample(s)" : "Samples already installed");
        } catch (Throwable t) {
            toast("Failed: " + t.getMessage());
        }
        reload();
    }

    private void reload() {
        items.clear();
        items.addAll(store.list());
        adapter.notifyDataSetChanged();
    }

    private String readUri(Uri uri) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = requireContext().getContentResolver().openInputStream(uri);
             BufferedReader r = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    private String displayName(Uri uri) {
        String last = uri.getLastPathSegment();
        if (last == null) {
            return null;
        }
        int slash = last.lastIndexOf('/');
        return slash >= 0 ? last.substring(slash + 1) : last;
    }

    private void toast(String msg) {
        Context ctx = getContext();
        if (ctx != null) {
            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show();
        }
    }

    // --- list adapter ------------------------------------------------------

    private class ExtAdapter extends BaseAdapter {
        @Override public int getCount() { return items.size(); }
        @Override public Object getItem(int i) { return items.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            Context ctx = parent.getContext();
            Extension e = items.get(position);

            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = Ui.dp(ctx, 12);
            row.setPadding(pad, pad / 2, pad, pad / 2);

            CheckBox toggle = new CheckBox(ctx);
            toggle.setChecked(e.enabled);
            toggle.setOnCheckedChangeListener((b, checked) -> {
                try {
                    store.setEnabled(e.id, checked);
                    e.enabled = checked;
                } catch (Throwable t) {
                    toast("Failed: " + t.getMessage());
                }
            });

            LinearLayout text = new LinearLayout(ctx);
            text.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(ctx);
            name.setText(e.name + (e.version != null && !e.version.isEmpty()
                    ? "  v" + e.version : ""));
            name.setTextSize(16);
            name.setTextColor(Ui.themedTextColor(ctx));
            TextView meta = new TextView(ctx);
            StringBuilder m = new StringBuilder(e.runAt);
            if (!e.matches.isEmpty()) {
                m.append(" · ").append(TextUtils_join(e.matches));
            }
            if (e.description != null && !e.description.isEmpty()) {
                m.append("\n").append(e.description);
            }
            meta.setText(m.toString());
            meta.setTextSize(12);
            meta.setTextColor(Color.GRAY);
            text.addView(name);
            text.addView(meta);

            Button delete = new Button(ctx);
            delete.setText("✕");
            delete.setOnClickListener(v -> {
                try {
                    store.remove(e.id);
                    toast("Removed " + e.name);
                    reload();
                } catch (Throwable t) {
                    toast("Failed: " + t.getMessage());
                }
            });

            row.addView(toggle);
            row.addView(text, new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            row.addView(delete);
            return row;
        }

        private String TextUtils_join(List<String> parts) {
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < parts.size(); i++) {
                if (i > 0) {
                    b.append(", ");
                }
                b.append(parts.get(i));
            }
            return b.toString();
        }
    }
}

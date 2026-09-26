# Settings Browser

A root-only Android system module that embeds a full web browser **inside the
Settings app** (`com.android.settings`), plus a JavaScript extension engine with
a privileged bridge API and an extension manager under **Settings → System**.

## Requirements

- Rooted device (Magisk / KernelSU / APatch)
- LSPosed (or another Xposed framework) supporting the **modern Xposed API**
  (api ≥ 100, i.e. LSPosed ≥ 1.9.x)
- The stock Settings app (`com.android.settings`); tested layout-wise against
  AOSP 12–14 with fallbacks for older/OEM dashboards

## What it adds

| Screen | Entry |
| --- | --- |
| Settings home page | **Browser** — opens the embedded WebView browser |
| Settings → System | **Browser** — same browser, shortcut inside System |
| Settings → System | **Browser extensions** — the extension manager |

The browser is a `WebView` hosted in a fragment injected directly into the
Settings process — it shares the Settings app's system uid, permissions and
private storage.

## How it works

This is an Xposed module using the modern `libxposed` API:

- `META-INF/xposed/java_init.list` registers `ModuleMain`
- `META-INF/xposed/scope.list` scopes it to `com.android.settings`
- `SettingsInjector` hooks the preference-fragment lifecycle
  (`onCreatePreferences`/`onResume` on `DashboardFragment`,
  `TopLevelSettings`/`DashboardSummary`, and `SystemDashboardFragment`) and
  appends `Preference` entries whose click handlers swap in our fragments via
  the fragment manager (container `R.id.main_content`, falling back to
  `android.R.id.content`).

Because the module's dex is loaded inside the Settings process, our
`BrowserFragment`/`ExtensionsFragment` can extend `androidx.fragment.app.Fragment`
and bind `androidx.preference.Preference` objects straight onto Settings'
preference screen.

## Extension API

Extensions are Greasemonkey-style `.js` files:

```js
// ==SettingsBrowser==
// @name        My extension
// @version     1.0
// @match       *://*.example.com/*
// @include     ^https?://example\.net/        (optional regex)
// @run-at      document-end                   (or document-start)
// ==/SettingsBrowser==
// Your code. __EXT_ID and __EXT_NAME are predefined; window.SettingsBrowser
// is the bridge object.
```

Match patterns follow Chrome semantics: `scheme://host/path` where scheme is
`*`/`http`/`https`, host is `*`/`*.domain`/`domain`, and `*` in the path is a
glob. `<all_urls>` is also accepted.

`document-start` runs at `WebViewClient.onPageStarted` (best effort — WebView
has no true main-world document-start), `document-end` at `onPageFinished`.

### `window.SettingsBrowser` bridge

All calls are synchronous from JS. Because they execute in the Settings
process, privileged calls work with Settings' permissions.

| Method | Description |
| --- | --- |
| `apiVersion()` | Bridge version string |
| `deviceInfo()` | JSON `{manufacturer, model, device, sdk, release}` |
| `navigate(url)` | Load a URL in the embedded WebView |
| `openSetting(action)` | Launch a settings screen, e.g. `android.settings.WIFI_SETTINGS` |
| `getSetting(ns, key)` | Read `Settings.System`/`Secure`/`Global` (ns = `system`\|`secure`\|`global`) |
| `putSetting(ns, key, value)` | Write the same providers |
| `getProp(key)` | `android.os.SystemProperties.get` |
| `exec(cmd)` | `sh -c <cmd>` as the system user; returns JSON `{ok, code, stdout, stderr}` |
| `execRoot(cmd)` | `su -c <cmd>` (needs su access granted to Settings) |
| `listExtensions()` | JSON array of installed extensions |
| `kvGet(extId, key)` / `kvSet(extId, key, value)` | Per-extension persistent key/value store |

## Build & install

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Then in LSPosed: enable the module, scope it to **Settings**, and force-stop
Settings. Open Settings → the **Browser** entry appears on the home page, and
**System → Browser extensions** manages extensions.

## Repository layout

```
app/src/main/java/org/communitypoke/settingsbrowser/
├── ModuleMain.java            module entry (META-INF/xposed/java_init.list)
├── SettingsInjector.java      hooks Settings preference screens
├── api/SettingsBrowserApi.java  JS bridge (window.SettingsBrowser)
├── ext/                       Extension model, store, engine, userscript parser
├── ui/                        Browser + extension-manager fragments
└── app/ModuleStatusActivity.java  standalone status/info screen
app/src/main/resources/META-INF/xposed/  module manifest (init/scope/props)
```

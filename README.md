# Settings Browser

A root-only Android app that embeds a full web browser with a JavaScript
extension engine and a privileged bridge API — **no Xposed/LSPosed required**.

This used to be an LSPosed module that injected its UI inside
`com.android.settings`. It has been rewritten as a standalone root app:
the browser and extension manager run in the app's own process, and
privileged operations are handled by a persistent `su` root shell
(`RootDaemon`) spawned once and reused for every privileged call.

## Requirements

- Rooted device (Magisk / KernelSU / APatch — anything providing `su`)
- Android 8.0+ (minSdk 26); the WebView needs a working system WebView provider

## What it provides

| Surface | Entry |
| --- | --- |
| Launcher | **Settings Browser** app — opens the embedded WebView browser |
| Quick Settings | **Settings Browser** tile — one-tap browser from the shade |
| Browser toolbar | **Ext** — the extension manager |

The one thing that cannot be preserved without a hook framework is UI *inside*
the Settings app: the old `Browser`/`Browser extensions` preference rows are
gone, replaced by the launcher activity and the QS tile.

## How it works

- `MainActivity` hosts `BrowserFragment`/`ExtensionsFragment` (androidx
  fragments, now bundled as real dependencies — no more `compileOnly`
  trickery relying on the host's classloader).
- `RootDaemon` keeps a single `su` process alive and pipes commands into it.
  Each command is delivered as a base64 script, executed with
  stdout/stderr/exit-code captured through sentinel markers.
- The `SettingsBrowser` JS bridge calls the root daemon for everything
  privileged: `settings get/put` for Settings.System/Secure/Global writes
  (a normal app can't hold `WRITE_SECURE_SETTINGS`), `getprop` fallback,
  and `execRoot`.

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

All calls are synchronous from JS. Privileged calls run through the root
shell rather than with Settings' system uid (as they did when the browser
lived inside the Settings process).

| Method | Description |
| --- | --- |
| `apiVersion()` | Bridge version string (`"2"`) |
| `rootAvailable()` | Whether a usable `su` shell is granted |
| `deviceInfo()` | JSON `{manufacturer, model, device, sdk, release, rooted}` |
| `navigate(url)` | Load a URL in the embedded WebView |
| `openSetting(action)` | Launch a settings screen, e.g. `android.settings.WIFI_SETTINGS` |
| `getSetting(ns, key)` | Read `Settings.System`/`Secure`/`Global` (ns = `system`\|`secure`\|`global`); API first, `settings get` via root fallback |
| `putSetting(ns, key, value)` | Write the same providers; via `settings put` as root |
| `getProp(key)` | System property read (`SystemProperties`, `getprop` fallback) |
| `exec(cmd)` | `sh -c <cmd>` as the app user; returns JSON `{ok, code, stdout, stderr}` |
| `execRoot(cmd)` | Run in the persistent `su` shell; same JSON result |
| `listExtensions()` | JSON array of installed extensions |
| `kvGet(extId, key)` / `kvSet(extId, key, value)` | Per-extension persistent key/value store |

## Build & install

```sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Open the app, grant the `su` request when your root manager prompts, and the
browser is ready. Add the **Settings Browser** tile to Quick Settings for
one-tap access.

Unit tests (pure-JVM, host-side) cover the userscript parser and match
patterns:

```sh
./gradlew testDebugUnitTest
```

## Repository layout

```
app/src/main/java/org/communitypoke/settingsbrowser/
├── app/MainActivity.java       launcher activity hosting the browser
├── app/BrowserTileService.java Quick Settings tile entry point
├── root/RootDaemon.java        persistent `su` root shell (the root daemon)
├── api/SettingsBrowserApi.java JS bridge (window.SettingsBrowser)
├── ext/                        Extension model, store, engine, userscript parser
└── ui/                         Browser + extension-manager fragments
app/src/test/java/...           JVM unit tests for MatchPattern/UserscriptParser
```

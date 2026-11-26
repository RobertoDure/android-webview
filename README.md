# GitHub WebView

A small, single-activity Android app that hosts the GitHub web app in a hardened
`WebView`. The project started in 2015 as an Eclipse ADT project (API 17, Java 6,
support-v4) and has been migrated to a current Android stack.

## What changed

| Area | Legacy project | This project |
| --- | --- | --- |
| Build | Eclipse ADT (`project.properties`, `bin/`, `gen/`) | Gradle **8.14.8** Kotlin DSL + version catalog |
| Language | Java 6 | **Kotlin 2.4**, JVM 17 bytecode |
| SDK | minSdk 14, compile/target 17 | **minSdk 26, compile/target 36** (Android 16) |
| UI | ActionBar + `ProgressDialog` + 2 s splash Activity | **Material 3**, system **SplashScreen API**, edge-to-edge, `LinearProgressIndicator` |
| Navigation | deprecated `shouldOverrideUrlLoading(WebView, String)` | `WebViewClientCompat` + `WebResourceRequest`, main-frame-only policy |
| Splash | second Activity + `Handler.postDelayed` | `androidx.core:core-splashscreen` with a bounded keep condition |
| Rotation | deprecated `saveState` with a bug in `onRestoreInstanceState` | `configChanges` keeps the WebView alive; URL persisted for process death |
| Back | `onBackPressed()` override | `OnBackPressedDispatcher` + predictive back |
| Dark mode | none | DayNight Material 3 + WebView algorithmic darkening (105+) |
| Errors | `Toast` only | Offline/error screen with **retry** and **open in browser** |
| Extras | none | pull-to-refresh, download handling, file upload, renderer-crash recovery |

## Requirements

- Android Studio (current) with **Android SDK 36**
- **JDK 17+** (the build targets Java 17 bytecode)
- A device or emulator running **Android 8.0 (API 26)** or newer

### Toolchain notes

- Gradle **8.14.8**, Android Gradle Plugin **8.13.2**, Kotlin **2.4.21**, JDK 17+.
- `compileSdk`/`targetSdk` are **36** (Android 16). AGP 8.13 is the newest AGP line
  that supports `compileSdk 36` together with Gradle 8.x.
- `androidx.core:core-ktx` is pinned to **1.18.0** on purpose: 1.19+ declares a
  requirement on `compileSdk 37` and AGP 9.1+. When moving to AGP 9, upgrade the
  Gradle wrapper, AGP, `compileSdk`/`targetSdk` and this dependency together.

## Build and run

```bash
./gradlew assembleDebug      # build the debug APK
./gradlew installDebug       # install on a connected device/emulator
./gradlew testDebugUnitTest  # run the JVM unit tests
./gradlew lintDebug          # Android Lint (fails on errors)
```

The full check used to validate this rewrite:

```bash
./gradlew clean assembleDebug testDebugUnitTest lintDebug assembleRelease
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

### Building on ARM64 Linux

Google only ships an x86-64 `aapt2`, so an aarch64 host fails with
`Exec format error` or a Rosetta loader error. Point Gradle at a native aarch64
`aapt2` from your `~/.gradle/gradle.properties`:

```properties
android.aapt2FromMavenOverride=/path/to/aarch64/aapt2
```

Everything else in the toolchain is architecture-independent.

### Change the start URL

The URL is defined once in `app/build.gradle.kts` and exposed as
`BuildConfig.START_URL`. Override it per build without editing code:

```bash
./gradlew assembleDebug -PstartUrl=https://github.com/orgs/github
```

The default is **https://github.com**.

## Project layout

```
app/src/main/java/br/com/webview/
  MainActivity.kt        one activity: insets, back, downloads, file chooser, error UI
  WebViewSetup.kt        hardened WebSettings and START_URL
  AppWebViewClient.kt    navigation policy, page callbacks, errors, renderer recovery
  AppWebChromeClient.kt  progress, file chooser, permissions, console logging
  UrlPolicy.kt           pure URL-scheme policy (unit-tested on the JVM)
  WebViewHost.kt         contract between the clients and the Activity
  WebViewApp.kt          application-wide setup (DevTools only in debug)
app/src/test/java/br/com/webview/UrlPolicyTest.kt
```

## Modern WebView practices in use

- **AndroidX WebKit** (`WebViewClientCompat`, `WebSettingsCompat`,
  `WebViewFeature`) instead of relying on API-level branching.
- **Algorithmic darkening** when WebView 105+ supports it, so pages that do not
  ship a dark theme still follow the system setting.
- **Renderer crash recovery** via `onRenderProcessGone`; the app recreates the
  Activity instead of being killed by the system.
- **Safe Browsing** enabled and **mixed content refused**.
- **HTTPS only**: `usesCleartextTraffic="false"` plus a network security config.
- **No local access**: file/content access and geolocation disabled; `file:`,
  `content:`, `res:` and `android.resource:` navigations are blocked.
- **No JavaScript bridge**: no `@JavascriptInterface` is exposed.
- **Runtime permissions denied by default** (camera, microphone, geolocation).
- **Edge-to-edge** with system-bar, cutout and IME insets handling (Android 15+
  enforces edge-to-edge for apps targeting SDK 35+).
- **Predictive back** enabled in the manifest and handled through
  `OnBackPressedDispatcher`.
- **Scoped storage**: downloads use `DownloadManager`; on Android 10+ they go to
  public Downloads without any storage permission.
- **File uploads** through the Activity Result API and
  `WebChromeClient.FileChooserParams`.

## Security notes

- `android:allowBackup="false"` keeps WebView cookies/session data out of cloud
  backups.
- External schemes (`mailto:`, `tel:`, `intent:`, ...) are handed to the OS only
  for main-frame navigations. Non-http(s) sub-frame requests are ignored.
- Third-party cookies are enabled because some GitHub OAuth/SSO flows render in
  an iframe. Disable `setAcceptThirdPartyCookies` in `MainActivity` if your
  deployment does not need them.
- No certificate pinning is configured: pinning GitHub would break the app on
  certificate rotation. The system trust store is used.

## Known limitations

- Google sign-in may be refused by Google inside embedded WebViews; use GitHub
  credentials or "Open in browser".
- On Android 8-9 (API 26-28) downloads are stored in app-specific external
  storage to avoid requesting the legacy storage permission.
- Release builds are unsigned by default; add your own `signingConfig` before
  publishing.
- The app is a thin shell, not a full browser: no tabs, bookmarks, find-in-page
  or ad blocking.

## Debugging

Debug builds enable Chrome DevTools remote debugging. Open
`chrome://inspect` on your desktop while the app is running.

## License

MIT. See [LICENSE](LICENSE).

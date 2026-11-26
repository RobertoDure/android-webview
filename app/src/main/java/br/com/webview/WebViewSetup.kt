package br.com.webview

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.core.content.ContextCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/**
 * Central WebView configuration for a remote, HTTPS-only web app.
 *
 * Security defaults intentionally deny everything the page does not need:
 * no file/content access, no geolocation, no mixed content, no popups.
 */
object WebViewSetup {

    /** Page loaded on launch; overridable at build time with -PstartUrl=... */
    val START_URL: String = BuildConfig.START_URL.ifBlank { "https://github.com" }

    @SuppressLint("SetJavaScriptEnabled")
    fun configure(webView: WebView, context: Context) {
        val settings = webView.settings

        // GitHub's web app requires JavaScript and DOM storage.
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.javaScriptCanOpenWindowsAutomatically = false

        // Keep target="_blank" links inside this WebView instead of spawning popups.
        settings.setSupportMultipleWindows(false)

        // Remote content must not reach local files or content providers.
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.setGeolocationEnabled(false)
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        // Correct responsive layout for mobile-first sites such as GitHub.
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.mediaPlaybackRequiresUserGesture = true

        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSafeBrowsingEnabled(true)

        // Identify the app without altering the browser feature set.
        settings.userAgentString = buildString {
            append(settings.userAgentString.orEmpty())
            append(" GitHubWebView/")
            append(BuildConfig.VERSION_NAME)
        }

        // Follow the system dark theme. Requires WebView 105+; safely a no-op otherwise.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
        }

        webView.setBackgroundColor(ContextCompat.getColor(context, R.color.webview_background))
    }
}

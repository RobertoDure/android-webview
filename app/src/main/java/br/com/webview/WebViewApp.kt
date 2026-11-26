package br.com.webview

import android.app.Application
import android.webkit.WebView

/**
 * Application entry point. Keeps global WebView configuration in one place.
 */
class WebViewApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Chrome DevTools remote debugging is useful in debug builds and must never
        // be enabled in a release APK.
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
    }
}

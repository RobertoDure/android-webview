package br.com.webview

import android.net.Uri
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Chrome-layer callbacks: load progress, file uploads, console output and
 * permission requests.
 */
class AppWebChromeClient(private val host: WebViewHost) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        host.onProgressChanged(newProgress)
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams,
    ): Boolean = host.onShowFileChooser(filePathCallback, fileChooserParams)

    override fun onPermissionRequest(request: PermissionRequest) {
        // Camera, microphone and geolocation are not needed by GitHub's web app.
        // Deny explicitly instead of relying on the system prompt.
        request.deny()
    }

    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        if (BuildConfig.DEBUG) {
            Log.d(
                TAG,
                "${consoleMessage.message()} " +
                    "(${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})",
            )
        }
        return true
    }

    private companion object {
        const val TAG = "WebViewConsole"
    }
}

package br.com.webview

import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebChromeClient

/**
 * Contract between the WebView clients (which must stay lightweight) and the
 * Activity that owns UI state.
 */
interface WebViewHost {

    fun onProgressChanged(progress: Int)

    fun onPageStarted(url: String?)

    fun onPageFinished(url: String?)

    fun onMainFrameError(errorCode: Int, description: String, failingUrl: String?)

    /** Opens non-http(s) URLs (mailto:, tel:, intent:, ...) with the system. */
    fun openExternal(uri: Uri): Boolean

    /** The WebView renderer died; the Activity must recover. */
    fun onRendererGone(didCrash: Boolean)

    fun onShowFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams,
    ): Boolean
}

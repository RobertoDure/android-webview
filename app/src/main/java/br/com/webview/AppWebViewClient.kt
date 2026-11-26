package br.com.webview

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.net.toUri
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewFeature

/**
 * Navigation policy and page callbacks for the app WebView.
 */
class AppWebViewClient(private val host: WebViewHost) : WebViewClientCompat() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        // Only main-frame navigations are policy decisions; sub-resources load normally.
        if (!request.isForMainFrame) return false
        return handleUri(request.url)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = handleUri(url.toUri())

    private fun handleUri(uri: Uri): Boolean {
        val url = uri.toString()
        return when {
            UrlPolicy.isBlockedScheme(url) -> true
            UrlPolicy.isInAppScheme(url) -> false
            else -> host.openExternal(uri)
        }
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        host.onPageStarted(url)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        host.onPageFinished(url)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceErrorCompat,
    ) {
        // Sub-resource failures are normal on flaky networks; only surface main-frame errors.
        if (!request.isForMainFrame) return

        // Accessors on WebResourceErrorCompat are optional WebView features; guard them
        // so an old WebView build still reports a useful error.
        val errorCode = if (
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_CODE)
        ) {
            error.errorCode
        } else {
            WebViewClient.ERROR_UNKNOWN
        }
        val description = if (
            WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION)
        ) {
            error.description.toString()
        } else {
            ""
        }
        host.onMainFrameError(errorCode, description, request.url.toString())
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        // Returning true tells the system the crash was handled and prevents the app
        // process from being killed. The Activity recreates itself with a fresh renderer.
        host.onRendererGone(detail.didCrash())
        return true
    }
}

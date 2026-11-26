package br.com.webview

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import br.com.webview.databinding.ActivityMainBinding

/**
 * Single-activity host for the GitHub web app.
 */
class MainActivity : AppCompatActivity(), WebViewHost {

    private lateinit var binding: ActivityMainBinding
    private lateinit var webView: WebView

    private var fileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var lastFailedUrl: String? = null

    private val createdAt = SystemClock.uptimeMillis()
    private var firstContentShown = false

    private val fileChooserLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = fileChooserCallback ?: return@registerForActivityResult
            fileChooserCallback = null
            callback.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data),
            )
        }

    private val webViewBackCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (webView.canGoBack()) {
                webView.goBack()
            } else {
                // Let the system handle the final back press (and predictive back).
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)

        // Hold the system splash until the first page is ready, but never longer than
        // [SPLASH_TIMEOUT_MS] so a slow network cannot make startup feel frozen. Only a
        // cold start waits: activity recreation (e.g. a theme change) must not block.
        val isColdStart = savedInstanceState == null
        splashScreen.setKeepOnScreenCondition {
            isColdStart &&
                !firstContentShown &&
                SystemClock.uptimeMillis() - createdAt < SPLASH_TIMEOUT_MS
        }

        enableEdgeToEdge()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applyWindowInsets()
        setupWebView()
        setupSwipeToRefresh()
        setupBackNavigation()

        binding.retryButton.setOnClickListener { retry() }
        binding.openInBrowserButton.setOnClickListener { openCurrentUrlInBrowser() }

        // Restore the last page after process death / configuration changes; WebView's
        // own saveState/restoreState is unreliable across versions, so we persist the URL.
        val initialUrl = savedInstanceState
            ?.getString(KEY_LAST_URL)
            ?.takeIf { it.startsWith("http") }
            ?: WebViewSetup.START_URL
        webView.loadUrl(initialUrl)
    }

    private fun applyWindowInsets() {
        // Edge-to-edge (enforced on Android 15+): pad the content instead of drawing
        // under the status/navigation bars and the keyboard.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            // On Android 11+ edge-to-edge windows are not resized for the keyboard,
            // so the IME inset must be applied manually. Pre-R still uses adjustResize.
            val imeType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowInsetsCompat.Type.ime()
            } else {
                0
            }
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
                    or WindowInsetsCompat.Type.displayCutout()
                    or imeType,
            )
            view.updatePadding(
                left = bars.left,
                top = bars.top,
                right = bars.right,
                bottom = bars.bottom,
            )
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupWebView() {
        webView = binding.webView
        WebViewSetup.configure(webView, this)
        webView.webViewClient = AppWebViewClient(this)
        webView.webChromeClient = AppWebChromeClient(this)
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            enqueueDownload(url, userAgent, contentDisposition, mimeType)
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            // Required for some GitHub OAuth/SSO flows that render in an iframe.
            setAcceptThirdPartyCookies(webView, true)
        }
    }

    private fun setupSwipeToRefresh() {
        binding.swipeRefresh.setOnRefreshListener { webView.reload() }
        binding.swipeRefresh.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
    }

    private fun setupBackNavigation() {
        onBackPressedDispatcher.addCallback(this, webViewBackCallback)
    }

    // region WebViewHost

    override fun onProgressChanged(progress: Int) {
        if (progress >= 100) {
            binding.progress.isVisible = false
        } else {
            binding.progress.isVisible = true
            binding.progress.setProgressCompat(progress, true)
        }
    }

    override fun onPageStarted(url: String?) {
        lastFailedUrl = null
        binding.errorGroup.isVisible = false
        binding.progress.isVisible = true
        binding.progress.setProgressCompat(0, false)
    }

    override fun onPageFinished(url: String?) {
        firstContentShown = true
        binding.progress.isVisible = false
        binding.swipeRefresh.isRefreshing = false
        binding.errorGroup.isVisible = false
    }

    override fun onMainFrameError(errorCode: Int, description: String, failingUrl: String?) {
        firstContentShown = true
        lastFailedUrl = failingUrl
        binding.progress.isVisible = false
        binding.swipeRefresh.isRefreshing = false

        val offline = errorCode in OFFLINE_ERROR_CODES
        binding.errorMessage.text = getString(
            if (offline) R.string.error_offline_message else R.string.error_generic_message,
        )
        binding.errorGroup.isVisible = true
        Log.w(TAG, "Main-frame load failed ($errorCode): $description")
    }

    override fun openExternal(uri: Uri): Boolean {
        return try {
            val intent = if (uri.scheme == "intent") {
                Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            } else {
                Intent(Intent.ACTION_VIEW, uri).apply {
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            }
            startActivity(intent)
            true
        } catch (exception: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_app_for_link, Toast.LENGTH_SHORT).show()
            true
        } catch (exception: Exception) {
            Log.w(TAG, "Could not open external URI: $uri", exception)
            Toast.makeText(this, R.string.error_no_app_for_link, Toast.LENGTH_SHORT).show()
            true
        }
    }

    override fun onRendererGone(didCrash: Boolean) {
        Log.e(TAG, "WebView renderer gone (didCrash=$didCrash)")
        Toast.makeText(this, R.string.error_renderer_crashed, Toast.LENGTH_LONG).show()
        // The dead WebView must not be touched again; recreate the Activity.
        Handler(Looper.getMainLooper()).post {
            if (!isFinishing && !isDestroyed) recreate()
        }
    }

    override fun onShowFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams,
    ): Boolean {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = filePathCallback
        return try {
            fileChooserLauncher.launch(fileChooserParams.createIntent())
            true
        } catch (exception: ActivityNotFoundException) {
            fileChooserCallback = null
            Toast.makeText(this, R.string.error_no_file_picker, Toast.LENGTH_SHORT).show()
            false
        }
    }

    // endregion

    private fun retry() {
        binding.errorGroup.isVisible = false
        val target = lastFailedUrl ?: webView.url ?: WebViewSetup.START_URL
        webView.loadUrl(target)
    }

    private fun openCurrentUrlInBrowser() {
        val target = lastFailedUrl ?: webView.url ?: WebViewSetup.START_URL
        openExternal(target.toUri())
    }

    private fun enqueueDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
    ) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val request = DownloadManager.Request(url.toUri()).apply {
                setTitle(fileName)
                setMimeType(mimeType)
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(false)
                userAgent?.let { addRequestHeader("User-Agent", it) }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Scoped storage: DownloadManager can write to public Downloads with
                    // no storage permission on Android 10+.
                    setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                } else {
                    // Android 8-9: stay in app-specific storage to avoid storage permission.
                    setDestinationInExternalFilesDir(
                        this@MainActivity,
                        Environment.DIRECTORY_DOWNLOADS,
                        fileName,
                    )
                }
            }

            val manager = getSystemService(DownloadManager::class.java)
            manager.enqueue(request)
            Toast.makeText(this, R.string.download_started, Toast.LENGTH_SHORT).show()
        } catch (exception: Exception) {
            Log.w(TAG, "Download failed for $url", exception)
            Toast.makeText(this, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-arm WebView history handling after a back press handed control to the system.
        webViewBackCallback.isEnabled = true
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        // Persist cookies/session data before the app can be killed in the background.
        CookieManager.getInstance().flush()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(KEY_LAST_URL, webView.url ?: WebViewSetup.START_URL)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        fileChooserCallback?.onReceiveValue(null)
        fileChooserCallback = null

        // onCreate can fail before the WebView exists; only tear it down when present.
        if (::webView.isInitialized) {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.stopLoading()
            webView.destroy()
        }

        super.onDestroy()
    }

    private companion object {
        const val TAG = "MainActivity"
        const val KEY_LAST_URL = "last_url"
        const val SPLASH_TIMEOUT_MS = 1_200L

        val OFFLINE_ERROR_CODES = setOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_TIMEOUT,
            WebViewClient.ERROR_IO,
        )
    }
}

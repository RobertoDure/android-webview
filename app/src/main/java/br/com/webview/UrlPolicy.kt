package br.com.webview

/**
 * Pure URL policy shared by the WebView client and unit tests.
 *
 * Kept free of Android framework types so it can be tested on the JVM.
 */
object UrlPolicy {

    /** Schemes the WebView itself is allowed to load. */
    private val IN_APP_SCHEMES = setOf("http", "https", "about", "javascript", "data", "blob")

    /** Local/privileged schemes that remote content must never navigate to. */
    private val BLOCKED_SCHEMES = setOf("file", "content", "android.resource", "res")

    /** Returns the lower-case scheme of [url], or null when the URL has none. */
    fun schemeOf(url: String): String? {
        val colon = url.indexOf(':')
        if (colon <= 0) return null
        val scheme = url.substring(0, colon).lowercase()
        // RFC 3986: a scheme starts with a letter and continues with
        // letters, digits, '+', '-' or '.'.
        if (!scheme.first().isLetter()) return null
        val isValid = scheme.all { character ->
            character.isLetterOrDigit() || character == '+' || character == '-' || character == '.'
        }
        return scheme.takeIf { isValid }
    }

    /** True when the URL should stay inside the app's WebView. */
    fun isInAppScheme(url: String): Boolean = schemeOf(url) in IN_APP_SCHEMES

    /** True when the URL targets local resources and must be refused outright. */
    fun isBlockedScheme(url: String): Boolean = schemeOf(url) in BLOCKED_SCHEMES
}

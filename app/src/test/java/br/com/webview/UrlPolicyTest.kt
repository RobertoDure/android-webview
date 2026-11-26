package br.com.webview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlPolicyTest {

    @Test
    fun httpAndHttpsStayInTheWebView() {
        assertTrue(UrlPolicy.isInAppScheme("https://github.com"))
        assertTrue(UrlPolicy.isInAppScheme("http://github.com"))
        assertFalse(UrlPolicy.isBlockedScheme("https://github.com"))
    }

    @Test
    fun schemeComparisonIsCaseInsensitive() {
        assertTrue(UrlPolicy.isInAppScheme("HTTPS://GITHUB.COM"))
    }

    @Test
    fun externalAppSchemesAreDelegated() {
        listOf(
            "mailto:someone@example.com",
            "tel:+5511999999999",
            "sms:+5511999999999",
            "intent://github.com#Intent;scheme=https;end",
            "market://details?id=com.github.android",
        ).forEach { url ->
            assertFalse(url, UrlPolicy.isInAppScheme(url))
            assertFalse(url, UrlPolicy.isBlockedScheme(url))
        }
    }

    @Test
    fun localAndPrivilegedSchemesAreBlocked() {
        listOf(
            "file:///etc/passwd",
            "content://com.example.provider/secret",
            "android.resource://br.com.webview/raw/secret",
            "res:///secret",
        ).forEach { url ->
            assertTrue(url, UrlPolicy.isBlockedScheme(url))
        }
    }

    @Test
    fun javascriptAndDataUrlsRemainInApp() {
        assertTrue(UrlPolicy.isInAppScheme("javascript:alert(1)"))
        assertTrue(UrlPolicy.isInAppScheme("data:text/html,<h1>hi</h1>"))
        assertTrue(UrlPolicy.isInAppScheme("about:blank"))
    }

    @Test
    fun urlsWithoutASchemeAreNotClassified() {
        assertNull(UrlPolicy.schemeOf("github.com"))
        assertNull(UrlPolicy.schemeOf("//github.com"))
        assertFalse(UrlPolicy.isInAppScheme("github.com"))
    }

    @Test
    fun invalidSchemeCharactersAreRejected() {
        assertNull(UrlPolicy.schemeOf("1http://github.com"))
        assertNull(UrlPolicy.schemeOf("ht tp://github.com"))
    }

    @Test
    fun schemeParserHandlesColonsInTheQuery() {
        assertEquals("https", UrlPolicy.schemeOf("https://github.com/search?q=android:webview"))
    }
}

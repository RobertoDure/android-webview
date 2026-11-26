# Keep the WebView JavaScript bridge safe if one is added later. This app currently
# exposes no @JavascriptInterface, so nothing else needs custom keep rules.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Preserve annotations/attributes used by reflective WebView callbacks.
-keepattributes *Annotation*

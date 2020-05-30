package org.videolan.television.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.FragmentActivity
import org.videolan.resources.util.applyOverscanMargin
import org.videolan.vlc.R
import org.videolan.vlc.util.Util

class LicenceActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(org.videolan.television.R.layout.about_licence)

        val webView = findViewById<WebView>(R.id.webview)
        val revision = getString(R.string.build_revision)
        webView.loadUrl("file:///android_asset/licence.htm")

        webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView, url: String) {
                if (url.startsWith("file:///android_asset")) {
                    // Inject CSS when page is done loading
                    injectCSS(view, "licence_dark.css")
                    injectCommitRevision(view, revision)
                }
                super.onPageFinished(view, url)

            }
        }

        applyOverscanMargin(this)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun injectCSS(webView: WebView, cssAsset: String) {
        try {
            webView.settings.javaScriptEnabled = true

            val buffer = Util.readAsset(cssAsset, "")
            val encoded = Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP)
            webView.loadUrl("javascript:(function() {" +
                    "var parent = document.getElementsByTagName('head').item(0);" +
                    "var style = document.createElement('style');" +
                    "style.type = 'text/css';" +
                    // Tell the browser to BASE64-decode the string into your script !!!
                    "style.innerHTML = window.atob('" + encoded + "');" +
                    "parent.appendChild(style);" +
                    "})()")

            webView.settings.javaScriptEnabled = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun injectCommitRevision(webView: WebView, revision: String) {
        try {
            webView.settings.javaScriptEnabled = true

            webView.loadUrl("javascript:(function() {" +
                    "var link = document.getElementById('revision_link');" +
                    "var newLink = link.href.replace('!COMMITID!', '$revision');" +
                    "link.setAttribute('href', newLink);" +
                    "link.innerText = newLink;" +
                    "})()")
            webView.settings.javaScriptEnabled = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

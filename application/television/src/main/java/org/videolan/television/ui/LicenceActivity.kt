package org.videolan.television.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.util.Base64
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.fragment.app.FragmentActivity
import org.videolan.vlc.R
import org.videolan.vlc.util.Util

class LicenceActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val backgroundColor = ContextCompat.getColor(this, R.color.grey850)

        val webView = WebView(this)
        val revision = getString(R.string.build_revision)
        webView.loadUrl("file:///android_asset/license.htm")
        webView.setBackgroundColor(backgroundColor)

        webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView, url: String) {
                if (url.startsWith("file:///android_asset")) {
                    // Inject CSS when page is done loading
                    injectCSS(view, "license_dark_tv.css")
                    injectCommitRevision(view, revision)
                }
                super.onPageFinished(view, url)

            }

            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                if (url.contains("file://")) {
                    view.loadUrl(url)
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, url.toUri())
                    try {
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        view.loadUrl(url)
                    }

                }
                return true
            }
        }
        setContentView(webView)
        (webView.layoutParams as? FrameLayout.LayoutParams)?.let {
            it.setMargins(0,0,0,0)
        }
        (webView.parent as View).setBackgroundColor(backgroundColor)
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

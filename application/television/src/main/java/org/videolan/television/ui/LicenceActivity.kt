package org.videolan.television.ui

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.fragment.app.FragmentActivity
import org.videolan.resources.util.applyOverscanMargin
import org.videolan.vlc.R
import org.videolan.vlc.util.Util

class LicenceActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val revision = getString(R.string.build_revision)
        val licence = WebView(this)

        licence.loadUrl("file:///android_asset/licence.htm")

        licence.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView, url: String) {
                if (url.startsWith("file:///android_asset")) {
                    injectCommitRevision(view, revision)
                }
                super.onPageFinished(view, url)

            }
        }

        setContentView(licence)
        (licence.parent as View).setBackgroundColor(Color.LTGRAY)
        applyOverscanMargin(this)
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

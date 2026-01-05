package com.labwiki.app

import android.net.Uri
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private lateinit var wikiManager: WikiManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        wikiManager = WikiManager(this)
        webView = findViewById(R.id.wikiWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/wiki/", WebViewAssetLoader.InternalStoragePathHandler(this, File(filesDir, "wiki")))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                if (uri.scheme == "app" && uri.host == "openWiki") {
                    val wikiId = uri.getQueryParameter("wiki")
                    if (!wikiId.isNullOrBlank()) {
                        openWiki(wikiId)
                    }
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) =
                assetLoader.shouldInterceptRequest(request.url)
        }

        webView.loadUrl("file:///android_asset/hub/index.html")
    }

    private fun openWiki(id: String) {
        val def = WikiRegistry.getById(id) ?: return
        val cached = wikiManager.isCached(def.id)
        val online = wikiManager.isNetworkAvailable()

        when {
            cached -> webView.loadUrl(wikiManager.localUrl(def.id))
            online -> webView.loadUrl(def.remoteStartUrl)
            else -> webView.loadUrl("file:///android_asset/hub/offline.html")
        }

        wikiManager.syncIfNeeded(def)
    }
}

package com.labwiki.app

<<<<<<< ours
import android.net.Uri
=======
>>>>>>> theirs
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
<<<<<<< ours
=======
import androidx.appcompat.widget.SearchView
>>>>>>> theirs
import androidx.webkit.WebViewAssetLoader
import java.io.File

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private lateinit var wikiManager: WikiManager
<<<<<<< ours
=======
    private var currentQuery: String? = null
>>>>>>> theirs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

<<<<<<< ours
=======
        setSupportActionBar(findViewById(R.id.topToolbar))
>>>>>>> theirs
        wikiManager = WikiManager(this)
        webView = findViewById(R.id.wikiWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/wiki/", WebViewAssetLoader.InternalStoragePathHandler(this, File(filesDir, "wiki")))
            .build()

<<<<<<< ours
=======
        webView.addJavascriptInterface(HubBridge(), "LabWiki")
>>>>>>> theirs
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
<<<<<<< ours
=======

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                refreshHub()
                currentQuery?.let { query ->
                    if (query.isNotBlank()) {
                        webView.findAllAsync(query)
                    }
                }
            }
>>>>>>> theirs
        }

        webView.loadUrl("file:///android_asset/hub/index.html")
    }

<<<<<<< ours
=======
    override fun onResume() {
        super.onResume()
        refreshHub()
    }

>>>>>>> theirs
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
<<<<<<< ours
=======

    private fun refreshHub() {
        if (webView.url?.startsWith("file:///android_asset/hub/") == true) {
            val script = "window.LabWikiHub && window.LabWikiHub.refresh && window.LabWikiHub.refresh();"
            webView.evaluateJavascript(script, null)
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        val searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.queryHint = "Im Wiki suchen"
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String): Boolean {
                currentQuery = query
                if (query.isNotBlank()) {
                    webView.findAllAsync(query)
                    webView.findNext(true)
                }
                return true
            }

            override fun onQueryTextChange(newText: String): Boolean {
                currentQuery = newText
                if (newText.isBlank()) {
                    webView.clearMatches()
                } else {
                    webView.findAllAsync(newText)
                }
                return true
            }
        })
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear_cache -> {
                wikiManager.clearCache()
                refreshHub()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private inner class HubBridge {
        @android.webkit.JavascriptInterface
        fun getHubState(): String = wikiManager.getHubStateJson()

        @android.webkit.JavascriptInterface
        fun toggleFavorite(wikiId: String) {
            wikiManager.toggleFavorite(wikiId)
            runOnUiThread { refreshHub() }
        }
    }
>>>>>>> theirs
}

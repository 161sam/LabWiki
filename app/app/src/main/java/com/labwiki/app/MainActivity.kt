package com.labwiki.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.webkit.WebResourceResponse
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.webkit.WebViewAssetLoader
import com.labwiki.app.data.db.WikiSearchIndexer
import com.labwiki.app.data.db.WikiSearchRepository
import java.io.File
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var assetLoader: WebViewAssetLoader
    private lateinit var wikiManager: WikiManager
    private lateinit var searchRepository: WikiSearchRepository
    private lateinit var topToolbar: androidx.appcompat.widget.Toolbar
    private var currentQuery: String? = null
    private var currentWikiId: String? = null
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        topToolbar = findViewById(R.id.topToolbar)
        setSupportActionBar(topToolbar)
        topToolbar.setOnClickListener { loadHub() }
        supportActionBar?.setDisplayHomeAsUpEnabled(false)

        wikiManager = WikiManager(this)
        searchRepository = WikiSearchRepository(this)
        webView = findViewById(R.id.wikiWebView)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler(
                "/wiki/",
                WebViewAssetLoader.InternalStoragePathHandler(this, File(filesDir, "wiki"))
            )
            .build()

        webView.addJavascriptInterface(HubBridge(), "LabWiki")

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
                if (uri.scheme == "app" && uri.host == "openPage") {
                    val wikiId = uri.getQueryParameter("wiki")
                    val page = uri.getQueryParameter("page")
                    if (!wikiId.isNullOrBlank() && !page.isNullOrBlank()) {
                        openPage(wikiId, page)
                    }
                    return true
                }

                if (!wikiManager.isOnlineForRemote() && isRemoteUrl(uri.toString())) {
                    webView.loadUrl("file:///android_asset/hub/offline.html")
                    return true
                }
                return false
            }

            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                val url = request.url.toString()
                if (!wikiManager.isOnlineForRemote() && isRemoteUrl(url)) {
                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                }
                return assetLoader.shouldInterceptRequest(request.url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                updateTopBarForUrl(url)
                refreshHub()
                maybeSnapshotRemotePage(url)
                currentQuery?.let { query ->
                    if (query.isNotBlank()) {
                        webView.findAllAsync(query)
                    }
                }
            }
        }

        val hubUrl = "file:///android_asset/hub/index.html"
        webView.loadUrl(hubUrl)
        updateTopBarForUrl(hubUrl)

        onBackPressedDispatcher.addCallback(this) {
            when {
                webView.canGoBack() -> webView.goBack()
                !isHubUrl(webView.url) -> loadHub()
                else -> finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshHub()
    }

    override fun onStart() {
        super.onStart()
        registerNetworkCallback()
    }

    override fun onStop() {
        super.onStop()
        unregisterNetworkCallback()
    }

    private fun openWiki(id: String) {
        val def = WikiRegistry.getById(id) ?: return
        val cached = wikiManager.isCached(def.id)
        val online = wikiManager.isOnlineForRemote()
        currentWikiId = def.id

        val targetUrl = when {
            cached -> wikiManager.localUrl(def.id)
            online -> def.remoteStartUrl
            else -> "file:///android_asset/hub/offline.html"
        }
        webView.loadUrl(targetUrl)
        updateTopBarForUrl(targetUrl)
        observeSync(def.id)

        if (cached && wikiManager.getIndexStatus(def.id) != IndexStatus.READY) {
            wikiManager.setIndexStatus(def.id, IndexStatus.BUILDING)
            launchIndexing(def.id)
        }
        wikiManager.syncIfNeeded(def)
    }

    private fun openPage(wikiId: String, pageUrl: String) {
        val cached = wikiManager.isCached(wikiId)
        val online = wikiManager.isOnlineForRemote()
        currentWikiId = wikiId

        val targetUrl = when {
            cached -> wikiManager.localPageUrl(wikiId, pageUrl)
            online -> {
                val def = WikiRegistry.getById(wikiId) ?: return
                def.remoteStartUrl
            }
            else -> "file:///android_asset/hub/offline.html"
        }
        webView.loadUrl(targetUrl)
        updateTopBarForUrl(targetUrl)
    }

    private fun refreshHub() {
        if (webView.url?.startsWith("file:///android_asset/hub/") == true) {
            val script = "window.LabWikiHub && window.LabWikiHub.refresh && window.LabWikiHub.refresh();"
            webView.evaluateJavascript(script, null)
        }
    }

    private fun maybeSnapshotRemotePage(url: String) {
        val wikiId = currentWikiId ?: return
        if (!isRemoteUrl(url)) return
        if (wikiManager.isCached(wikiId)) return
        val def = WikiRegistry.getById(wikiId) ?: return
        if (!url.startsWith(def.remoteStartUrl)) return
        cacheRemoteSnapshot(wikiId)
    }

    private fun cacheRemoteSnapshot(wikiId: String) {
        val script = "document.documentElement.outerHTML"
        webView.evaluateJavascript(script) { raw ->
            if (raw.isNullOrBlank() || raw == "null") return@evaluateJavascript
            val html = try {
                JSONArray("[$raw]").getString(0)
            } catch (ex: Exception) {
                raw.trim('"')
            }
            lifecycleScope.launch(Dispatchers.IO) {
                val wikiDir = File(filesDir, "wiki/$wikiId")
                if (!wikiDir.exists()) wikiDir.mkdirs()
                File(wikiDir, "index.html").writeText(html)
                wikiManager.setIndexStatus(wikiId, IndexStatus.BUILDING)
                withContext(Dispatchers.Main) { refreshHub() }
                launchIndexing(wikiId)
            }
        }
    }

    private fun launchIndexing(wikiId: String) {
        lifecycleScope.launch {
            WikiSearchIndexer(this@MainActivity).indexWiki(wikiId)
            refreshHub()
        }
    }

    private fun registerNetworkCallback() {
        if (networkCallback != null) return
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager = manager
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread { refreshHub() }
            }

            override fun onLost(network: Network) {
                runOnUiThread { refreshHub() }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                runOnUiThread { refreshHub() }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            manager.registerDefaultNetworkCallback(callback)
        } else {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            manager.registerNetworkCallback(request, callback)
        }
        networkCallback = callback
    }

    private fun unregisterNetworkCallback() {
        val manager = connectivityManager ?: return
        val callback = networkCallback ?: return
        manager.unregisterNetworkCallback(callback)
        networkCallback = null
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
            android.R.id.home -> {
                loadHub()
                true
            }
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

        @android.webkit.JavascriptInterface
        fun search(query: String, tagsCsv: String?) {
            val tags = tagsCsv?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }?.toSet()
                ?: emptySet()
            lifecycleScope.launch {
                val results = withContext(Dispatchers.IO) {
                    searchRepository.search(query, tags)
                }
                val payload = buildSearchJson(results)
                val script = "window.LabWikiHub && window.LabWikiHub.onSearchResults && " +
                    "window.LabWikiHub.onSearchResults($payload);"
                webView.post { webView.evaluateJavascript(script, null) }
            }
        }
    }

    private fun buildSearchJson(results: List<com.labwiki.app.ui.search.WikiSearchResult>): String {
        val array = JSONArray()
        results.forEach { result ->
            val item = JSONObject()
            item.put("wikiId", result.wikiId)
            item.put("wikiName", result.wikiName)
            item.put("pageUrl", result.pageUrl)
            item.put("title", result.title)
            item.put("snippet", result.snippet)
            array.put(item)
        }
        val root = JSONObject()
        root.put("results", array)
        return root.toString()
    }

    private fun isRemoteUrl(url: String): Boolean {
        if (!url.startsWith("http")) return false
        return !url.startsWith("https://appassets.androidplatform.net/")
    }

    private fun isHubUrl(url: String?): Boolean {
        return url?.startsWith("file:///android_asset/hub/") == true
    }

    private fun loadHub() {
        val hubUrl = "file:///android_asset/hub/index.html"
        webView.loadUrl(hubUrl)
        currentWikiId = null
        updateTopBarForUrl(hubUrl)
    }

    private fun updateTopBarForUrl(url: String?) {
        val inHub = isHubUrl(url)
        val title = if (inHub) {
            "LabWiki"
        } else {
            currentWikiId?.let { id -> WikiRegistry.getById(id)?.name } ?: "LabWiki"
        }
        supportActionBar?.title = title
        supportActionBar?.setDisplayHomeAsUpEnabled(!inHub)
        supportActionBar?.setHomeButtonEnabled(!inHub)
    }

    private fun observeSync(wikiId: String) {
        val workName = "wiki_sync_$wikiId"
        val workManager = WorkManager.getInstance(this)
        workManager.getWorkInfosForUniqueWorkLiveData(workName).observe(this) { infos ->
            val info = infos.maxByOrNull { it.runAttemptCount } ?: return@observe
            when (info.state) {
                WorkInfo.State.RUNNING -> {
                    wikiManager.setSyncState(wikiId, SyncState.SYNCING)
                }
                WorkInfo.State.SUCCEEDED -> {
                    val result = info.outputData.getString(WikiSyncWorker.KEY_RESULT_STATUS)
                    if (result == WikiSyncWorker.RESULT_BUNDLE_MISSING) {
                        wikiManager.setOfflineBundleSupported(wikiId, false)
                        wikiManager.setSyncState(wikiId, SyncState.IDLE)
                    } else {
                        wikiManager.setOfflineBundleSupported(wikiId, true)
                        wikiManager.setSyncState(wikiId, SyncState.UP_TO_DATE)
                        wikiManager.setLastSyncTimestamp(wikiId, System.currentTimeMillis())
                        wikiManager.setIndexStatus(wikiId, IndexStatus.BUILDING)
                        launchIndexing(wikiId)
                    }
                }
                WorkInfo.State.FAILED,
                WorkInfo.State.CANCELLED -> {
                    wikiManager.setSyncState(wikiId, SyncState.ERROR)
                }
                else -> Unit
            }
            if (isHubUrl(webView.url)) {
                refreshHub()
            }
        }
    }

}

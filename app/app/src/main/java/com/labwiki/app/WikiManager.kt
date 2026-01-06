package com.labwiki.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.labwiki.app.data.db.WikiDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WikiManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isCached(id: String): Boolean {
        val indexFile = File(context.filesDir, "wiki/$id/index.html")
        return indexFile.exists()
    }

    fun localUrl(id: String): String {
        return "https://appassets.androidplatform.net/wiki/$id/index.html"
    }

    fun localPageUrl(id: String, pageUrl: String): String {
        return "https://appassets.androidplatform.net/wiki/$id/$pageUrl"
    }

    fun syncIfNeeded(def: WikiDef) {
        if (!isOnlineForSync()) return

        val data = Data.Builder()
            .putString(WikiSyncWorker.KEY_WIKI_ID, def.id)
            .putString(WikiSyncWorker.KEY_REMOTE_OFFLINE_BASE, def.remoteOfflineBase)
            .build()

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<WikiSyncWorker>()
            .setInputData(data)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context)
            .enqueueUniqueWork("wiki_sync_${def.id}", ExistingWorkPolicy.REPLACE, request)
    }

    fun isNetworkAvailable(): Boolean {
        return isOnlineForRemote()
    }

    fun isOnlineForRemote(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun isOnlineForSync(): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        if (!capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_SUSPENDED)
        } else {
            true
        }
    }

    fun isFavorite(wikiId: String): Boolean = getFavorites().contains(wikiId)

    fun toggleFavorite(wikiId: String) {
        val favorites = getFavorites().toMutableSet()
        if (!favorites.add(wikiId)) favorites.remove(wikiId)
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
    }

    fun getFavorites(): Set<String> =
        prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()

    fun setSyncState(wikiId: String, state: SyncState) {
        prefs.edit().putString(syncStateKey(wikiId), state.value).apply()
    }

    fun getSyncState(wikiId: String): SyncState {
        val raw = prefs.getString(syncStateKey(wikiId), SyncState.IDLE.value) ?: SyncState.IDLE.value
        return SyncState.from(raw)
    }

    fun setLastSyncTimestamp(wikiId: String, timestamp: Long) {
        prefs.edit().putLong(syncTimeKey(wikiId), timestamp).apply()
    }

    fun getLastSyncTimestamp(wikiId: String): Long =
        prefs.getLong(syncTimeKey(wikiId), 0L)

    fun setIndexStatus(wikiId: String, status: IndexStatus) {
        prefs.edit().putString(indexStatusKey(wikiId), status.value).apply()
    }

    fun setOfflineBundleSupported(wikiId: String, supported: Boolean) {
        prefs.edit().putBoolean(offlineBundleKey(wikiId), supported).apply()
    }

    fun isOfflineBundleSupported(wikiId: String): Boolean =
        prefs.getBoolean(offlineBundleKey(wikiId), true)

    fun getIndexStatus(wikiId: String): IndexStatus {
        val raw = prefs.getString(indexStatusKey(wikiId), IndexStatus.NONE.value)
            ?: IndexStatus.NONE.value
        return IndexStatus.from(raw)
    }

    fun clearCache() {
        val wikiDir = File(context.filesDir, "wiki")
        if (wikiDir.exists()) wikiDir.deleteRecursively()

        Thread { WikiDatabase.get(context).clearAllTables() }.start()

        val editor = prefs.edit()
        for (def in WikiRegistry.wikis) {
            editor.remove(syncStateKey(def.id))
            editor.remove(syncTimeKey(def.id))
            editor.remove(indexStatusKey(def.id))
            editor.remove(offlineBundleKey(def.id))
        }
        editor.apply()
    }

    fun getHubStateJson(): String {
        val root = JSONObject()
        val list = JSONArray()
        val hasNetwork = isOnlineForRemote()

        for (def in WikiRegistry.wikis) {
            val item = JSONObject()
            item.put("id", def.id)
            item.put("name", def.name)
            item.put("description", def.description)
            item.put("tags", JSONArray(def.tags))
            item.put("cached", isCached(def.id))
            item.put("favorite", isFavorite(def.id))
            item.put("syncState", getSyncState(def.id).value)
            item.put("lastSync", getLastSyncTimestamp(def.id))
            item.put("indexStatus", getIndexStatus(def.id).value)
            item.put("offlineBundleSupported", isOfflineBundleSupported(def.id))
            list.put(item)
        }

        root.put("hasNetwork", hasNetwork)
        root.put("wikis", list)
        return root.toString()
    }

    private fun syncStateKey(id: String) = "sync_state_$id"
    private fun syncTimeKey(id: String) = "sync_time_$id"
    private fun indexStatusKey(id: String) = "index_status_$id"
    private fun offlineBundleKey(id: String) = "offline_bundle_supported_$id"

    companion object {
        private const val PREFS_NAME = "labwiki_prefs"
        private const val KEY_FAVORITES = "favorite_ids"
    }
}

enum class SyncState(val value: String) {
    IDLE("idle"),
    SYNCING("syncing"),
    ERROR("error"),
    UP_TO_DATE("up-to-date");

    companion object {
        fun from(raw: String): SyncState =
            values().firstOrNull { it.value == raw } ?: IDLE
    }
}

enum class IndexStatus(val value: String) {
    NONE("none"),
    BUILDING("building"),
    READY("ready"),
    ERROR("error");

    companion object {
        fun from(raw: String): IndexStatus =
            values().firstOrNull { it.value == raw } ?: NONE
    }
}

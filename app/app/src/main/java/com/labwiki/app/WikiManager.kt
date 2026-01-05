package com.labwiki.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
<<<<<<< ours
import java.io.File

class WikiManager(private val context: Context) {
=======
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class WikiManager(private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

>>>>>>> theirs
    fun isCached(id: String): Boolean {
        val indexFile = File(context.filesDir, "wiki/$id/index.html")
        return indexFile.exists()
    }

    fun localUrl(id: String): String {
        return "https://appassets.androidplatform.net/wiki/$id/index.html"
    }

    fun syncIfNeeded(def: WikiDef) {
        if (!isNetworkAvailable()) {
            return
        }

<<<<<<< ours
=======
        setSyncState(def.id, SyncState.SYNCING)
>>>>>>> theirs
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
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
<<<<<<< ours
=======

    fun isFavorite(wikiId: String): Boolean = getFavorites().contains(wikiId)

    fun toggleFavorite(wikiId: String) {
        val favorites = getFavorites().toMutableSet()
        if (!favorites.add(wikiId)) {
            favorites.remove(wikiId)
        }
        prefs.edit().putStringSet(KEY_FAVORITES, favorites).apply()
    }

    fun getFavorites(): Set<String> = prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet()

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

    fun getLastSyncTimestamp(wikiId: String): Long = prefs.getLong(syncTimeKey(wikiId), 0L)

    fun clearCache() {
        val wikiDir = File(context.filesDir, "wiki")
        if (wikiDir.exists()) {
            wikiDir.deleteRecursively()
        }
    }

    fun getHubStateJson(): String {
        val root = JSONObject()
        val list = JSONArray()
        val hasNetwork = isNetworkAvailable()

        for (def in WikiRegistry.wikis) {
            val item = JSONObject()
            val cached = isCached(def.id)
            val favorite = isFavorite(def.id)
            val syncState = getSyncState(def.id)
            val lastSync = getLastSyncTimestamp(def.id)

            item.put("id", def.id)
            item.put("cached", cached)
            item.put("favorite", favorite)
            item.put("syncState", syncState.value)
            item.put("lastSync", lastSync)
            list.put(item)
        }

        root.put("hasNetwork", hasNetwork)
        root.put("wikis", list)
        return root.toString()
    }

    private fun syncStateKey(id: String) = "sync_state_$id"
    private fun syncTimeKey(id: String) = "sync_time_$id"

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
        fun from(raw: String): SyncState = values().firstOrNull { it.value == raw } ?: IDLE
    }
>>>>>>> theirs
}

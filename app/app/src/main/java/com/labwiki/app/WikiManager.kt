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
import java.io.File

class WikiManager(private val context: Context) {
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
}

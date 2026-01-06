package com.labwiki.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.labwiki.app.data.db.WikiSearchIndexer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

class WikiSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val wikiId = inputData.getString(KEY_WIKI_ID)
        val remoteOfflineBase = inputData.getString(KEY_REMOTE_OFFLINE_BASE)
        if (wikiId.isNullOrBlank() || remoteOfflineBase.isNullOrBlank()) {
            return@withContext Result.failure()
        }

        try {
            val normalizedBase = remoteOfflineBase.trimEnd('/')
            val candidateBases = buildBaseCandidates(normalizedBase)
            val versionResult = fetchTextWithFallback(candidateBases, "version.json")
            val remoteVersionRaw = versionResult.body
            val remoteVersionJson = JSONObject(remoteVersionRaw)
            val remoteCommit = remoteVersionJson.optString("commit", "")
            val remoteVersion = remoteVersionJson.optString("version", "")

            val wikiDir = File(applicationContext.filesDir, "wiki/$wikiId")
            val localVersionFile = File(wikiDir, "version.json")
            val localIndexFile = File(wikiDir, "index.html")

            val localVersionRaw = if (localVersionFile.exists()) localVersionFile.readText() else ""
            val localVersionJson = if (localVersionRaw.isNotBlank()) JSONObject(localVersionRaw) else null

            val localCommit = localVersionJson?.optString("commit", "") ?: ""
            val localVersion = localVersionJson?.optString("version", "") ?: ""

            val commitComparable = remoteCommit.isNotBlank() || localCommit.isNotBlank()
            val commitChanged = commitComparable && remoteCommit != localCommit
            val versionChanged = !commitComparable && remoteVersion.isNotBlank() && remoteVersion != localVersion
            val needsUpdate = commitChanged || versionChanged || !localIndexFile.exists()

            if (needsUpdate) {
                val zipFile = File(applicationContext.cacheDir, "wiki-$wikiId-build.zip")
                val zipBases = listOf(versionResult.base) + candidateBases.filterNot { it == versionResult.base }
                downloadFileWithFallback(zipBases, "build.zip", zipFile)

                val tempDir = File(applicationContext.cacheDir, "wiki-$wikiId-${System.currentTimeMillis()}")
                if (!tempDir.mkdirs()) throw IllegalStateException("Failed to create temp dir")

                unzip(zipFile, tempDir)
                zipFile.delete()

                if (wikiDir.exists()) wikiDir.deleteRecursively()

                if (!tempDir.renameTo(wikiDir)) {
                    copyDirectory(tempDir, wikiDir)
                    tempDir.deleteRecursively()
                }

                if (!wikiDir.exists()) throw IllegalStateException("Failed to move wiki content")
                if (!File(wikiDir, "index.html").exists()) {
                    throw IllegalStateException("index.html missing after sync")
                }

                localVersionFile.writeText(remoteVersionRaw)
            }

            val manager = WikiManager(applicationContext)
            manager.setSyncState(wikiId, SyncState.UP_TO_DATE)
            manager.setLastSyncTimestamp(wikiId, System.currentTimeMillis())
            WikiSearchIndexer(applicationContext).indexWiki(wikiId)
            Result.success()
        } catch (ex: Exception) {
            val manager = WikiManager(applicationContext)
            manager.setSyncState(wikiId, SyncState.ERROR)
            Result.retry()
        }
    }

    private fun fetchTextWithFallback(bases: List<String>, path: String): FetchResult {
        var lastError: Exception? = null
        for (base in bases) {
            val url = "$base/$path"
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 15000
                connection.instanceFollowRedirects = true
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                    continue
                }
                if (code >= 400) {
                    throw IllegalStateException("HTTP $code for $url")
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                return FetchResult(base, body)
            } catch (ex: Exception) {
                lastError = ex
            }
        }
        throw lastError ?: IllegalStateException("No reachable base for $path")
    }

    private fun downloadFileWithFallback(bases: List<String>, path: String, destination: File) {
        var lastError: Exception? = null
        for (base in bases) {
            val url = "$base/$path"
            try {
                val connection = URL(url).openConnection() as HttpURLConnection
                connection.connectTimeout = 10000
                connection.readTimeout = 20000
                connection.instanceFollowRedirects = true
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_NOT_FOUND) {
                    continue
                }
                if (code >= 400) {
                    throw IllegalStateException("HTTP $code for $url")
                }
                connection.inputStream.use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return
            } catch (ex: Exception) {
                lastError = ex
            }
        }
        throw lastError ?: IllegalStateException("No reachable base for $path")
    }

    private fun buildBaseCandidates(base: String): List<String> {
        val candidates = mutableListOf(base)
        if (!base.endsWith("/offline")) {
            candidates.add("$base/offline")
        }
        return candidates
    }

    private data class FetchResult(val base: String, val body: String)

    private fun unzip(zipFile: File, destDir: File) {
        ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                val newFile = File(destDir, entry.name)
                val canonicalPath = newFile.canonicalPath
                if (!canonicalPath.startsWith(destDir.canonicalPath + File.separator)) {
                    throw IllegalStateException("Zip entry outside target dir")
                }

                if (entry.isDirectory) {
                    newFile.mkdirs()
                } else {
                    newFile.parentFile?.mkdirs()
                    FileOutputStream(newFile).use { fos -> zis.copyTo(fos) }
                }

                entry = zis.nextEntry
            }
        }
    }

    private fun copyDirectory(source: File, target: File) {
        if (source.isDirectory) {
            if (!target.exists()) target.mkdirs()
            source.listFiles()?.forEach { file -> copyDirectory(file, File(target, file.name)) }
        } else {
            target.parentFile?.mkdirs()
            source.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    companion object {
        const val KEY_WIKI_ID = "wiki_id"
        const val KEY_REMOTE_OFFLINE_BASE = "remote_offline_base"
    }
}

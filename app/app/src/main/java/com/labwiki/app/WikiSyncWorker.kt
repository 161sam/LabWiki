package com.labwiki.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
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
            val remoteVersionUrl = "$normalizedBase/version.json"
            val remoteZipUrl = "$normalizedBase/build.zip"

            val remoteVersionRaw = fetchText(remoteVersionUrl)
            val remoteVersionJson = JSONObject(remoteVersionRaw)
            val remoteCommit = remoteVersionJson.optString("commit", "")
            val remoteVersion = remoteVersionJson.optString("version", "")

            val wikiDir = File(applicationContext.filesDir, "wiki/$wikiId")
            val localVersionFile = File(wikiDir, "version.json")
            val localIndexFile = File(wikiDir, "index.html")

            val localVersionRaw = if (localVersionFile.exists()) {
                localVersionFile.readText()
            } else {
                ""
            }

            val localVersionJson = if (localVersionRaw.isNotBlank()) {
                JSONObject(localVersionRaw)
            } else {
                null
            }

            val localCommit = localVersionJson?.optString("commit", "") ?: ""
            val localVersion = localVersionJson?.optString("version", "") ?: ""

            val commitComparable = remoteCommit.isNotBlank() || localCommit.isNotBlank()
            val commitChanged = commitComparable && remoteCommit != localCommit
            val versionChanged = !commitComparable && remoteVersion.isNotBlank() && remoteVersion != localVersion
            val needsUpdate = commitChanged || versionChanged || !localIndexFile.exists()

            if (needsUpdate) {
                val zipFile = File(applicationContext.cacheDir, "wiki-$wikiId-build.zip")
                downloadFile(remoteZipUrl, zipFile)

                val tempDir = File(applicationContext.cacheDir, "wiki-$wikiId-${System.currentTimeMillis()}")
                if (!tempDir.mkdirs()) {
                    throw IllegalStateException("Failed to create temp dir")
                }

                unzip(zipFile, tempDir)
                zipFile.delete()

                if (wikiDir.exists()) {
                    wikiDir.deleteRecursively()
                }

                if (!tempDir.renameTo(wikiDir)) {
                    copyDirectory(tempDir, wikiDir)
                    tempDir.deleteRecursively()
                }

                if (!wikiDir.exists()) {
                    throw IllegalStateException("Failed to move wiki content")
                }

                localVersionFile.writeText(remoteVersionRaw)
            }

            Result.success()
        } catch (ex: Exception) {
            Result.retry()
        }
    }

    private fun fetchText(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        return connection.inputStream.bufferedReader().use { it.readText() }
    }

    private fun downloadFile(url: String, destination: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 20000
        connection.inputStream.use { input ->
            destination.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

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
                    FileOutputStream(newFile).use { fos ->
                        zis.copyTo(fos)
                    }
                }

                entry = zis.nextEntry
            }
        }
    }

    private fun copyDirectory(source: File, target: File) {
        if (source.isDirectory) {
            if (!target.exists()) {
                target.mkdirs()
            }
            source.listFiles()?.forEach { file ->
                copyDirectory(file, File(target, file.name))
            }
        } else {
            target.parentFile?.mkdirs()
            source.inputStream().use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
    }

    companion object {
        const val KEY_WIKI_ID = "wiki_id"
        const val KEY_REMOTE_OFFLINE_BASE = "remote_offline_base"
    }
}

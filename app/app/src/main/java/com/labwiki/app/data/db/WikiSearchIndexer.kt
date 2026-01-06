package com.labwiki.app.data.db

import android.content.Context
import com.labwiki.app.IndexStatus
import com.labwiki.app.WikiManager
import com.labwiki.app.data.entity.WikiPageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class WikiSearchIndexer(private val context: Context) {
    private val repository = WikiSearchRepository(context)
    private val manager = WikiManager(context)

    suspend fun indexWiki(wikiId: String) = withContext(Dispatchers.IO) {
        val wikiDir = File(context.filesDir, "wiki/$wikiId")
        if (!wikiDir.exists()) {
            repository.replaceWikiPages(wikiId, emptyList())
            manager.setIndexStatus(wikiId, IndexStatus.NONE)
            return@withContext
        }

        manager.setIndexStatus(wikiId, IndexStatus.BUILDING)
        try {
            val pages = mutableListOf<WikiPageEntity>()
            val indexedAt = System.currentTimeMillis()
            wikiDir.walkTopDown()
                .filter { it.isFile && it.extension.equals("html", ignoreCase = true) }
                .forEach { file ->
                    val html = file.readText()
                    val title = extractTitle(html) ?: file.nameWithoutExtension
                    val contentText = extractText(html)
                    val snippet = createSnippet(contentText)
                    val relativePath = file.relativeTo(wikiDir).path.replace(File.separatorChar, '/')
                    pages.add(
                        WikiPageEntity(
                            wikiId = wikiId,
                            pageUrl = relativePath,
                            title = title,
                            snippet = snippet,
                            lastIndexedAt = indexedAt,
                            contentText = contentText
                        )
                    )
                }

            repository.replaceWikiPages(wikiId, pages)
            manager.setIndexStatus(wikiId, IndexStatus.READY)
        } catch (ex: Exception) {
            manager.setIndexStatus(wikiId, IndexStatus.ERROR)
        }
    }

    private fun extractTitle(html: String): String? {
        val match = Regex(
            "<title>(.*?)</title>",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
            .find(html)
        return match?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun extractText(html: String): String {
        val withoutScripts = html
            .replace(
                Regex("<script.*?>.*?</script>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
                " "
            )
            .replace(
                Regex("<style.*?>.*?</style>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)),
                " "
            )
        return withoutScripts
            .replace(Regex("<[^>]+>"), " ")
            .replace(Regex("&nbsp;"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun createSnippet(text: String): String {
        if (text.isBlank()) return ""
        val maxLength = 200
        return if (text.length <= maxLength) text else text.take(maxLength).trimEnd() + "…"
    }
}

package com.labwiki.app.data.db

import android.content.Context
import com.labwiki.app.WikiRegistry
import com.labwiki.app.data.entity.WikiPageEntity
import com.labwiki.app.ui.search.WikiSearchResult

class WikiSearchRepository(private val context: Context) {
    private val dao = WikiDatabase.get(context).wikiPageDao()

    suspend fun search(query: String, selectedTags: Set<String>, limit: Int = 50): List<WikiSearchResult> {
        if (query.isBlank()) return emptyList()

        val ftsQuery = toFtsQuery(query)
        val pages = if (ftsQuery.isNotBlank()) {
            runCatching { dao.search(ftsQuery, limit) }.getOrElse { emptyList() }
        } else {
            emptyList()
        }

        val results = if (pages.isNotEmpty()) {
            pages
        } else {
            val likeQuery = "%${query.trim()}%"
            dao.searchFallback(likeQuery, limit)
        }

        return results
            .filter { page -> matchesTags(page, selectedTags) }
            .mapNotNull { page ->
                val wiki = WikiRegistry.getById(page.wikiId) ?: return@mapNotNull null
                WikiSearchResult(
                    wikiId = page.wikiId,
                    wikiName = wiki.name,
                    pageUrl = page.pageUrl,
                    title = page.title,
                    snippet = page.snippet
                )
            }
    }

    suspend fun replaceWikiPages(wikiId: String, pages: List<WikiPageEntity>) {
        dao.deleteByWikiId(wikiId)
        if (pages.isNotEmpty()) {
            dao.insertAll(pages)
        }
    }

    suspend fun clearAll() {
        WikiDatabase.get(context).clearAllTables()
    }

    private fun toFtsQuery(raw: String): String {
        val tokens = raw.trim()
            .split(Regex("\\s+"))
            .map { it.replace(Regex("[^\\p{L}\\p{N}]+"), "") }
            .filter { it.isNotBlank() }

        return tokens.joinToString(" AND ") { "${it}*" }
    }

    private fun matchesTags(page: WikiPageEntity, selectedTags: Set<String>): Boolean {
        if (selectedTags.isEmpty()) return true
        val wikiTags = WikiRegistry.getById(page.wikiId)?.tags ?: return false
        return wikiTags.any { tag -> selectedTags.contains(tag) }
    }
}

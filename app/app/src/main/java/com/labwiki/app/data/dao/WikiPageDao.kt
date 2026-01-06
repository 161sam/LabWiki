package com.labwiki.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.labwiki.app.data.entity.WikiPageEntity

@Dao
interface WikiPageDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(pages: List<WikiPageEntity>)

    @Query("DELETE FROM wiki_pages WHERE wikiId = :wikiId")
    suspend fun deleteByWikiId(wikiId: String)

    @Query(
        """
        SELECT wiki_pages.*
        FROM wiki_pages
        JOIN wiki_pages_fts ON wiki_pages.id = wiki_pages_fts.rowid
        WHERE wiki_pages_fts MATCH :query
        LIMIT :limit
        """
    )
    suspend fun search(query: String, limit: Int): List<WikiPageEntity>

    @Query(
        """
        SELECT * FROM wiki_pages
        WHERE title LIKE :likeQuery OR snippet LIKE :likeQuery
        LIMIT :limit
        """
    )
    suspend fun searchFallback(likeQuery: String, limit: Int): List<WikiPageEntity>
}

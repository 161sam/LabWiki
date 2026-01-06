package com.labwiki.app.data.entity

import androidx.room.Entity
import androidx.room.Fts4

@Fts4(contentEntity = WikiPageEntity::class)
@Entity(tableName = "wiki_pages_fts")
data class WikiPageFtsEntity(
    val title: String,
    val snippet: String,
    val contentText: String?
)

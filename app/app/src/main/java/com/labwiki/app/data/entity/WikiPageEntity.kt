package com.labwiki.app.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "wiki_pages")
data class WikiPageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val wikiId: String,
    val pageUrl: String,
    val title: String,
    val snippet: String,
    val lastIndexedAt: Long,
    val contentText: String?
)

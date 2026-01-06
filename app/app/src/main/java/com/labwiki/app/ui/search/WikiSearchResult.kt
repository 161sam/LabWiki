package com.labwiki.app.ui.search

data class WikiSearchResult(
    val wikiId: String,
    val wikiName: String,
    val pageUrl: String,
    val title: String,
    val snippet: String
)

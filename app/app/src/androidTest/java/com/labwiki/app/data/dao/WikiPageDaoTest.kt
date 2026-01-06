package com.labwiki.app.data.dao

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.labwiki.app.data.db.WikiDatabase
import com.labwiki.app.data.entity.WikiPageEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WikiPageDaoTest {
    private lateinit var db: WikiDatabase
    private lateinit var dao: WikiPageDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            WikiDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = db.wikiPageDao()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun searchReturnsMatchingPage() = runBlocking {
        val page = WikiPageEntity(
            wikiId = "pentest-lab",
            pageUrl = "index.html",
            title = "Pentest Basics",
            snippet = "Einführung in Pentest.",
            lastIndexedAt = System.currentTimeMillis(),
            contentText = "Pentest Grundlagen und Tools"
        )
        dao.insertAll(listOf(page))

        val results = dao.search("Pentest*", 10)

        assertEquals(1, results.size)
        assertEquals("Pentest Basics", results.first().title)
    }
}

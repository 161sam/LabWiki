package com.labwiki.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.labwiki.app.data.dao.WikiPageDao
import com.labwiki.app.data.entity.WikiPageEntity
import com.labwiki.app.data.entity.WikiPageFtsEntity

@Database(
    entities = [WikiPageEntity::class, WikiPageFtsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WikiDatabase : RoomDatabase() {
    abstract fun wikiPageDao(): WikiPageDao

    companion object {
        @Volatile
        private var INSTANCE: WikiDatabase? = null

        fun get(context: Context): WikiDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    WikiDatabase::class.java,
                    "wiki_search.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

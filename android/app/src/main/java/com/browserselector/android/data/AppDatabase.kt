package com.browserselector.android.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.browserselector.android.model.Browser
import com.browserselector.android.model.UrlRule

/**
 * Room database for BrowserSelector app.
 * Stores browsers and URL rules.
 */
@Database(
    entities = [Browser::class, UrlRule::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun browserDao(): BrowserDao
    abstract fun urlRuleDao(): UrlRuleDao

    companion object {
        private const val DATABASE_NAME = "browser_selector.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}

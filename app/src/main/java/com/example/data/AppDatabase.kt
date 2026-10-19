package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.*
import com.example.data.model.*

@Database(
    entities = [
        BrowserProfile::class,
        BrowserTab::class,
        Bookmark::class,
        HistoryItem::class,
        DownloadItem::class,
        SiteException::class,
        CustomShortcut::class,
        DismissedShortcut::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun tabDao(): TabDao
    abstract fun bookmarkDao(): BookmarkDao
    abstract fun historyDao(): HistoryDao
    abstract fun shortcutDao(): ShortcutDao
    abstract fun dismissedShortcutDao(): DismissedShortcutDao
    abstract fun downloadDao(): DownloadDao
    abstract fun privacyDao(): PrivacyDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        internal val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE browser_profiles ADD COLUMN adBlockEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE browser_profiles ADD COLUMN blockThirdPartyCookies INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE browser_profiles ADD COLUMN backgroundPlayEnabled INTEGER NOT NULL DEFAULT 1")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "lightweight_browser.db"
                )
                    .addMigrations(MIGRATION_4_5)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

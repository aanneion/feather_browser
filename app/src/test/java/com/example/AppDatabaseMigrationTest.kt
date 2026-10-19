package com.example

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppDatabaseMigrationTest {

    private lateinit var context: Context
    private lateinit var databaseFile: File
    private var database: AppDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        databaseFile = context.getDatabasePath("feather_migration_test.db")
        databaseFile.parentFile?.mkdirs()
        if (databaseFile.exists()) databaseFile.delete()
        createVersion4Database(databaseFile)
    }

    @After
    fun tearDown() {
        database?.close()
        database = null
        if (databaseFile.exists()) databaseFile.delete()
    }

    @Test
    fun migration4To5_preservesProfilesAndAddsPrivacyDefaults() = runBlocking {
        database = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "feather_migration_test.db"
        )
            .addMigrations(AppDatabase.MIGRATION_4_5)
            .allowMainThreadQueries()
            .build()

        // Force the open/upgrade path.
        database!!.openHelper.writableDatabase

        val profile = database!!.profileDao().getProfileById("default_personal")
        assertNotNull("Existing profile must survive the migration", profile)
        assertEquals("Personal", profile!!.displayName)
        assertEquals("DEFAULT", profile.fingerprintPreset)
        assertEquals("", profile.customUserAgent)
        assertTrue("New ad-block default must be enabled", profile.adBlockEnabled)
        assertTrue("New cookie default must be enabled", profile.blockThirdPartyCookies)
        assertTrue("New background-play default must be enabled", profile.backgroundPlayEnabled)
    }

    private fun createVersion4Database(file: File) {
        val db = SQLiteDatabase.openOrCreateDatabase(file, null)
        try {
            // Exactly the v4 schema. browser_profiles intentionally lacks the three new
            // per-profile privacy columns.
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `browser_profiles` (" +
                    "`id` TEXT NOT NULL, `displayName` TEXT NOT NULL, `iconName` TEXT NOT NULL, " +
                    "`colorHex` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `isPrivate` INTEGER NOT NULL, " +
                    "`fingerprintPreset` TEXT NOT NULL, `customUserAgent` TEXT NOT NULL, " +
                    "PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `browser_tabs` (`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, " +
                    "`url` TEXT NOT NULL, `title` TEXT NOT NULL, `faviconUrl` TEXT, " +
                    "`lastAccessedAt` INTEGER NOT NULL, `isPrivate` INTEGER NOT NULL, " +
                    "`isDesktopMode` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`profileId` TEXT NOT NULL, `folderName` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`url` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `history_entries` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`profileId` TEXT NOT NULL, `url` TEXT NOT NULL, `title` TEXT NOT NULL, " +
                    "`visitedAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `downloads` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`profileId` TEXT NOT NULL, `url` TEXT NOT NULL, `filename` TEXT NOT NULL, " +
                    "`mimeType` TEXT NOT NULL, `status` TEXT NOT NULL, `fileUri` TEXT, " +
                    "`fileSizeBytes` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL)"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `site_adblock_exceptions` (`domain` TEXT NOT NULL, " +
                    "`isAdBlockDisabled` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`domain`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `custom_shortcuts` (`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `url` TEXT NOT NULL, `position` INTEGER NOT NULL, " +
                    "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `dismissed_shortcuts` (`id` TEXT NOT NULL, `profileId` TEXT NOT NULL, " +
                    "`url` TEXT NOT NULL, `dismissedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "INSERT INTO `browser_profiles` " +
                    "(`id`, `displayName`, `iconName`, `colorHex`, `createdAt`, `isPrivate`, " +
                    "`fingerprintPreset`, `customUserAgent`) " +
                    "VALUES ('default_personal', 'Personal', 'person', '#3B82F6', 1234, 0, 'DEFAULT', '')"
            )
            db.version = 4
        } finally {
            db.close()
        }
    }
}

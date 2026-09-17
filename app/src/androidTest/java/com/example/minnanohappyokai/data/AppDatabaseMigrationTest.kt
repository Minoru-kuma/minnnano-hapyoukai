package com.example.minnanohappyokai.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @Test
    fun migrateV1RetainsOnlyNewestRecitalCascadesOldWorkingDataAndKeepsMasters() {
        val databaseName = "migration-test"
        helper.createDatabase(databaseName, 1).apply {
            execSQL("INSERT INTO recitals(id, name, dateEpochDay, venue) VALUES(1, '古い架空発表会', NULL, '')")
            execSQL("INSERT INTO recitals(id, name, dateEpochDay, venue) VALUES(2, '現在の架空発表会', NULL, '')")
            execSQL("INSERT INTO sections(id, recitalId, name, displayOrder) VALUES(10, 1, '古い部', 0)")
            execSQL("INSERT INTO sections(id, recitalId, name, displayOrder) VALUES(20, 2, '現在の部', 0)")
            execSQL("INSERT INTO performers(id, name, type, grade) VALUES(1, '架空生徒あ', 'STUDENT', '小3')")
            execSQL("INSERT INTO performers(id, name, type, grade) VALUES(2, '架空生徒い', 'STUDENT', '小4')")
            execSQL("INSERT INTO performances(id, sectionId, displayOrder) VALUES(10, 10, 0)")
            execSQL("INSERT INTO performances(id, sectionId, displayOrder) VALUES(20, 20, 0)")
            execSQL("INSERT INTO performance_members(performanceId, performerId, displayOrder) VALUES(10, 1, 0)")
            execSQL("INSERT INTO performance_members(performanceId, performerId, displayOrder) VALUES(20, 2, 0)")
            execSQL("INSERT INTO composers(id, canonicalName) VALUES(1, '架空作曲家')")
            execSQL("INSERT INTO composer_aliases(id, composerId, displayName) VALUES(1, 1, '架空別表記')")
            execSQL("INSERT INTO pieces(id, performanceId, title, displayOrder, composerId, composerDisplayText) VALUES(10, 10, '古い曲', 0, 1, '古い表記')")
            execSQL("INSERT INTO pieces(id, performanceId, title, displayOrder, composerId, composerDisplayText) VALUES(20, 20, '現在の曲', 0, 1, '現在の表記')")
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 2, true, AppDatabase.MIGRATION_1_2).apply {
            query("SELECT id, activeSlot FROM recitals ORDER BY id").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2L, cursor.getLong(0))
                assertEquals(CURRENT_RECITAL_SLOT, cursor.getInt(1))
                assertFalse(cursor.moveToNext())
            }
            query("SELECT recitalId, performerId FROM recital_participants").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2L, cursor.getLong(0))
                assertEquals(2L, cursor.getLong(1))
                assertFalse(cursor.moveToNext())
            }
            query("SELECT COUNT(*) FROM sections").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            query("SELECT COUNT(*) FROM performances").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            query("SELECT COUNT(*) FROM performance_members").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            query("SELECT title FROM pieces").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("現在の曲", cursor.getString(0))
            }
            query("SELECT COUNT(*) FROM performers").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2, cursor.getInt(0))
            }
            query("SELECT COUNT(*) FROM composers").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            query("SELECT COUNT(*) FROM composer_aliases").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1, cursor.getInt(0))
            }
            query("PRAGMA foreign_key_list(performers)").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("assignedTeacherId", cursor.getString(cursor.getColumnIndexOrThrow("from")))
            }
            close()
        }
    }
}

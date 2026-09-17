package com.example.minnanohappyokai.data

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
    fun migrateV1KeepsDataMarksNewestRecitalCurrentAndSeedsItsUsedParticipants() {
        val databaseName = "migration-test"
        helper.createDatabase(databaseName, 1).apply {
            execSQL("INSERT INTO recitals(id, name, dateEpochDay, venue) VALUES(1, '古い架空発表会', NULL, '')")
            execSQL("INSERT INTO recitals(id, name, dateEpochDay, venue) VALUES(2, '現在の架空発表会', NULL, '')")
            execSQL("INSERT INTO sections(id, recitalId, name, displayOrder) VALUES(1, 2, '自由な部名', 0)")
            execSQL("INSERT INTO performers(id, name, type, grade) VALUES(1, '架空生徒', 'STUDENT', '小3')")
            execSQL("INSERT INTO performances(id, sectionId, displayOrder) VALUES(1, 1, 0)")
            execSQL("INSERT INTO performance_members(performanceId, performerId, displayOrder) VALUES(1, 1, 0)")
            close()
        }

        helper.runMigrationsAndValidate(databaseName, 2, true, AppDatabase.MIGRATION_1_2).apply {
            query("SELECT id, activeSlot FROM recitals ORDER BY id").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(1L, cursor.getLong(0))
                assertTrue(cursor.isNull(1))
                assertTrue(cursor.moveToNext())
                assertEquals(2L, cursor.getLong(0))
                assertEquals(CURRENT_RECITAL_SLOT, cursor.getInt(1))
            }
            query("SELECT recitalId, performerId FROM recital_participants").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(2L, cursor.getLong(0))
                assertEquals(1L, cursor.getLong(1))
            }
            query("PRAGMA foreign_key_list(performers)").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("assignedTeacherId", cursor.getString(cursor.getColumnIndexOrThrow("from")))
            }
            close()
        }
    }
}

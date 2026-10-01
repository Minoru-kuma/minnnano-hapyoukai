package com.example.minnanohappyokai.data

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
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
    fun migrateV1WithoutRecitalsKeepsMastersAndHasNoWorkingData() {
        assertMigration("migration-empty", retainedRecitalId = null, participantIds = emptyList()) {}
    }

    @Test
    fun migrateV1WithOneRecitalPreservesItsProgramAndSeedsOnlyItsMembers() {
        assertMigration("migration-single", retainedRecitalId = 7, participantIds = listOf(2, 3)) {
            execSQL("INSERT INTO recitals(id, name, dateEpochDay, venue) VALUES(7, '一つの架空発表会', 20000, '架空会場あ')")
            insertRetainedProgram(7)
        }
    }

    @Test
    fun migrateV1RetainsMaximumIdRecitalDeletesOldWorkingDataAndKeepsMasters() {
        assertMigration("migration-multiple", retainedRecitalId = 30, participantIds = listOf(2, 3)) {
            // Keep the maximum recital ID, independently of insertion order or child IDs.
            execSQL("INSERT INTO recitals(id, name, dateEpochDay, venue) VALUES(30, '保持する架空発表会', 19900, '架空会場い')")
            execSQL("INSERT INTO recitals(id, name, dateEpochDay, venue) VALUES(4, '古い架空発表会あ', 20000, '架空会場う')")
            execSQL("INSERT INTO recitals(id, name, dateEpochDay, venue) VALUES(16, '古い架空発表会い', NULL, '')")
            insertRetainedProgram(30)
            execSQL("INSERT INTO sections(id, recitalId, name, displayOrder) VALUES(110, 4, '古い部あ', 0), (220, 16, '古い部い', 0)")
            execSQL("INSERT INTO performances(id, sectionId, displayOrder) VALUES(510, 110, 0), (520, 220, 0)")
            execSQL("INSERT INTO performance_members(performanceId, performerId, displayOrder) VALUES(510, 1, 0), (510, 2, 1), (520, 1, 0)")
            execSQL("INSERT INTO pieces(id, performanceId, title, displayOrder, composerId, composerDisplayText) VALUES(700, 510, '古い架空曲あ', 0, 1, '古い架空表記'), (710, 520, '古い架空曲い', 0, 1, '別の古い表記')")
        }
    }

    private fun assertMigration(
        databaseName: String,
        retainedRecitalId: Long?,
        participantIds: List<Long>,
        seed: SupportSQLiteDatabase.() -> Unit,
    ) {
        val (mastersBefore, retainedProgramBefore) = helper.createDatabase(databaseName, 1).use { db ->
            assertEquals(listOf(listOf("0")), db.rows("PRAGMA foreign_keys"))
            db.insertMasters()
            db.seed()
            db.masterRows() to db.programRows(retainedRecitalId)
        }

        helper.runMigrationsAndValidate(databaseName, 2, true, AppDatabase.MIGRATION_1_2).use { db ->
            // Exercise the default migration connection; cleanup must not depend on CASCADE.
            assertEquals(listOf(listOf("0")), db.rows("PRAGMA foreign_keys"))
            assertEquals(mastersBefore, db.masterRows())
            assertEquals(retainedProgramBefore, db.programRows())
            val expectedRecitals = retainedRecitalId?.let {
                listOf(listOf(it.toString(), CURRENT_RECITAL_SLOT.toString()))
            } ?: emptyList()
            assertEquals(expectedRecitals, db.rows("SELECT id, activeSlot FROM recitals ORDER BY id"))
            val expectedParticipants = participantIds.map {
                listOf(checkNotNull(retainedRecitalId).toString(), it.toString())
            }
            assertEquals(expectedParticipants, db.rows("SELECT recitalId, performerId FROM recital_participants ORDER BY recitalId, performerId"))
            assertTrue(db.rows("SELECT id FROM performers WHERE assignedTeacherId IS NOT NULL").isEmpty())
            db.query("PRAGMA foreign_key_list(performers)").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("assignedTeacherId", cursor.getString(cursor.getColumnIndexOrThrow("from")))
            }
            db.query("PRAGMA foreign_key_check").use { cursor ->
                assertFalse("Migration must leave no foreign-key violations", cursor.moveToFirst())
            }
        }
    }

    private fun SupportSQLiteDatabase.insertMasters() {
        // IDs 1, 2/3, and 4 cover old-only, retained-program, and unused directory entries.
        execSQL("INSERT INTO performers(id, name, type, grade) VALUES(1, '架空生徒あ', 'STUDENT', '小2'), (2, '架空生徒い', 'STUDENT', '小4'), (3, '架空講師', 'TEACHER', NULL), (4, '架空生徒う', 'STUDENT', '個別学年')")
        execSQL("INSERT INTO composers(id, canonicalName) VALUES(1, '古い曲の架空作曲家'), (2, '保持曲の架空作曲家'), (3, '未使用の架空作曲家')")
        execSQL("INSERT INTO composer_aliases(id, composerId, displayName) VALUES(1, 1, '古い架空別表記'), (2, 2, '  Ｋ.架空／別表記  '), (3, 3, '未使用の架空別表記')")
    }

    private fun SupportSQLiteDatabase.insertRetainedProgram(recitalId: Long) {
        execSQL("INSERT INTO sections(id, recitalId, name, displayOrder) VALUES(10, $recitalId, '保持する部あ', 4), (12, $recitalId, '保持する部い', 1)")
        execSQL("INSERT INTO performances(id, sectionId, displayOrder) VALUES(20, 10, 7), (21, 10, 3), (22, 12, 2)")
        // One duet, one piece-less draft, and one teacher slot; repeated members seed once.
        execSQL("INSERT INTO performance_members(performanceId, performerId, displayOrder) VALUES(20, 2, 1), (20, 3, 0), (21, 2, 0), (22, 3, 0)")
        execSQL("INSERT INTO pieces(id, performanceId, title, displayOrder, composerId, composerDisplayText) VALUES(5, 20, '保持する架空曲あ', 6, 2, '  Ｋ.架空／別表記  '), (6, 20, '保持する架空曲い', 2, 2, '架空作曲家の別表記'), (7, 22, '保持する架空講師曲', 3, NULL, '手入力の架空表記')")
    }

    private fun SupportSQLiteDatabase.masterRows() = mapOf(
        "performers" to rows("SELECT id, name, type, grade FROM performers ORDER BY id"),
        "composers" to rows("SELECT * FROM composers ORDER BY id"),
        "composer_aliases" to rows("SELECT * FROM composer_aliases ORDER BY id"),
    )

    private fun SupportSQLiteDatabase.programRows(recitalId: Long? = null): Map<String, List<List<String?>>> {
        val recitalFilter = recitalId?.let { " WHERE id = $it" }.orEmpty()
        val sectionFilter = recitalId?.let { " WHERE recitalId = $it" }.orEmpty()
        val performanceFilter = recitalId?.let {
            " WHERE sectionId IN (SELECT id FROM sections WHERE recitalId = $it)"
        }.orEmpty()
        val childFilter = recitalId?.let {
            " WHERE performanceId IN (SELECT id FROM performances$performanceFilter)"
        }.orEmpty()
        return mapOf(
            "recitals" to rows("SELECT id, name, dateEpochDay, venue FROM recitals$recitalFilter ORDER BY id"),
            "sections" to rows("SELECT * FROM sections$sectionFilter ORDER BY id"),
            "performances" to rows("SELECT * FROM performances$performanceFilter ORDER BY id"),
            "performance_members" to rows("SELECT * FROM performance_members$childFilter ORDER BY performanceId, performerId"),
            "pieces" to rows("SELECT * FROM pieces$childFilter ORDER BY id"),
        )
    }

    private fun SupportSQLiteDatabase.rows(sql: String): List<List<String?>> = query(sql).use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add((0 until cursor.columnCount).map { column ->
                    if (cursor.isNull(column)) null else cursor.getString(column)
                })
            }
        }
    }
}

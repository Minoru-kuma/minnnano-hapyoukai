package com.example.minnanohappyokai.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecitalDatabaseTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: RecitalDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
        dao = database.recitalDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun arbitrarySectionsDuetAndMultiplePiecesHaveIndependentOrders() = runBlocking {
        val recitalId = dao.insert(Recital(name = "架空の発表会"))
        val lastSection = dao.insert(Section(recitalId = recitalId, name = "星の時間", displayOrder = 1))
        val firstSection = dao.insert(Section(recitalId = recitalId, name = "花の時間", displayOrder = 0))
        val anotherRecital = dao.insert(Recital(name = "別の架空発表会"))
        dao.insert(Section(recitalId = anotherRecital, name = "別の部", displayOrder = 0))
        val performanceId = dao.insert(Performance(sectionId = firstSection, displayOrder = 0))
        val teacherA = dao.insert(Performer(name = "架空講師あ", type = PerformerType.TEACHER))
        val teacherB = dao.insert(Performer(name = "架空講師い", type = PerformerType.TEACHER))
        dao.insertMembers(listOf(
            PerformanceMember(performanceId, teacherA, displayOrder = 1),
            PerformanceMember(performanceId, teacherB, displayOrder = 0),
        ))
        val secondPiece = dao.insert(Piece(performanceId = performanceId, title = "架空曲二", displayOrder = 1))
        val firstPiece = dao.insert(Piece(performanceId = performanceId, title = "架空曲一", displayOrder = 0))

        assertEquals(listOf(firstSection, lastSection), dao.observeSections(recitalId).first().map { it.id })
        assertEquals(listOf(teacherB, teacherA), dao.observeMembers(performanceId).first().map { it.performerId })
        assertEquals(listOf(firstPiece, secondPiece), dao.observePieces(performanceId).first().map { it.id })
        assertTrue(dao.observePerformances(lastSection).first().isEmpty())
    }

    @Test
    fun deletingRecitalCascadesProgramButKeepsSharedPeopleAndComposers() = runBlocking {
        val recital = Recital(id = dao.insert(Recital(name = "架空の発表会")), name = "架空の発表会")
        val sectionId = dao.insert(Section(recitalId = recital.id, name = "自由な部名", displayOrder = 0))
        val performanceId = dao.insert(Performance(sectionId = sectionId, displayOrder = 0))
        val performerId = dao.insert(Performer(name = "架空生徒あ", type = PerformerType.STUDENT, grade = "小学3年"))
        dao.insertMembers(listOf(PerformanceMember(performanceId, performerId, 0)))
        val composerId = dao.insert(Composer(canonicalName = "架空作曲家"))
        dao.insert(ComposerAlias(composerId = composerId, displayName = "架空別表記"))
        dao.insert(Piece(performanceId = performanceId, title = "架空曲", displayOrder = 0, composerId = composerId))

        val deletion = runCatching { dao.delete(dao.getPerformers(listOf(performerId)).single()) }
        assertTrue("A referenced performer must not be deleted", deletion.exceptionOrNull() is SQLiteConstraintException)
        assertEquals(1, dao.countMemberships(performerId))

        dao.delete(recital)

        assertNull(dao.observeRecital(recital.id).first())
        assertTrue(dao.observeSections(recital.id).first().isEmpty())
        assertTrue(dao.observePerformances(sectionId).first().isEmpty())
        assertTrue(dao.observeMembers(performanceId).first().isEmpty())
        assertTrue(dao.observePieces(performanceId).first().isEmpty())
        assertEquals(listOf(performerId), dao.observePerformers().first().map { it.id })
        assertEquals(listOf(composerId), dao.observeComposers().first().map { it.id })
        assertEquals(1, dao.observeComposerAliases(composerId).first().size)
    }

    @Test
    fun composerChangesAndDeletionPreserveEachPiecesExactNotation() = runBlocking {
        val recitalId = dao.insert(Recital(name = "架空の発表会"))
        val sectionId = dao.insert(Section(recitalId = recitalId, name = "自由な部名", displayOrder = 0))
        val performanceId = dao.insert(Performance(sectionId = sectionId, displayOrder = 0))
        val composerId = dao.insert(Composer(canonicalName = "架空作曲家"))
        val aliasId = dao.insert(ComposerAlias(composerId = composerId, displayName = "架空別表記"))
        val notations = listOf("  K.テスト  ", "架空作曲家・別の表記")
        notations.forEachIndexed { order, notation ->
            dao.insert(Piece(
                performanceId = performanceId, title = "架空曲${order + 1}", displayOrder = order,
                composerId = composerId, composerDisplayText = notation,
            ))
        }

        val renamedComposer = Composer(id = composerId, canonicalName = "変更後の架空作曲家")
        dao.update(renamedComposer)
        dao.update(ComposerAlias(id = aliasId, composerId = composerId, displayName = "変更後の別表記"))
        assertEquals(notations, dao.observePieces(performanceId).first().map { it.composerDisplayText })

        dao.delete(renamedComposer)

        val pieces = dao.observePieces(performanceId).first()
        assertEquals(notations, pieces.map { it.composerDisplayText })
        assertTrue(pieces.all { it.composerId == null })
        assertTrue(dao.observeComposerAliases(composerId).first().isEmpty())
    }

    @Test
    fun repositoryDefaultsStudentsBeforeTeachersAndPreservesManualOrderAfterMemberEdits() = runBlocking {
        val repository = RecitalRepository(database)
        val recitalId = repository.createRecital("架空の発表会")
        val sectionId = repository.createSection(recitalId, "自由な部名")
        val student = repository.createPerformer("架空生徒あ", PerformerType.STUDENT)
        val teacherA = repository.createPerformer("架空講師あ", PerformerType.TEACHER)
        val teacherB = repository.createPerformer("架空講師い", PerformerType.TEACHER)
        val duet = repository.createPerformance(sectionId, listOf(teacherA, teacherB))
        val solo = repository.createPerformance(sectionId, listOf(student))
        val mixed = repository.createPerformance(sectionId, listOf(student, teacherA))
        assertEquals(listOf(solo, mixed, duet), dao.performanceIds(sectionId))

        repository.reorderPerformances(sectionId, listOf(duet, solo, mixed))
        repository.replacePerformanceMembers(duet, listOf(teacherB, teacherA))
        assertEquals(listOf(duet, solo, mixed), dao.performanceIds(sectionId))
        assertEquals(listOf(teacherB, teacherA), dao.observeMembers(duet).first().map { it.performerId })
        assertEquals(listOf(0, 1, 2), dao.observePerformances(sectionId).first().map { it.displayOrder })
        assertEquals(false, repository.deletePerformer(dao.getPerformers(listOf(student)).single()))
    }

    @Test
    fun failedPieceInsertRollsBackPerformanceMembersPiecesAndShiftedOrder() = runBlocking {
        val repository = RecitalRepository(database)
        val recitalId = repository.createRecital("架空の発表会")
        val sectionId = repository.createSection(recitalId, "自由な部名")
        val teacher = repository.createPerformer("架空講師あ", PerformerType.TEACHER)
        val student = repository.createPerformer("架空生徒あ", PerformerType.STUDENT)
        val teacherPerformance = repository.createPerformance(sectionId, listOf(teacher))
        val before = dao.observePerformances(sectionId).first()

        val result = runCatching {
            repository.createPerformance(sectionId, listOf(student), listOf(
                NewPiece(title = "先に挿入される架空曲"),
                NewPiece(title = "存在しない作曲家の架空曲", composerId = Long.MAX_VALUE),
            ))
        }

        assertTrue("The invalid composer reference must reject the compound write", result.exceptionOrNull() is SQLiteConstraintException)
        assertEquals(before, dao.observePerformances(sectionId).first())
        assertEquals(0, dao.countMemberships(student))
        assertEquals(1, dao.countMemberships(teacher))
        assertTrue(dao.observePieces(teacherPerformance).first().isEmpty())
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM pieces").use {
            assertTrue(it.moveToFirst())
            assertEquals(0, it.getInt(0))
        }
    }

    @Test
    fun reorderingRequiresAllChildrenOfTheSameParentAndRejectsDuplicateMembers() = runBlocking {
        val repository = RecitalRepository(database)
        val recitalId = repository.createRecital("架空の発表会")
        val firstSection = repository.createSection(recitalId, "花の時間")
        val secondSection = repository.createSection(recitalId, "星の時間")
        val otherRecital = repository.createRecital("別の架空発表会")
        val foreignSection = repository.createSection(otherRecital, "別の部")
        repository.reorderSections(recitalId, listOf(secondSection, firstSection))
        val invalidOrder = runCatching {
            repository.reorderSections(recitalId, listOf(firstSection, foreignSection))
        }
        assertTrue(invalidOrder.exceptionOrNull() is IllegalArgumentException)
        assertEquals(listOf(secondSection, firstSection), dao.sectionIds(recitalId))
        assertEquals(listOf(foreignSection), dao.sectionIds(otherRecital))

        val student = repository.createPerformer("架空生徒あ", PerformerType.STUDENT)
        val performance = repository.createPerformance(firstSection, listOf(student), listOf(
            NewPiece(title = "架空曲一"), NewPiece(title = "架空曲二"),
        ))
        val pieces = dao.pieceIds(performance)
        repository.reorderPieces(performance, pieces.reversed())
        assertEquals(pieces.reversed(), dao.pieceIds(performance))
        val duplicateOrder = runCatching { repository.reorderPieces(performance, listOf(pieces[0], pieces[0])) }
        assertTrue(duplicateOrder.exceptionOrNull() is IllegalArgumentException)
        assertEquals(pieces.reversed(), dao.pieceIds(performance))
        val duplicateMembers = runCatching { repository.replacePerformanceMembers(performance, listOf(student, student)) }
        val emptyMembers = runCatching { repository.replacePerformanceMembers(performance, emptyList()) }
        assertTrue(duplicateMembers.exceptionOrNull() is IllegalArgumentException)
        assertTrue(emptyMembers.exceptionOrNull() is IllegalArgumentException)
        assertEquals(listOf(student), dao.observeMembers(performance).first().map { it.performerId })
    }
}

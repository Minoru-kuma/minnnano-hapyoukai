package com.example.minnanohappyokai.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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

    @Test
    fun compoundEditPreservesPerformancePositionAndSavesMemberAndPieceOrder() = runBlocking {
        val repository = RecitalRepository(database)
        val recital = repository.createRecital("架空の発表会")
        val section = repository.createSection(recital, "架空の部")
        val student = repository.createPerformer("架空生徒", PerformerType.STUDENT)
        val teacher = repository.createPerformer("架空講師", PerformerType.TEACHER)
        val performance = repository.createPerformance(section, listOf(student), listOf(NewPiece("架空旧曲")))
        val before = dao.getPerformance(performance)
        val notation = "  K.架空／別表記  "
        repository.updatePerformanceProgram(performance, listOf(teacher, student), listOf(
            NewPiece("架空曲二", composerDisplayText = notation),
            NewPiece("架空曲一"),
        ))
        assertEquals(before, dao.getPerformance(performance))
        assertEquals(listOf(teacher, student), dao.observeMembers(performance).first().map { it.performerId })
        val pieces = dao.observePieces(performance).first()
        assertEquals(listOf("架空曲二", "架空曲一"), pieces.map { it.title })
        assertEquals(listOf(0, 1), pieces.map { it.displayOrder })
        assertEquals(notation, pieces.first().composerDisplayText)
        repository.updatePerformanceProgram(performance, listOf(teacher), emptyList())
        assertTrue(dao.observePieces(performance).first().isEmpty())
        assertEquals(before, dao.getPerformance(performance))
    }

    @Test
    fun failedCompoundEditRestoresOriginalMembersAndPieces() = runBlocking {
        val repository = RecitalRepository(database)
        val recital = repository.createRecital("架空の発表会")
        val section = repository.createSection(recital, "架空の部")
        val student = repository.createPerformer("架空生徒", PerformerType.STUDENT)
        val teacher = repository.createPerformer("架空講師", PerformerType.TEACHER)
        val performance = repository.createPerformance(section, listOf(student), listOf(
            NewPiece("架空旧曲", composerDisplayText = "  架空表記  "),
        ))
        val before = repository.observeProgram().first()
        val failure = runCatching {
            repository.updatePerformanceProgram(performance, listOf(teacher), listOf(
                NewPiece("架空新曲"),
                NewPiece("架空不正曲", composerId = Long.MAX_VALUE),
            ))
        }
        assertTrue(failure.exceptionOrNull() is SQLiteConstraintException)
        assertEquals(before, repository.observeProgram().first())
        for (members in listOf(emptyList(), listOf(student, student), listOf(Long.MAX_VALUE))) {
            assertTrue(runCatching {
                repository.updatePerformanceProgram(performance, members, emptyList())
            }.exceptionOrNull() is IllegalArgumentException)
            assertEquals(before, repository.observeProgram().first())
        }
    }

    @Test
    fun programObserverEmitsCompleteTransactionsAndTracksAllSharedTables() = runBlocking {
        val repository = RecitalRepository(database)
        val recital = repository.createRecital("架空の発表会")
        val section = repository.createSection(recital, "架空の部")
        val first = repository.createPerformer("架空生徒一", PerformerType.STUDENT)
        val second = repository.createPerformer("架空生徒二", PerformerType.STUDENT)
        val performance = repository.createPerformance(section, listOf(first), listOf(NewPiece("架空曲一")))
        val emissions = Channel<ProgramSnapshot>(Channel.UNLIMITED)
        val observer = launch { repository.observeProgram().collect { emissions.send(it) } }
        suspend fun awaitSnapshot(predicate: (ProgramSnapshot) -> Boolean): ProgramSnapshot = withTimeout(5_000) {
            while (true) {
                val snapshot = emissions.receive()
                val member = snapshot.members.singleOrNull { it.performanceId == performance }
                if (member != null) {
                    val expectedTitle = if (member.performerId == first) "架空曲一" else "架空曲二"
                    assertEquals(expectedTitle, snapshot.pieces.single { it.performanceId == performance }.title)
                }
                if (predicate(snapshot)) return@withTimeout snapshot
            }
            @Suppress("UNREACHABLE_CODE")
            error("Unreachable")
        }
        try {
            awaitSnapshot { it.performances.size == 1 }
            repeat(10) { index ->
                val useSecond = index % 2 == 0
                repository.updatePerformanceProgram(performance, listOf(if (useSecond) second else first), listOf(
                    NewPiece(if (useSecond) "架空曲二" else "架空曲一"),
                ))
                awaitSnapshot { it.members.single().performerId == if (useSecond) second else first }
            }
            val composer = repository.createComposer("架空作曲家")
            awaitSnapshot { it.composers.any { entry -> entry.id == composer } }
            val alias = repository.createComposerAlias(composer, "架空別表記")
            awaitSnapshot { it.aliases.any { entry -> entry.id == alias } }
            val newPerson = repository.createPerformer("架空追加講師", PerformerType.TEACHER)
            awaitSnapshot { it.performers.any { entry -> entry.id == newPerson } }
            repository.deleteRecital(dao.getAllRecitals().single())
            val after = awaitSnapshot { it.recitals.isEmpty() }
            assertTrue(after.sections.isEmpty())
            assertTrue(after.performances.isEmpty())
            assertTrue(after.members.isEmpty())
            assertTrue(after.pieces.isEmpty())
            assertEquals(3, after.performers.size)
            assertEquals(1, after.composers.size)
            assertEquals(1, after.aliases.size)
        } finally {
            observer.cancel()
            observer.join()
            emissions.close()
        }
    }

    @Test
    fun updatingDeletedItemsFailsInsteadOfReportingSuccessfulSave() = runBlocking {
        val repository = RecitalRepository(database)
        assertTrue(runCatching {
            repository.updateRecital(Recital(id = 42, name = "架空削除済み発表会"))
        }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching {
            repository.updateSection(Section(id = 42, recitalId = 42, name = "架空削除済み部", displayOrder = 0))
        }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching {
            repository.updatePerformer(Performer(id = 42, name = "架空削除済み出演者", type = PerformerType.STUDENT))
        }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching {
            repository.updatePerformanceProgram(42, listOf(42), emptyList())
        }.exceptionOrNull() is IllegalStateException)
    }

}

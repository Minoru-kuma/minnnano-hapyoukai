package com.example.minnanohappyokai.data

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RecitalDatabaseTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: RecitalDao
    private lateinit var repository: RecitalRepository

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
        dao = database.recitalDao()
        repository = RecitalRepository(database)
    }

    @After
    fun closeDatabase() = database.close()

    @Test
    fun onlyOneCurrentRecitalIsAllowedAtRepositoryAndDatabaseBoundaries() = runBlocking {
        val recitalId = repository.startRecital("架空の発表会")

        assertTrue(runCatching { repository.startRecital("別の架空発表会") }.exceptionOrNull() is IllegalStateException)
        assertTrue(runCatching { dao.insert(Recital(name = "直接追加の架空発表会")) }
            .exceptionOrNull() is SQLiteConstraintException)
        assertEquals(recitalId, repository.observeProgram().first().activeRecital?.id)
    }

    @Test
    fun onlyCurrentParticipantsCanBePlacedInPerformancesAndUsedParticipantsCannotBeRemoved() = runBlocking {
        val recitalId = repository.startRecital("架空の発表会")
        val sectionId = repository.createSection(recitalId, "自由な部名")
        val selected = repository.createPerformer("架空生徒あ", PerformerType.STUDENT, "小3")
        val notSelected = repository.createPerformer("架空生徒い", PerformerType.STUDENT, "小4")
        repository.addCurrentParticipant(selected)

        assertTrue(runCatching { repository.createPerformance(sectionId, listOf(notSelected)) }
            .exceptionOrNull() is IllegalArgumentException)
        val performanceId = repository.createPerformance(sectionId, listOf(selected))

        assertFalse(repository.removeCurrentParticipant(selected))
        assertEquals(listOf(selected), repository.observeProgram().first().participants.map { it.performerId })
        assertEquals(listOf(selected), repository.observeProgram().first().members
            .filter { it.performanceId == performanceId }.map { it.performerId })
    }

    @Test
    fun teacherAssignmentsArePersistentReferencesAndCannotBecomeInconsistent() = runBlocking {
        repository.startRecital("架空の発表会")
        val teacher = repository.createPerformer("架空講師", PerformerType.TEACHER)
        val student = repository.createPerformer("架空生徒", PerformerType.STUDENT, "小2", teacher)
        val teacherEntry = checkNotNull(dao.getPerformer(teacher))

        assertEquals(teacher, dao.getPerformer(student)?.assignedTeacherId)
        assertTrue(runCatching {
            repository.createPerformer("不正な架空講師", PerformerType.TEACHER, grade = "小1")
        }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching {
            repository.createPerformer("不正な架空生徒", PerformerType.STUDENT, assignedTeacherId = student)
        }.exceptionOrNull() is IllegalArgumentException)
        assertTrue(runCatching { repository.updatePerformer(teacherEntry.copy(type = PerformerType.STUDENT)) }
            .exceptionOrNull() is IllegalArgumentException)
        assertEquals(teacherEntry, dao.getPerformer(teacher))
        assertEquals(teacher, dao.getPerformer(student)?.assignedTeacherId)
        assertFalse(repository.deletePerformer(teacherEntry))
    }

    @Test
    fun sectionUpdatesRejectChangedRecitalAndKeepProgramUnchanged() = runBlocking {
        val recital = repository.startRecital("架空の発表会")
        val sectionId = repository.createSection(recital, "架空の部")
        val student = repository.createCurrentParticipantPerformer("架空生徒", PerformerType.STUDENT)
        repository.createPerformance(sectionId, listOf(student), listOf(NewPiece("架空曲")))
        val section = checkNotNull(dao.getSection(sectionId))

        // No second recital is valid. Require repository rejection rather than an FK failure.
        assertUpdateRejectedWithoutChanges {
            repository.updateSection(section.copy(recitalId = Long.MAX_VALUE, name = "変更後の部", displayOrder = 3))
        }
        assertEquals(section, dao.getSection(sectionId))
    }

    @Test
    fun performanceUpdatesRejectChangedSectionAndKeepProgramUnchanged() = runBlocking {
        val recital = repository.startRecital("架空の発表会")
        val originalSection = repository.createSection(recital, "架空の部あ")
        val otherSection = repository.createSection(recital, "架空の部い")
        val student = repository.createCurrentParticipantPerformer("架空生徒", PerformerType.STUDENT)
        val performanceId = repository.createPerformance(originalSection, listOf(student), listOf(NewPiece("架空曲")))
        val performance = checkNotNull(dao.getPerformance(performanceId))

        assertUpdateRejectedWithoutChanges {
            repository.updatePerformance(performance.copy(sectionId = otherSection, displayOrder = 3))
        }
        assertEquals(performance, dao.getPerformance(performanceId))
    }

    @Test
    fun pieceUpdatesRejectChangedPerformanceAndKeepProgramUnchanged() = runBlocking {
        val recital = repository.startRecital("架空の発表会")
        val section = repository.createSection(recital, "架空の部")
        val student = repository.createCurrentParticipantPerformer("架空生徒", PerformerType.STUDENT)
        val originalPerformance = repository.createPerformance(section, listOf(student))
        val otherPerformance = repository.createPerformance(section, listOf(student))
        val pieceId = repository.createPiece(originalPerformance, NewPiece("架空曲", composerDisplayText = "元の表記"))
        val piece = checkNotNull(dao.getPiece(pieceId))
        val composer = repository.createComposer("架空作曲家")

        assertUpdateRejectedWithoutChanges {
            repository.updatePiece(piece.copy(
                performanceId = otherPerformance,
                title = "変更後の曲",
                displayOrder = 3,
                composerId = composer,
                composerDisplayText = "変更後の表記",
            ))
        }
        assertEquals(piece, dao.getPiece(pieceId))
    }

    @Test
    fun composerAliasUpdatesRejectChangedComposerAndKeepProgramUnchanged() = runBlocking {
        val composer = repository.createComposer("架空作曲家あ")
        val otherComposer = repository.createComposer("架空作曲家い")
        val aliasId = repository.createComposerAlias(composer, "元の架空表記")
        val alias = checkNotNull(dao.getComposerAlias(aliasId))

        assertUpdateRejectedWithoutChanges {
            repository.updateComposerAlias(alias.copy(composerId = otherComposer, displayName = "変更後の表記"))
        }
        assertEquals(alias, dao.getComposerAlias(aliasId))
    }

    @Test
    fun ordinaryUpdatesKeepParentsAndSaveEditableFields() = runBlocking {
        val recital = repository.startRecital("架空の発表会")
        val sectionId = repository.createSection(recital, "架空の部")
        val student = repository.createCurrentParticipantPerformer("架空生徒", PerformerType.STUDENT)
        val performanceId = repository.createPerformance(sectionId, listOf(student))
        val composer = repository.createComposer("架空作曲家あ")
        val otherComposer = repository.createComposer("架空作曲家い")
        val aliasId = repository.createComposerAlias(composer, "元の架空表記")
        val pieceId = repository.createPiece(performanceId, NewPiece("架空曲", composer, "元の架空表記"))
        val membersBefore = repository.observeProgram().first().members
        val section = checkNotNull(dao.getSection(sectionId)).copy(name = "変更後の部", displayOrder = 3)
        val performance = checkNotNull(dao.getPerformance(performanceId)).copy(displayOrder = 4)
        val piece = checkNotNull(dao.getPiece(pieceId)).copy(
            title = "変更後の曲",
            displayOrder = 5,
            composerId = otherComposer,
            composerDisplayText = "  Ｋ.架空／別表記  ",
        )
        val alias = checkNotNull(dao.getComposerAlias(aliasId)).copy(displayName = "  変更後の架空表記  ")

        repository.updateSection(section)
        repository.updatePerformance(performance)
        repository.updatePiece(piece)
        repository.updateComposerAlias(alias)

        assertEquals(section, dao.getSection(sectionId))
        assertEquals(performance, dao.getPerformance(performanceId))
        assertEquals(piece, dao.getPiece(pieceId))
        assertEquals(alias, dao.getComposerAlias(aliasId))
        assertEquals(membersBefore, repository.observeProgram().first().members)
        repository.updatePiece(piece.copy(composerId = null))
        assertEquals(piece.copy(composerId = null), dao.getPiece(pieceId))
    }

    @Test
    fun teacherToStudentUpdateRejectsSelfAssignmentAndKeepsOriginalTeacher() = runBlocking {
        val teacherId = repository.createPerformer("架空講師", PerformerType.TEACHER)
        val teacher = checkNotNull(dao.getPerformer(teacherId))
        assertEquals(0, dao.countAssignedStudents(teacherId))

        assertUpdateRejectedWithoutChanges {
            repository.updatePerformer(teacher.copy(
                name = "変更後の架空生徒",
                type = PerformerType.STUDENT,
                grade = "小3",
                assignedTeacherId = teacherId,
            ))
        }
        assertEquals(teacher, dao.getPerformer(teacherId))
    }

    @Test
    fun performerUpdatesRejectInvalidTeacherAssignmentsWithoutChangingDirectory() = runBlocking {
        val teacherId = repository.createPerformer("架空講師あ", PerformerType.TEACHER)
        val otherTeacherId = repository.createPerformer("架空講師い", PerformerType.TEACHER)
        val studentId = repository.createPerformer("架空生徒あ", PerformerType.STUDENT, "小2", teacherId)
        val otherStudentId = repository.createPerformer("架空生徒い", PerformerType.STUDENT)
        val student = checkNotNull(dao.getPerformer(studentId))
        val teacher = checkNotNull(dao.getPerformer(teacherId))

        listOf(studentId, otherStudentId, Long.MAX_VALUE).forEach { invalidTeacherId ->
            assertUpdateRejectedWithoutChanges {
                repository.updatePerformer(student.copy(name = "変更後の架空生徒", assignedTeacherId = invalidTeacherId))
            }
        }
        assertUpdateRejectedWithoutChanges {
            repository.updatePerformer(teacher.copy(name = "変更後の架空講師", assignedTeacherId = otherTeacherId))
        }
        assertUpdateRejectedWithoutChanges {
            repository.updatePerformer(teacher.copy(assignedTeacherId = teacherId))
        }
        assertEquals(student, dao.getPerformer(studentId))
        assertEquals(teacher, dao.getPerformer(teacherId))
    }

    @Test
    fun performerUpdatesAllowAnotherTeacherAndValidTypeChanges() = runBlocking {
        val teacherId = repository.createPerformer("架空講師あ", PerformerType.TEACHER)
        val otherTeacherId = repository.createPerformer("架空講師い", PerformerType.TEACHER)
        val studentId = repository.createPerformer("架空生徒", PerformerType.STUDENT, "小2", teacherId)
        val student = checkNotNull(dao.getPerformer(studentId)).copy(
            name = "変更後の架空生徒", grade = "小3", assignedTeacherId = otherTeacherId,
        )
        repository.updatePerformer(student)
        assertEquals(student, dao.getPerformer(studentId))

        // Once no students reference this teacher, a valid type change must still be allowed.
        val formerTeacher = checkNotNull(dao.getPerformer(teacherId)).copy(
            type = PerformerType.STUDENT, grade = "小4", assignedTeacherId = otherTeacherId,
        )
        repository.updatePerformer(formerTeacher)
        assertEquals(formerTeacher, dao.getPerformer(teacherId))
        val newTeacher = student.copy(type = PerformerType.TEACHER, grade = null, assignedTeacherId = null)
        repository.updatePerformer(newTeacher)
        assertEquals(newTeacher, dao.getPerformer(studentId))
    }

    @Test
    fun endingCurrentRecitalDeletesOnlyRecitalScopedData() = runBlocking {
        val recital = repository.startRecital("架空の発表会")
        val section = repository.createSection(recital, "自由な部名")
        val student = repository.createCurrentParticipantPerformer("架空生徒", PerformerType.STUDENT, "小3")
        val performance = repository.createPerformance(section, listOf(student), listOf(NewPiece("架空曲")))
        val composer = repository.createComposer("架空作曲家")
        repository.createComposerAlias(composer, "架空別表記")
        repository.createPiece(performance, NewPiece("架空曲二", composer))

        repository.endCurrentRecital()
        val after = repository.observeProgram().first()
        assertNull(after.activeRecital)
        assertTrue(after.sections.isEmpty())
        assertTrue(after.participants.isEmpty())
        assertTrue(after.performances.isEmpty())
        assertTrue(after.members.isEmpty())
        assertTrue(after.pieces.isEmpty())
        assertEquals(listOf(student), after.performers.map { it.id })
        assertEquals(listOf(composer), after.composers.map { it.id })
        assertEquals(1, after.aliases.size)
    }

    @Test
    fun orderingMultiplePiecesAndExactComposerNotationArePreserved() = runBlocking {
        val recital = repository.startRecital("架空の発表会")
        val section = repository.createSection(recital, "自由な部名")
        val student = repository.createCurrentParticipantPerformer("架空生徒", PerformerType.STUDENT)
        val teacher = repository.createCurrentParticipantPerformer("架空講師", PerformerType.TEACHER)
        val teacherPerformance = repository.createPerformance(section, listOf(teacher))
        val studentPerformance = repository.createPerformance(section, listOf(student), listOf(
            NewPiece("架空曲一", composerDisplayText = "  K.架空／別表記  "),
            NewPiece("架空曲二"),
        ))

        assertEquals(listOf(studentPerformance, teacherPerformance), dao.performanceIds(section))
        repository.reorderPerformances(section, listOf(teacherPerformance, studentPerformance))
        repository.replacePerformanceMembers(teacherPerformance, listOf(teacher))
        val pieceIds = dao.pieceIds(studentPerformance)
        repository.reorderPieces(studentPerformance, pieceIds.reversed())

        val snapshot = repository.observeProgram().first()
        assertEquals(listOf(teacherPerformance, studentPerformance), dao.performanceIds(section))
        assertEquals("  K.架空／別表記  ", snapshot.pieces.single { it.title == "架空曲一" }.composerDisplayText)
        assertEquals(pieceIds.reversed(), dao.pieceIds(studentPerformance))
    }

    @Test
    fun invalidCompoundEditsRollBackAndOrderingRejectsPartialLists() = runBlocking {
        val recital = repository.startRecital("架空の発表会")
        val section = repository.createSection(recital, "自由な部名")
        val teacher = repository.createCurrentParticipantPerformer("架空講師", PerformerType.TEACHER)
        val student = repository.createCurrentParticipantPerformer("架空生徒", PerformerType.STUDENT)
        val teacherPerformance = repository.createPerformance(section, listOf(teacher))
        val before = repository.observeProgram().first()

        val failure = runCatching {
            repository.createPerformance(section, listOf(student), listOf(
                NewPiece("先に挿入される架空曲"),
                NewPiece("存在しない作曲家の架空曲", composerId = Long.MAX_VALUE),
            ))
        }
        assertTrue(failure.exceptionOrNull() is SQLiteConstraintException)
        assertEquals(before, repository.observeProgram().first())
        assertTrue(runCatching { repository.reorderPerformances(section, listOf(teacherPerformance)) }
            .exceptionOrNull() == null)
        assertTrue(runCatching { repository.reorderPerformances(section, emptyList()) }
            .exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun gradeSuggestionsNeedExplicitConfirmationAndFinalizedSnapshotCopiesValues() = runBlocking {
        val recital = repository.startRecital("架空の発表会")
        val student = repository.createCurrentParticipantPerformer("架空生徒", PerformerType.STUDENT, "小3")
        val suggestions = repository.gradeProgressionSuggestions()
        assertEquals(listOf(GradeProgressionSuggestion(student, "小3", "小4")), suggestions)
        assertEquals("小3", dao.getPerformer(student)?.grade)

        repository.applyConfirmedGradeProgression(listOf(ConfirmedGradeChange(student, "小5")))
        val section = repository.createSection(recital, "自由な部名")
        repository.createPerformance(section, listOf(student), listOf(NewPiece("架空曲")))
        val finalized = repository.finalizedProgramSnapshot()
        repository.updatePerformer(checkNotNull(dao.getPerformer(student)).copy(name = "変更後の架空生徒", grade = "小6"))

        assertEquals("架空生徒", finalized.sections.single().performances.single().performers.single().name)
        assertEquals("小5", finalized.sections.single().performances.single().performers.single().grade)
        assertEquals("小6", dao.getPerformer(student)?.grade)
    }

    private suspend fun assertUpdateRejectedWithoutChanges(update: suspend () -> Unit) {
        val before = repository.observeProgram().first()
        val failure = runCatching { update() }.exceptionOrNull()
        assertTrue("Expected IllegalArgumentException, got $failure", failure is IllegalArgumentException)
        assertEquals(before, repository.observeProgram().first())
    }
}

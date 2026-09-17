package com.example.minnanohappyokai.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Local-only persistence gateway for the one current recital and reusable directories. */
class RecitalRepository(private val database: AppDatabase) {
    private val dao = database.recitalDao()

    /** Every emission is a single, current-recital-consistent view of the local database. */
    fun observeProgram(): Flow<ProgramSnapshot> = database.invalidationTracker.createFlow(
        "recitals", "sections", "performers", "recital_participants", "performances",
        "performance_members", "pieces", "composers", "composer_aliases",
        emitInitialState = true,
    ).map {
        database.withTransaction { readCurrentProgram() }
    }.distinctUntilChanged()

    fun observeActiveRecital() = dao.observeActiveRecital()
    fun observePerformers() = dao.observePerformers()
    fun observeCurrentParticipantPerformers() = dao.observeActiveParticipantPerformers()
    fun observeComposers() = dao.observeComposers()
    fun observeComposerAliases(composerId: Long) = dao.observeComposerAliases(composerId)

    /** Starts the only current recital. The unique Room index also protects concurrent writes. */
    suspend fun startRecital(name: String, dateEpochDay: Long? = null, venue: String = ""): Long =
        database.withTransaction {
            require(name.isNotBlank()) { "Recital name must not be blank" }
            check(dao.getActiveRecital() == null) { "A current recital already exists" }
            dao.insert(Recital(name = name, dateEpochDay = dateEpochDay, venue = venue))
        }

    suspend fun updateRecital(recital: Recital) {
        require(recital.name.isNotBlank()) { "Recital name must not be blank" }
        database.withTransaction {
            val active = requireActiveRecital()
            require(recital.id == active.id && recital.activeSlot == CURRENT_RECITAL_SLOT) {
                "Only the current recital can be edited"
            }
            check(dao.update(recital) == 1) { "The item no longer exists" }
        }
    }

    /**
     * Must only be called after the caller has confirmed successful PDF (and optional archive)
     * output. It removes current-recital data through FK cascades and keeps master directories.
     */
    suspend fun endCurrentRecital() = database.withTransaction {
        dao.delete(requireActiveRecital())
    }

    suspend fun createSection(recitalId: Long, name: String): Long = database.withTransaction {
        require(name.isNotBlank()) { "Section name must not be blank" }
        val active = requireActiveRecital()
        require(recitalId == active.id) { "Sections must belong to the current recital" }
        dao.insert(Section(recitalId = recitalId, name = name, displayOrder = dao.nextSectionOrder(recitalId)))
    }

    suspend fun updateSection(section: Section) = database.withTransaction {
        require(section.name.isNotBlank()) { "Section name must not be blank" }
        require(section.displayOrder >= 0) { "Order must not be negative" }
        requireActiveSection(section.id)
        check(dao.update(section) == 1) { "The item no longer exists" }
    }

    suspend fun deleteSection(section: Section) = database.withTransaction {
        requireActiveSection(section.id)
        dao.delete(section)
    }

    /** Adds a reusable directory entry only; it does not silently make somebody a participant. */
    suspend fun createPerformer(
        name: String,
        type: PerformerType,
        grade: String? = null,
        assignedTeacherId: Long? = null,
    ): Long = database.withTransaction {
        insertValidatedPerformer(name, type, grade, assignedTeacherId)
    }

    /** Explicit convenience for the "register this person for this year's recital" workflow. */
    suspend fun createCurrentParticipantPerformer(
        name: String,
        type: PerformerType,
        grade: String? = null,
        assignedTeacherId: Long? = null,
    ): Long = database.withTransaction {
        val active = requireActiveRecital()
        val performerId = insertValidatedPerformer(name, type, grade, assignedTeacherId)
        dao.insert(RecitalParticipant(active.id, performerId))
        performerId
    }

    suspend fun updatePerformer(performer: Performer) = database.withTransaction {
        require(dao.getPerformer(performer.id) != null) { "The item no longer exists" }
        validatePerformer(performer.name, performer.type, performer.grade, performer.assignedTeacherId)
        if (performer.type != PerformerType.TEACHER) {
            require(dao.countAssignedStudents(performer.id) == 0) {
                "A teacher assigned to students cannot be changed to a student"
            }
        }
        check(dao.update(performer) == 1) { "The item no longer exists" }
    }

    /** Never removes a directory entry while any active relationship still points to it. */
    suspend fun deletePerformer(performer: Performer): Boolean = database.withTransaction {
        require(dao.getPerformer(performer.id) != null) { "The item no longer exists" }
        if (
            dao.countMemberships(performer.id) > 0 ||
            dao.countParticipations(performer.id) > 0 ||
            dao.countAssignedStudents(performer.id) > 0
        ) {
            false
        } else {
            dao.delete(performer)
            true
        }
    }

    suspend fun addCurrentParticipant(performerId: Long) = database.withTransaction {
        val active = requireActiveRecital()
        require(dao.getPerformer(performerId) != null) { "Performer does not exist" }
        require(dao.isParticipant(active.id, performerId) == 0) { "Performer is already a participant" }
        dao.insert(RecitalParticipant(active.id, performerId))
    }

    /** Returns false when an existing program entry still uses the participant. */
    suspend fun removeCurrentParticipant(performerId: Long): Boolean = database.withTransaction {
        val active = requireActiveRecital()
        require(dao.isParticipant(active.id, performerId) == 1) { "Performer is not a participant" }
        if (dao.countMembershipsInRecital(active.id, performerId) > 0) {
            false
        } else {
            dao.delete(RecitalParticipant(active.id, performerId))
            true
        }
    }

    /**
     * Member order follows performerIds. Teacher-only entries default to the end; a new student
     * entry defaults before teacher-only entries. Later manual ordering is never rewritten.
     */
    suspend fun createPerformance(
        sectionId: Long,
        performerIds: List<Long>,
        pieces: List<NewPiece> = emptyList(),
    ): Long = database.withTransaction {
        val active = requireActiveSection(sectionId)
        val performers = requireCurrentParticipants(active.id, performerIds)
        pieces.forEach { require(it.title.isNotBlank()) { "Piece title must not be blank" } }
        val nextOrder = dao.nextPerformanceOrder(sectionId)
        val order = if (performers.all { it.type == PerformerType.TEACHER }) {
            nextOrder
        } else {
            dao.firstTeacherPerformanceOrder(sectionId) ?: nextOrder
        }
        dao.shiftPerformanceOrders(sectionId, order)
        val id = dao.insert(Performance(sectionId = sectionId, displayOrder = order))
        dao.insertMembers(performerIds.mapIndexed { index, performerId ->
            PerformanceMember(performanceId = id, performerId = performerId, displayOrder = index)
        })
        pieces.forEachIndexed { index, piece -> dao.insert(piece.toEntity(id, index)) }
        id
    }

    /** Saves a draft atomically and preserves its established section order. */
    suspend fun updatePerformanceProgram(
        performanceId: Long,
        performerIds: List<Long>,
        pieces: List<NewPiece>,
    ) = database.withTransaction {
        val active = requireActivePerformance(performanceId)
        requireCurrentParticipants(active.id, performerIds)
        pieces.forEach { require(it.title.isNotBlank()) { "Piece title must not be blank" } }
        dao.deleteMembers(performanceId)
        dao.insertMembers(performerIds.mapIndexed { index, performerId ->
            PerformanceMember(performanceId, performerId, index)
        })
        dao.deletePieces(performanceId)
        pieces.forEachIndexed { index, piece -> dao.insert(piece.toEntity(performanceId, index)) }
    }

    suspend fun updatePerformance(performance: Performance) = database.withTransaction {
        require(performance.displayOrder >= 0) { "Order must not be negative" }
        requireActivePerformance(performance.id)
        check(dao.update(performance) == 1) { "The item no longer exists" }
    }

    suspend fun deletePerformance(performance: Performance) = database.withTransaction {
        requireActivePerformance(performance.id)
        dao.delete(performance)
    }

    /** Adds, removes and orders members together, while keeping every member current-year valid. */
    suspend fun replacePerformanceMembers(performanceId: Long, performerIds: List<Long>) =
        database.withTransaction {
            val active = requireActivePerformance(performanceId)
            requireCurrentParticipants(active.id, performerIds)
            dao.deleteMembers(performanceId)
            dao.insertMembers(performerIds.mapIndexed { index, performerId ->
                PerformanceMember(performanceId = performanceId, performerId = performerId, displayOrder = index)
            })
        }

    suspend fun createPiece(performanceId: Long, piece: NewPiece): Long = database.withTransaction {
        requireActivePerformance(performanceId)
        require(piece.title.isNotBlank()) { "Piece title must not be blank" }
        dao.insert(piece.toEntity(performanceId, dao.nextPieceOrder(performanceId)))
    }

    suspend fun updatePiece(piece: Piece) = database.withTransaction {
        require(piece.title.isNotBlank()) { "Piece title must not be blank" }
        require(piece.displayOrder >= 0) { "Order must not be negative" }
        requireActivePerformance(piece.performanceId)
        check(dao.update(piece) == 1) { "The item no longer exists" }
    }

    suspend fun deletePiece(piece: Piece) = database.withTransaction {
        require(dao.getPiece(piece.id) != null) { "The item no longer exists" }
        requireActivePerformance(piece.performanceId)
        dao.delete(piece)
    }

    suspend fun createComposer(canonicalName: String): Long {
        require(canonicalName.isNotBlank()) { "Composer name must not be blank" }
        return dao.insert(Composer(canonicalName = canonicalName))
    }

    suspend fun updateComposer(composer: Composer) {
        require(composer.canonicalName.isNotBlank()) { "Composer name must not be blank" }
        check(dao.update(composer) == 1) { "The item no longer exists" }
    }

    // SET_NULL removes identity only; a Piece retains its exact program notation.
    suspend fun deleteComposer(composer: Composer) = dao.delete(composer)

    suspend fun createComposerAlias(composerId: Long, displayName: String): Long {
        require(displayName.isNotBlank()) { "Composer notation must not be blank" }
        return dao.insert(ComposerAlias(composerId = composerId, displayName = displayName))
    }

    suspend fun updateComposerAlias(alias: ComposerAlias) {
        require(alias.displayName.isNotBlank()) { "Composer notation must not be blank" }
        check(dao.update(alias) == 1) { "The item no longer exists" }
    }

    suspend fun deleteComposerAlias(alias: ComposerAlias) = dao.delete(alias)

    suspend fun reorderSections(recitalId: Long, orderedIds: List<Long>) = database.withTransaction {
        val active = requireActiveRecital()
        require(recitalId == active.id) { "Only current-recital sections can be ordered" }
        requireSameIds(dao.sectionIds(recitalId), orderedIds)
        orderedIds.forEachIndexed { index, id -> dao.setSectionOrder(id, index) }
    }

    suspend fun reorderPerformances(sectionId: Long, orderedIds: List<Long>) = database.withTransaction {
        requireActiveSection(sectionId)
        requireSameIds(dao.performanceIds(sectionId), orderedIds)
        orderedIds.forEachIndexed { index, id -> dao.setPerformanceOrder(id, index) }
    }

    suspend fun reorderPieces(performanceId: Long, orderedIds: List<Long>) = database.withTransaction {
        requireActivePerformance(performanceId)
        requireSameIds(dao.pieceIds(performanceId), orderedIds)
        orderedIds.forEachIndexed { index, id -> dao.setPieceOrder(id, index) }
    }

    /** Returns suggested changes only. Nothing is written until [applyConfirmedGradeProgression]. */
    suspend fun gradeProgressionSuggestions(): List<GradeProgressionSuggestion> = database.withTransaction {
        dao.getAllPerformers().asSequence()
            .filter { it.type == PerformerType.STUDENT }
            .mapNotNull { performer ->
                GradeProgression.nextGrade(performer.grade)?.let { next ->
                    GradeProgressionSuggestion(performer.id, performer.grade!!, next)
                }
            }
            .toList()
    }

    /** Applies the user-reviewed list atomically; callers may replace any suggested target grade. */
    suspend fun applyConfirmedGradeProgression(changes: List<ConfirmedGradeChange>) = database.withTransaction {
        require(changes.map { it.performerId }.distinct().size == changes.size) {
            "A performer can only appear once in a grade update"
        }
        changes.forEach { change ->
            require(change.grade.isNotBlank()) { "Grade must not be blank" }
            val performer = dao.getPerformer(change.performerId)
            require(performer?.type == PerformerType.STUDENT) { "Only students have current grades" }
            dao.update(performer.copy(grade = change.grade))
        }
    }

    /** A value snapshot for the future PDF/archive exporter, independent of later directory edits. */
    suspend fun finalizedProgramSnapshot(): FinalizedProgramSnapshot = database.withTransaction {
        readCurrentProgram().toFinalizedProgramSnapshot()
    }

    private suspend fun readCurrentProgram() = ProgramSnapshot(
        activeRecital = dao.getActiveRecital(),
        sections = dao.getActiveSections(),
        performers = dao.getAllPerformers(),
        participants = dao.getActiveParticipants(),
        performances = dao.getActivePerformances(),
        members = dao.getActiveMembers(),
        pieces = dao.getActivePieces(),
        composers = dao.getAllComposers(),
        aliases = dao.getAllAliases(),
    )

    private suspend fun insertValidatedPerformer(
        name: String,
        type: PerformerType,
        grade: String?,
        assignedTeacherId: Long?,
    ): Long {
        validatePerformer(name, type, grade, assignedTeacherId)
        return dao.insert(Performer(name = name, type = type, grade = grade, assignedTeacherId = assignedTeacherId))
    }

    private suspend fun validatePerformer(
        name: String,
        type: PerformerType,
        grade: String?,
        assignedTeacherId: Long?,
    ) {
        require(name.isNotBlank()) { "Performer name must not be blank" }
        if (type == PerformerType.TEACHER) {
            require(grade == null) { "Teachers do not have a current grade" }
            require(assignedTeacherId == null) { "Teachers do not have an assigned teacher" }
        }
        if (assignedTeacherId != null) {
            require(type == PerformerType.STUDENT) { "Only students can have an assigned teacher" }
            require(dao.getPerformer(assignedTeacherId)?.type == PerformerType.TEACHER) {
                "Assigned teacher must be a registered teacher"
            }
        }
    }

    private suspend fun requireActiveRecital(): Recital =
        checkNotNull(dao.getActiveRecital()) { "There is no current recital" }

    private suspend fun requireActiveSection(sectionId: Long): Recital {
        val active = requireActiveRecital()
        val section = checkNotNull(dao.getSection(sectionId)) { "Section does not exist" }
        require(section.recitalId == active.id) { "Section is not part of the current recital" }
        return active
    }

    private suspend fun requireActivePerformance(performanceId: Long): Recital {
        val performance = checkNotNull(dao.getPerformance(performanceId)) { "Performance does not exist" }
        return requireActiveSection(performance.sectionId)
    }

    private suspend fun requireCurrentParticipants(recitalId: Long, ids: List<Long>): List<Performer> {
        require(ids.isNotEmpty()) { "At least one performer is required" }
        require(ids.distinct().size == ids.size) { "Duplicate performers are not allowed" }
        val performers = dao.getPerformers(ids)
        require(performers.size == ids.size) { "Every performer must exist" }
        require(ids.all { dao.isParticipant(recitalId, it) == 1 }) {
            "Every performer must be selected for the current recital"
        }
        return performers
    }

    private fun requireSameIds(existingIds: List<Long>, orderedIds: List<Long>) {
        require(orderedIds.size == existingIds.size && orderedIds.toSet() == existingIds.toSet()) {
            "Order must contain every child of this parent exactly once"
        }
    }
}

data class NewPiece(
    val title: String,
    val composerId: Long? = null,
    val composerDisplayText: String = "",
) {
    internal fun toEntity(performanceId: Long, displayOrder: Int) = Piece(
        performanceId = performanceId,
        title = title,
        displayOrder = displayOrder,
        composerId = composerId,
        composerDisplayText = composerDisplayText,
    )
}

data class GradeProgressionSuggestion(
    val performerId: Long,
    val currentGrade: String,
    val suggestedGrade: String,
)

data class ConfirmedGradeChange(val performerId: Long, val grade: String)

object GradeProgression {
    private val nextGrades = mapOf(
        "年少" to "年中", "年中" to "年長", "年長" to "小1",
        "小1" to "小2", "小2" to "小3", "小3" to "小4", "小4" to "小5",
        "小5" to "小6", "小6" to "中1", "中1" to "中2", "中2" to "中3",
        "中3" to "高1", "高1" to "高2", "高2" to "高3",
    )

    fun nextGrade(currentGrade: String?): String? = currentGrade?.let(nextGrades::get)
}

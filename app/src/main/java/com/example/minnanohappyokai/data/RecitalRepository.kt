package com.example.minnanohappyokai.data

import androidx.room.withTransaction

/** Local persistence only. Compound edits either commit together or leave the program unchanged. */
class RecitalRepository(private val database: AppDatabase) {
    private val dao = database.recitalDao()

    fun observeRecitals() = dao.observeRecitals()
    fun observeRecital(id: Long) = dao.observeRecital(id)
    fun observeSections(recitalId: Long) = dao.observeSections(recitalId)
    fun observePerformers() = dao.observePerformers()
    fun observePerformances(sectionId: Long) = dao.observePerformances(sectionId)
    fun observeMembers(performanceId: Long) = dao.observeMembers(performanceId)
    fun observePieces(performanceId: Long) = dao.observePieces(performanceId)
    fun observeComposers() = dao.observeComposers()
    fun observeComposerAliases(composerId: Long) = dao.observeComposerAliases(composerId)

    suspend fun createRecital(name: String, dateEpochDay: Long? = null, venue: String = ""): Long {
        require(name.isNotBlank()) { "Recital name must not be blank" }
        return dao.insert(Recital(name = name, dateEpochDay = dateEpochDay, venue = venue))
    }

    suspend fun updateRecital(recital: Recital) {
        require(recital.name.isNotBlank()) { "Recital name must not be blank" }
        dao.update(recital)
    }

    // Deleting an event intentionally removes its sections, performances, members and pieces.
    suspend fun deleteRecital(recital: Recital) = dao.delete(recital)

    suspend fun createSection(recitalId: Long, name: String): Long = database.withTransaction {
        require(name.isNotBlank()) { "Section name must not be blank" }
        dao.insert(Section(recitalId = recitalId, name = name, displayOrder = dao.nextSectionOrder(recitalId)))
    }

    suspend fun updateSection(section: Section) {
        require(section.name.isNotBlank()) { "Section name must not be blank" }
        require(section.displayOrder >= 0) { "Order must not be negative" }
        dao.update(section)
    }

    suspend fun deleteSection(section: Section) = dao.delete(section)

    suspend fun createPerformer(name: String, type: PerformerType, grade: String? = null): Long {
        require(name.isNotBlank()) { "Performer name must not be blank" }
        return dao.insert(Performer(name = name, type = type, grade = grade))
    }

    suspend fun updatePerformer(performer: Performer) {
        require(performer.name.isNotBlank()) { "Performer name must not be blank" }
        dao.update(performer)
    }

    /** Returns false while the performer belongs to any performance; never removes those links. */
    suspend fun deletePerformer(performer: Performer): Boolean = database.withTransaction {
        if (dao.countMemberships(performer.id) > 0) {
            false
        } else {
            dao.delete(performer)
            true
        }
    }

    /**
     * Member order follows performerIds. Teacher-only entries default to the end; a new student
     * entry defaults to before the first teacher-only entry. Later manual ordering is preserved.
     * Empty piece lists are allowed while a program entry is being prepared.
     */
    suspend fun createPerformance(
        sectionId: Long,
        performerIds: List<Long>,
        pieces: List<NewPiece> = emptyList(),
    ): Long = database.withTransaction {
        val performers = requirePerformers(performerIds)
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

    suspend fun updatePerformance(performance: Performance) {
        require(performance.displayOrder >= 0) { "Order must not be negative" }
        dao.update(performance)
    }

    suspend fun deletePerformance(performance: Performance) = dao.delete(performance)

    /** Adds, removes and orders members together, without leaving an empty performance. */
    suspend fun replacePerformanceMembers(performanceId: Long, performerIds: List<Long>) {
        database.withTransaction {
            require(dao.getPerformance(performanceId) != null) { "Performance does not exist" }
            requirePerformers(performerIds)
            dao.deleteMembers(performanceId)
            dao.insertMembers(performerIds.mapIndexed { index, performerId ->
                PerformanceMember(performanceId = performanceId, performerId = performerId, displayOrder = index)
            })
        }
    }

    suspend fun createPiece(performanceId: Long, piece: NewPiece): Long = database.withTransaction {
        require(piece.title.isNotBlank()) { "Piece title must not be blank" }
        dao.insert(piece.toEntity(performanceId, dao.nextPieceOrder(performanceId)))
    }

    suspend fun updatePiece(piece: Piece) {
        require(piece.title.isNotBlank()) { "Piece title must not be blank" }
        require(piece.displayOrder >= 0) { "Order must not be negative" }
        dao.update(piece)
    }

    suspend fun deletePiece(piece: Piece) = dao.delete(piece)

    suspend fun createComposer(canonicalName: String): Long {
        require(canonicalName.isNotBlank()) { "Composer name must not be blank" }
        return dao.insert(Composer(canonicalName = canonicalName))
    }

    suspend fun updateComposer(composer: Composer) {
        require(composer.canonicalName.isNotBlank()) { "Composer name must not be blank" }
        dao.update(composer)
    }

    // SET_NULL removes only identity links; each piece retains its own program notation.
    suspend fun deleteComposer(composer: Composer) = dao.delete(composer)

    suspend fun createComposerAlias(composerId: Long, displayName: String): Long {
        require(displayName.isNotBlank()) { "Composer notation must not be blank" }
        return dao.insert(ComposerAlias(composerId = composerId, displayName = displayName))
    }

    suspend fun updateComposerAlias(alias: ComposerAlias) {
        require(alias.displayName.isNotBlank()) { "Composer notation must not be blank" }
        dao.update(alias)
    }

    suspend fun deleteComposerAlias(alias: ComposerAlias) = dao.delete(alias)

    suspend fun reorderSections(recitalId: Long, orderedIds: List<Long>) {
        database.withTransaction {
            requireSameIds(dao.sectionIds(recitalId), orderedIds)
            orderedIds.forEachIndexed { index, id -> dao.setSectionOrder(id, index) }
        }
    }

    suspend fun reorderPerformances(sectionId: Long, orderedIds: List<Long>) {
        database.withTransaction {
            requireSameIds(dao.performanceIds(sectionId), orderedIds)
            orderedIds.forEachIndexed { index, id -> dao.setPerformanceOrder(id, index) }
        }
    }

    suspend fun reorderPieces(performanceId: Long, orderedIds: List<Long>) {
        database.withTransaction {
            requireSameIds(dao.pieceIds(performanceId), orderedIds)
            orderedIds.forEachIndexed { index, id -> dao.setPieceOrder(id, index) }
        }
    }

    private suspend fun requirePerformers(ids: List<Long>): List<Performer> {
        require(ids.isNotEmpty()) { "At least one performer is required" }
        require(ids.distinct().size == ids.size) { "Duplicate performers are not allowed" }
        return dao.getPerformers(ids).also {
            require(it.size == ids.size) { "Every performer must exist" }
        }
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

package com.example.minnanohappyokai.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecitalDao {
    /** All recital-scoped reads below deliberately exclude v1 migration leftovers. */
    @Query("SELECT * FROM recitals WHERE activeSlot = :slot LIMIT 1")
    suspend fun getActiveRecital(slot: Int = CURRENT_RECITAL_SLOT): Recital?

    @Query("SELECT * FROM recitals WHERE activeSlot = :slot LIMIT 1")
    fun observeActiveRecital(slot: Int = CURRENT_RECITAL_SLOT): Flow<Recital?>

    @Query("""
        SELECT s.* FROM sections s
        INNER JOIN recitals r ON r.id = s.recitalId
        WHERE r.activeSlot = :slot
        ORDER BY s.displayOrder, s.id
    """)
    suspend fun getActiveSections(slot: Int = CURRENT_RECITAL_SLOT): List<Section>

    @Query("""
        SELECT p.* FROM performances p
        INNER JOIN sections s ON s.id = p.sectionId
        INNER JOIN recitals r ON r.id = s.recitalId
        WHERE r.activeSlot = :slot
        ORDER BY p.sectionId, p.displayOrder, p.id
    """)
    suspend fun getActivePerformances(slot: Int = CURRENT_RECITAL_SLOT): List<Performance>

    @Query("""
        SELECT m.* FROM performance_members m
        INNER JOIN performances p ON p.id = m.performanceId
        INNER JOIN sections s ON s.id = p.sectionId
        INNER JOIN recitals r ON r.id = s.recitalId
        WHERE r.activeSlot = :slot
        ORDER BY m.performanceId, m.displayOrder, m.performerId
    """)
    suspend fun getActiveMembers(slot: Int = CURRENT_RECITAL_SLOT): List<PerformanceMember>

    @Query("""
        SELECT piece.* FROM pieces piece
        INNER JOIN performances p ON p.id = piece.performanceId
        INNER JOIN sections s ON s.id = p.sectionId
        INNER JOIN recitals r ON r.id = s.recitalId
        WHERE r.activeSlot = :slot
        ORDER BY piece.performanceId, piece.displayOrder, piece.id
    """)
    suspend fun getActivePieces(slot: Int = CURRENT_RECITAL_SLOT): List<Piece>

    @Query("""
        SELECT participant.* FROM recital_participants participant
        INNER JOIN recitals r ON r.id = participant.recitalId
        WHERE r.activeSlot = :slot
        ORDER BY participant.performerId
    """)
    suspend fun getActiveParticipants(slot: Int = CURRENT_RECITAL_SLOT): List<RecitalParticipant>

    @Query("SELECT * FROM performers ORDER BY name, id")
    suspend fun getAllPerformers(): List<Performer>

    @Query("SELECT * FROM composers ORDER BY canonicalName, id")
    suspend fun getAllComposers(): List<Composer>

    @Query("SELECT * FROM composer_aliases ORDER BY composerId, displayName, id")
    suspend fun getAllAliases(): List<ComposerAlias>

    @Query("SELECT * FROM recitals WHERE id = :id")
    suspend fun getRecital(id: Long): Recital?

    @Query("SELECT * FROM sections WHERE id = :id")
    suspend fun getSection(id: Long): Section?

    @Query("SELECT * FROM performers WHERE id = :id")
    suspend fun getPerformer(id: Long): Performer?

    @Query("SELECT * FROM performances WHERE id = :id")
    suspend fun getPerformance(id: Long): Performance?

    @Query("SELECT * FROM pieces WHERE id = :id")
    suspend fun getPiece(id: Long): Piece?

    @Query("SELECT * FROM performers ORDER BY name, id")
    fun observePerformers(): Flow<List<Performer>>

    @Query("""
        SELECT person.* FROM performers person
        INNER JOIN recital_participants participant ON participant.performerId = person.id
        INNER JOIN recitals r ON r.id = participant.recitalId
        WHERE r.activeSlot = :slot
        ORDER BY person.name, person.id
    """)
    fun observeActiveParticipantPerformers(slot: Int = CURRENT_RECITAL_SLOT): Flow<List<Performer>>

    @Query("SELECT * FROM composers ORDER BY canonicalName, id")
    fun observeComposers(): Flow<List<Composer>>

    @Query("SELECT * FROM composer_aliases WHERE composerId = :composerId ORDER BY displayName, id")
    fun observeComposerAliases(composerId: Long): Flow<List<ComposerAlias>>

    @Insert suspend fun insert(recital: Recital): Long
    @Insert suspend fun insert(section: Section): Long
    @Insert suspend fun insert(performer: Performer): Long
    @Insert suspend fun insert(participant: RecitalParticipant)
    @Insert suspend fun insert(performance: Performance): Long
    @Insert suspend fun insertMembers(members: List<PerformanceMember>)
    @Insert suspend fun insert(piece: Piece): Long
    @Insert suspend fun insert(composer: Composer): Long
    @Insert suspend fun insert(alias: ComposerAlias): Long

    @Update suspend fun update(recital: Recital): Int
    @Update suspend fun update(section: Section): Int
    @Update suspend fun update(performer: Performer): Int
    @Update suspend fun update(performance: Performance): Int
    @Update suspend fun update(piece: Piece): Int
    @Update suspend fun update(composer: Composer): Int
    @Update suspend fun update(alias: ComposerAlias): Int

    @Delete suspend fun delete(recital: Recital)
    @Delete suspend fun delete(section: Section)
    @Delete suspend fun delete(performer: Performer)
    @Delete suspend fun delete(participant: RecitalParticipant)
    @Delete suspend fun delete(performance: Performance)
    @Delete suspend fun delete(piece: Piece)
    @Delete suspend fun delete(composer: Composer)
    @Delete suspend fun delete(alias: ComposerAlias)

    @Query("DELETE FROM performance_members WHERE performanceId = :performanceId")
    suspend fun deleteMembers(performanceId: Long)

    @Query("DELETE FROM pieces WHERE performanceId = :performanceId")
    suspend fun deletePieces(performanceId: Long)

    @Query("SELECT * FROM performers WHERE id IN (:ids)")
    suspend fun getPerformers(ids: List<Long>): List<Performer>

    @Query("SELECT COUNT(*) FROM performance_members WHERE performerId = :performerId")
    suspend fun countMemberships(performerId: Long): Int

    @Query("SELECT COUNT(*) FROM recital_participants WHERE performerId = :performerId")
    suspend fun countParticipations(performerId: Long): Int

    @Query("SELECT COUNT(*) FROM performers WHERE assignedTeacherId = :performerId")
    suspend fun countAssignedStudents(performerId: Long): Int

    @Query("""
        SELECT COUNT(*) FROM performance_members m
        INNER JOIN performances p ON p.id = m.performanceId
        INNER JOIN sections s ON s.id = p.sectionId
        WHERE s.recitalId = :recitalId AND m.performerId = :performerId
    """)
    suspend fun countMembershipsInRecital(recitalId: Long, performerId: Long): Int

    @Query("SELECT COUNT(*) FROM recital_participants WHERE recitalId = :recitalId AND performerId = :performerId")
    suspend fun isParticipant(recitalId: Long, performerId: Long): Int

    @Query("SELECT COALESCE(MAX(displayOrder) + 1, 0) FROM sections WHERE recitalId = :recitalId")
    suspend fun nextSectionOrder(recitalId: Long): Int

    @Query("SELECT COALESCE(MAX(displayOrder) + 1, 0) FROM performances WHERE sectionId = :sectionId")
    suspend fun nextPerformanceOrder(sectionId: Long): Int

    @Query("SELECT COALESCE(MAX(displayOrder) + 1, 0) FROM pieces WHERE performanceId = :performanceId")
    suspend fun nextPieceOrder(performanceId: Long): Int

    @Query("""
        SELECT MIN(p.displayOrder) FROM performances p
        WHERE p.sectionId = :sectionId
          AND EXISTS (SELECT 1 FROM performance_members m WHERE m.performanceId = p.id)
          AND NOT EXISTS (
              SELECT 1 FROM performance_members m
              JOIN performers person ON person.id = m.performerId
              WHERE m.performanceId = p.id AND person.type != 'TEACHER'
          )
    """)
    suspend fun firstTeacherPerformanceOrder(sectionId: Long): Int?

    @Query("UPDATE performances SET displayOrder = displayOrder + 1 WHERE sectionId = :sectionId AND displayOrder >= :fromOrder")
    suspend fun shiftPerformanceOrders(sectionId: Long, fromOrder: Int)

    @Query("SELECT id FROM sections WHERE recitalId = :recitalId ORDER BY displayOrder, id")
    suspend fun sectionIds(recitalId: Long): List<Long>

    @Query("SELECT id FROM performances WHERE sectionId = :sectionId ORDER BY displayOrder, id")
    suspend fun performanceIds(sectionId: Long): List<Long>

    @Query("SELECT id FROM pieces WHERE performanceId = :performanceId ORDER BY displayOrder, id")
    suspend fun pieceIds(performanceId: Long): List<Long>

    @Query("UPDATE sections SET displayOrder = :order WHERE id = :id")
    suspend fun setSectionOrder(id: Long, order: Int)

    @Query("UPDATE performances SET displayOrder = :order WHERE id = :id")
    suspend fun setPerformanceOrder(id: Long, order: Int)

    @Query("UPDATE pieces SET displayOrder = :order WHERE id = :id")
    suspend fun setPieceOrder(id: Long, order: Int)
}

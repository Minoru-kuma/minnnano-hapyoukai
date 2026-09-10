package com.example.minnanohappyokai.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RecitalDao {
    // The repository reads these together inside one transaction for a coherent screen state.
    @Query("SELECT * FROM recitals ORDER BY dateEpochDay IS NULL, dateEpochDay, id")
    suspend fun getAllRecitals(): List<Recital>

    @Query("SELECT * FROM sections ORDER BY recitalId, displayOrder, id")
    suspend fun getAllSections(): List<Section>

    @Query("SELECT * FROM performers ORDER BY name, id")
    suspend fun getAllPerformers(): List<Performer>

    @Query("SELECT * FROM performances ORDER BY sectionId, displayOrder, id")
    suspend fun getAllPerformances(): List<Performance>

    @Query("SELECT * FROM performance_members ORDER BY performanceId, displayOrder, performerId")
    suspend fun getAllMembers(): List<PerformanceMember>

    @Query("SELECT * FROM pieces ORDER BY performanceId, displayOrder, id")
    suspend fun getAllPieces(): List<Piece>

    @Query("SELECT * FROM composers ORDER BY canonicalName, id")
    suspend fun getAllComposers(): List<Composer>

    @Query("SELECT * FROM composer_aliases ORDER BY composerId, displayName, id")
    suspend fun getAllAliases(): List<ComposerAlias>

    @Query("SELECT * FROM recitals ORDER BY dateEpochDay IS NULL, dateEpochDay, id")
    fun observeRecitals(): Flow<List<Recital>>

    @Query("SELECT * FROM recitals WHERE id = :id")
    fun observeRecital(id: Long): Flow<Recital?>

    @Query("SELECT * FROM sections WHERE recitalId = :recitalId ORDER BY displayOrder, id")
    fun observeSections(recitalId: Long): Flow<List<Section>>

    @Query("SELECT * FROM performers ORDER BY name, id")
    fun observePerformers(): Flow<List<Performer>>

    @Query("SELECT * FROM performances WHERE sectionId = :sectionId ORDER BY displayOrder, id")
    fun observePerformances(sectionId: Long): Flow<List<Performance>>

    @Query("SELECT * FROM performance_members WHERE performanceId = :performanceId ORDER BY displayOrder, performerId")
    fun observeMembers(performanceId: Long): Flow<List<PerformanceMember>>

    @Query("SELECT * FROM pieces WHERE performanceId = :performanceId ORDER BY displayOrder, id")
    fun observePieces(performanceId: Long): Flow<List<Piece>>

    @Query("SELECT * FROM composers ORDER BY canonicalName, id")
    fun observeComposers(): Flow<List<Composer>>

    @Query("SELECT * FROM composer_aliases WHERE composerId = :composerId ORDER BY displayName, id")
    fun observeComposerAliases(composerId: Long): Flow<List<ComposerAlias>>

    @Insert suspend fun insert(recital: Recital): Long
    @Insert suspend fun insert(section: Section): Long
    @Insert suspend fun insert(performer: Performer): Long
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

    @Query("SELECT * FROM performances WHERE id = :id")
    suspend fun getPerformance(id: Long): Performance?

    @Query("SELECT COUNT(*) FROM performance_members WHERE performerId = :performerId")
    suspend fun countMemberships(performerId: Long): Int

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

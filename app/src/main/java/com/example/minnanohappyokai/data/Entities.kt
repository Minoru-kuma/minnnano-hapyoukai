package com.example.minnanohappyokai.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.TypeConverter

@Entity(tableName = "recitals")
data class Recital(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val dateEpochDay: Long? = null,
    val venue: String = "",
)

@Entity(
    tableName = "sections",
    foreignKeys = [ForeignKey(
        entity = Recital::class, parentColumns = ["id"], childColumns = ["recitalId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["recitalId", "displayOrder"])],
)
data class Section(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recitalId: Long,
    val name: String,
    val displayOrder: Int,
)

enum class PerformerType { STUDENT, TEACHER }

@Entity(tableName = "performers")
data class Performer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: PerformerType,
    val grade: String? = null,
)

@Entity(
    tableName = "performances",
    foreignKeys = [ForeignKey(
        entity = Section::class, parentColumns = ["id"], childColumns = ["sectionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["sectionId", "displayOrder"])],
)
data class Performance(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectionId: Long,
    val displayOrder: Int,
)

@Entity(
    tableName = "performance_members",
    primaryKeys = ["performanceId", "performerId"],
    foreignKeys = [
        ForeignKey(
            entity = Performance::class, parentColumns = ["id"],
            childColumns = ["performanceId"], onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Performer::class, parentColumns = ["id"],
            childColumns = ["performerId"], onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index(value = ["performanceId", "displayOrder"]), Index("performerId")],
)
data class PerformanceMember(
    val performanceId: Long,
    val performerId: Long,
    val displayOrder: Int,
)

@Entity(tableName = "composers")
data class Composer(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val canonicalName: String,
)

@Entity(
    tableName = "composer_aliases",
    foreignKeys = [ForeignKey(
        entity = Composer::class, parentColumns = ["id"], childColumns = ["composerId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index(value = ["composerId", "displayName"], unique = true)],
)
data class ComposerAlias(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val composerId: Long,
    val displayName: String,
)

@Entity(
    tableName = "pieces",
    foreignKeys = [
        ForeignKey(
            entity = Performance::class, parentColumns = ["id"],
            childColumns = ["performanceId"], onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Composer::class, parentColumns = ["id"],
            childColumns = ["composerId"], onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index(value = ["performanceId", "displayOrder"]), Index("composerId")],
)
data class Piece(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val performanceId: Long,
    val title: String,
    val displayOrder: Int,
    val composerId: Long? = null,
    // Program notation belongs to this entry, independently of composer identity/aliases.
    val composerDisplayText: String = "",
)

class DatabaseConverters {
    @TypeConverter
    fun performerTypeToString(value: PerformerType): String = value.name

    @TypeConverter
    fun stringToPerformerType(value: String): PerformerType = PerformerType.valueOf(value)
}

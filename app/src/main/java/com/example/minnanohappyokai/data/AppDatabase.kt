package com.example.minnanohappyokai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        Recital::class, Section::class, Performer::class, RecitalParticipant::class,
        Performance::class, PerformanceMember::class, Piece::class, Composer::class,
        ComposerAlias::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recitalDao(): RecitalDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /**
         * v1 allowed several recitals. v2 keeps only the most recently created one (maximum ID)
         * as current and deletes all other recital records with their recital-scoped children.
         * Performer, Composer and ComposerAlias are independent master data and remain intact.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE recitals ADD COLUMN activeSlot INTEGER NOT NULL " +
                        "DEFAULT $CURRENT_RECITAL_SLOT",
                )
                db.execSQL(
                    "DELETE FROM recitals WHERE id != (SELECT MAX(id) FROM recitals)",
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_recitals_activeSlot " +
                        "ON recitals(activeSlot)",
                )

                db.execSQL(
                    "ALTER TABLE performers ADD COLUMN assignedTeacherId INTEGER " +
                        "REFERENCES performers(id) ON DELETE RESTRICT",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_performers_assignedTeacherId " +
                        "ON performers(assignedTeacherId)",
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS recital_participants (
                        recitalId INTEGER NOT NULL,
                        performerId INTEGER NOT NULL,
                        PRIMARY KEY(recitalId, performerId),
                        FOREIGN KEY(recitalId) REFERENCES recitals(id) ON DELETE CASCADE,
                        FOREIGN KEY(performerId) REFERENCES performers(id) ON DELETE RESTRICT
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_recital_participants_performerId " +
                        "ON recital_participants(performerId)",
                )
                // v1 had no participant selection. Preserve working entries by selecting only
                // the people already used by the chosen current recital's performances.
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO recital_participants(recitalId, performerId)
                    SELECT r.id, member.performerId
                    FROM recitals r
                    INNER JOIN sections section ON section.recitalId = r.id
                    INNER JOIN performances performance ON performance.sectionId = section.id
                    INNER JOIN performance_members member ON member.performanceId = performance.id
                    WHERE r.activeSlot = $CURRENT_RECITAL_SLOT
                    """.trimIndent(),
                )
            }
        }

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "recitals.db",
            ).addMigrations(MIGRATION_1_2).build().also { instance = it }
        }
    }
}

/** Create once at the application boundary and pass the repository to ViewModels. */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val repository: RecitalRepository by lazy {
        RecitalRepository(AppDatabase.getInstance(applicationContext))
    }
}

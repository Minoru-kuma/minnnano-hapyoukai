package com.example.minnanohappyokai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        Recital::class, Section::class, Performer::class, Performance::class,
        PerformanceMember::class, Piece::class, Composer::class, ComposerAlias::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun recitalDao(): RecitalDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "recitals.db",
            ).build().also { instance = it }
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

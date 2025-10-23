package com.picpose.bestphotographyapp.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        FavoritePrompt::class,  // existing
        StatsEntity::class      // ✅ add this new entity
    ],
    version = 2, // ✅ increment version
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun favoriteDao(): FavoritePromptDao
    abstract fun statsDao(): StatsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "picpose_db"
                )
                    .fallbackToDestructiveMigration() // ✅ wipes and rebuilds clean DB automatically
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

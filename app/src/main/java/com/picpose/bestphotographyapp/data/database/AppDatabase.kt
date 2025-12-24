package com.picpose.bestphotographyapp.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.picpose.bestphotographyapp.data.database.dao.EngagementDao
import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity

@Database(
    entities = [
        FavoritePrompt::class,
        LikedPrompt::class,
        StatsEntity::class,
        EngagementEntity::class // ✅ ADD THIS
    ],
    version = 3, // 🔥 VERY IMPORTANT — incremented
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun favoriteDao(): FavoritePromptDao
    abstract fun likedPromptDao(): LikedPromptDao
    abstract fun statsDao(): StatsDao
    abstract fun engagementDao(): EngagementDao

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
                    // ✅ DEV SAFE: wipes old DB automatically
                    .fallbackToDestructiveMigration()
                    .build()

                INSTANCE = instance
                instance
            }
        }
    }
}

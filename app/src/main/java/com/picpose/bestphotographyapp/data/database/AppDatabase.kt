package com.picpose.bestphotographyapp.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.picpose.bestphotographyapp.data.database.dao.EngagementDao
import com.picpose.bestphotographyapp.data.database.entities.EngagementEntity

@Database(
    entities = [
        FavoritePrompt::class,
        LikedPrompt::class,
        StatsEntity::class,
        EngagementEntity::class
    ],
    version = 2, // ✅ Version 1 for fresh development
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
                    // ✅ DEVELOPMENT: Destructive migration for easy testing
                    .fallbackToDestructiveMigration()
                    // ✅ Optional: Add callbacks for debugging
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            android.util.Log.d("AppDatabase", "Database created")
                        }

                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            android.util.Log.d("AppDatabase", "Database opened")
                        }
                    })
                    .build()

                INSTANCE = instance
                instance
            }
        }

        // Helper function for testing/development
        fun destroyInstance() {
            INSTANCE = null
        }
    }
}
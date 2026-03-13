/**
 * ---
 * File: AppDatabase.kt
 * Layer: Data (Room)
 * Project: PicPose
 *
 * Purpose:
 * Room database definition for PicPose local persistence. It stores lightweight
 * engagement, favorites, and quick-stat tables that survive process death.
 *
 * Interactions:
 * Repositories obtain DAO instances from this database and use them as the
 * local data source in the MVVM chain.
 *
 * Data Flow:
 * ViewModel -> Repository -> AppDatabase -> DAO -> Entity table -> Flow back to UI
 *
 * Maintainer Notes:
 * - Add real migrations when schema changes must preserve user data.
 * - TODO: Export schema history to make future Room migrations safer.
 * ---
 */

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
/**
 * Central Room database holder.
 *
 * This class should stay focused on table registration and DAO exposure. Query
 * details belong in DAO interfaces, and merge/business rules belong in repositories.
 */
abstract class AppDatabase : RoomDatabase() {

    /** Stores favorited prompt ids and timestamps for bookmark-style features. */
    abstract fun favoriteDao(): FavoritePromptDao
    /** Stores locally liked prompt ids for engagement reconciliation. */
    abstract fun likedPromptDao(): LikedPromptDao
    /** Stores the single quick-stats snapshot rendered on Home. */
    abstract fun statsDao(): StatsDao
    /** Stores local engagement counters and flags shared across prompt flows. */
    abstract fun engagementDao(): EngagementDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                // A singleton avoids opening multiple Room instances for the same file.
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

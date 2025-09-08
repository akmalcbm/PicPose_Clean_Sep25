package com.picpose.bestphotographyapp.data.database

import android.content.Context
import androidx.room.*
import androidx.room.Room
import com.picpose.bestphotographyapp.data.models.AIPrompt

// Favorite entity
@Entity(tableName = "favorite_prompts")
data class FavoritePrompt(
    @PrimaryKey val id: String,
    val promptId: String,
    val title: String,
    val shortPrompt: String,
    val fullPrompt: String,
    val imageUrl: String,
    val category: String,
    val likes: Int,
    val isPopular: Boolean,
    val tags: List<String> = emptyList(),
    val dateAdded: Long = System.currentTimeMillis(),
    val favoritedAt: Long = System.currentTimeMillis()
)

// DAO
@Dao
interface FavoritePromptDao {
    @Query("SELECT * FROM favorite_prompts ORDER BY favoritedAt DESC")
    suspend fun getAllFavorites(): List<FavoritePrompt>

    @Query("SELECT * FROM favorite_prompts WHERE promptId = :promptId")
    suspend fun getFavoriteById(promptId: String): FavoritePrompt?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToFavorites(favorite: FavoritePrompt)

    @Query("DELETE FROM favorite_prompts WHERE promptId = :promptId")
    suspend fun removeFromFavorites(promptId: String)

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_prompts WHERE promptId = :promptId)")
    suspend fun isFavorite(promptId: String): Boolean

    @Query("SELECT COUNT(*) FROM favorite_prompts")
    suspend fun getFavoriteCount(): Int
}

// Database
@Database(
    entities = [FavoritePrompt::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class) // Add this line
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoritePromptDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "picpose_database"
                ).fallbackToDestructiveMigration() // Add this for development
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

// Extension functions
fun FavoritePrompt.toAIPrompt() = AIPrompt(
    id = promptId,
    title = title,
    shortPrompt = shortPrompt,
    fullPrompt = fullPrompt,
    imageUrl = imageUrl,
    category = category,
    tags = emptyList(),
    likes = likes,
    isPopular = isPopular,
    isFavorite = true
)

fun AIPrompt.toFavoritePrompt() = FavoritePrompt(
    id = "${id}_fav",
    promptId = id,
    title = title,
    shortPrompt = shortPrompt,
    fullPrompt = fullPrompt,
    imageUrl = imageUrl,
    category = category,
    likes = likes,
    isPopular = isPopular
)

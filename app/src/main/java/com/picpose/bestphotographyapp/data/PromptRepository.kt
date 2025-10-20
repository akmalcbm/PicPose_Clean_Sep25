package com.picpose.bestphotographyapp.data

import android.util.Log
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * PromptRepository - Data access layer for AI prompts
 * 
 * This is a simplified in-memory implementation for demonstration.
 * In a real app, this would fetch from network/database via HomeRepository.
 */
class PromptRepository {
    
    companion object {
        private const val TAG = "PromptRepository"
        
        // Test native ad ID (fallback)
        private const val TEST_NATIVE_AD_ID = "ca-app-pub-3940256099942544/2247696110"
    }
    
    // Mock data for demonstration
    private val mockPrompts = listOf(
        AIPrompt(
            id = "1",
            title = "Sunset Portrait Photography",
            shortPrompt = "Professional portrait during golden hour",
            fullPrompt = "Professional portrait photography of a person during golden hour, soft natural lighting, shallow depth of field, Canon EOS R5, 85mm f/1.4 lens, warm tones, cinematic quality",
            imageUrl = "https://picsum.photos/400/600?random=1",
            category = "Portrait",
            tags = listOf("portrait", "sunset", "golden-hour", "professional"),
            likes = 245,
            isPopular = true,
            isFavorite = false
        ),
        AIPrompt(
            id = "2",
            title = "Mountain Landscape",
            shortPrompt = "Epic mountain landscape at sunrise",
            fullPrompt = "Epic mountain landscape photography at sunrise, dramatic clouds, ray of light breaking through clouds, vivid colors, ultra wide angle, 16mm lens, high dynamic range, professional landscape photography",
            imageUrl = "https://picsum.photos/400/600?random=2",
            category = "Landscape",
            tags = listOf("landscape", "mountain", "sunrise", "nature"),
            likes = 189,
            isPopular = true,
            isFavorite = false
        ),
        AIPrompt(
            id = "3",
            title = "Urban Architecture",
            shortPrompt = "Modern architecture in urban setting",
            fullPrompt = "Modern architectural photography, geometric patterns, leading lines, minimalist composition, black and white, tilt-shift lens effect, professional architecture photography",
            imageUrl = "https://picsum.photos/400/600?random=3",
            category = "Architecture",
            tags = listOf("architecture", "urban", "modern", "geometric"),
            likes = 167,
            isPopular = false,
            isFavorite = false
        ),
        AIPrompt(
            id = "4",
            title = "Wildlife Close-up",
            shortPrompt = "Close-up wildlife photography",
            fullPrompt = "Close-up wildlife photography of an eagle, sharp focus on eyes, shallow depth of field, telephoto lens 400mm, natural habitat background, professional wildlife photography",
            imageUrl = "https://picsum.photos/400/600?random=4",
            category = "Wildlife",
            tags = listOf("wildlife", "bird", "closeup", "nature"),
            likes = 312,
            isPopular = true,
            isFavorite = false
        ),
        AIPrompt(
            id = "5",
            title = "Street Photography Scene",
            shortPrompt = "Candid street photography moment",
            fullPrompt = "Candid street photography, decisive moment, urban life, black and white, Leica M10, 35mm lens, documentary style, human interest, authentic moment",
            imageUrl = "https://picsum.photos/400/600?random=5",
            category = "Street",
            tags = listOf("street", "candid", "urban", "documentary"),
            likes = 223,
            isPopular = false,
            isFavorite = false
        ),
        AIPrompt(
            id = "6",
            title = "Product Photography",
            shortPrompt = "Commercial product photography",
            fullPrompt = "Commercial product photography, clean white background, perfect lighting, macro details, high resolution, studio setup, professional e-commerce photography",
            imageUrl = "https://picsum.photos/400/600?random=6",
            category = "Product",
            tags = listOf("product", "commercial", "studio", "macro"),
            likes = 145,
            isPopular = false,
            isFavorite = false
        ),
        AIPrompt(
            id = "7",
            title = "Astrophotography",
            shortPrompt = "Milky Way and starry night sky",
            fullPrompt = "Astrophotography of Milky Way, starry night sky, long exposure, foreground landscape silhouette, 14mm ultra wide lens, f/2.8, ISO 3200, professional night sky photography",
            imageUrl = "https://picsum.photos/400/600?random=7",
            category = "Landscape",
            tags = listOf("astrophotography", "milky-way", "night", "stars"),
            likes = 456,
            isPopular = true,
            isFavorite = false
        ),
        AIPrompt(
            id = "8",
            title = "Fashion Editorial",
            shortPrompt = "High fashion editorial photography",
            fullPrompt = "High fashion editorial photography, dramatic lighting, studio setup, fashion model, designer clothing, Vogue style, professional fashion photography, medium format camera",
            imageUrl = "https://picsum.photos/400/600?random=8",
            category = "Fashion",
            tags = listOf("fashion", "editorial", "studio", "model"),
            likes = 389,
            isPopular = true,
            isFavorite = false
        ),
        AIPrompt(
            id = "9",
            title = "Food Photography",
            shortPrompt = "Appetizing food photography",
            fullPrompt = "Appetizing food photography, overhead shot, natural lighting, rustic wooden table, fresh ingredients, shallow depth of field, professional food styling, editorial quality",
            imageUrl = "https://picsum.photos/400/600?random=9",
            category = "Food",
            tags = listOf("food", "culinary", "overhead", "natural-light"),
            likes = 178,
            isPopular = false,
            isFavorite = false
        ),
        AIPrompt(
            id = "10",
            title = "Macro Nature",
            shortPrompt = "Extreme macro nature photography",
            fullPrompt = "Extreme macro nature photography, water droplets on flower petals, shallow depth of field, bokeh background, 100mm macro lens, natural lighting, fine details",
            imageUrl = "https://picsum.photos/400/600?random=10",
            category = "Nature",
            tags = listOf("macro", "nature", "flower", "water"),
            likes = 267,
            isPopular = false,
            isFavorite = false
        )
    )
    
    /**
     * Get prompt by ID
     */
    suspend fun getPromptById(promptId: String): Flow<Result<AIPrompt>> = flow {
        try {
            Log.d(TAG, "Fetching prompt by ID: $promptId")
            // Simulate network delay
            delay(500)
            
            val prompt = mockPrompts.find { it.id == promptId }
            if (prompt != null) {
                emit(Result.success(prompt))
            } else {
                emit(Result.failure(Exception("Prompt not found")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching prompt: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Get similar prompts by category
     */
    suspend fun getSimilarPrompts(
        category: String,
        excludeId: String,
        limit: Int = 10
    ): Flow<Result<List<AIPrompt>>> = flow {
        try {
            Log.d(TAG, "Fetching similar prompts for category: $category")
            // Simulate network delay
            delay(300)
            
            val similar = mockPrompts
                .filter { it.category == category && it.id != excludeId }
                .take(limit)
            
            emit(Result.success(similar))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching similar prompts: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Get all prompts with pagination
     */
    suspend fun getAllPrompts(
        page: Int = 1,
        limit: Int = 20
    ): Flow<Result<List<AIPrompt>>> = flow {
        try {
            Log.d(TAG, "Fetching all prompts - page: $page, limit: $limit")
            // Simulate network delay
            delay(500)
            
            val start = (page - 1) * limit
            val end = minOf(start + limit, mockPrompts.size)
            
            if (start < mockPrompts.size) {
                val prompts = mockPrompts.subList(start, end)
                emit(Result.success(prompts))
            } else {
                emit(Result.success(emptyList()))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching all prompts: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)
    
    /**
     * Get app settings for AdMob configuration
     */
    suspend fun getAppSettings(): Flow<Result<AppSettings>> = flow {
        try {
            Log.d(TAG, "Fetching app settings")
            // Simulate network delay
            delay(200)
            
            // Mock app settings - in real app this would come from server
            val settings = AppSettings(
                appId = "ca-app-pub-3940256099942544~3347511713",
                banner1Id = "",
                banner2Id = "",
                interstitial1Id = "ca-app-pub-3940256099942544/1033173712",
                interstitial2Id = "ca-app-pub-3940256099942544/1033173712",
                native1Id = TEST_NATIVE_AD_ID,
                native2Id = TEST_NATIVE_AD_ID,
                native3Id = TEST_NATIVE_AD_ID,
                rewarded1Id = ""
            )
            
            emit(Result.success(settings))
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching app settings: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)
}

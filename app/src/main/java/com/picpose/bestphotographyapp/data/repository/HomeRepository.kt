package com.picpose.bestphotographyapp.data.repository

import android.content.Context
import com.picpose.bestphotographyapp.data.database.AppDatabase
import com.picpose.bestphotographyapp.data.database.FavoritePrompt
import com.picpose.bestphotographyapp.data.database.FavoritePromptDao
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.Category
import com.picpose.bestphotographyapp.data.models.Post
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class HomeRepository(private val context: Context) {
    private val apiService = RetrofitClient.apiService
    private val apiKey = "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c"
    private val database = AppDatabase.getDatabase(context)
    private val favoriteDao: FavoritePromptDao = database.favoriteDao()

    // POSTS METHODS
    suspend fun getFeaturedPosts(): Flow<Result<List<Post>>> = flow {
        try {
            val response = apiService.getFeaturedPosts(apiKey, 5)
            if (response.isSuccessful && response.body() != null) {
                emit(Result.success(response.body()!!.data))
            } else {
                emit(Result.failure(Exception("Failed to load featured posts")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun getRecentPosts(): Flow<Result<List<Post>>> = flow {
        try {
            val response = apiService.getPosts(apiKey, 10, 0)
            if (response.isSuccessful && response.body() != null) {
                emit(Result.success(response.body()!!.data))
            } else {
                emit(Result.failure(Exception("Failed to load recent posts")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun getCategories(): Flow<Result<List<Category>>> = flow {
        try {
            val response = apiService.getCategories(apiKey)
            if (response.isSuccessful && response.body() != null) {
                emit(Result.success(response.body()!!.data))
            } else {
                emit(Result.failure(Exception("Failed to load categories")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    // AI PROMPTS METHODS - COMPLETE WITH ALL YOUR REMAINING CODE

    suspend fun getAllAIPrompts(
        page: Int = 1,
        category: String? = null,
        search: String? = null
    ): Flow<Result<List<AIPrompt>>> = flow {
        try {
            val response = apiService.getAllAIPrompts(
                apiKey = apiKey,
                page = page,
                category = category,
                search = search
            )
            if (response.isSuccessful && response.body() != null) {
                val prompts = response.body()!!.data.map { dto ->
                    val aiPrompt = dto.toAIPrompt()
                    // IMPORTANT: Check if it's favorited from database
                    aiPrompt.copy(isFavorite = favoriteDao.isFavorite(aiPrompt.id))
                }
                emit(Result.success(prompts))
            } else {
                // Fallback to mock data with favorite status
                val mockPrompts = getMockAIPromptsWithFavoriteStatus()
                emit(Result.success(mockPrompts))
            }
        } catch (e: Exception) {
            // Fallback to mock data with favorite status
            val mockPrompts = getMockAIPromptsWithFavoriteStatus()
            emit(Result.success(mockPrompts))
        }
    }

    suspend fun getPromptById(promptId: String): Flow<Result<AIPrompt>> = flow {
        try {
            val allPrompts = getMockAIPromptsWithFavoriteStatus()
            val prompt = allPrompts.find { it.id == promptId }

            if (prompt != null) {
                emit(Result.success(prompt))
            } else {
                emit(Result.failure(Exception("Prompt not found with ID: $promptId")))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    // NEW: Get mock prompts with actual favorite status from database
    private suspend fun getMockAIPromptsWithFavoriteStatus(): List<AIPrompt> {
        return getMockAIPrompts().map { prompt ->
            val isFavorite = try {
                favoriteDao.isFavorite(prompt.id)
            } catch (e: Exception) {
                false
            }
            prompt.copy(isFavorite = isFavorite)
        }
    }

    suspend fun toggleFavorite(prompt: AIPrompt): Flow<Result<Boolean>> = flow {
        try {
            val currentlyFavorite = favoriteDao.isFavorite(prompt.id)

            if (currentlyFavorite) {
                // Remove from favorites
                favoriteDao.removeFromFavorites(prompt.id)
                emit(Result.success(false))
            } else {
                // Add to favorites
                val favoritePrompt = prompt.copy(isFavorite = true).toFavoritePrompt()
                favoriteDao.addToFavorites(favoritePrompt)
                emit(Result.success(true))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }


    suspend fun getFavoritePrompts(): Flow<Result<List<AIPrompt>>> = flow {
        try {
            // First try to get from local database
            val favorites = favoriteDao.getAllFavorites()
            val aiPrompts = favorites.map { it.toAIPrompt() }
            emit(Result.success(aiPrompts))
        } catch (e: Exception) {
            // Fallback to filtering mock data by favorite status
            try {
                val allPrompts = getMockAIPrompts()
                val mockFavorites = allPrompts.filter { it.isFavorite }
                emit(Result.success(mockFavorites))
            } catch (fallbackError: Exception) {
                emit(Result.failure(fallbackError))
            }
        }
    }

    suspend fun getFeaturedAIPrompts(): Flow<Result<List<AIPrompt>>> = flow {
        try {
            val response = apiService.getFeaturedAIPrompts(apiKey, 5)
            if (response.isSuccessful && response.body() != null) {
                val prompts = response.body()!!.data.map { dto ->
                    val aiPrompt = dto.toAIPrompt()
                    aiPrompt.copy(isFavorite = favoriteDao.isFavorite(aiPrompt.id))
                }
                emit(Result.success(prompts))
            } else {
                // Get featured prompts (popular or top-rated) from mock data
                val allPrompts = getMockAIPrompts()
                val featuredPrompts = allPrompts.filter { it.isPopular }.take(10)
                emit(Result.success(featuredPrompts))
            }
        } catch (e: Exception) {
            // Get featured prompts (popular or top-rated) from mock data
            val allPrompts = getMockAIPrompts()
            val featuredPrompts = allPrompts.filter { it.isPopular }.take(10)
            emit(Result.success(featuredPrompts))
        }
    }

    // FAVORITES MANAGEMENT - YOUR COMPLETE REMAINING CODE
    suspend fun isFavorite(promptId: String): Boolean {
        return try {
            favoriteDao.isFavorite(promptId)
        } catch (e: Exception) {
            // Fallback to checking mock data
            val mockPrompts = getMockAIPrompts()
            mockPrompts.find { it.id == promptId }?.isFavorite ?: false
        }
    }

    suspend fun addToFavorites(prompt: AIPrompt) {
        try {
            val favoritePrompt = prompt.toFavoritePrompt()
            favoriteDao.addToFavorites(favoritePrompt)
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun removeFromFavorites(promptId: String) {
        try {
            favoriteDao.removeFromFavorites(promptId)
        } catch (e: Exception) {
            throw e
        }
    }

    suspend fun getFavoriteCount(): Int {
        return try {
            favoriteDao.getFavoriteCount()
        } catch (e: Exception) {
            // Fallback to mock data count
            getMockAIPrompts().count { it.isFavorite }
        }
    }

    // SEARCH AND FILTER METHODS - YOUR COMPLETE REMAINING CODE
    suspend fun searchPrompts(query: String): Flow<Result<List<AIPrompt>>> = flow {
        try {
            val allPrompts = getMockAIPrompts()
            val filteredPrompts = allPrompts.filter { prompt ->
                prompt.title.contains(query, ignoreCase = true) ||
                        prompt.shortPrompt.contains(query, ignoreCase = true) ||
                        prompt.category.contains(query, ignoreCase = true) ||
                        prompt.tags.any { it.contains(query, ignoreCase = true) }
            }
            emit(Result.success(filteredPrompts))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    suspend fun getPromptsByCategory(category: String): Flow<Result<List<AIPrompt>>> = flow {
        try {
            val allPrompts = getMockAIPrompts()
            val categoryPrompts = if (category == "All") {
                allPrompts
            } else {
                allPrompts.filter { it.category == category }
            }
            emit(Result.success(categoryPrompts))
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }

    // Enhanced mock data
    fun getMockAIPrompts(): List<AIPrompt> = listOf(
        AIPrompt(
            id = "1",
            title = "Cinematic Portrait Lighting",
            shortPrompt = "Professional portrait with dramatic cinematic lighting and soft shadows.",
            fullPrompt = "Create a cinematic portrait of a person with dramatic lighting, soft shadows, professional studio setup, warm golden hour lighting with subtle background blur, high-end photography equipment visible, moody atmosphere, professional headshot style, 85mm lens, f/1.4 aperture, professional makeup and styling.",
            imageUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=600&h=400&fit=crop",
            category = "Portrait",
            tags = listOf("portrait", "cinematic", "lighting", "professional"),
            likes = 1234,
            isPopular = true
        ),
        AIPrompt(
            id = "2",
            title = "Minimalist Studio Setup",
            shortPrompt = "Clean minimal studio with professional lighting equipment and modern aesthetic.",
            fullPrompt = "Professional photography studio with minimalist design, clean white background, modern lighting equipment, sleek photography gear, minimal furniture, bright and airy atmosphere, contemporary studio design, professional workspace, softboxes, beauty dishes, seamless paper backdrop.",
            imageUrl = "https://images.unsplash.com/photo-1553062407-98eeb64c6a62?w=600&h=400&fit=crop",
            category = "Studio",
            tags = listOf("studio", "minimal", "professional", "clean"),
            likes = 892,
            isPopular = true
        ),
        AIPrompt(
            id = "3",
            title = "Cyberpunk Night Photography",
            shortPrompt = "Futuristic neon-lit cityscape with cyberpunk aesthetic and dramatic atmosphere.",
            fullPrompt = "Create a futuristic cyberpunk scene with bright neon lights, dark urban atmosphere, high-tech cityscape, glowing signs, rain-soaked streets, dramatic lighting contrasts, sci-fi elements, metropolitan night scene, electric blue and pink color palette, street photography style, moody and atmospheric.",
            imageUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af2176?w=600&h=400&fit=crop",
            category = "Urban",
            tags = listOf("cyberpunk", "neon", "futuristic", "night", "urban"),
            likes = 2156,
            isPopular = true
        ),
        AIPrompt(
            id = "4",
            title = "Golden Hour Nature Portrait",
            shortPrompt = "Natural outdoor portrait during golden hour with beautiful warm lighting.",
            fullPrompt = "Outdoor portrait session using natural golden hour lighting, scenic forest background, warm sunset glow, natural poses, lifestyle photography, organic lighting, beautiful natural backdrop, soft warm tones, environmental portrait, 35mm lens, natural makeup, casual styling.",
            imageUrl = "https://images.unsplash.com/photo-1492691527719-9d1e07e534b4?w=600&h=400&fit=crop",
            category = "Natural",
            tags = listOf("natural", "outdoor", "golden hour", "portrait"),
            likes = 756,
            isPopular = false
        ),
        AIPrompt(
            id = "5",
            title = "Corporate Professional Headshot",
            shortPrompt = "Business professional headshot with clean background and proper lighting.",
            fullPrompt = "Corporate professional headshot photography with clean neutral background, proper business lighting, professional attire, confident expression, suitable for LinkedIn and business profiles, executive style photography, sharp focus, professional grooming, business casual attire.",
            imageUrl = "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=600&h=400&fit=crop",
            category = "Corporate",
            tags = listOf("headshot", "corporate", "professional", "business"),
            likes = 434,
            isPopular = false
        ),
        AIPrompt(
            id = "6",
            title = "Street Photography Candid",
            shortPrompt = "Authentic street photography capturing real moments in urban environments.",
            fullPrompt = "Street photography capturing candid moments in urban settings, documentary style, authentic expressions, city life, natural interactions, decisive moment photography, black and white processing, photojournalistic approach, 50mm lens, available light.",
            imageUrl = "https://images.unsplash.com/photo-1449824913935-59a10b8d2000?w=600&h=400&fit=crop",
            category = "Street",
            tags = listOf("street", "candid", "documentary", "urban"),
            likes = 623,
            isPopular = false
        ),
        AIPrompt(
            id = "7",
            title = "Product Photography Clean",
            shortPrompt = "Clean professional product photography with minimalist styling.",
            fullPrompt = "Professional product photography with clean white background, minimalist styling, perfect lighting setup, commercial quality, high-end product presentation, macro lens details, studio lighting, gradient background, commercial photography standards.",
            imageUrl = "https://images.unsplash.com/photo-1542291026-7eec264c27ff?w=600&h=400&fit=crop",
            category = "Commercial",
            tags = listOf("product", "commercial", "clean", "minimal"),
            likes = 891,
            isPopular = true
        ),
        AIPrompt(
            id = "8",
            title = "Fashion Portrait Editorial",
            shortPrompt = "High-fashion editorial portrait with dramatic styling and lighting.",
            fullPrompt = "Editorial fashion portrait with dramatic lighting, high-end styling, professional makeup, fashion photography, magazine quality, creative posing, designer clothing, beauty retouching, studio or location, fashion industry standards.",
            imageUrl = "https://images.unsplash.com/photo-1469334031218-e382a71b716b?w=600&h=400&fit=crop",
            category = "Fashion",
            tags = listOf("fashion", "editorial", "portrait", "styling"),
            likes = 1567,
            isPopular = true
        ),
        AIPrompt(
            id = "9",
            title = "Landscape Scenic Vista",
            shortPrompt = "Breathtaking landscape photography with perfect natural lighting.",
            fullPrompt = "Stunning landscape photography with perfect natural lighting, scenic vista, dramatic sky, foreground interest, depth of field, wide-angle lens, golden hour timing, nature photography, environmental conservation, outdoor adventure.",
            imageUrl = "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=600&h=400&fit=crop",
            category = "Landscape",
            tags = listOf("landscape", "nature", "scenic", "outdoor"),
            likes = 945,
            isPopular = false
        ),
        AIPrompt(
            id = "10",
            title = "Wedding Documentary Style",
            shortPrompt = "Emotional wedding photography capturing authentic moments and joy.",
            fullPrompt = "Wedding photography with documentary style, authentic emotions, candid moments, natural lighting, photojournalistic approach, wedding day timeline, emotional storytelling, family portraits, reception photography, romantic atmosphere.",
            imageUrl = "https://images.unsplash.com/photo-1519741497674-611481863552?w=600&h=400&fit=crop",
            category = "Wedding",
            tags = listOf("wedding", "documentary", "emotional", "candid"),
            likes = 1123,
            isPopular = true
        )
    )

    // Mock data as fallback for offline mode
    fun getMockFeaturedPosts(): List<Post> = listOf(
        Post(
            id = "1",
            title = "Golden Hour Portrait Mastery",
            description = "Learn the secrets of capturing stunning portraits during golden hour with professional lighting techniques.",
            image = "https://scontent.flko10-1.fna.fbcdn.net/v/t39.30808-6/539403726_759525540326780_2460644145648515262_n.jpg?stp=dst-jpg_s640x640_tt6&_nc_cat=111&ccb=1-7&_nc_sid=127cfc&_nc_ohc=ufMYi0olijsQ7kNvwGQ6Pw9&_nc_oc=AdlPTyvGHEuNsCe6nAdZWheqZ5HLCijalnClIGnZcpDoyPAQsCxRK8mfeiz2xd2yyWM-ZjwS3JRttL6egzggnWAo&_nc_zt=23&_nc_ht=scontent.flko10-1.fna&_nc_gid=fTqMTkUhnRwXytlUEWMERw&oh=00_AfYomHoshWTqYHTnbCtpt8PIb8cUXKXoDAeOWGz0tR7eQw&oe=68BF1A34",
            category = "Portrait",
            author = "Sarah Johnson",
            created_at = "2024-01-15T10:30:00Z",
            likes = 324,
            views = 2150,
            is_featured = true
        ),
        Post(
            id = "2",
            title = "Street Photography Secrets",
            description = "Master the art of candid street photography with these professional tips and techniques.",
            image = "https://images.unsplash.com/photo-1449824913935-59a10b8d2000?w=800",
            category = "Street",
            author = "Mike Chen",
            created_at = "2024-01-14T15:45:00Z",
            likes = 198,
            views = 1820,
            is_featured = true
        ),
        Post(
            id = "3",
            title = "Landscape Composition Rules",
            description = "Discover the fundamental rules of landscape composition that will transform your nature photography.",
            image = "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=800",
            category = "Landscape",
            author = "Emma Wilson",
            created_at = "2024-01-13T08:20:00Z",
            likes = 456,
            views = 3240,
            is_featured = true
        )
    )

    fun getMockCategories(): List<Category> = listOf(
        Category("1", "Portrait", "People & Portrait Photography", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400", 45),
        Category("2", "Landscape", "Nature & Scenic Photography", "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400", 32),
        Category("3", "Street", "Urban & Street Photography", "https://images.unsplash.com/photo-1449824913935-59a10b8d2000?w=400", 28),
        Category("4", "Wildlife", "Animals & Wildlife Photography", "https://images.unsplash.com/photo-1564349683136-77e08dba1ef7?w=400", 19),
        Category("5", "Architecture", "Buildings & Architecture", "https://images.unsplash.com/photo-1487958449943-2429e8be8625?w=400", 24)
    )

    // Extension functions
    fun AIPrompt.toFavoritePrompt(): FavoritePrompt {
        return FavoritePrompt(
            id = this.id, // Using AIPrompt id as primary key
            promptId = this.id, // Store original prompt ID
            title = this.title,
            shortPrompt = this.shortPrompt,
            fullPrompt = this.fullPrompt,
            imageUrl = this.imageUrl,
            category = this.category,
            likes = this.likes,
            isPopular = this.isPopular,
            tags = this.tags, // Now this parameter exists
            dateAdded = System.currentTimeMillis(), // Now this parameter exists
            favoritedAt = System.currentTimeMillis()
        )
    }

    fun FavoritePrompt.toAIPrompt(): AIPrompt {
        return AIPrompt(
            id = this.promptId, // Use promptId for AIPrompt id
            title = this.title,
            shortPrompt = this.shortPrompt,
            fullPrompt = this.fullPrompt,
            imageUrl = this.imageUrl,
            category = this.category,
            tags = this.tags, // Now available from FavoritePrompt
            likes = this.likes,
            isPopular = this.isPopular,
            isFavorite = true // Always true since it's from favorites
        )
    }


}

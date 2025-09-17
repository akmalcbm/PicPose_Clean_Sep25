package com.picpose.bestphotographyapp.data.repository

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import com.picpose.bestphotographyapp.data.database.AppDatabase
import com.picpose.bestphotographyapp.data.database.FavoritePrompt
import com.picpose.bestphotographyapp.data.database.FavoritePromptDao
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.AIPromptDto
import com.picpose.bestphotographyapp.data.models.Category
import com.picpose.bestphotographyapp.data.models.DailyTip
import com.picpose.bestphotographyapp.data.models.MetaDto
import com.picpose.bestphotographyapp.data.models.Post
import com.picpose.bestphotographyapp.data.models.toAIPrompt
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.remote.ApiResponseDailyTips
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import retrofit2.Response
import org.json.JSONObject

// Small wrapper for paginated responses
data class PaginatedResult<T>(val items: List<T>, val meta: MetaDto? = null)

class HomeRepository(
    private val context: Context,
    private val useMocks: Boolean = false // set false for production builds
) {
    private val apiService = RetrofitClient.apiService

    // TODO: move to BuildConfig for production
    private val apiKey =
        "7a6f3c27a1b6d5e8e4c8a2b3f9e6d1f47c5b8a9d3e7f2c6a4b9e3d1c5f8a7b2c"

    private val database = AppDatabase.getDatabase(context)
    private val favoriteDao: FavoritePromptDao = database.favoriteDao()

    // Generic safe API helper
    private suspend fun <T> safeApiCall(block: suspend () -> Response<T>): Result<T> {
        return try {
            val resp = block()
            if (resp.isSuccessful) {
                val body = resp.body()
                if (body != null) Result.success(body) else Result.failure(Exception("Empty response body"))
            } else {
                val message = try { resp.errorBody()?.string() } catch (_: Exception) { resp.message() }
                Result.failure(Exception("HTTP ${resp.code()}: $message"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // --- inside HomeRepository class get Daily Tips ---
    suspend fun getDailyTips(): Flow<Result<List<DailyTip>>> = flow {
        if (useMocks) {
            // convert fallback strings to DailyTip objects
            val fallback = photographyTips.mapIndexed { idx: Int, tip: String ->
                DailyTip(
                    id = "fallback_$idx",
                    tip = tip,
                    isActive = true,
                    order = idx,
                    createdAt = null,
                    updatedAt = null
                )
            }
            emit(Result.success(fallback))
            return@flow
        }

        // call safeApiCall which returns Result<T> where T = ApiResponseDailyTips<List<DailyTip>>
        val result: Result<ApiResponseDailyTips<List<DailyTip>>> = safeApiCall { apiService.getDailyTips(apiKey = apiKey) }

        result.fold(
            onSuccess = { body: ApiResponseDailyTips<List<DailyTip>> ->
                try {
                    val tips: List<DailyTip> = body.data ?: emptyList()
                    emit(Result.success(tips))
                } catch (e: Exception) {
                    emit(Result.failure(e))
                }
            },
            onFailure = { err: Throwable ->
                emit(Result.failure(err))
            }
        )
    }.flowOn(Dispatchers.IO)

    @Suppress("UNCHECKED_CAST")
    private suspend fun mapAnyListToAIPromptList(data: Any?): List<AIPrompt> {
        if (data == null) return emptyList()

        // If it's already a List<AIPrompt>, return filtered list
        if (data is List<*>) {
            val list = data as List<*>
            if (list.isEmpty()) return emptyList()

            // Case 1: Already domain models
            if (list.first() is AIPrompt) {
                return list.filterIsInstance<AIPrompt>()
            }

            // Case 2: DTO objects - map to AIPrompt
            if (list.first() is AIPromptDto) {
                return list.filterIsInstance<AIPromptDto>().map { dto ->
                    // check favorite status from Room (safe)
                    val fav = try { favoriteDao.isFavorite(dto.id ?: "") } catch (_: Exception) { false }
                    dto.toAIPrompt(isFavorite = fav)
                }
            }

            // Case 3: Maybe Gson created LinkedTreeMap etc — attempt to convert via Gson to AIPrompt
            try {
                // Use Gson to map each element to AIPrompt (requires Gson available)
                val gson = com.google.gson.Gson()
                val mapped = list.mapNotNull { elem ->
                    try {
                        val json = gson.toJson(elem)
                        gson.fromJson(json, AIPrompt::class.java)
                    } catch (_: Exception) {
                        null
                    }
                }
                if (mapped.isNotEmpty()) return mapped
            } catch (_: Exception) { /* ignore */ }
        }

        return emptyList()
    }

    suspend fun getAllAIPromptsSimple(
        page: Int = 1,
        limit: Int = 20,
        category: String? = null,
        search: String? = null
    ): Flow<Result<List<AIPrompt>>> = flow {
        if (useMocks) {
            emit(Result.success(getMockAIPrompts()))
            return@flow
        }

        // call retrofit - your safeApiCall returns Result<T> where T is whatever Response.body() is
        val apiResult: Result<Any?> = safeApiCall {
            // your ApiService returns Response<AIPromptResponse> but safeApiCall has generic; call the service
            apiService.getAllAIPrompts(apiKey = apiKey, page = page, limit = limit, category = category, search = search)
        }

        apiResult.fold(
            onSuccess = { rawBody ->
                try {
                    // rawBody may be an ApiResponse wrapper object (with 'data'), or an AIPromptResponse, etc.
                    val extractedList: List<AIPrompt> = when (rawBody) {
                        is ApiResponseDailyTips<*> -> {
                            // generic wrapper: data should already be typed as List<*> (probably List<AIPromptDto> or List<AIPrompt>)
                            val data = rawBody.data
                            mapAnyListToAIPromptList(data)
                        }
                        else -> {
                            // try reflection / property-access fallback to get field named 'data'
                            val dataField = try {
                                rawBody?.javaClass?.getDeclaredField("data")?.also { it.isAccessible = true }?.get(rawBody)
                            } catch (e: Exception) {
                                null
                            }
                            mapAnyListToAIPromptList(dataField)
                        }
                    }

                    emit(Result.success(extractedList))
                } catch (e: Exception) {
                    emit(Result.failure(e))
                }
            },
            onFailure = { err ->
                emit(Result.failure(err))
            }
        )
    }.catch { e ->
        emit(Result.failure(e))
    }.flowOn(Dispatchers.IO)



    // -------------------------
    // POSTS (Featured / Recent)
    // -------------------------
    suspend fun getFeaturedPosts(limit: Int = 5): Flow<Result<List<Post>>> = flow {
        if (useMocks) {
            emit(Result.success(getMockFeaturedPosts().take(limit)))
            return@flow
        }

        val result = safeApiCall { apiService.getLatestPosts(apiKey = apiKey, limit = limit) }
        result.fold(
            onSuccess = { body ->
                val posts = try {
                    val json = JSONObject(body.toString())
                    if (json.has("data")) {
                        val arr = json.getJSONArray("data")
                        (0 until arr.length()).mapNotNull { idx ->
                            val o = arr.optJSONObject(idx) ?: return@mapNotNull null
                            mapJsonToPost(o)
                        }
                    } else emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
                emit(Result.success(posts))
            },
            onFailure = { err ->
                // if production: return failure so UI can show error; if dev and still useMocks off, return empty list
                emit(Result.failure(err))
            }
        )
    }.flowOn(Dispatchers.IO)

    suspend fun getRecentPosts(limit: Int = 10, offset: Int = 0): Flow<Result<List<Post>>> = flow {
        if (useMocks) {
            emit(Result.success(getMockFeaturedPosts()))
            return@flow
        }

        val result = safeApiCall { apiService.getPosts(apiKey = apiKey, limit = limit, offset = offset) }
        result.fold(
            onSuccess = { body ->
                val posts = try {
                    val json = JSONObject(body.toString())
                    if (json.has("data")) {
                        val arr = json.getJSONArray("data")
                        (0 until arr.length()).mapNotNull { idx ->
                            val o = arr.optJSONObject(idx) ?: return@mapNotNull null
                            mapJsonToPost(o)
                        }
                    } else emptyList()
                } catch (e: Exception) {
                    emptyList()
                }
                emit(Result.success(posts))
            },
            onFailure = { err ->
                emit(Result.failure(err))
            }
        )
    }.flowOn(Dispatchers.IO)

    suspend fun getCategories(): Flow<Result<List<Category>>> = flow {
        if (useMocks) {
            emit(Result.success(getMockCategories()))
            return@flow
        }
        // Prefer a real categories endpoint (not present in ApiService sample). If not available, return failure.
        emit(Result.failure(Exception("Categories endpoint not implemented on server")))
    }.flowOn(Dispatchers.IO)

    // -------------------------
    // AI PROMPTS (paginated)
    // -------------------------
    suspend fun getAllAIPrompts(
        page: Int = 1,
        limit: Int = 20,
        category: String? = null,
        search: String? = null
    ): Flow<Result<PaginatedResult<AIPrompt>>> = flow {
        if (useMocks) {
            val mocks = getMockAIPromptsWithFavoriteStatus()
            val start = (page - 1) * limit
            val end = (start + limit).coerceAtMost(mocks.size)
            val pageItems = if (start < mocks.size) mocks.subList(start, end) else emptyList()
            val meta = MetaDto(total = mocks.size, page = page, limit = limit, hasMore = end < mocks.size)
            emit(Result.success(PaginatedResult(pageItems, meta)))
            return@flow
        }

        val result = safeApiCall {
            apiService.getAllAIPrompts(
                apiKey = apiKey,
                page = page,
                limit = limit,
                category = category,
                search = search
            )
        }

        result.fold(
            onSuccess = { body ->
                try {
                    val json = JSONObject(body.toString())
                    val items = mutableListOf<AIPrompt>()
                    var meta: MetaDto? = null

                    if (json.has("data")) {
                        val arr = json.getJSONArray("data")
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val id = o.optString("id")
                            val title = o.optString("title")
                            val shortPrompt = o.optString("shortPrompt", "")
                            val fullPrompt = o.optString("fullPrompt", "")
                            val imageUrl = o.optString("imageUrl", "")
                            val categoryName = o.optString("category", "")
                            val tagsList = mutableListOf<String>()
                            if (o.has("tags")) {
                                val tagsJson = o.optJSONArray("tags")
                                if (tagsJson != null) {
                                    for (t in 0 until tagsJson.length()) {
                                        tagsList.add(tagsJson.optString(t))
                                    }
                                }
                            }
                            val likes = o.optInt("likes", 0)
                            val isPopular = o.optBoolean("isPopular", false)
                            val status = o.optString("status", "published")
                            val priority = o.optInt("priority", 0)
                            val createdAt = o.optString("createdAt", null)
                            val updatedAt = o.optString("updatedAt", null)

                            val fav = try { favoriteDao.isFavorite(id) } catch (_: Exception) { false }

                            val prompt = AIPrompt(
                                id = id,
                                title = title,
                                shortPrompt = shortPrompt,
                                fullPrompt = fullPrompt,
                                imageUrl = imageUrl,
                                category = categoryName,
                                tags = tagsList,
                                likes = likes,
                                isPopular = isPopular,
                                status = status,
                                priority = priority,
                                createdAt = createdAt,
                                updatedAt = updatedAt,
                                isFavorite = fav
                            )
                            items.add(prompt)
                        }
                    }

                    if (json.has("meta")) {
                        val m = json.optJSONObject("meta")
                        if (m != null) {
                            meta = MetaDto(
                                total = m.optInt("total", 0),
                                page = m.optInt("page", page),
                                limit = m.optInt("limit", limit),
                                hasMore = m.optBoolean("hasMore", false)
                            )
                        }
                    }

                    emit(Result.success(PaginatedResult(items, meta)))
                } catch (e: Exception) {
                    emit(Result.failure(e))
                }
            },
            onFailure = { err ->
                emit(Result.failure(err))
            }
        )
    }.flowOn(Dispatchers.IO)

    suspend fun getPromptById(promptId: String): Flow<Result<AIPrompt>> = flow {
        if (useMocks) {
            val fallback = getMockAIPromptsWithFavoriteStatus().find { it.id == promptId }
            if (fallback != null) emit(Result.success(fallback)) else emit(Result.failure(Exception("Prompt not found")))
            return@flow
        }

        val result = safeApiCall { apiService.getAIPromptById(apiKey = apiKey, id = promptId) }
        result.fold(
            onSuccess = { body ->
                try {
                    val json = JSONObject(body.toString())
                    val arr = json.optJSONArray("data")
                    var found: AIPrompt? = null
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            if (o.optString("id") == promptId) {
                                val tagsList = mutableListOf<String>()
                                val tagsJson = o.optJSONArray("tags")
                                if (tagsJson != null) for (t in 0 until tagsJson.length()) tagsList.add(tagsJson.optString(t))
                                val fav = try { favoriteDao.isFavorite(promptId) } catch (_: Exception) { false }
                                found = AIPrompt(
                                    id = o.optString("id"),
                                    title = o.optString("title"),
                                    shortPrompt = o.optString("shortPrompt", ""),
                                    fullPrompt = o.optString("fullPrompt", ""),
                                    imageUrl = o.optString("imageUrl", ""),
                                    category = o.optString("category", ""),
                                    tags = tagsList,
                                    likes = o.optInt("likes", 0),
                                    isPopular = o.optBoolean("isPopular", false),
                                    status = o.optString("status", "published"),
                                    priority = o.optInt("priority", 0),
                                    createdAt = o.optString("createdAt", null),
                                    updatedAt = o.optString("updatedAt", null),
                                    isFavorite = fav
                                )
                                break
                            }
                        }
                    }

                    if (found != null) emit(Result.success(found))
                    else emit(Result.failure(Exception("Prompt not found")))
                } catch (e: Exception) {
                    emit(Result.failure(e))
                }
            },
            onFailure = { err ->
                emit(Result.failure(err))
            }
        )
    }.flowOn(Dispatchers.IO)

    suspend fun getFeaturedAIPrompts(limit: Int = 5): Flow<Result<List<AIPrompt>>> = flow {
        if (useMocks) {
            emit(Result.success(getMockAIPromptsWithFavoriteStatus().filter { it.isPopular }.take(limit)))
            return@flow
        }

        val result = safeApiCall { apiService.getAllAIPrompts(apiKey = apiKey, page = 1, limit = limit) }
        result.fold(
            onSuccess = { body ->
                try {
                    val json = JSONObject(body.toString())
                    val arr = json.optJSONArray("data")
                    val items = mutableListOf<AIPrompt>()
                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val o = arr.getJSONObject(i)
                            val tagsList = mutableListOf<String>()
                            val tagsJson = o.optJSONArray("tags")
                            if (tagsJson != null) for (t in 0 until tagsJson.length()) tagsList.add(tagsJson.optString(t))
                            val id = o.optString("id")
                            val fav = try { favoriteDao.isFavorite(id) } catch (_: Exception) { false }
                            items.add(
                                AIPrompt(
                                    id = id,
                                    title = o.optString("title"),
                                    shortPrompt = o.optString("shortPrompt", ""),
                                    fullPrompt = o.optString("fullPrompt", ""),
                                    imageUrl = o.optString("imageUrl", ""),
                                    category = o.optString("category", ""),
                                    tags = tagsList,
                                    likes = o.optInt("likes", 0),
                                    isPopular = o.optBoolean("isPopular", false),
                                    status = o.optString("status", "published"),
                                    priority = o.optInt("priority", 0),
                                    createdAt = o.optString("createdAt", null),
                                    updatedAt = o.optString("updatedAt", null),
                                    isFavorite = fav
                                )
                            )
                        }
                    }
                    emit(Result.success(items.filter { it.isPopular }.take(limit)))
                } catch (e: Exception) {
                    emit(Result.failure(e))
                }
            },
            onFailure = { err ->
                emit(Result.failure(err))
            }
        )
    }.flowOn(Dispatchers.IO)

    // -------------------------
    // FAVORITES (local Room)
    // -------------------------
    suspend fun toggleFavorite(prompt: AIPrompt): Flow<Result<Boolean>> = flow {
        try {
            val currentlyFavorite = favoriteDao.isFavorite(prompt.id)
            if (currentlyFavorite) {
                favoriteDao.removeFromFavorites(prompt.id)
                emit(Result.success(false))
            } else {
                favoriteDao.addToFavorites(prompt.toFavoritePrompt())
                emit(Result.success(true))
            }
        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun getFavoritePrompts(): Flow<Result<List<AIPrompt>>> = flow {
        try {
            val favorites = favoriteDao.getAllFavorites()
            val aiPrompts = favorites.map { it.toAIPrompt() }
            emit(Result.success(aiPrompts))
        } catch (e: Exception) {
            if (useMocks) emit(Result.success(getMockAIPrompts().filter { it.isFavorite })) else emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun isFavorite(promptId: String): Boolean {
        return try {
            favoriteDao.isFavorite(promptId)
        } catch (e: Exception) {
            if (useMocks) getMockAIPrompts().find { it.id == promptId }?.isFavorite ?: false else false
        }
    }

    suspend fun addToFavorites(prompt: AIPrompt) {
        favoriteDao.addToFavorites(prompt.toFavoritePrompt())
    }

    suspend fun removeFromFavorites(promptId: String) {
        favoriteDao.removeFromFavorites(promptId)
    }

    suspend fun getFavoriteCount(): Int {
        return try {
            favoriteDao.getFavoriteCount()
        } catch (e: Exception) {
            if (useMocks) getMockAIPrompts().count { it.isFavorite } else 0
        }
    }

    // -------------------------
    // SEARCH / FILTER helpers
    // -------------------------
    suspend fun searchPrompts(query: String, page: Int = 1, limit: Int = 20): Flow<Result<List<AIPrompt>>> = flow {
        val apiFlow = getAllAIPrompts(page = page, limit = limit, search = query)
        var emitted = false
        apiFlow.collect { res ->
            res.fold(
                onSuccess = { pag ->
                    emit(Result.success(pag.items))
                    emitted = true
                },
                onFailure = { err ->
                    if (useMocks) {
                        val fallback = getMockAIPrompts().filter { p ->
                            p.title.contains(query, true) ||
                                    p.shortPrompt.contains(query, true) ||
                                    p.category.contains(query, true) ||
                                    p.tags.any { it.contains(query, true) }
                        }
                        emit(Result.success(fallback))
                    } else {
                        emit(Result.failure(err))
                    }
                    emitted = true
                }
            )
        }
        if (!emitted) emit(Result.success(emptyList()))
    }.flowOn(Dispatchers.IO)

    suspend fun getPromptsByCategory(category: String, page: Int = 1, limit: Int = 20): Flow<Result<List<AIPrompt>>> = flow {
        val apiFlow = getAllAIPrompts(page = page, limit = limit, category = category)
        var emitted = false
        apiFlow.collect { res ->
            res.fold(
                onSuccess = { pag ->
                    emit(Result.success(pag.items))
                    emitted = true
                },
                onFailure = {
                    if (useMocks) {
                        val fallback = if (category == "All") getMockAIPrompts() else getMockAIPrompts().filter { it.category.equals(category, true) }
                        emit(Result.success(fallback))
                    } else {
                        emit(Result.failure(Exception("Network error")))
                    }
                    emitted = true
                }
            )
        }
        if (!emitted) emit(Result.success(emptyList()))
    }.flowOn(Dispatchers.IO)

    // Extension functions mapping domain <-> room entities (kept as member helpers)
    fun AIPrompt.toFavoritePrompt(): FavoritePrompt {
        return FavoritePrompt(
            id = this.id,               // primary key in FavoritePrompt table
            promptId = this.id,
            title = this.title,
            shortPrompt = this.shortPrompt,
            fullPrompt = this.fullPrompt,
            imageUrl = this.imageUrl,
            category = this.category,
            likes = this.likes,
            isPopular = this.isPopular,
            tags = this.tags,
            dateAdded = System.currentTimeMillis(),
            favoritedAt = System.currentTimeMillis()
        )
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun FavoritePrompt.toAIPrompt(): AIPrompt {
        // Adjust these field names if your Room entity differs.
        return AIPrompt(
            id = this.promptId ?: this.id,
            title = this.title ?: "",
            shortPrompt = this.shortPrompt ?: "",
            fullPrompt = this.fullPrompt ?: "",
            imageUrl = this.imageUrl ?: "",
            category = this.category ?: "",
            tags = this.tags ?: emptyList(),
            likes = this.likes ?: 0,
            isPopular = this.isPopular ?: false,
            isFavorite = true,
            status = this.status ?: "published",
            priority = this.priority ?: 0,
            createdAt = this.dateAdded?.let { java.time.Instant.ofEpochMilli(it).toString() },
            updatedAt = this.favoritedAt?.let { java.time.Instant.ofEpochMilli(it).toString() }
        )
    }

    // Helper: map JSON object to Post domain model
    private fun mapJsonToPost(o: JSONObject): Post {
        return Post(
            id = o.optString("id"),
            title = o.optString("title"),
            description = o.optString("shortPrompt", o.optString("excerpt", o.optString("fullPrompt", ""))),
            image = o.optString("imageUrl", o.optString("featured_image", "")),
            category = o.optString("category", ""),
            author = o.optString("author", ""),
            created_at = o.optString("createdAt", o.optString("created_at", "")),
            likes = o.optInt("likes", 0),
            views = o.optInt("views", 0),
            is_featured = o.optBoolean("isFeatured", o.optBoolean("is_featured", false))
        )
    }

    // -------------------------
    // Mock helpers (safe, no TODOs)
    // -------------------------
    private suspend fun getMockAIPromptsWithFavoriteStatus(): List<AIPrompt> {
        return getMockAIPrompts().map { prompt ->
            val isFavorite = try { favoriteDao.isFavorite(prompt.id) } catch (e: Exception) { false }
            prompt.copy(isFavorite = isFavorite)
        }
    }

    private val photographyTips = listOf(
        "🎯 Use the rule of thirds for better composition",
        "🌅 Golden hour provides the most flattering natural light",
        "👁️ Focus on the eyes in portrait photography",
        "🛤️ Use leading lines to guide the viewer's attention",
        "📐 Experiment with different angles and perspectives",
        "⚪ Learn to use negative space effectively",
        "⚙️ Master manual camera settings for creative control",
        "📖 Practice the art of storytelling through images",
        "🤖 Try AI prompts: 'Professional headshot, soft lighting, clean background'",
        "✨ AI Prompt: 'Cinematic portrait, golden hour, bokeh background'",
        "🎨 Use AI: 'Street photography style, black and white, urban setting'",
        "🌟 AI Magic: 'Fashion photography, dramatic lighting, studio setup'"
    )

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
            isPopular = true,
            isFavorite = false,
            status = "published",
            priority = 0,
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = null
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
            isPopular = true,
            isFavorite = false,
            status = "published",
            priority = 0,
            createdAt = "2024-01-01T00:00:00Z",
            updatedAt = null
        )
    )

    fun getMockFeaturedPosts(): List<Post> = listOf(
        Post(
            id = "1",
            title = "Golden Hour Portrait Mastery",
            description = "Learn the secrets of capturing stunning portraits during golden hour with professional lighting techniques.",
            image = "https://scontent.flko10-1.fna.fbcdn.net/v/t39.30808-6/539403726_759525540326780_2460644145648515262_n.jpg",
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
        )
    )

    fun getMockCategories(): List<Category> = listOf(
        Category("1", "Portrait", "People & Portrait Photography", "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=400", 45),
        Category("2", "Landscape", "Nature & Scenic Photography", "https://images.unsplash.com/photo-1506905925346-21bda4d32df4?w=400", 32)
    )

}
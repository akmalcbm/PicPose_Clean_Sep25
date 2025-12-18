package com.picpose.bestphotographyapp.data.repository

import android.content.Context
import android.util.Log
import com.picpose.bestphotographyapp.data.database.AppDatabase
import com.picpose.bestphotographyapp.data.database.FavoritePromptDao
import com.picpose.bestphotographyapp.data.database.LikedPrompt
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.AppSettings
import com.picpose.bestphotographyapp.data.models.Category
import com.picpose.bestphotographyapp.data.models.CategoryDto
import com.picpose.bestphotographyapp.data.models.DailyTip
import com.picpose.bestphotographyapp.data.models.GuidePost
import com.picpose.bestphotographyapp.data.models.MetaDto
import com.picpose.bestphotographyapp.data.models.toCategory
import com.picpose.bestphotographyapp.data.models.toGuidePost
import com.picpose.bestphotographyapp.data.network.ApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.remote.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import retrofit2.Response
import kotlin.coroutines.cancellation.CancellationException

private const val TAG = "HomeRepository"

// Small wrapper for paginated responses
data class PaginatedResult<T>(val items: List<T>, val meta: MetaDto? = null)

class HomeRepository(
    context: Context,
    // if you want to use mocks set true (keeps backwards compatibility from your old code)
    private val useMocks: Boolean = false,
    // optional override apiKey (if null we rely on RetrofitClient.defaultApiKey)
    apiKey: String? = null,

    // Limit concurrent API calls (protect backend)
    private val apiSemaphore: Semaphore = Semaphore(3)

) {
    private val apiService: ApiService = RetrofitClient.apiService
    private val database = AppDatabase.getDatabase(context)
    private val favoriteDao: FavoritePromptDao = database.favoriteDao()
    private val likedDao = database.likedPromptDao()

    private val appSettingsCache = com.picpose.bestphotographyapp.data.datastore.AppSettingsCache(context)

    // API key to use for all requests - null is acceptable per API definition
    private val requestApiKey: String? = apiKey ?: RetrofitClient.defaultApiKey


    companion object {
        private var lastTotalPrompts: Int = 0

        fun getLastTotalPrompts(): Int = lastTotalPrompts

        fun setLastTotalPrompts(total: Int) {
            lastTotalPrompts = total
        }
    }


    init {
        // optionally set global API key for RetrofitClient
        apiKey?.let { RetrofitClient.defaultApiKey = it }
    }

    // Generic safe API helper (keeps error mapping consistent)
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
        } catch (e: Throwable) {
            if (e is CancellationException) throw e // important: propagate cancellation
            Result.failure(e)
        }
    }


    /**
     * Executes a network call with concurrency limiting and retries (exponential backoff + jitter).
     * The block *must* return a retrofit Response<T>.
     */
    private suspend fun <T> callWithRetries(
        maxRetries: Int = 3,
        initialDelayMs: Long = 400,
        factor: Double = 2.0,
        block: suspend () -> Response<T>
    ): Response<T> {
        var currentDelay = initialDelayMs

        repeat(maxRetries) { attempt ->
            try {
                // Execute the network call within the semaphore
                val resp = apiSemaphore.withPermit { block() }

                // If HTTP 4xx -> don't retry (client error / unauthorized etc.)
                val code = resp.code()
                if (code in 400..499) {
                    Log.w(TAG, "Non-retriable HTTP $code received - not retrying")
                    return resp
                }

                // otherwise return (200 or 5xx -> let caller decide)
                return resp
            } catch (e: Throwable) {
                // If coroutine was cancelled, rethrow immediately so upstream can handle it.
                if (e is CancellationException) {
                    Log.w(TAG, "API call cancelled: ${e.message}")
                    throw e
                }

                // For other exceptions (e.g., IOExceptions), retry after backoff.
                val jitter = kotlin.random.Random.nextLong(0, 200)
                val delayMs = (currentDelay + jitter).coerceAtMost(10_000)
                Log.w(TAG, "API attempt ${attempt + 1} failed: ${e.message}. Retrying in ${delayMs}ms")
                kotlinx.coroutines.delay(delayMs)
                currentDelay = (currentDelay * factor).toLong()
            }
        }

        // Final attempt (let exceptions bubble)
        return apiSemaphore.withPermit { block() }
    }

    // -------------------------
    // DAILY TIPS
    // -------------------------
    // Returns Flow<Result<List<DailyTip>>>
    suspend fun getDailyTips(): Flow<Result<List<DailyTip>>> = flow {
        if (useMocks) {
            // lightweight fallback tips (same as previously used)
            val fallback = listOf(
                DailyTip(id = "fallback_1", tip = "Use specific descriptive words in your prompts for better AI results!", isActive = true, order = 0, createdAt = null, updatedAt = null),
                DailyTip(id = "fallback_2", tip = "Try combining different art styles like 'watercolor meets cyberpunk'!", isActive = true, order = 1, createdAt = null, updatedAt = null),
                DailyTip(id = "fallback_3", tip = "Add lighting conditions like 'golden hour' or 'dramatic shadows' to enhance your images!", isActive = true, order = 2, createdAt = null, updatedAt = null)
            )
            emit(Result.success(fallback))
            return@flow
        }

        // Use callWithRetries wrapped by safeApiCall to get consistent Result<T> mapping
        val apiResult: Result<ApiResponse<List<DailyTip>>> = safeApiCall {
            callWithRetries {
                apiService.getDailyTips(apiKey = requestApiKey)
            }
        }

        apiResult.fold(
            onSuccess = { wrapper ->
                try {
                    val tips = wrapper.data ?: emptyList()
                    emit(Result.success(tips))
                } catch (e: Exception) {
                    emit(Result.failure(e))
                }
            },
            onFailure = { err ->
                emit(Result.failure(err))
            }
        )
    }.flowOn(Dispatchers.IO).catch { e ->
        Log.e(TAG, "getDailyTips flow exception: ${e.message}")
        emit(Result.failure(e))
    }

    // Optionally expose a simpler list-based version (no meta)
    // Optionally expose a simpler list-based version (no meta, but we still read total)
    // simple list-based
    suspend fun getAiPostsSimple(
        page: Int = 1,
        limit: Int = 20,
        category: String? = null,
        search: String? = null
    ): Flow<Result<List<AIPrompt>>> = flow {
        try {
            val offsetValue = (page - 1) * limit

            val response = apiService.getAiPosts(
                apiKey = requestApiKey,
                limit = limit,
                offset = offsetValue,
                category = category,
                q = search,
                status = "published"
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!
                val data = body.data ?: emptyList()
                lastTotalPrompts = body.total ?: data.size      // ✅ save total

                emit(
                    Result.success(
                        enrichWithLocalState(data)
                    )
                )

            } else {
                emit(Result.failure(Exception(response.body()?.message ?: "Unknown API error")))
            }

        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)


    // Also fix getFavoriteCount function:
    suspend fun getFavoriteCount(): Int {
        return try {
            // Already has withContext(Dispatchers.IO) - good!
            withContext(Dispatchers.IO) {
                favoriteDao.getFavoriteCount()
            }
        } catch (e: Exception) {
            Log.e(TAG, "getFavoriteCount exception: ${e.message}")
            0
        }
    }

    suspend fun toggleFavoriteLocal(prompt: AIPrompt): AIPrompt {
        return withContext(Dispatchers.IO) {
            val isFav = favoriteDao.isBookmarked(prompt.id)

            if (isFav) {
                favoriteDao.removeFromFavorites(prompt.id)
            } else {
                favoriteDao.addToFavorites(prompt.toFavoritePrompt())
            }

            prompt.copy(isFavouriteBookmarked = !isFav)
        }
    }



    /**
     * Paginated fetch of AI Prompts (Typed)
     * Updates last total count for UI pagination
     */
    suspend fun getAllAIPromptsTyped(
        page: Int = 1,
        limit: Int = 20,
        category: String? = null,
        search: String? = null
    ): Flow<Result<List<AIPrompt>>> = flow {
        try {
            val offset = (page - 1) * limit

            Log.d(TAG, "getAllAIPromptsTyped => page=$page, limit=$limit, offset=$offset, category=$category, search=$search")

            val response = apiService.getAiPosts(
                apiKey = requestApiKey,
                limit = limit,
                offset = offset,
                category = category,
                q = search,
                status = "published"
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!
                val data = body.data ?: emptyList()

                // ⭐ Update last total count for pagination & UI
                body.total?.let { setLastTotalPrompts(it) }

                // ⭐ Enrich local favorite flags
                val enriched = data.map { p ->
                    val fav = favoriteDao.isBookmarked(p.id)
                    val liked = likedDao.isLiked(p.id)
                    p.copy(
                        isFavouriteBookmarked = fav,
                        isLiked = liked
                    )
                }


                emit(Result.success(enriched))
            } else {
                emit(Result.failure(Exception(response.body()?.message ?: "API error")))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAllAIPromptsTyped exception: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)


    /**
     * Returns favorite prompts stored in Room as AIPrompt objects.
     * Uses the toAIPrompt() extension function for proper mapping.
     */
    suspend fun getFavoritePrompts(): Flow<Result<List<AIPrompt>>> = flow {
        try {
            // Fetch favorites from Room database
            val favEntities = withContext(Dispatchers.IO) {
                favoriteDao.getAllFavorites()
            }

            // Map favorite entity to AIPrompt using extension function
            // This ensures proper field mapping including imageUrl and promptId
            val list = favEntities.map { fav ->
                val liked = likedDao.isLiked(fav.promptId)
                fav.toAIPrompt().copy(
                    isFavouriteBookmarked = true,
                    isLiked = liked
                )

            }

            emit(Result.success(list))
        } catch (e: Exception) {
            Log.e(TAG, "getFavoritePrompts failed: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO).catch { e ->
        Log.e(TAG, "getFavoritePrompts flow exception: ${e.message}")
        emit(Result.failure(e))
    }

    /**
     * Get single prompt by ID - EFFICIENT VERSION
     */
    suspend fun getPromptById(promptId: String): Flow<Result<AIPrompt>> = flow {
        try {
            val response = apiService.getPromptById(
                promptId = promptId,
                apiKey = requestApiKey
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!
                val prompt = body.data ?: throw Exception("Prompt not found")

                // fetch favorite state from Room
                val isFav = withContext(Dispatchers.IO) {
                    favoriteDao.isBookmarked(prompt.id)
                }

                val isLiked = likedDao.isLiked(prompt.id)

                emit(
                    Result.success(
                        prompt.copy(
                            isFavouriteBookmarked = isFav,
                            isLiked = isLiked
                        )
                    )
                )

            } else {
                emit(Result.failure(Exception(response.body()?.message ?: "Prompt not found")))
            }

        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)



    // -------------------------
    // GUIDE POSTS (paginated)
    // -------------------------

    /**
     * Get guide posts with pagination and filtering
     */
    suspend fun getGuidePosts(
        page: Int = 1,
        limit: Int = 10,
        search: String? = null,
        featured: Boolean? = null,
        popular: Boolean? = null,
        status: String? = "published"
    ): kotlinx.coroutines.flow.Flow<Result<PaginatedResult<com.picpose.bestphotographyapp.data.models.GuidePost>>> = kotlinx.coroutines.flow.flow {
        if (useMocks) {
            emit(Result.success(PaginatedResult(items = emptyList(), meta = null)))
            return@flow
        }

        // safeApiCall and callWithRetries helpers exist in this repo
        val apiResult: Result<com.picpose.bestphotographyapp.data.remote.ApiResponse<List<com.picpose.bestphotographyapp.data.models.GuidePostDto>>> = safeApiCall {
            callWithRetries {
                apiService.getGuidePosts(
                    apiKey = requestApiKey,
                    limit = limit,
                    offset = (page - 1) * limit,
                    page = page,
                    q = search,
                    featured = featured,
                    popular = popular,
                    status = status
                )
            }
        }

        apiResult.fold(
            onSuccess = { wrapper ->
                try {
                    val dtos = wrapper.data ?: emptyList()
                    Log.d(TAG, "getGuidePosts: received ${dtos.size} DTOs from API")
                    Log.d(TAG, "getGuidePosts: first DTO sample: ${dtos.firstOrNull()}")

                    val guidePosts = dtos.mapNotNull { dto ->
                        try {
                            val guidePost = dto.toGuidePost("https://picpose.iamakmal.in/")
                            Log.d(TAG, "getGuidePosts: mapped DTO id=${dto.id} to GuidePost id=${guidePost.id}")
                            guidePost
                        } catch (e: Exception) {
                            Log.e(TAG, "getGuidePosts: failed to map DTO id=${dto.id}, error: ${e.message}")
                            null
                        }
                    }
                    Log.d(TAG, "getGuidePosts: successfully mapped ${guidePosts.size} guide posts")

                    // Try to extract meta if present (wrapper may have page/limit/total) - build best-effort MetaDto
                    val meta = try {
                        // try wrapper.page/wrapper.limit/wrapper.total if present
                        val wrapperClass = wrapper::class.java
                        val p = try { wrapperClass.getDeclaredField("page").apply { isAccessible = true }.get(wrapper) as? Int } catch (_: Throwable) { null }
                        val l = try { wrapperClass.getDeclaredField("limit").apply { isAccessible = true }.get(wrapper) as? Int } catch (_: Throwable) { null }
                        val t = try { wrapperClass.getDeclaredField("total").apply { isAccessible = true }.get(wrapper) as? Int } catch (_: Throwable) { null }
                        if (p != null || l != null || t != null) {
                            com.picpose.bestphotographyapp.data.models.MetaDto(
                                total = t ?: guidePosts.size,
                                page = p ?: page,
                                limit = l ?: limit,
                                hasMore = ((p ?: page) * (l ?: limit)) < (t ?: guidePosts.size)
                            )
                        } else null
                    } catch (_: Throwable) { null }

                    emit(Result.success(PaginatedResult(items = guidePosts, meta = meta)))
                } catch (e: Exception) {
                    Log.e(TAG, "getGuidePosts: exception during mapping: ${e.message}")
                    emit(Result.failure(e))
                }
            },
            onFailure = { err ->
                Log.e(TAG, "getGuidePosts: API call failed: ${err.message}")
                emit(Result.failure(err))
            }
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)


    // -------------------------
    // APP SETTINGS (AdMob)
    // -------------------------

    /**
     * Fetch app settings from server for AdMob configuration
     * Uses cache-first strategy for offline support
     */
    suspend fun getAppSettings(forceRefresh: Boolean = false): Flow<Result<AppSettings>> = flow {
        try {
            Log.d(TAG, "Fetching app settings (forceRefresh=$forceRefresh)")

            // Get cached settings once at the start to avoid multiple DataStore reads
            val cachedSettings = withContext(Dispatchers.IO) {
                try {
                    appSettingsCache.cachedSettings.first()
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to read cache: ${e.message}")
                    null
                }
            }

            // Use cache first if not forcing refresh and cache exists
            if (!forceRefresh && cachedSettings != null) {
                Log.d(TAG, "Using cached app settings")
                emit(Result.success(cachedSettings))
            }

            // Fetch from API
            val apiResult = safeApiCall {
                callWithRetries {
                    apiService.getAppSettings(apiKey = requestApiKey)
                }
            }

            apiResult.fold(
                onSuccess = { response ->
                    if (response.success) {
                        Log.d(TAG, "App settings fetched successfully from API")
                        // Save to cache
                        withContext(Dispatchers.IO) {
                            appSettingsCache.saveSettings(response.data)
                        }
                        emit(Result.success(response.data))
                    } else {
                        Log.w(TAG, "Server returned unsuccessful response: ${response.message}")
                        // Use cache on server error if available
                        if (cachedSettings != null) {
                            Log.d(TAG, "Falling back to cached settings after server error")
                            emit(Result.success(cachedSettings))
                        } else {
                            emit(Result.failure(Exception(response.message ?: "Empty app settings response")))
                        }
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to fetch app settings: ${error.message}")
                    // Use cache on network error if available
                    if (cachedSettings != null) {
                        Log.d(TAG, "Falling back to cached settings after network error")
                        emit(Result.success(cachedSettings))
                    } else {
                        emit(Result.failure(error))
                    }
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "getAppSettings exception: ${e.message}")
            // Final fallback to cache
            withContext(Dispatchers.IO) {
                try {
                    appSettingsCache.cachedSettings.first()?.let { cached ->
                        Log.d(TAG, "Using cached settings after exception")
                        emit(Result.success(cached))
                    } ?: emit(Result.failure(e))
                } catch (cacheError: Exception) {
                    Log.e(TAG, "Failed to read cache: ${cacheError.message}")
                    emit(Result.failure(e))
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    // -------------------------
    // CATEGORIES
    // -------------------------

    /**
     * Get all categories for filtering
     */
    suspend fun getCategories(): Flow<Result<List<Category>>> = flow {
        try {
            Log.d(TAG, "getCategories: fetching from API...")

            val apiResult: Result<ApiResponse<List<CategoryDto>>> = safeApiCall {
                callWithRetries {
                    apiService.getCategories(apiKey = requestApiKey)
                }
            }

            apiResult.fold(
                onSuccess = { wrapper ->
                    if (wrapper.success && wrapper.data != null) {
                        val flatList = mutableListOf<CategoryDto>()

                        // Flatten nested children recursively
                        fun flattenCategories(list: List<CategoryDto>) {
                            list.forEach { cat ->
                                flatList.add(cat)
                                cat.children?.let { flattenCategories(it) }
                            }
                        }

                        flattenCategories(wrapper.data)

                        val categories = flatList.map { it.toCategory() }
                        Log.d(TAG, "getCategories: loaded ${categories.size} categories from server")
                        emit(Result.success(categories))
                    } else {
                        Log.w(TAG, "getCategories: empty or invalid response")
                        emit(Result.failure(Exception("No categories available")))
                    }
                },
                onFailure = { err ->
                    Log.e(TAG, "getCategories failed: ${err.message}")
                    emit(Result.failure(err))
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "getCategories exception: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)


    // -------------------------
    // FAVORITES - GUIDE POSTS
    // -------------------------

    /**
     * Toggle guide post favorite status
     */
    suspend fun toggleGuidePostFavorite(postId: String): Flow<Result<GuidePost>> = flow {
        try {
            // For guide posts, we'll use a simple in-memory approach since we don't have a dedicated DAO
            // In a real app, you'd have a GuidePostDao similar to FavoritePromptDao

            // For now, just return a mock updated guide post
            val updatedPost = GuidePost(
                id = postId,
                title = "Sample Guide Post",
                content = "Sample guide content",
                isFavorited = true // fix: use isFavorited instead of isFavorited (it's correct now)
            )

            Log.d(TAG, "Toggled guide post favorite: $postId")
            emit(Result.success(updatedPost))
        } catch (e: Exception) {
            Log.e(TAG, "toggleGuidePostFavorite exception: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)


    /**
     * Fetch the latest 5 AI posts (ordered by created_at DESC)
     */
    suspend fun getLatestRecent5AiPosts(
        limit: Int = 5
    ): Flow<Result<List<AIPrompt>>> = flow {
        Log.d(TAG, "getLatestRecent5AiPosts: fetching latest $limit posts")

        val result: Result<ApiResponse<List<AIPrompt>>> = safeApiCall {
            callWithRetries {
                apiService.getLatestRecent5AiPosts(
                    apiKey = requestApiKey,
                    limit = limit,
                    order = "desc"
                )
            }
        }

        result.fold(
            onSuccess = { wrapper ->
                val list = wrapper.data ?: emptyList()
                val sorted = list.sortedByDescending { it.createdAt ?: "" }
                emit(Result.success(sorted.take(limit)))
            },
            onFailure = { error ->
                Log.e(TAG, "getLatestRecent5AiPosts failed: ${error.message}")
                emit(Result.failure(error))
            }
        )
    }.flowOn(Dispatchers.IO)


    /**
     * Fetch Most Liked AI posts
     * Uses your new get_ai_post_by_likes.php endpoint
     */
    suspend fun getMostLikedAiPosts(
        limit: Int = 20,
        offset: Int = 0
    ): Flow<Result<List<AIPrompt>>> = flow {
        val apiResult: Result<ApiResponse<List<AIPrompt>>> = safeApiCall {
            callWithRetries {
                apiService.getMostLikedAiPosts(
                    apiKey = requestApiKey,
                    limit = limit,
                    offset = offset
                )
            }
        }

        apiResult.fold(
            onSuccess = { wrapper ->
                val list = wrapper.data ?: emptyList()
                emit(
                    Result.success(
                        enrichWithLocalState(list)
                    )
                )

            },
            onFailure = { err ->
                emit(Result.failure(err))
            }
        )
    }.flowOn(Dispatchers.IO)


    /**
     * ✅ Fetch Trending AI posts (based on engagement: likes + favorites > 0)
     */
    suspend fun getTrendingAiPosts(
        limit: Int = 20,
        offset: Int = 0
    ): Flow<Result<List<AIPrompt>>> = flow {
        val apiResult: Result<ApiResponse<List<AIPrompt>>> = safeApiCall {
            callWithRetries {
                apiService.getTrendingAiPosts(
                    apiKey = requestApiKey,
                    type = "popular", // still acceptable, ignored by backend now
                    limit = limit,
                    offset = offset
                )
            }
        }

        apiResult.fold(
            onSuccess = { wrapper ->
                emit(Result.success(enrichWithLocalState(wrapper.data ?: emptyList()))) },

            onFailure =
                { err -> emit(Result.failure(err)) }
        )
    }.flowOn(Dispatchers.IO)


    /**
     * ✅ Fetch Featured AI posts (admin marked is_featured = 1)
     */
    suspend fun getFeaturedAiPosts(
        limit: Int = 20,
        offset: Int = 0
    ): Flow<Result<List<AIPrompt>>> = flow {
        Log.d(TAG, "getFeaturedAiPosts: fetching featured posts (is_featured = 1)")

        val apiResult: Result<ApiResponse<List<AIPrompt>>> = safeApiCall {
            callWithRetries {
                apiService.getAiPosts(
                    apiKey = requestApiKey,
                    limit = limit,
                    offset = offset,
                    featured = true, // ✅ this filter actually exists in your API call
                    status = "published"
                )
            }
        }

        apiResult.fold(
            onSuccess = { wrapper ->
                emit(
                    Result.success(
                        enrichWithLocalState(wrapper.data ?: emptyList())
                    )
                )
            },
            onFailure = { err ->
                Log.e(TAG, "getFeaturedAiPosts failed: ${err.message}")
                emit(Result.failure(err))
            }
        )

    }.flowOn(Dispatchers.IO)

    /**
     * ✅ Fetch Popular AI posts (admin marked is_popular = 1)
     */
    suspend fun getPopularAiPosts(
        limit: Int = 20,
        offset: Int = 0
    ): Flow<Result<List<AIPrompt>>> = flow {
        Log.d(TAG, "getPopularAiPosts: fetching admin marked popular posts (is_popular = 1)")

        val apiResult: Result<ApiResponse<List<AIPrompt>>> = safeApiCall {
            callWithRetries {
                apiService.getAiPosts(
                    apiKey = requestApiKey,
                    limit = limit,
                    offset = offset,
                    popular = true, // ✅ your API supports this param
                    status = "published"
                )
            }
        }

        apiResult.fold(
            onSuccess = { wrapper ->
                emit(
                    Result.success(
                        enrichWithLocalState(wrapper.data ?: emptyList())
                    )
                )
            },
            onFailure = { err ->
                Log.e(TAG, "getPopularAiPosts failed: ${err.message}")
                emit(Result.failure(err))
            }
        )
    }.flowOn(Dispatchers.IO)



    /**
     * ✅ Fetch Similar AI posts in Horizonal Lists at the Bottom (Show on AIPromptDetailScreen.kt)
     */
    suspend fun getSimilarAiPrompts(
        category: String,
        excludePromptId: String,
        limit: Int = 10
    ): Flow<Result<List<AIPrompt>>> = flow {
        try {
            val response = apiService.getAiPosts(
                apiKey = requestApiKey,
                limit = limit + 1,          // extra for exclusion
                offset = 0,
                category = category,
                status = "published"
            )

            if (response.isSuccessful && response.body()?.success == true) {
                val body = response.body()!!
                val data = body.data ?: emptyList()

                val filtered = data
                    .filter { it.id != excludePromptId }
                    .take(limit)

                emit(
                    Result.success(
                        enrichWithLocalState(filtered)
                    )
                )

            } else {
                emit(Result.failure(Exception(response.body()?.message ?: "API error")))
            }

        } catch (e: Exception) {
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)


    // 🔥 SINGLE SOURCE OF TRUTH — Local Like + Favorite merge
    private suspend fun enrichWithLocalState(
        list: List<AIPrompt>
    ): List<AIPrompt> {
        return withContext(Dispatchers.IO) {
            list.map { prompt ->
                val fav = favoriteDao.isBookmarked(prompt.id)
                val liked = likedDao.isLiked(prompt.id)

                prompt.copy(
                    isFavouriteBookmarked = fav,
                    isLiked = liked
                )
            }
        }
    }

    suspend fun toggleLikeLocal(prompt: AIPrompt): AIPrompt {
        return withContext(Dispatchers.IO) {

            val isLiked = likedDao.isLiked(prompt.id)

            if (isLiked) {
                likedDao.removeLiked(prompt.id)   // ✅ correct
            } else {
                likedDao.addLiked(
                    LikedPrompt(promptId = prompt.id) // ✅ correct
                )
            }

            prompt.copy(isLiked = !isLiked)
        }
    }



}

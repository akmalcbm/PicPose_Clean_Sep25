package com.picpose.bestphotographyapp.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.picpose.bestphotographyapp.data.database.AppDatabase
import com.picpose.bestphotographyapp.data.database.FavoritePromptDao
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.AppSettings
import com.picpose.bestphotographyapp.data.models.DailyTip
import com.picpose.bestphotographyapp.data.models.GuidePost
import com.picpose.bestphotographyapp.data.models.GuidePostDto
import com.picpose.bestphotographyapp.data.models.MetaDto
import com.picpose.bestphotographyapp.data.models.toGuidePost
import com.picpose.bestphotographyapp.data.network.ApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.remote.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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
    private val gson = Gson()

    init {
        // optionally set global API key for RetrofitClient (used by interceptor)
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
                apiService.getDailyTips()
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

    // -------------------------
    // AI POSTS (paginated)
    // -------------------------
    // Returns PaginatedResult<AIPrompt> with favorite flags checked from Room
    suspend fun getAiPosts(
        page: Int = 1,
        limit: Int = 20,
        category: String? = null,
        search: String? = null,
        tag: String? = null,
        popular: Boolean? = null,
        featured: Boolean? = null,
        status: String? = "published"
    ): Flow<Result<PaginatedResult<AIPrompt>>> = flow {
        if (useMocks) {
            // if you keep mock provider in project reuse it; here we return empty for brevity
            emit(Result.success(PaginatedResult(items = emptyList(), meta = null)))
            return@flow
        }

        Log.d(TAG, "Fetching AI posts: page=$page limit=$limit category=$category search=$search tag=$tag popular=$popular featured=$featured status=$status")

        // Use retries + concurrency limiting for the network call
        val apiResult: Result<ApiResponse<List<AIPrompt>>> = safeApiCall {
            callWithRetries {
                apiService.getAiPosts(
                    apiKey = null, // RetrofitClient.defaultApiKey used by interceptor; pass non-null to override
                    limit = limit,
                    offset = (page - 1) * limit,
                    q = search,
                    category = category,
                    tag = tag,
                    popular = popular,
                    status = status,
                    featured = featured
                )
            }
        }

        apiResult.fold(
            onSuccess = { wrapper ->
                try {
                    // wrapper.data may already be List<AIPrompt> (typed by Retrofit) or generic objects - handle both
                    val rawList = wrapper.data
                    val prompts: List<AIPrompt> = when {
                        rawList == null -> emptyList()
                        rawList.isEmpty() -> emptyList()
                        // If already parsed as AIPrompt (common case)
                        rawList.first() is AIPrompt -> rawList.filterIsInstance<AIPrompt>()
                        else -> {
                            // fallback: convert each element via Gson (handles LinkedTreeMap etc)
                            rawList.mapNotNull { elem ->
                                try {
                                    val json = gson.toJson(elem)
                                    gson.fromJson(json, AIPrompt::class.java)
                                } catch (_: Exception) {
                                    null
                                }
                            }
                        }
                    }

                    // Enrich with favorite status from Room (safely)
                    val enriched = prompts.map { p ->
                        val fav = try { favoriteDao.isFavorite(p.id) } catch (_: Exception) { false }
                        p.copy(isFavorite = fav)
                    }

                    // Try to extract meta if present in wrapper (if ApiResponse supports meta)
                    val meta: MetaDto? = try {
                        // wrapper may have 'meta' property (Retrofit typed ApiResponse could include it)
                        val metaField = wrapper.javaClass.getDeclaredField("meta").also { it.isAccessible = true }
                        metaField.get(wrapper) as? MetaDto
                    } catch (_: Exception) {
                        null
                    }

                    emit(Result.success(PaginatedResult(items = enriched, meta = meta)))
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing AI posts: ${e.message}")
                    emit(Result.failure(e))
                }
            },
            onFailure = { err ->
                Log.w(TAG, "getAiPosts failed: ${err.message}")
                emit(Result.failure(err))
            }
        )
    }.flowOn(Dispatchers.IO).catch { e ->
        Log.e(TAG, "getAiPosts flow exception: ${e.message}")
        emit(Result.failure(e))
    }

    // Optionally expose a simpler list-based version (no meta)
    suspend fun getAiPostsSimple(
        page: Int = 1,
        limit: Int = 20,
        category: String? = null,
        search: String? = null
    ): Flow<Result<List<AIPrompt>>> = flow {
        val res = getAiPosts(page = page, limit = limit, category = category, search = search)
        var emitted = false
        res.collect { r ->
            r.fold(
                onSuccess = { pag ->
                    emit(Result.success(pag.items))
                    emitted = true
                },
                onFailure = { err ->
                    emit(Result.failure(err))
                    emitted = true
                }
            )
        }
        if (!emitted) emit(Result.success(emptyList()))
    }.flowOn(Dispatchers.IO).catch { e ->
        Log.e(TAG, "getAiPostsSimple flow exception: ${e.message}")
        emit(Result.failure(e))
    }


    // Replace your current toggleFavorite function with this FIXED version:

    fun toggleFavorite(prompt: com.picpose.bestphotographyapp.data.models.AIPrompt): kotlinx.coroutines.flow.Flow<Result<Boolean>> = flow {
        try {
            // ✅ FIXED: Ensure all database operations happen on IO dispatcher
            val currentlyFavorite = withContext(Dispatchers.IO) {
                favoriteDao.isFavorite(prompt.id)
            }

            if (currentlyFavorite) {
                withContext(Dispatchers.IO) {
                    favoriteDao.removeFromFavorites(prompt.id)
                }
                emit(Result.success(false))
            } else {
                withContext(Dispatchers.IO) {
                    favoriteDao.addToFavorites(prompt.toFavoritePrompt())
                }
                emit(Result.success(true))
            }
        } catch (e: Exception) {
            Log.e(TAG, "toggleFavorite exception: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO) // ✅ CRITICAL: Ensure entire flow runs on IO dispatcher

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


    /**
     * Returns a typed list flow similar to older API naming used in ViewModel.
     * Internally uses getAiPostsSimple.
     */
    suspend fun getAllAIPromptsTyped(
        page: Int = 1,
        limit: Int = 20,
        category: String? = null,
        search: String? = null,
        tag: String? = null
    ): Flow<Result<List<AIPrompt>>> = flow {
        try {
            val inner = getAiPostsSimple(page = page, limit = limit, category = category, search = search)
            inner.collect { r ->
                r.fold(
                    onSuccess = { list -> emit(Result.success(list)) },
                    onFailure = { err -> emit(Result.failure(err)) }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAllAIPromptsTyped exception: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO).catch { e ->
        Log.e(TAG, "getAllAIPromptsTyped flow exception: ${e.message}")
        emit(Result.failure(e))
    }

    /**
     * Alias for paginated get - matches ViewModel expected name getAllAIPrompts.
     */
    suspend fun getAllAIPrompts(
        page: Int = 1,
        limit: Int = 20,
        category: String? = null,
        search: String? = null,
        tag: String? = null,
        popular: Boolean? = null,
        featured: Boolean? = null,
        status: String? = "published"
    ): Flow<Result<PaginatedResult<AIPrompt>>> {
        // reuse existing getAiPosts implementation (signature matches closely)
        return getAiPosts(
            page = page,
            limit = limit,
            category = category,
            search = search,
            tag = tag,
            popular = popular,
            featured = featured,
            status = status
        )
    }

    /**
     * Alias for simple list - matches ViewModel expected name getAllAIPromptsSimple.
     */
    suspend fun getAllAIPromptsSimple(
        page: Int = 1,
        limit: Int = 20,
        category: String? = null,
        search: String? = null
    ): Flow<Result<List<AIPrompt>>> {
        return getAiPostsSimple(page = page, limit = limit, category = category, search = search)
    }

    /**
     * Returns favorite prompts stored in Room as AIPrompt objects.
     * IMPORTANT: this assumes your FavoritePromptDao exposes a method like `getAllFavorites(): List<FavoritePrompt>`
     * and that FavoritePrompt contains at least the fields needed to map back to AIPrompt.
     *
     * If your DAO method name or favorite entity structure differs, adjust mapping accordingly.
     */
    suspend fun getFavoritePrompts(): Flow<Result<List<AIPrompt>>> = flow {
        try {
            // Try DAO method - adjust name if your DAO uses different function
            val favEntities = try {
                // Example DAO method - change if different
                favoriteDao.getAllFavorites()
            } catch (e: NoSuchMethodError) {
                // Fallback: if DAO doesn't provide batch fetch, try returning empty list
                emptyList()
            }

            // Map favorite entity to AIPrompt (best-effort). Adjust fields if your FavoritePrompt entity field names differ.
            val list = favEntities.mapNotNull { fav ->
                try {
                    AIPrompt(
                        id = fav.id.toString(),              // convert Long → String
                        title = fav.title ?: "",             // handle nullable
                        shortPrompt = fav.shortPrompt ?: "", // handle nullable
                        fullPrompt = fav.fullPrompt ?: "",   // handle nullable
                        category = fav.category,             // already String? matches AIPrompt
                        tags = fav.tags ?: emptyList(),      // handle nullable List<String>?
                        isFavorite = true,
                        createdAt = null,                    // not available in FavoritePrompt
                        updatedAt = null                     // not available in FavoritePrompt
                        // add/adjust any other AIPrompt fields if your model has more
                    )
                } catch (_: Exception) {
                    null
                }
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
            // ✅ If you have a single prompt API endpoint, use it:
            // GET /api/get_ai_post.php?id={promptId}&api_key={key}

            val response = apiSemaphore.withPermit {
                // Replace with your actual single prompt API call
                apiService.getAiPosts(
                    apiKey = null, // Uses default from interceptor
                    limit = 1,
                    offset = 0,
                    q = promptId // or however your API filters by ID
                )
            }

            val result = safeApiCall { response }
            result.fold(
                onSuccess = { wrapper ->
                    val prompts = wrapper.data?.firstOrNull()
                    if (prompts != null) {
                        emit(Result.success(prompts))
                    } else {
                        emit(Result.failure(Exception("Prompt not found")))
                    }
                },
                onFailure = { error ->
                    emit(Result.failure(error))
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "getPromptById exception: ${e.message}")
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
                    apiKey = null,
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
                    val guidePosts = dtos.mapNotNull { dto ->
                        try { dto.toGuidePost() } catch (_: Exception) { null }
                    }
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
                    emit(Result.failure(e))
                }
            },
            onFailure = { err ->
                emit(Result.failure(err))
            }
        )
    }.flowOn(kotlinx.coroutines.Dispatchers.IO)

    /**
     * Get single guide post by ID
     */
    suspend fun getGuidePostById(guidePostId: String): Flow<Result<GuidePost>> = flow {
        try {
            val response = apiSemaphore.withPermit {
                apiService.getGuidePostById(
                    guidePostId = guidePostId,
                    apiKey = null // Uses default from interceptor
                )
            }

            val result = safeApiCall { response }
            result.fold(
                onSuccess = { wrapper ->
                    val guidePostDto = wrapper.data
                    if (guidePostDto != null) {
                        emit(Result.success(guidePostDto.toGuidePost()))
                    } else {
                        emit(Result.failure(Exception("Guide post not found")))
                    }
                },
                onFailure = { error ->
                    emit(Result.failure(error))
                }
            )

        } catch (e: Exception) {
            Log.e(TAG, "getGuidePostById exception: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

    // -------------------------
    // APP SETTINGS (AdMob)
    // -------------------------
    
    /**
     * Fetch app settings from server for AdMob configuration
     */
    suspend fun getAppSettings(): Flow<Result<AppSettings>> = flow {
        try {
            Log.d(TAG, "Fetching app settings from server")
            
            val apiResult = safeApiCall {
                callWithRetries {
                    apiService.getAppSettings()
                }
            }
            
            apiResult.fold(
                onSuccess = { response ->
                    if (response.success && response.data != null) {
                        Log.d(TAG, "App settings fetched successfully")
                        emit(Result.success(response.data))
                    } else {
                        Log.w(TAG, "Server returned empty app settings: ${response.message}")
                        emit(Result.failure(Exception("Empty app settings response")))
                    }
                },
                onFailure = { error ->
                    Log.e(TAG, "Failed to fetch app settings: ${error.message}")
                    emit(Result.failure(error))
                }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "getAppSettings exception: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO)

}

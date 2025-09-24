package com.picpose.bestphotographyapp.data.repository

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.picpose.bestphotographyapp.data.database.AppDatabase
import com.picpose.bestphotographyapp.data.database.FavoritePromptDao
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.DailyTip
import com.picpose.bestphotographyapp.data.models.MetaDto
import com.picpose.bestphotographyapp.data.network.ApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.data.remote.ApiResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import retrofit2.Response
import kotlin.coroutines.cancellation.CancellationException
import kotlin.random.Random

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


    // Toggle favorite (returns Flow<Result<Boolean>>) — existing logic you had previously
    // Keeps using Room FavoritePromptDao and existing toFavorite mapping.
    fun toggleFavorite(prompt: com.picpose.bestphotographyapp.data.models.AIPrompt) : kotlinx.coroutines.flow.Flow<Result<Boolean>> = flow {
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
            Log.e(TAG, "toggleFavorite exception: ${e.message}")
            emit(Result.failure(e))
        }
    }

    // getFavoriteCount() - returns the count of favorites (suspend)
    suspend fun getFavoriteCount(): Int {
        return try {
            // Ensure DAO access runs on IO dispatcher to avoid "Cannot access database on the main thread"
            withContext(Dispatchers.IO) {
                favoriteDao.getFavoriteCount()
            }
        } catch (e: Exception) {
            Log.w(TAG, "getFavoriteCount failed: ${e.message}")
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
     * Try to fetch a single prompt by id.
     * Attempt typed single-endpoint first (if you have one), then fallback to searching pages.
     */
    suspend fun getPromptById(promptId: String): Flow<Result<AIPrompt>> = flow {
        var found: AIPrompt? = null
        var lastError: Exception? = null

        try {
            // If your ApiService has a single-item endpoint, call it here.
            // Example: apiService.getAiPostById(apiKey = null, id = promptId)
            // If you don't have such endpoint, skip to list-based searches below.

            // Fallback: search in favorites first (fast)
            try {
                val favs = getFavoritePrompts()
                favs.collect { res ->
                    res.onSuccess { list ->
                        found = list.find { it.id == promptId } ?: found
                    }
                }
            } catch (_: Exception) {
                // ignore
            }

            // Fallback: search in simple list (pages)
            if (found == null) {
                val simpleFlow = getAiPostsSimple(page = 1, limit = 200)
                simpleFlow.collect { res ->
                    res.fold(onSuccess = { list ->
                        found = list.find { it.id == promptId } ?: found
                    }, onFailure = { err -> lastError = err as? Exception ?: lastError })
                }
            }

            // Fallback: try paginated fetch (single page)
            if (found == null) {
                val pagFlow = getAiPosts(page = 1, limit = 200)
                pagFlow.collect { pres ->
                    pres.fold(onSuccess = { pag ->
                        found = pag.items.find { it.id == promptId } ?: found
                    }, onFailure = { err -> lastError = err as? Exception ?: lastError })
                }
            }

            if (found != null) emit(Result.success(found!!))
            else emit(Result.failure(lastError ?: Exception("Prompt not found")))

        } catch (e: Exception) {
            Log.e(TAG, "getPromptById exception: ${e.message}")
            emit(Result.failure(e))
        }
    }.flowOn(Dispatchers.IO).catch { e ->
        Log.e(TAG, "getPromptById flow exception: ${e.message}")
        emit(Result.failure(e))
    }

}

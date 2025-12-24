package com.picpose.bestphotographyapp.data

import android.util.Log
import com.picpose.bestphotographyapp.data.models.AIPrompt
import com.picpose.bestphotographyapp.data.models.Admob
import com.picpose.bestphotographyapp.data.models.AppSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PromptRepository
 *
 * 🔥 SINGLE SOURCE OF TRUTH for all prompts (in-memory cache)
 * 🔥 Used by EngagementRepository for Favorites screen
 * 🔥 Safe for long-term & API migration
 */
@Singleton
class PromptRepository @Inject constructor() {

    companion object {
        private const val TAG = "PromptRepository"
        private const val TEST_NATIVE_AD_ID =
            "ca-app-pub-3940256099942544/2247696110"
    }

    /* ---------------------------------------------------------------------- */
    /* 🔥 IN-MEMORY CACHE (SOURCE OF TRUTH) */
    /* ---------------------------------------------------------------------- */

    private val _promptState = MutableStateFlow<List<AIPrompt>>(emptyList())
    val promptState: StateFlow<List<AIPrompt>> = _promptState.asStateFlow()

    /**
     * 🔥 EngagementRepository uses this
     */
    fun observeAllPrompts(): StateFlow<List<AIPrompt>> {
        Log.e("FAV_DEBUG", "observeAllPrompts() called, size = ${_promptState.value.size}")
        return promptState
    }


    /**
     * 🔥 Called by ViewModel after API fetch
     */
    fun syncPromptCache(prompts: List<AIPrompt>) {
        Log.e("FAV_DEBUG", "syncPromptCache() called with ${prompts.size} prompts")
        _promptState.value = prompts
    }


    /* ---------------------------------------------------------------------- */
    /* MOCK DATA (DEV / DEMO) */
    /* ---------------------------------------------------------------------- */

    private val mockPrompts = listOf(
        AIPrompt(
            id = "1",
            title = "Sunset Portrait Photography",
            shortPrompt = "Professional portrait during golden hour",
            fullPrompt = "Professional portrait photography of a person during golden hour...",
            imageUrl = "https://picsum.photos/400/600?random=1",
            category = "Portrait",
            tags = listOf("portrait", "sunset"),
            likes = 245,
            isPopular = true
        ),
        AIPrompt(
            id = "2",
            title = "Mountain Landscape",
            shortPrompt = "Epic mountain landscape",
            fullPrompt = "Epic mountain landscape photography at sunrise...",
            imageUrl = "https://picsum.photos/400/600?random=2",
            category = "Landscape",
            tags = listOf("mountain", "sunrise"),
            likes = 189,
            isPopular = true
        )
        // 👉 keep adding if needed
    )

    init {
        // 🔥 Seed cache ONCE (important)
        _promptState.value = mockPrompts
    }

    /* ---------------------------------------------------------------------- */
    /* READ APIs — ALWAYS USE CACHE */
    /* ---------------------------------------------------------------------- */

    suspend fun getPromptById(promptId: String): Flow<Result<AIPrompt>> = flow {
        delay(300)

        val prompt = _promptState.value.find { it.id == promptId }
        if (prompt != null) {
            emit(Result.success(prompt))
        } else {
            emit(Result.failure(Exception("Prompt not found")))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun getSimilarPrompts(
        category: String,
        excludeId: String,
        limit: Int = 10
    ): Flow<Result<List<AIPrompt>>> = flow {
        delay(300)

        val similar = _promptState.value
            .filter { it.category == category && it.id != excludeId }
            .take(limit)

        emit(Result.success(similar))
    }.flowOn(Dispatchers.IO)

    suspend fun getAllPrompts(
        page: Int = 1,
        limit: Int = 20
    ): Flow<Result<List<AIPrompt>>> = flow {
        delay(400)

        val start = (page - 1) * limit
        val end = minOf(start + limit, _promptState.value.size)

        if (start < _promptState.value.size) {
            emit(Result.success(_promptState.value.subList(start, end)))
        } else {
            emit(Result.success(emptyList()))
        }
    }.flowOn(Dispatchers.IO)

    /* ---------------------------------------------------------------------- */
    /* APP SETTINGS (MOCK) */
    /* ---------------------------------------------------------------------- */

    suspend fun getAppSettings(): Flow<Result<AppSettings>> = flow {
        delay(200)

        emit(
            Result.success(
                AppSettings(
                    admob = Admob(
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
                )
            )
        )
    }.flowOn(Dispatchers.IO)

    /* ---------------------------------------------------------------------- */
    /* ⚠️ NOTE */
    /* ---------------------------------------------------------------------- */
    /*
     * ❌ No toggleFavorite() here
     * 👉 Favorites are handled ONLY by Room (EngagementRepository)
     * 👉 This avoids double source of truth bugs
     */
}

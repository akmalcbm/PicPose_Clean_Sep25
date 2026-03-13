/**
 * ---
 * File: GuidePostViewModel.kt
 * Layer: Presentation (MVVM)
 * Project: PicPose
 *
 * Purpose:
 * Owns screen state and coordinates the MVVM flow between Compose UI and repository/data operations.
 *
 * Interactions:
 * Observed by Compose screens. It transforms repository results into StateFlow values that the UI collects.
 *
 * Data Flow:
 * UI (Compose) -> ViewModel -> Repository -> Local/Remote Data Source -> Room/API
 *
 * Maintainer Notes:
 * - Expose observable UI state here, but keep composable rendering decisions in the UI layer.
 * - Business rules belong in repositories or dedicated domain classes if the project introduces use cases later.
 * ---
 */

package com.picpose.bestphotographyapp.presentation.viewmodels

import android.content.Context
import android.text.Html
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.core.analytics.AnalyticsLogger
import com.picpose.bestphotographyapp.core.crash.CrashReporter
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.datastore.DeviceIdStore
import com.picpose.bestphotographyapp.data.models.GuideContentBlock
import com.picpose.bestphotographyapp.data.models.GuidePost
import com.picpose.bestphotographyapp.data.repository.HomeRepository
import com.picpose.bestphotographyapp.core.utils.MediaUrlResolver
import com.picpose.bestphotographyapp.core.utils.ShareUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "GuidePostViewModel"

data class GuidePostUiState(
    val isLoading: Boolean = false,
    val guidePosts: List<GuidePost> = emptyList(),
    val selectedGuidePost: GuidePost? = null,
    val blocks: List<GuideContentBlock> = emptyList(),
    val isLiked: Boolean = false,
    val displayLikes: Int = 0,
    val displayViews: Int = 0,
    val readMinutes: Int = 0,
    val error: String? = null,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class GuidePostViewModel @Inject constructor(
    private val repository: HomeRepository,
    private val deviceIdStore: DeviceIdStore,
    private val analyticsLogger: AnalyticsLogger,
    private val crashReporter: CrashReporter,
    @ApplicationContext private val appContext: Context
) : ViewModel() {
    private val viewedInSession = mutableSetOf<String>()

    private val _uiState = MutableStateFlow(GuidePostUiState())
    val uiState: StateFlow<GuidePostUiState> = _uiState.asStateFlow()

    init {
        loadGuidePosts()
    }

    fun loadGuidePosts(forceRefresh: Boolean = false) {
        if (!forceRefresh && _uiState.value.guidePosts.isNotEmpty()) {
            return // Already loaded
        }

        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)

                repository.getGuidePosts().collect { result ->
                    result.fold(
                        onSuccess = { pagination ->
                            Log.d(TAG, "loadGuidePosts: loaded ${pagination.items.size} guide posts")
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                guidePosts = pagination.items,
                                blocks = emptyList(),
                                isLiked = false,
                                displayLikes = 0,
                                displayViews = 0,
                                readMinutes = 0,
                                error = null
                            )
                        },
                        onFailure = { exception ->
                            Log.e(TAG, "loadGuidePosts: failed", exception)
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = exception.message ?: appContext.getString(R.string.failed_to_load_guide_posts)
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "loadGuidePosts: unexpected error", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message ?: appContext.getString(R.string.unexpected_error_occurred)
                )
            }
        }
    }

    fun refresh() {
        _uiState.value = _uiState.value.copy(isRefreshing = true)
        loadGuidePosts(forceRefresh = true)
        _uiState.value = _uiState.value.copy(isRefreshing = false)
    }

    fun findGuidePostById(id: String): GuidePost? {
        return _uiState.value.guidePosts.find { it.id == id }
    }

    fun loadGuidePostById(id: String) {
        viewModelScope.launch {
            try {
                _uiState.value = _uiState.value.copy(isLoading = true, error = null)
                val deviceId = runCatching { deviceIdStore.getOrCreateDeviceId() }.getOrNull()
                repository.getGuidePostById(id, deviceId = deviceId).collect { result ->
                    result.fold(
                        onSuccess = { post ->
                            val renderedBlocks = if (post.contentBlocks.isNotEmpty()) {
                                post.contentBlocks
                            } else {
                                buildFallbackBlocks(post)
                            }
                            val serverLiked = post.isLiked
                            val displayLikes = post.likes.coerceAtLeast(0)
                            val displayViews = post.views.coerceAtLeast(0)
                            val readMinutes = estimateReadMinutes(
                                buildReadableContent(post, renderedBlocks)
                            )
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                selectedGuidePost = post,
                                blocks = renderedBlocks,
                                isLiked = serverLiked,
                                displayLikes = displayLikes,
                                displayViews = displayViews,
                                readMinutes = readMinutes,
                                error = null,
                                guidePosts = if (_uiState.value.guidePosts.any { it.id == post.id }) {
                                    _uiState.value.guidePosts.map {
                                        if (it.id == post.id) post else it
                                    }
                                } else {
                                    _uiState.value.guidePosts + post
                                }
                            )
                            analyticsLogger.logGuideView(post.id, post.category)
                            registerGuideView(post.id)
                        },
                        onFailure = { exception ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                blocks = emptyList(),
                                isLiked = false,
                                displayLikes = 0,
                                displayViews = 0,
                                readMinutes = 0,
                                error = exception.message ?: appContext.getString(R.string.guide_post_not_found)
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                crashReporter.recordUnexpectedNetworkFailure("guide_detail_load", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    blocks = emptyList(),
                    isLiked = false,
                    displayLikes = 0,
                    displayViews = 0,
                    readMinutes = 0,
                    error = e.message ?: appContext.getString(R.string.unexpected_error_occurred)
                )
            }
        }
    }

    fun toggleFavorite(guidePost: GuidePost) {
        viewModelScope.launch {
            try {
                val updatedGuidePosts = _uiState.value.guidePosts.map { guide ->
                    if (guide.id == guidePost.id) {
                        guide.copy(isFavorited = !guide.isFavorited)
                    } else {
                        guide
                    }
                }
                _uiState.value = _uiState.value.copy(guidePosts = updatedGuidePosts)
                _uiState.value.selectedGuidePost?.let { selected ->
                    if (selected.id == guidePost.id) {
                        _uiState.value = _uiState.value.copy(
                            selectedGuidePost = selected.copy(isFavorited = !selected.isFavorited)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "toggleFavorite: failed", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun toggleGuidePostLike(guidePostId: String) {
        viewModelScope.launch {
            try {
                val current = _uiState.value
                val selected = current.selectedGuidePost ?: return@launch
                if (selected.id != guidePostId) return@launch
                val guideIdInt = guidePostId.toIntOrNull() ?: run {
                    _uiState.value = current.copy(error = appContext.getString(R.string.unexpected_error_occurred))
                    return@launch
                }

                val wasLiked = current.isLiked
                val previousLikes = current.displayLikes.coerceAtLeast(0)
                val optimisticLiked = !wasLiked
                val optimisticLikes = (previousLikes + if (optimisticLiked) 1 else -1).coerceAtLeast(0)

                _uiState.value = current.copy(
                    selectedGuidePost = selected.copy(isLiked = optimisticLiked, likes = optimisticLikes),
                    guidePosts = current.guidePosts.map { guide ->
                        if (guide.id == guidePostId) guide.copy(isLiked = optimisticLiked, likes = optimisticLikes) else guide
                    },
                    isLiked = optimisticLiked,
                    displayLikes = optimisticLikes
                )

                val deviceId = deviceIdStore.getOrCreateDeviceId()
                val serverState = repository.toggleGuideLike(guideIdInt, deviceId)
                _uiState.value = _uiState.value.copy(
                    selectedGuidePost = selected.copy(isLiked = serverState.isLiked, likes = serverState.likesTotal),
                    guidePosts = _uiState.value.guidePosts.map { guide ->
                        if (guide.id == guidePostId) {
                            guide.copy(isLiked = serverState.isLiked, likes = serverState.likesTotal)
                        } else {
                            guide
                        }
                    },
                    isLiked = serverState.isLiked,
                    displayLikes = serverState.likesTotal.coerceAtLeast(0)
                )
                analyticsLogger.logGuideLike(guidePostId)
            } catch (e: Exception) {
                Log.e(TAG, "toggleGuidePostLike: failed", e)
                crashReporter.recordUnexpectedNetworkFailure("guide_like_toggle", e)
                val current = _uiState.value
                val selected = current.selectedGuidePost
                if (selected != null && selected.id == guidePostId) {
                    val rollbackLiked = !current.isLiked
                    val rollbackLikes = (current.displayLikes + if (rollbackLiked) 1 else -1).coerceAtLeast(0)
                    _uiState.value = current.copy(
                        selectedGuidePost = selected.copy(isLiked = rollbackLiked, likes = rollbackLikes),
                        guidePosts = current.guidePosts.map { guide ->
                            if (guide.id == guidePostId) guide.copy(isLiked = rollbackLiked, likes = rollbackLikes) else guide
                        },
                        isLiked = rollbackLiked,
                        displayLikes = rollbackLikes,
                        error = e.message ?: appContext.getString(R.string.error)
                    )
                } else {
                    _uiState.value = _uiState.value.copy(error = e.message ?: appContext.getString(R.string.error))
                }
            }
        }
    }

    fun registerGuideView(guidePostId: String) {
        if (guidePostId.isBlank() || viewedInSession.contains(guidePostId)) return
        viewModelScope.launch {
            try {
                val current = _uiState.value
                val selected = current.selectedGuidePost ?: return@launch
                if (selected.id != guidePostId) return@launch

                viewedInSession.add(guidePostId)
                val guideIdInt = guidePostId.toIntOrNull() ?: return@launch
                val serverViews = repository.incrementGuideView(guideIdInt).coerceAtLeast(0)

                _uiState.value = current.copy(
                    selectedGuidePost = selected.copy(views = serverViews, viewCount = serverViews),
                    guidePosts = current.guidePosts.map { guide ->
                        if (guide.id == guidePostId) guide.copy(views = serverViews, viewCount = serverViews) else guide
                    },
                    displayViews = serverViews
                )
            } catch (e: Exception) {
                Log.e(TAG, "registerGuideView: failed", e)
            }
        }
    }

    fun shareGuidePost(context: Context, guidePost: GuidePost) {
        viewModelScope.launch {
            try {
                val body = guidePost.shortDescription
                    .ifBlank { guidePost.excerpt }
                    .ifBlank { guidePost.description }
                    .ifBlank {
                        stripHtmlToText(guidePost.longDescriptionHtml)
                            .lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .take(8)
                            .joinToString("\n")
                    }
                    .ifBlank {
                        stripHtmlToText(guidePost.content)
                            .lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotBlank() }
                            .take(8)
                            .joinToString("\n")
                    }

                val heroImage = MediaUrlResolver.resolve(
                    guidePost.imageUrl.ifBlank { guidePost.image }
                )

                ShareUtils.sharePrompt(
                    context = context,
                    promptText = body,
                    imageUrl = heroImage,
                    title = guidePost.title.ifBlank { context.getString(R.string.guide_posts) },
                    chooserTitle = context.getString(R.string.share_guide_via)
                )
                analyticsLogger.logShareGuide(guidePost.id)
            } catch (e: Exception) {
                crashReporter.recordUnexpectedNetworkFailure("guide_share", e)
                _uiState.value = _uiState.value.copy(error = e.message ?: appContext.getString(R.string.error))
            }
        }
    }

    fun clearError() {
        if (_uiState.value.error != null) {
            _uiState.value = _uiState.value.copy(error = null)
        }
    }

    private fun buildFallbackBlocks(post: GuidePost): List<GuideContentBlock> {
        val blocks = mutableListOf<GuideContentBlock>()
        val paragraphs = stripHtmlToText(post.content)
            .split(Regex("\\n\\s*\\n+"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        var imageIndex = 0
        paragraphs.forEachIndexed { index, para ->
            blocks += GuideContentBlock.Paragraph(para)
            if ((index + 1) % 2 == 0 && imageIndex < post.images.size) {
                blocks += GuideContentBlock.Image(post.images[imageIndex], null, null)
                imageIndex++
            }
        }
        while (imageIndex < post.images.size) {
            blocks += GuideContentBlock.Image(post.images[imageIndex], null, null)
            imageIndex++
        }
        post.videoItems.forEach { v ->
            blocks += GuideContentBlock.Video(v.url, v.provider, v.caption)
        }
        if (blocks.isEmpty()) {
            blocks += GuideContentBlock.Paragraph(post.description.ifBlank { post.title })
        }
        return blocks
    }

    fun estimateReadMinutes(text: String): Int {
        val readableText = stripHtmlToText(text).trim()
        if (readableText.isBlank()) return 0
        val words = readableText.split(Regex("\\s+")).count { it.isNotBlank() }
        if (words <= 0) return 0
        return kotlin.math.ceil(words / 200.0).toInt().coerceAtLeast(1)
    }

    private fun buildReadableContent(post: GuidePost, blocks: List<GuideContentBlock>): String {
        val blockText = blocks.joinToString("\n") { block ->
            when (block) {
                is GuideContentBlock.Heading -> block.text
                is GuideContentBlock.Paragraph -> block.text
                is GuideContentBlock.Image -> listOfNotNull(block.caption, block.alt).joinToString(" ")
                is GuideContentBlock.Video -> block.caption.orEmpty()
                is GuideContentBlock.Callout -> "${block.title} ${block.text}"
                is GuideContentBlock.OrderedList -> block.items.joinToString(" ")
                is GuideContentBlock.UnorderedList -> block.items.joinToString(" ")
                GuideContentBlock.Divider -> ""
            }
        }
        return buildString {
            append(post.shortDescription)
            append('\n')
            append(post.longDescriptionHtml)
            append('\n')
            append(post.content)
            append('\n')
            append(blockText)
        }
    }

    private fun stripHtmlToText(input: String): String {
        if (input.isBlank()) return ""
        return Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .replace('\u00A0', ' ')
            .trim()
    }


}

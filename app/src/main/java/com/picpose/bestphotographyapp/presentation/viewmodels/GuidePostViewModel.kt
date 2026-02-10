package com.picpose.bestphotographyapp.presentation.viewmodels

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.models.GuideContentBlock
import com.picpose.bestphotographyapp.data.models.GuidePost
import com.picpose.bestphotographyapp.data.repository.HomeRepository
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
    val error: String? = null,
    val isRefreshing: Boolean = false
)

@HiltViewModel
class GuidePostViewModel @Inject constructor(
    private val repository: HomeRepository,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

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
                repository.getGuidePostById(id).collect { result ->
                    result.fold(
                        onSuccess = { post ->
                            val renderedBlocks = if (post.contentBlocks.isNotEmpty()) {
                                post.contentBlocks
                            } else {
                                buildFallbackBlocks(post)
                            }
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                selectedGuidePost = post,
                                blocks = renderedBlocks,
                                error = null,
                                guidePosts = if (_uiState.value.guidePosts.any { it.id == post.id }) {
                                    _uiState.value.guidePosts.map { if (it.id == post.id) post else it }
                                } else {
                                    _uiState.value.guidePosts + post
                                }
                            )
                        },
                        onFailure = { exception ->
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                blocks = emptyList(),
                                error = exception.message ?: appContext.getString(R.string.guide_post_not_found)
                            )
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    blocks = emptyList(),
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

    fun incrementLikes(guidePostId: String) {
        viewModelScope.launch {
            try {
                val updatedGuidePosts = _uiState.value.guidePosts.map { guide ->
                    if (guide.id == guidePostId) {
                        val nowLiked = !guide.isLiked
                        guide.copy(
                            likes = if (nowLiked) guide.likes + 1 else (guide.likes - 1).coerceAtLeast(0),
                            isLiked = nowLiked
                        )
                    } else {
                        guide
                    }
                }
                _uiState.value = _uiState.value.copy(guidePosts = updatedGuidePosts)
                _uiState.value.selectedGuidePost?.let { selected ->
                    if (selected.id == guidePostId) {
                        val nowLiked = !selected.isLiked
                        _uiState.value = _uiState.value.copy(
                            selectedGuidePost = selected.copy(
                                likes = if (nowLiked) selected.likes + 1 else (selected.likes - 1).coerceAtLeast(0),
                                isLiked = nowLiked
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "incrementLikes: failed", e)
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }

    fun shareGuidePost(context: Context, guidePost: GuidePost) {
        try {
            val guideLink = guidePost.shareUrl
                ?.takeIf { it.startsWith("http://") || it.startsWith("https://") }
                ?: guidePost.imageUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") }
            val shareText = buildString {
                append(guidePost.title.ifBlank { context.getString(R.string.guide_posts) })
                val body = guidePost.excerpt.ifBlank { guidePost.description.ifBlank { guidePost.content.take(180) } }
                if (body.isNotBlank()) append("\n\n$body")
                if (!guideLink.isNullOrBlank()) append("\n\n$guideLink")
                append("\n\n#PicPose #Guide")
            }
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, guidePost.title.ifBlank { context.getString(R.string.guide_posts) })
                putExtra(Intent.EXTRA_TEXT, shareText)
            }
            context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share)))
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = e.message ?: appContext.getString(R.string.error))
        }
    }

    fun clearError() {
        if (_uiState.value.error != null) {
            _uiState.value = _uiState.value.copy(error = null)
        }
    }

    private fun buildFallbackBlocks(post: GuidePost): List<GuideContentBlock> {
        val blocks = mutableListOf<GuideContentBlock>()
        val paragraphs = post.content
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


}

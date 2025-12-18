package com.picpose.bestphotographyapp.presentation.components

import android.Manifest
import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.RequiresPermission
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.picpose.bestphotographyapp.data.models.AIPrompt
import kotlinx.coroutines.launch

@Composable
fun AIPromptCardWithEffects(
    prompt: AIPrompt,
    onClick: () -> Unit,
    onCopy: () -> Unit,
    showFavoriteIcon: Boolean = true,

    // REQUIRED CALLBACKS
    onLikeClick: (AIPrompt) -> Unit,
    onBookmarkClick: (AIPrompt) -> Unit,

    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // ======================================================
    // Optimistic Local UI State
    // ======================================================
    var localLiked by remember(prompt.id) { mutableStateOf(prompt.isLiked) }
    var localLikesCount by remember(prompt.id) { mutableStateOf(prompt.likes) }
    var localBookmarked by remember(prompt.id) { mutableStateOf(prompt.isFavouriteBookmarked) }

    LaunchedEffect(prompt.id, prompt.isLiked, prompt.isFavouriteBookmarked, prompt.likes) {
        localLiked = prompt.isLiked
        localBookmarked = prompt.isFavouriteBookmarked
        localLikesCount = prompt.likes
    }

    // ======================================================
    // Animations
    // ======================================================
    val likeScale = remember { Animatable(1f) }
    val bookmarkPulse = remember { Animatable(1f) }

    suspend fun playLikeBounce() {
        likeScale.animateTo(1.3f, tween(130))
        likeScale.animateTo(1f, tween(160))
    }

    suspend fun playBookmarkPulse() {
        bookmarkPulse.animateTo(0.6f, tween(140))
        bookmarkPulse.animateTo(1f, tween(180))
    }

    // ======================================================
    // Modern Vibration Helper (NO Deprecated API)
    // ======================================================
    @RequiresPermission(Manifest.permission.VIBRATE)
    fun vibrateCompat(context: Context) {
        try {
            val vib = ContextCompat.getSystemService(context, Vibrator::class.java)
            vib?.vibrate(
                VibrationEffect.createOneShot(
                    25,
                    VibrationEffect.DEFAULT_AMPLITUDE
                )
            )
        } catch (_: Exception) {}
    }

    // ======================================================
    // LIKE handler
    // ======================================================
    fun handleLike() {
        val now = !localLiked
        localLiked = now
        localLikesCount = if (now) localLikesCount + 1 else maxOf(0, localLikesCount - 1)

        scope.launch { playLikeBounce() }
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        vibrateCompat(context)

        onLikeClick(
            prompt.copy(
                isLiked = localLiked,
                likes = localLikesCount
            )
        )
    }

    // ======================================================
    // BOOKMARK handler
    // ======================================================
    fun handleBookmark() {
        localBookmarked = !localBookmarked

        scope.launch { playBookmarkPulse() }
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        vibrateCompat(context)

        onBookmarkClick(prompt.copy(isFavouriteBookmarked = localBookmarked))
    }

    // ======================================================
    // Flicker-proof Image Loader
    // ======================================================
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(prompt.imageUrl)
            .crossfade(true)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .build(),
        filterQuality = FilterQuality.None
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column {

            // IMAGE
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painter,
                    contentDescription = prompt.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                when (painter.state) {
                    is AsyncImagePainter.State.Loading -> CircularProgressIndicator()
                    is AsyncImagePainter.State.Error ->
                        Icon(Icons.Default.BrokenImage, null, tint = Color.Red, modifier = Modifier.size(40.dp))
                    else -> {}
                }
            }

            // CONTENT
            Column(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    prompt.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    prompt.shortPrompt ?: "",
                    style = MaterialTheme.typography.bodyMedium.copy(color = colors.onSurfaceVariant),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(Modifier.height(12.dp))

                // ACTION ROW
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {

                    // LEFT: Category + Popular
                    Row(verticalAlignment = Alignment.CenterVertically) {

                        prompt.category?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    color = colors.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        if (prompt.isPopular) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Whatshot,
                                    null,
                                    tint = colors.error,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    "Popular",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.error
                                )
                            }
                        }
                    }

                    // RIGHT GROUP
                    Row(verticalAlignment = Alignment.CenterVertically) {

                        // ❤️ LIKE BUTTON
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clickable { handleLike() }
                                .padding(4.dp)
                        ) {
                            Icon(
                                imageVector =
                                    if (localLiked)
                                        Icons.Default.ThumbUp
                                    else
                                        Icons.Default.ThumbUpOffAlt,
                                contentDescription = "Like",
                                tint =
                                    if (localLiked)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        colors.onSurfaceVariant,
                                modifier = Modifier
                                    .size(22.dp)
                                    .scale(likeScale.value)
                            )


                            Spacer(Modifier.width(4.dp))

                            Text("$localLikesCount")
                        }

                        Spacer(Modifier.width(14.dp))

                        // 🔖 BOOKMARK BUTTON (blue when selected, grey when not)
                        Icon(
                            if (localBookmarked) Icons.Default.BookmarkAdded else Icons.Default.BookmarkBorder,
                            null,
                            tint = if (localBookmarked) colors.primary else colors.onSurfaceVariant,
                            modifier = Modifier
                                .size(22.dp)
                                .scale(bookmarkPulse.value)
                                .clickable { handleBookmark() }
                        )

                        Spacer(Modifier.width(14.dp))

                        // 👁 VIEWS
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Visibility,
                                null,
                                tint = colors.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("${prompt.views}")
                        }
                    }
                }
            }
        }
    }
}

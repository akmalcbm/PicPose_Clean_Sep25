package com.picpose.bestphotographyapp.data.rembg

import com.picpose.bestphotographyapp.util.ImageIO
import com.picpose.bestphotographyapp.util.NetworkMonitor
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackgroundRemovalRepository @Inject constructor(
    private val remoteService: RemoteBgRemovalService,
    private val localService: LocalBgRemovalService,
    private val imageIO: ImageIO,
    private val networkMonitor: NetworkMonitor
) {

    suspend fun removeBackground(request: BackgroundRemovalRequest): Result<BackgroundRemovalResult> {
        val shouldUseRemote = request.qualityMode == BgRemovalQualityMode.HIGH_QUALITY_ONLINE &&
            networkMonitor.isOnline()

        val service = if (shouldUseRemote) remoteService else localService
        val removedUri = service.removeBackground(
            sourceUri = request.sourceUri,
            previewSize = request.previewSize
        ).getOrElse {
            if (shouldUseRemote && request.qualityMode == BgRemovalQualityMode.HIGH_QUALITY_ONLINE) {
                // Auto fallback to offline mode when remote path fails.
                return@removeBackground localService.removeBackground(
                    sourceUri = request.sourceUri,
                    previewSize = request.previewSize
                ).mapCatching { localUri ->
                    val preview = imageIO.compositeBackground(
                        originalUri = request.sourceUri,
                        cutoutUri = localUri,
                        backgroundOption = request.backgroundOption
                    ).getOrThrow()
                    BackgroundRemovalResult(cutoutUri = localUri, previewUri = preview)
                }
            }
            return Result.failure(it)
        }

        val previewUri = imageIO.compositeBackground(
            originalUri = request.sourceUri,
            cutoutUri = removedUri,
            backgroundOption = request.backgroundOption
        ).getOrElse { error ->
            return Result.failure(IOException("Processed image generated, but preview failed", error))
        }

        return Result.success(
            BackgroundRemovalResult(
                cutoutUri = removedUri,
                previewUri = previewUri
            )
        )
    }

    suspend fun savePngToGallery(uri: android.net.Uri): Result<android.net.Uri> =
        imageIO.savePngToGallery(uri)
}

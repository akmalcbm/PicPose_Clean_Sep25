package com.picpose.bestphotographyapp.data.rembg

import android.net.Uri
import com.picpose.bestphotographyapp.data.network.RemoveBgApiService
import com.picpose.bestphotographyapp.data.network.RetrofitClient
import com.picpose.bestphotographyapp.util.ImageIO
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

@Singleton
class RemoteBgRemovalService @Inject constructor(
    private val imageIO: ImageIO
) : BackgroundRemovalService {

    private val api by lazy { RetrofitClient.createService(RemoveBgApiService::class.java) }

    override suspend fun removeBackground(sourceUri: Uri, previewSize: Boolean): Result<Uri> {
        return runCatching {
            withTimeout(45_000L) {
                val uploadFile = imageIO.prepareUploadImage(sourceUri).getOrThrow()
                val imageBody = uploadFile.asRequestBody("image/jpeg".toMediaType())
                val imagePart = MultipartBody.Part.createFormData("image", uploadFile.name, imageBody)
                val modeBody = "quality".toRequestBody("text/plain".toMediaType())
                val sizeBody = if (previewSize) "preview" else "full"
                val sizeReq = sizeBody.toRequestBody("text/plain".toMediaType())
                val formatReq = "png".toRequestBody("text/plain".toMediaType())

                val response = api.removeBg(
                    image = imagePart,
                    mode = modeBody,
                    size = sizeReq,
                    format = formatReq
                )

                if (!response.isSuccessful) {
                    val message = response.errorBody()?.string()?.take(200)
                    if (!message.isNullOrBlank()) {
                        throw IOException("Background removal failed (${response.code()}): $message")
                    }
                    throw HttpException(response)
                }

                val body = response.body() ?: throw IOException("Empty response from background removal")
                imageIO.persistPngFromResolver { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }.getOrThrow()
            }
        }.recoverCatching { throwable ->
            when (throwable) {
                is CancellationException -> throw throwable
                is TimeoutCancellationException -> throw IOException("Request timed out. Please try again.")
                else -> throw throwable
            }
        }
    }
}

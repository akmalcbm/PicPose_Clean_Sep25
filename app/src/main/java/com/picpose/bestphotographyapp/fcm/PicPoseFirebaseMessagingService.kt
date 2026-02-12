package com.picpose.bestphotographyapp.fcm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.picpose.bestphotographyapp.MainActivity
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.data.datastore.UserSessionManager
import java.net.HttpURLConnection
import java.net.URL
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class PicPoseFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.i(TAG, "new token received")

        serviceScope.launch {
            val userId = UserSessionManager(applicationContext).userId.firstOrNull()?.toIntOrNull()
            FcmTokenSyncManager.syncToken(
                context = applicationContext,
                token = token,
                userId = userId,
                reason = "on_new_token",
                force = true
            )
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        val title = data["title"]
            ?: message.notification?.title
            ?: getString(R.string.app_name)
        val body = data["message"]
            ?: data["body"]
            ?: message.notification?.body
            ?: ""

        if (body.isBlank()) {
            Log.w(TAG, "message ignored because body is empty")
            return
        }

        val deepLink = data["deep_link"]
            ?: data["deeplink"]
            ?: data["route"]
            ?: buildDeepLinkFromIds(data)
            ?: "app://home"

        val imageUrl = data["image_url"]
            ?: data["image"]
            ?: message.notification?.imageUrl?.toString()

        val notificationId = data["notification_id"] ?: data["campaign_id"]
        val channelId = resolveChannelId(data)

        Log.i(TAG, "message parsed channel=$channelId deepLink=${deepLink ?: "none"}")
        showNotification(title, body, deepLink, imageUrl, notificationId, channelId)
    }

    private fun showNotification(
        title: String,
        body: String,
        deepLink: String?,
        imageUrl: String?,
        notificationId: String?,
        channelId: String
    ) {
        createNotificationChannels()

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_DEEP_LINK, deepLink)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            (notificationId ?: Random.nextInt().toString()).hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notifyId = notificationId?.hashCode() ?: Random.nextInt()

        if (imageUrl.isNullOrBlank()) {
            notifySafely(notifyId, builder)
            return
        }

        serviceScope.launch {
            val bitmap = loadBitmap(imageUrl)
            if (bitmap != null) {
                builder.setStyle(
                    NotificationCompat.BigPictureStyle()
                        .bigPicture(bitmap)
                        .setSummaryText(body)
                )
            }
            notifySafely(notifyId, builder)
        }
    }

    private fun notifySafely(notificationId: Int, builder: NotificationCompat.Builder) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                Log.w(TAG, "notification skipped due to missing POST_NOTIFICATIONS")
                return
            }
        }

        NotificationManagerCompat.from(this).notify(notificationId, builder.build())
        Log.i(TAG, "notification displayed id=$notificationId")
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channels = listOf(
            NotificationChannel(
                CHANNEL_GENERAL,
                getString(R.string.notification_channel_general_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_general_description)
            },
            NotificationChannel(
                CHANNEL_GUIDES,
                getString(R.string.notification_channel_guides_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_guides_description)
            },
            NotificationChannel(
                CHANNEL_PROMPTS,
                getString(R.string.notification_channel_prompts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.notification_channel_prompts_description)
            }
        )

        channels.forEach(manager::createNotificationChannel)
    }

    private fun resolveChannelId(data: Map<String, String>): String {
        val explicit = data["channel_id"]?.trim().orEmpty()
        if (explicit in setOf(CHANNEL_GENERAL, CHANNEL_GUIDES, CHANNEL_PROMPTS)) {
            return explicit
        }

        val type = data["type"]?.lowercase().orEmpty()
        return when {
            type.contains("guide") -> CHANNEL_GUIDES
            type.contains("prompt") -> CHANNEL_PROMPTS
            else -> CHANNEL_GENERAL
        }
    }

    private fun buildDeepLinkFromIds(data: Map<String, String>): String? {
        val guideId = data["guide_id"]
        if (!guideId.isNullOrBlank()) {
            return "app://guides/$guideId"
        }

        val promptId = data["prompt_id"]
        if (!promptId.isNullOrBlank()) {
            return "app://prompts/$promptId"
        }

        return null
    }

    private fun loadBitmap(imageUrl: String): Bitmap? {
        return try {
            val connection = (URL(imageUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 7000
                doInput = true
            }
            connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } catch (e: Exception) {
            Log.e(TAG, "image load failed", e)
            null
        }
    }

    companion object {
        private const val TAG = "PicPoseFCM"
        private const val CHANNEL_GENERAL = "picpose_general"
        private const val CHANNEL_GUIDES = "picpose_guides"
        private const val CHANNEL_PROMPTS = "picpose_prompts"

        const val EXTRA_DEEP_LINK = "deep_link"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}

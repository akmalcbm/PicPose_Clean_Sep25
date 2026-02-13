package com.picpose.bestphotographyapp.fcm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.tasks.await

object NotificationSettingsCoordinator {

    private val TOPICS = listOf("all", "android", "general", "guides", "prompts")
    private const val CHANNEL_GENERAL = "picpose_general"
    private const val CHANNEL_GUIDES = "picpose_guides"
    private const val CHANNEL_PROMPTS = "picpose_prompts"

    suspend fun enableNotifications(
        context: Context,
        userId: Int?
    ): Result<Unit> {
        return runCatching {
            ensureNotificationChannels(context)
            TOPICS.forEach { topic ->
                FirebaseMessaging.getInstance().subscribeToTopic(topic).await()
            }
            FcmTokenSyncManager.syncCurrentToken(
                context = context,
                userId = userId,
                reason = "notifications_enabled",
                force = true
            )
        }
    }

    suspend fun disableNotifications(): Result<Unit> {
        return runCatching {
            TOPICS.forEach { topic ->
                FirebaseMessaging.getInstance().unsubscribeFromTopic(topic).await()
            }
        }
    }

    fun ensureNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channels = listOf(
            NotificationChannel(
                CHANNEL_GENERAL,
                context.getString(com.picpose.bestphotographyapp.R.string.notification_channel_general_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(com.picpose.bestphotographyapp.R.string.notification_channel_general_description)
            },
            NotificationChannel(
                CHANNEL_GUIDES,
                context.getString(com.picpose.bestphotographyapp.R.string.notification_channel_guides_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(com.picpose.bestphotographyapp.R.string.notification_channel_guides_description)
            },
            NotificationChannel(
                CHANNEL_PROMPTS,
                context.getString(com.picpose.bestphotographyapp.R.string.notification_channel_prompts_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(com.picpose.bestphotographyapp.R.string.notification_channel_prompts_description)
            }
        )
        channels.forEach(manager::createNotificationChannel)
    }
}

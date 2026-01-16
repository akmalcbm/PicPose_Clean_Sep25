package com.picpose.bestphotographyapp.fcm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.picpose.bestphotographyapp.R
import com.picpose.bestphotographyapp.presentation.MainActivity
import kotlinx.coroutines.*
import java.net.URL
import kotlin.random.Random

class PicPoseFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO
    )

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCM", "🔥 New token: $token")

        // TODO: send token to server
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val data = message.data
        if (data.isEmpty()) return

        val title = data["title"] ?: getString(R.string.app_name)
        val body = data["message"] ?: ""
        val deepLink = data["deep_link"]
        val imageUrl = data["image_url"]
        val notificationId = data["notification_id"]

        showNotification(title, body, deepLink, imageUrl, notificationId)
    }


    private fun showNotification(
        title: String,
        body: String,
        deepLink: String?,
        imageUrl: String?,
        notificationId: String?
    ) {
        val channelId = "picpose_default_channel"
        createNotificationChannel(channelId)

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("deep_link", deepLink)
            putExtra("notification_id", notificationId)
        }

        val requestCode = Random.nextInt()

        val pendingIntent = PendingIntent.getActivity(
            this,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification) // ✅ white icon only
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        val notifyId = notificationId?.hashCode() ?: Random.nextInt()

        if (!imageUrl.isNullOrEmpty()) {
            serviceScope.launch {
                try {
                    val bitmap = BitmapFactory.decodeStream(URL(imageUrl).openStream())
                    builder.setStyle(
                        NotificationCompat.BigPictureStyle()
                            .bigPicture(bitmap)
                            .setSummaryText(body)
                    )
                } catch (e: Exception) {
                    Log.e("FCM", "Image load failed", e)
                }

                notifySafely(notifyId, builder) // ✅ IMPORTANT FIX
            }
        } else {
            notifySafely(notifyId, builder)
        }

    }

    private fun notifySafely(
        notificationId: Int,
        builder: NotificationCompat.Builder
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!granted) {
                Log.w("FCM", "Notification permission not granted, skipping notify()")
                return
            }
        }

        NotificationManagerCompat.from(this)
            .notify(notificationId, builder.build())
    }


    private fun createNotificationChannel(channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "PicPose Notifications",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "PicPose app notifications"
            }

            val manager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}

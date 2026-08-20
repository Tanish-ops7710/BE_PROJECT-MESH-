package com.demo.upimesh.util

import android.annotation.SuppressLint
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.demo.upimesh.R

object NotificationUtils {

    @SuppressLint("MissingPermission")
    fun sendNotification(context: Context, title: String, message: String, notificationId: Int = System.currentTimeMillis().toInt()) {
        try {
            val builder = NotificationCompat.Builder(context, "upi_mesh_channel")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(message)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            with(NotificationManagerCompat.from(context)) {
                notify(notificationId, builder.build())
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

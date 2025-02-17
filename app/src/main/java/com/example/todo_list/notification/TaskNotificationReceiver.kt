package com.example.todo_list.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.todo_list.MainActivity
import com.example.todo_list.R

class TaskNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Get the task information from the intent
        val taskTitle = intent?.getStringExtra("taskTitle")
        val taskDescription = intent?.getStringExtra("taskDescription")

        // Create the notification
        val notificationManager = context?.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationChannelId = "task_notifications"

        // Create Notification Channel (for devices running Android 8.0 and above)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                notificationChannelId,
                "Task Notifications",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Build the notification
        val notification = NotificationCompat.Builder(context, notificationChannelId)
            .setContentTitle("Task Expiry Reminder")
            .setContentText("$taskTitle is about to expire: $taskDescription")
            .setSmallIcon(R.drawable.unamed)
            .setAutoCancel(true)
            .build()

        // Show the notification
        notificationManager.notify(taskTitle.hashCode(), notification)

    }
}

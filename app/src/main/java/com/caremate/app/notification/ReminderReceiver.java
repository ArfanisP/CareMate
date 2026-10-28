package com.caremate.app.notification;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.caremate.app.R;
import com.caremate.app.ui.main.MainActivity;

public class ReminderReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        String title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE);
        String message = intent.getStringExtra(ReminderScheduler.EXTRA_MESSAGE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            rescheduleDailyReminder(context, intent, title, message);
            return;
        }

        ReminderScheduler.createNotificationChannel(context);

        Intent openAppIntent = new Intent(context, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        int notificationId = intent.getIntExtra(ReminderScheduler.EXTRA_NOTIFICATION_ID, 1);

        // Tapping a reminder returns the user to the main CareMate screen.
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ReminderScheduler.CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_check)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(contentIntent);

        NotificationManagerCompat.from(context).notify(notificationId, builder.build());

        if (intent.getBooleanExtra(ReminderScheduler.EXTRA_REPEAT_DAILY, false)) {
            rescheduleDailyReminder(context, intent, title, message);
        }
    }

    private void rescheduleDailyReminder(Context context, Intent intent, String title, String message) {
        String category = intent.getStringExtra(ReminderScheduler.EXTRA_CATEGORY);
        String reminderId = intent.getStringExtra(ReminderScheduler.EXTRA_REMINDER_ID);
        long triggerAtMillis = intent.getLongExtra(ReminderScheduler.EXTRA_TRIGGER_AT, 0L);
        if (category == null || reminderId == null || triggerAtMillis <= 0L) {
            return;
        }

        long nextTriggerAtMillis = triggerAtMillis + ReminderScheduler.DAY_INTERVAL_MILLIS;
        while (nextTriggerAtMillis <= System.currentTimeMillis()) {
            nextTriggerAtMillis += ReminderScheduler.DAY_INTERVAL_MILLIS;
        }

        new ReminderScheduler(context).scheduleDaily(category, reminderId, nextTriggerAtMillis, title, message);
    }
}

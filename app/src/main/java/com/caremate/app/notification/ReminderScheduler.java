package com.caremate.app.notification;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.caremate.app.R;

public class ReminderScheduler {

    public static final String CHANNEL_ID = "caremate_reminders";
    public static final String EXTRA_TITLE = "extra_title";
    public static final String EXTRA_MESSAGE = "extra_message";
    public static final String EXTRA_NOTIFICATION_ID = "extra_notification_id";
    public static final String EXTRA_CATEGORY = "extra_category";
    public static final String EXTRA_REMINDER_ID = "extra_reminder_id";
    public static final String EXTRA_REPEAT_DAILY = "extra_repeat_daily";
    public static final String EXTRA_TRIGGER_AT = "extra_trigger_at";

    public static final long DAY_INTERVAL_MILLIS = 24L * 60L * 60L * 1000L;

    private final Context context;
    private final AlarmManager alarmManager;

    public ReminderScheduler(Context context) {
        this.context = context.getApplicationContext();
        alarmManager = (AlarmManager) this.context.getSystemService(Context.ALARM_SERVICE);
        createNotificationChannel(this.context);
    }

    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return;
        }

        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
        );
        channel.setDescription(context.getString(R.string.notification_channel_description));

        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.createNotificationChannel(channel);
        }
    }

    public boolean canPostNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    public boolean scheduleDaily(String category, String reminderId, long triggerAtMillis, String title, String message) {
        if (triggerAtMillis <= 0L) {
            return false;
        }

        // Stable request codes let alarm updates replace older reminders.
        scheduleAlarm(triggerAtMillis, createPendingIntent(category, reminderId, title, message, true, triggerAtMillis));
        return true;
    }

    public boolean scheduleOnce(String category, String reminderId, long triggerAtMillis, String title, String message) {
        if (triggerAtMillis <= 0L) {
            return false;
        }

        scheduleAlarm(triggerAtMillis, createPendingIntent(category, reminderId, title, message, false, triggerAtMillis));
        return true;
    }

    public void cancel(String category, String reminderId) {
        alarmManager.cancel(createPendingIntent(category, reminderId, "", "", false, 0L));
    }

    private void scheduleAlarm(long triggerAtMillis, PendingIntent pendingIntent) {
        // Prefer exact alarms, with a safe fallback on restricted devices.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            return;
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
            }
        } catch (SecurityException exception) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
        }
    }

    private PendingIntent createPendingIntent(
            String category,
            String reminderId,
            String title,
            String message,
            boolean repeatDaily,
            long triggerAtMillis
    ) {
        int requestCode = Math.abs((category + ":" + reminderId).hashCode());
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.putExtra(EXTRA_NOTIFICATION_ID, requestCode);
        intent.putExtra(EXTRA_CATEGORY, category);
        intent.putExtra(EXTRA_REMINDER_ID, reminderId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_MESSAGE, message);
        intent.putExtra(EXTRA_REPEAT_DAILY, repeatDaily);
        intent.putExtra(EXTRA_TRIGGER_AT, triggerAtMillis);

        return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
    }
}

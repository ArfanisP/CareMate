package com.caremate.app.util;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

public final class ReminderDateTimeParser {

    // Keep accepted date formats explicit for predictable reminder scheduling.
    private static final String[] DATE_PATTERNS = {
            "dd/MM/yyyy",
            "dd-MM-yyyy",
            "yyyy-MM-dd"
    };

    private ReminderDateTimeParser() {
    }

    public static long nextDailyTimeMillis(String timeText) {
        Calendar time = parseTime(timeText);
        if (time == null) {
            return 0L;
        }

        Calendar reminder = Calendar.getInstance();
        reminder.set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY));
        reminder.set(Calendar.MINUTE, time.get(Calendar.MINUTE));
        reminder.set(Calendar.SECOND, 0);
        reminder.set(Calendar.MILLISECOND, 0);

        if (reminder.getTimeInMillis() <= System.currentTimeMillis()) {
            reminder.add(Calendar.DAY_OF_YEAR, 1);
        }

        return reminder.getTimeInMillis();
    }

    public static long dateTimeMillis(String dateText, String timeText) {
        return dateTimeMillisBefore(dateText, timeText, 0);
    }

    public static long dateTimeMillisBefore(String dateText, String timeText, int minutesBefore) {
        Calendar date = parseDate(dateText);
        Calendar time = parseTime(timeText);
        if (date == null || time == null) {
            return 0L;
        }

        date.set(Calendar.HOUR_OF_DAY, time.get(Calendar.HOUR_OF_DAY));
        date.set(Calendar.MINUTE, time.get(Calendar.MINUTE));
        date.set(Calendar.SECOND, 0);
        date.set(Calendar.MILLISECOND, 0);
        date.add(Calendar.MINUTE, -minutesBefore);

        long reminderTime = date.getTimeInMillis();
        return reminderTime > System.currentTimeMillis() ? reminderTime : 0L;
    }

    private static Calendar parseDate(String dateText) {
        if (dateText == null) {
            return null;
        }

        String cleanDate = dateText.trim();
        for (String pattern : DATE_PATTERNS) {
            SimpleDateFormat dateFormat = new SimpleDateFormat(pattern, Locale.getDefault());
            dateFormat.setLenient(false);
            try {
                Date parsedDate = dateFormat.parse(cleanDate);
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(parsedDate);
                return calendar;
            } catch (ParseException ignored) {
                // Try the next supported date format.
            }
        }

        return null;
    }

    private static Calendar parseTime(String timeText) {
        if (timeText == null) {
            return null;
        }

        String[] parts = timeText.trim().split(":");
        if (parts.length != 2) {
            return null;
        }

        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }

            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, hour);
            calendar.set(Calendar.MINUTE, minute);
            return calendar;
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}

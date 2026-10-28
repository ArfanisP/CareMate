package com.caremate.app.util;

import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.Context;

import com.google.android.material.textfield.TextInputEditText;

import java.util.Calendar;
import java.util.Locale;

public final class DateTimePickerHelper {

    private DateTimePickerHelper() {
    }

    public static void attachDatePicker(Context context, TextInputEditText editText) {
        editText.setFocusable(false);
        editText.setOnClickListener(view -> showDatePicker(context, editText));
    }

    public static void attachTimePicker(Context context, TextInputEditText editText) {
        editText.setFocusable(false);
        editText.setOnClickListener(view -> showTimePicker(context, editText));
    }

    private static void showDatePicker(Context context, TextInputEditText editText) {
        Calendar calendar = Calendar.getInstance();
        DatePickerDialog dialog = new DatePickerDialog(
                context,
                (view, year, month, dayOfMonth) ->
                        editText.setText(String.format(Locale.getDefault(), "%02d/%02d/%04d", dayOfMonth, month + 1, year)),
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private static void showTimePicker(Context context, TextInputEditText editText) {
        Calendar calendar = Calendar.getInstance();
        TimePickerDialog dialog = new TimePickerDialog(
                context,
                (view, hourOfDay, minute) ->
                        editText.setText(String.format(Locale.getDefault(), "%02d:%02d", hourOfDay, minute)),
                calendar.get(Calendar.HOUR_OF_DAY),
                calendar.get(Calendar.MINUTE),
                true
        );
        dialog.show();
    }
}

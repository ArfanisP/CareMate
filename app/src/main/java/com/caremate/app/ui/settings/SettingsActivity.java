package com.caremate.app.ui.settings;

import android.Manifest;
import android.app.AlarmManager;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.caremate.app.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.slider.Slider;

public class SettingsActivity extends AppCompatActivity {

    private static final int MICROPHONE_PERMISSION_REQUEST_CODE = 201;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 202;
    private static final String PREFS_NAME = "caremate_settings";
    private static final String KEY_TTS_RATE = "tts_speech_rate";
    private static final float DEFAULT_TTS_RATE = 0.9f;

    private TextView microphoneStatusTextView;
    private TextView notificationStatusTextView;
    private TextView exactAlarmStatusTextView;
    private TextView ttsSpeedValueTextView;
    private MaterialButton microphonePermissionButton;
    private MaterialButton notificationPermissionButton;
    private MaterialButton exactAlarmPermissionButton;
    private Slider ttsSpeedSlider;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_settings);
        preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        bindViews();
        setupInsets();
        setupActions();
        setupTtsSpeed();
        updatePermissionStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
    }

    private void bindViews() {
        microphoneStatusTextView = findViewById(R.id.microphoneStatusTextView);
        notificationStatusTextView = findViewById(R.id.notificationStatusTextView);
        exactAlarmStatusTextView = findViewById(R.id.exactAlarmStatusTextView);
        ttsSpeedValueTextView = findViewById(R.id.ttsSpeedValueTextView);
        microphonePermissionButton = findViewById(R.id.microphonePermissionButton);
        notificationPermissionButton = findViewById(R.id.notificationPermissionButton);
        exactAlarmPermissionButton = findViewById(R.id.exactAlarmPermissionButton);
        ttsSpeedSlider = findViewById(R.id.ttsSpeedSlider);
    }

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.settingsRoot), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupActions() {
        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        microphonePermissionButton.setOnClickListener(view -> requestMicrophonePermission());
        notificationPermissionButton.setOnClickListener(view -> requestNotificationPermission());
        exactAlarmPermissionButton.setOnClickListener(view -> openExactAlarmSettings());
        findViewById(R.id.openAppSettingsButton).setOnClickListener(view -> openAppSettings());
    }

    private void setupTtsSpeed() {
        float savedRate = preferences.getFloat(KEY_TTS_RATE, DEFAULT_TTS_RATE);
        float valueFrom = ttsSpeedSlider.getValueFrom();
        float valueTo = ttsSpeedSlider.getValueTo();
        float clampedRate = Math.round(Math.max(valueFrom, Math.min(valueTo, savedRate)) * 10.0f) / 10.0f;
        ttsSpeedSlider.setValue(clampedRate);
        updateTtsSpeedLabel(clampedRate);
        // Persist voice speed for the next spoken prompt.
        ttsSpeedSlider.addOnChangeListener((slider, value, fromUser) -> {
            preferences.edit().putFloat(KEY_TTS_RATE, value).apply();
            updateTtsSpeedLabel(value);
        });
    }

    private void updateTtsSpeedLabel(float value) {
        ttsSpeedValueTextView.setText(getString(R.string.settings_tts_speed_value, Math.round(value * 100)));
    }

    private void updatePermissionStatus() {
        boolean microphoneGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
        boolean notificationsGranted = notificationsEnabled();
        boolean exactAlarmsAllowed = exactAlarmsAllowed();

        microphoneStatusTextView.setText(microphoneGranted
                ? R.string.settings_permission_granted
                : R.string.settings_permission_denied);
        notificationStatusTextView.setText(notificationsGranted
                ? R.string.settings_permission_granted
                : R.string.settings_permission_denied);
        exactAlarmStatusTextView.setText(exactAlarmsAllowed
                ? R.string.settings_permission_granted
                : R.string.settings_permission_denied);

        microphonePermissionButton.setEnabled(!microphoneGranted);
        notificationPermissionButton.setEnabled(!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU);
        exactAlarmPermissionButton.setEnabled(!exactAlarmsAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S);
    }

    private boolean notificationsEnabled() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }

        return NotificationManagerCompat.from(this).areNotificationsEnabled();
    }

    private boolean exactAlarmsAllowed() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return true;
        }

        AlarmManager alarmManager = getSystemService(AlarmManager.class);
        return alarmManager != null && alarmManager.canScheduleExactAlarms();
    }

    private void requestMicrophonePermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[]{Manifest.permission.RECORD_AUDIO},
                MICROPHONE_PERMISSION_REQUEST_CODE
        );
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST_CODE
            );
        } else {
            openAppSettings();
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", getPackageName(), null));
        startActivity(intent);
    }

    private void openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException exception) {
                openAppSettings();
            }
        } else {
            openAppSettings();
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        updatePermissionStatus();

        if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, R.string.settings_permission_not_changed, Toast.LENGTH_SHORT).show();
        }
    }
}

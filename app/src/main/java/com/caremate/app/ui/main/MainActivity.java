package com.caremate.app.ui.main;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.caremate.app.R;
import com.caremate.app.model.Appointment;
import com.caremate.app.model.Examination;
import com.caremate.app.model.Medication;
import com.caremate.app.notification.ReminderScheduler;
import com.caremate.app.repository.AppointmentRepository;
import com.caremate.app.repository.AuthRepository;
import com.caremate.app.repository.ExaminationRepository;
import com.caremate.app.repository.MedicationRepository;
import com.caremate.app.ui.appointment.AppointmentActivity;
import com.caremate.app.ui.examination.ExaminationActivity;
import com.caremate.app.ui.medication.MedicationActivity;
import com.caremate.app.ui.settings.SettingsActivity;
import com.caremate.app.util.ReminderDateTimeParser;
import com.google.android.gms.tasks.Task;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private enum VoiceAction {
        NONE,
        ADD,
        DELETE,
        EDIT
    }

    private enum VoiceTarget {
        NONE,
        MEDICATION,
        APPOINTMENT,
        EXAMINATION
    }

    private enum VoiceStep {
        IDLE,
        MEDICATION_NAME,
        MEDICATION_DOSAGE,
        MEDICATION_TIME,
        APPOINTMENT_TITLE,
        APPOINTMENT_DATE,
        APPOINTMENT_TIME,
        EXAMINATION_TITLE,
        EXAMINATION_DATE,
        EXAMINATION_TIME,
        EDIT_NAME,
        DELETE_NAME,
        CONFIRM_DELETE
    }

    private static final int VOICE_REQUEST_CODE = 100;
    private static final int RECORD_AUDIO_PERMISSION_REQUEST_CODE = 101;
    private static final int NOTIFICATION_PERMISSION_REQUEST_CODE = 102;
    private static final int EVENT_REMINDER_MINUTES_BEFORE = 60;
    private static final String PREFS_NAME = "caremate_settings";
    private static final String KEY_TTS_RATE = "tts_speech_rate";
    private static final float DEFAULT_TTS_RATE = 0.9f;

    private AuthRepository authRepository;
    private MedicationRepository medicationRepository;
    private AppointmentRepository appointmentRepository;
    private ExaminationRepository examinationRepository;
    private ReminderScheduler reminderScheduler;
    private TextToSpeech textToSpeech;
    private boolean textToSpeechReady;
    private VoiceAction pendingVoiceAction = VoiceAction.NONE;
    private VoiceTarget pendingVoiceTarget = VoiceTarget.NONE;
    private VoiceStep pendingVoiceStep = VoiceStep.IDLE;
    private String voiceTitle;
    private String voiceDosage;
    private String voiceDate;
    private String voiceTime;
    private String pendingDeleteId;
    private String pendingEditId;
    private Intent pendingRecognitionIntent;
    private String pendingRecognitionUtteranceId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        authRepository = new AuthRepository();
        medicationRepository = new MedicationRepository();
        appointmentRepository = new AppointmentRepository();
        examinationRepository = new ExaminationRepository();
        reminderScheduler = new ReminderScheduler(this);
        ReminderScheduler.createNotificationChannel(this);
        setupTextToSpeech();
        requestNotificationPermissionIfNeeded();
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        setupHomeActions();
        signInAnonymously();
    }

    private void setupHomeActions() {
        findViewById(R.id.medicationsCard).setOnClickListener(view ->
                startActivity(new Intent(this, MedicationActivity.class))
        );
        findViewById(R.id.appointmentsCard).setOnClickListener(view ->
                startActivity(new Intent(this, AppointmentActivity.class))
        );
        findViewById(R.id.examinationsCard).setOnClickListener(view ->
                startActivity(new Intent(this, ExaminationActivity.class))
        );
        findViewById(R.id.voiceCommandButton).setOnClickListener(view -> startVoiceCommand());
        findViewById(R.id.settingsButton).setOnClickListener(view ->
                startActivity(new Intent(this, SettingsActivity.class))
        );
    }

    private void signInAnonymously() {
        if (authRepository.getCurrentUser() != null) {
            return;
        }

        authRepository.signInAnonymouslyIfNeeded()
                .addOnFailureListener(exception ->
                        Toast.makeText(
                                this,
                                R.string.anonymous_sign_in_error,
                                Toast.LENGTH_SHORT
                        ).show()
                );
    }

    private void setupTextToSpeech() {
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Match spoken prompts to the active app language.
                textToSpeechReady = applyTextToSpeechSettings();
                textToSpeech.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        startRecognitionAfterPrompt(utteranceId);
                    }

                    @Override
                    public void onError(String utteranceId) {
                        startRecognitionAfterPrompt(utteranceId);
                    }
                });
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (textToSpeech != null && textToSpeechReady) {
            textToSpeechReady = applyTextToSpeechSettings();
        }
    }

    private boolean applyTextToSpeechSettings() {
        int languageResult = textToSpeech.setLanguage(getUiSpeechLocale());
        textToSpeech.setSpeechRate(getSavedTtsRate());
        return languageResult != TextToSpeech.LANG_MISSING_DATA
                && languageResult != TextToSpeech.LANG_NOT_SUPPORTED;
    }

    private Locale getUiSpeechLocale() {
        Locale uiLocale = getResources().getConfiguration().getLocales().get(0);
        if ("el".equals(uiLocale.getLanguage())) {
            return new Locale("el", "GR");
        }

        return Locale.US;
    }

    private float getSavedTtsRate() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        return preferences.getFloat(KEY_TTS_RATE, DEFAULT_TTS_RATE);
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    NOTIFICATION_PERMISSION_REQUEST_CODE
            );
        }
    }

    private void startVoiceCommand() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    RECORD_AUDIO_PERMISSION_REQUEST_CODE
            );
            return;
        }

        resetVoiceFlow();
        listenForVoice(getString(R.string.voice_listening));
    }

    private void listenForVoice(String prompt) {
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        intent.putExtra(RecognizerIntent.EXTRA_PROMPT, prompt);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ArrayList<String> allowedLanguages = new ArrayList<>(Arrays.asList("el-GR", "en-US"));
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true);
            intent.putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_QUICK_RESPONSE);
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, allowedLanguages);
            intent.putStringArrayListExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, allowedLanguages);
        }

        speakThenListen(prompt, intent);
    }

    private void speakThenListen(String prompt, Intent intent) {
        pendingRecognitionIntent = intent;
        pendingRecognitionUtteranceId = "caremate_prompt_" + System.currentTimeMillis();

        // Start listening only after TTS finishes the current prompt.
        if (textToSpeech == null || !textToSpeechReady) {
            startPendingSpeechRecognition();
            return;
        }

        textToSpeechReady = applyTextToSpeechSettings();
        if (!textToSpeechReady) {
            startPendingSpeechRecognition();
            return;
        }

        int speakResult = textToSpeech.speak(
                prompt,
                TextToSpeech.QUEUE_FLUSH,
                null,
                pendingRecognitionUtteranceId
        );

        if (speakResult == TextToSpeech.ERROR) {
            startPendingSpeechRecognition();
        }
    }

    private void startRecognitionAfterPrompt(String utteranceId) {
        if (utteranceId == null || !utteranceId.equals(pendingRecognitionUtteranceId)) {
            return;
        }

        runOnUiThread(this::startPendingSpeechRecognition);
    }

    private void startPendingSpeechRecognition() {
        Intent intent = pendingRecognitionIntent;
        pendingRecognitionIntent = null;
        pendingRecognitionUtteranceId = null;
        if (intent == null || isFinishing()) {
            return;
        }

        try {
            startActivityForResult(intent, VOICE_REQUEST_CODE);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(this, R.string.voice_not_available, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode != VOICE_REQUEST_CODE || resultCode != Activity.RESULT_OK || data == null) {
            return;
        }

        ArrayList<String> matches = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
        if (matches == null || matches.isEmpty()) {
            showVoiceNotUnderstood();
            return;
        }

        String command = cleanVoiceText(matches.get(0));
        if (pendingVoiceStep == VoiceStep.IDLE) {
            handleInitialVoiceCommand(command);
        } else {
            handleVoiceAnswer(command);
        }
    }

    private void handleInitialVoiceCommand(String command) {
        VoiceAction action = detectVoiceAction(command);
        VoiceTarget target = detectVoiceTarget(command);

        // Voice commands use a short state machine for add/edit/delete flows.
        if (action == VoiceAction.ADD && target != VoiceTarget.NONE) {
            startAddFlow(target);
        } else if (action == VoiceAction.EDIT && target != VoiceTarget.NONE) {
            startEditFlow(target);
        } else if (action == VoiceAction.DELETE && target != VoiceTarget.NONE) {
            startDeleteFlow(target);
        } else if (target == VoiceTarget.MEDICATION) {
            openSection(MedicationActivity.class, R.string.home_medications_title);
        } else if (target == VoiceTarget.APPOINTMENT) {
            openSection(AppointmentActivity.class, R.string.home_appointments_title);
        } else if (target == VoiceTarget.EXAMINATION) {
            openSection(ExaminationActivity.class, R.string.home_examinations_title);
        } else {
            showVoiceNotUnderstood();
        }
    }

    private void startEditFlow(VoiceTarget target) {
        pendingVoiceAction = VoiceAction.EDIT;
        pendingVoiceTarget = target;
        pendingVoiceStep = VoiceStep.EDIT_NAME;
        voiceTitle = "";
        voiceDosage = "";
        voiceDate = "";
        voiceTime = "";
        pendingEditId = "";

        if (target == VoiceTarget.MEDICATION) {
            listenForVoice(getString(R.string.voice_prompt_edit_medication));
        } else if (target == VoiceTarget.APPOINTMENT) {
            listenForVoice(getString(R.string.voice_prompt_edit_appointment));
        } else if (target == VoiceTarget.EXAMINATION) {
            listenForVoice(getString(R.string.voice_prompt_edit_examination));
        }
    }

    private void startAddFlow(VoiceTarget target) {
        pendingVoiceAction = VoiceAction.ADD;
        pendingVoiceTarget = target;
        voiceTitle = "";
        voiceDosage = "";
        voiceDate = "";
        voiceTime = "";

        if (target == VoiceTarget.MEDICATION) {
            pendingVoiceStep = VoiceStep.MEDICATION_NAME;
            listenForVoice(getString(R.string.voice_prompt_medication_name));
        } else if (target == VoiceTarget.APPOINTMENT) {
            pendingVoiceStep = VoiceStep.APPOINTMENT_TITLE;
            listenForVoice(getString(R.string.voice_prompt_appointment_title));
        } else if (target == VoiceTarget.EXAMINATION) {
            pendingVoiceStep = VoiceStep.EXAMINATION_TITLE;
            listenForVoice(getString(R.string.voice_prompt_examination_title));
        }
    }

    private void startDeleteFlow(VoiceTarget target) {
        pendingVoiceAction = VoiceAction.DELETE;
        pendingVoiceTarget = target;
        pendingVoiceStep = VoiceStep.DELETE_NAME;

        if (target == VoiceTarget.MEDICATION) {
            listenForVoice(getString(R.string.voice_prompt_delete_medication));
        } else if (target == VoiceTarget.APPOINTMENT) {
            listenForVoice(getString(R.string.voice_prompt_delete_appointment));
        } else if (target == VoiceTarget.EXAMINATION) {
            listenForVoice(getString(R.string.voice_prompt_delete_examination));
        }
    }

    private void handleVoiceAnswer(String answer) {
        if (answer.isEmpty()) {
            showVoiceNotUnderstood();
            return;
        }

        switch (pendingVoiceStep) {
            case MEDICATION_NAME:
                voiceTitle = answer;
                pendingVoiceStep = VoiceStep.MEDICATION_DOSAGE;
                listenForVoice(getString(R.string.voice_prompt_medication_dosage));
                break;
            case MEDICATION_DOSAGE:
                voiceDosage = answer;
                pendingVoiceStep = VoiceStep.MEDICATION_TIME;
                listenForVoice(getString(R.string.voice_prompt_time));
                break;
            case MEDICATION_TIME:
                voiceTime = normalizeSpokenTime(answer);
                saveMedicationByVoice();
                break;
            case APPOINTMENT_TITLE:
                voiceTitle = answer;
                pendingVoiceStep = VoiceStep.APPOINTMENT_DATE;
                listenForVoice(getString(R.string.voice_prompt_date));
                break;
            case APPOINTMENT_DATE:
                voiceDate = normalizeSpokenDate(answer);
                pendingVoiceStep = VoiceStep.APPOINTMENT_TIME;
                listenForVoice(getString(R.string.voice_prompt_time));
                break;
            case APPOINTMENT_TIME:
                voiceTime = normalizeSpokenTime(answer);
                saveAppointmentByVoice();
                break;
            case EXAMINATION_TITLE:
                voiceTitle = answer;
                pendingVoiceStep = VoiceStep.EXAMINATION_DATE;
                listenForVoice(getString(R.string.voice_prompt_date));
                break;
            case EXAMINATION_DATE:
                voiceDate = normalizeSpokenDate(answer);
                pendingVoiceStep = VoiceStep.EXAMINATION_TIME;
                listenForVoice(getString(R.string.voice_prompt_time));
                break;
            case EXAMINATION_TIME:
                voiceTime = normalizeSpokenTime(answer);
                saveExaminationByVoice();
                break;
            case EDIT_NAME:
                editByVoice(answer);
                break;
            case DELETE_NAME:
                deleteByVoice(answer);
                break;
            case CONFIRM_DELETE:
                handleDeleteConfirmation(answer);
                break;
            default:
                showVoiceNotUnderstood();
                break;
        }
    }

    private void saveMedicationByVoice() {
        long reminderTime = ReminderDateTimeParser.nextDailyTimeMillis(voiceTime);
        if (voiceTitle.isEmpty() || voiceDosage.isEmpty() || reminderTime <= 0L) {
            showVoiceInvalidFields();
            return;
        }

        // Reuse the pending id when the voice flow edits an existing item.
        Medication medication = new Medication(
                pendingVoiceAction == VoiceAction.EDIT ? pendingEditId : null,
                voiceTitle,
                voiceDosage,
                voiceTime,
                ""
        );
        Task<Void> saveTask = medicationRepository.saveMedication(medication);
        if (saveTask == null) {
            showAuthNotReady();
            return;
        }

        saveTask
                .addOnSuccessListener(unused -> {
                    reminderScheduler.scheduleDaily(
                            "medication",
                            medication.getId(),
                            reminderTime,
                            getString(R.string.notification_medication_title),
                            getString(R.string.notification_medication_message, medication.getName(), medication.getDosage())
                    );
                    showVoiceSuccess(pendingVoiceAction == VoiceAction.EDIT
                            ? R.string.voice_edited_medication
                            : R.string.voice_added_medication);
                })
                .addOnFailureListener(exception -> showVoiceOperationFailed());
    }

    private void saveAppointmentByVoice() {
        long reminderTime = ReminderDateTimeParser.dateTimeMillisBefore(
                voiceDate,
                voiceTime,
                EVENT_REMINDER_MINUTES_BEFORE
        );
        if (voiceTitle.isEmpty() || reminderTime <= 0L) {
            showVoiceInvalidFields();
            return;
        }

        Appointment appointment = new Appointment(
                pendingVoiceAction == VoiceAction.EDIT ? pendingEditId : null,
                voiceTitle,
                voiceDate,
                voiceTime,
                ""
        );
        Task<Void> saveTask = appointmentRepository.saveAppointment(appointment);
        if (saveTask == null) {
            showAuthNotReady();
            return;
        }

        saveTask
                .addOnSuccessListener(unused -> {
                    reminderScheduler.scheduleOnce(
                            "appointment",
                            appointment.getId(),
                            reminderTime,
                            getString(R.string.notification_appointment_title),
                            getString(R.string.notification_appointment_message, appointment.getTitle(), appointment.getTime())
                    );
                    showVoiceSuccess(pendingVoiceAction == VoiceAction.EDIT
                            ? R.string.voice_edited_appointment
                            : R.string.voice_added_appointment);
                })
                .addOnFailureListener(exception -> showVoiceOperationFailed());
    }

    private void saveExaminationByVoice() {
        long reminderTime = ReminderDateTimeParser.dateTimeMillisBefore(
                voiceDate,
                voiceTime,
                EVENT_REMINDER_MINUTES_BEFORE
        );
        if (voiceTitle.isEmpty() || reminderTime <= 0L) {
            showVoiceInvalidFields();
            return;
        }

        Examination examination = new Examination(
                pendingVoiceAction == VoiceAction.EDIT ? pendingEditId : null,
                voiceTitle,
                voiceDate,
                voiceTime,
                ""
        );
        Task<Void> saveTask = examinationRepository.saveExamination(examination);
        if (saveTask == null) {
            showAuthNotReady();
            return;
        }

        saveTask
                .addOnSuccessListener(unused -> {
                    reminderScheduler.scheduleOnce(
                            "examination",
                            examination.getId(),
                            reminderTime,
                            getString(R.string.notification_examination_title),
                            getString(R.string.notification_examination_message, examination.getTitle(), examination.getTime())
                    );
                    showVoiceSuccess(pendingVoiceAction == VoiceAction.EDIT
                            ? R.string.voice_edited_examination
                            : R.string.voice_added_examination);
                })
                .addOnFailureListener(exception -> showVoiceOperationFailed());
    }

    private void editByVoice(String spokenName) {
        if (pendingVoiceTarget == VoiceTarget.MEDICATION) {
            editMedicationByVoice(spokenName);
        } else if (pendingVoiceTarget == VoiceTarget.APPOINTMENT) {
            editAppointmentByVoice(spokenName);
        } else if (pendingVoiceTarget == VoiceTarget.EXAMINATION) {
            editExaminationByVoice(spokenName);
        } else {
            showVoiceNotUnderstood();
        }
    }

    private void editMedicationByVoice(String spokenName) {
        medicationRepository.loadMedications(new MedicationRepository.MedicationLoadCallback() {
            @Override
            public void onMedicationsLoaded(List<Medication> medications) {
                Medication medication = findMedicationByName(medications, spokenName);
                if (medication == null) {
                    showVoiceItemNotFound();
                    return;
                }

                pendingEditId = medication.getId();
                pendingVoiceStep = VoiceStep.MEDICATION_NAME;
                listenForVoice(getString(R.string.voice_prompt_new_medication_name));
            }

            @Override
            public void onError(String message) {
                showVoiceOperationFailed();
            }
        });
    }

    private void editAppointmentByVoice(String spokenName) {
        appointmentRepository.loadAppointments(new AppointmentRepository.AppointmentLoadCallback() {
            @Override
            public void onAppointmentsLoaded(List<Appointment> appointments) {
                Appointment appointment = findAppointmentByTitle(appointments, spokenName);
                if (appointment == null) {
                    showVoiceItemNotFound();
                    return;
                }

                pendingEditId = appointment.getId();
                pendingVoiceStep = VoiceStep.APPOINTMENT_TITLE;
                listenForVoice(getString(R.string.voice_prompt_new_appointment_title));
            }

            @Override
            public void onError(String message) {
                showVoiceOperationFailed();
            }
        });
    }

    private void editExaminationByVoice(String spokenName) {
        examinationRepository.loadExaminations(new ExaminationRepository.ExaminationLoadCallback() {
            @Override
            public void onExaminationsLoaded(List<Examination> examinations) {
                Examination examination = findExaminationByTitle(examinations, spokenName);
                if (examination == null) {
                    showVoiceItemNotFound();
                    return;
                }

                pendingEditId = examination.getId();
                pendingVoiceStep = VoiceStep.EXAMINATION_TITLE;
                listenForVoice(getString(R.string.voice_prompt_new_examination_title));
            }

            @Override
            public void onError(String message) {
                showVoiceOperationFailed();
            }
        });
    }

    private void deleteByVoice(String spokenName) {
        if (pendingVoiceTarget == VoiceTarget.MEDICATION) {
            deleteMedicationByVoice(spokenName);
        } else if (pendingVoiceTarget == VoiceTarget.APPOINTMENT) {
            deleteAppointmentByVoice(spokenName);
        } else if (pendingVoiceTarget == VoiceTarget.EXAMINATION) {
            deleteExaminationByVoice(spokenName);
        } else {
            showVoiceNotUnderstood();
        }
    }

    private void deleteMedicationByVoice(String spokenName) {
        medicationRepository.loadMedications(new MedicationRepository.MedicationLoadCallback() {
            @Override
            public void onMedicationsLoaded(List<Medication> medications) {
                Medication medication = findMedicationByName(medications, spokenName);
                if (medication == null) {
                    showVoiceItemNotFound();
                    return;
                }

                askDeleteConfirmation(medication.getId(), medication.getName());
            }

            @Override
            public void onError(String message) {
                showVoiceOperationFailed();
            }
        });
    }

    private void deleteAppointmentByVoice(String spokenName) {
        appointmentRepository.loadAppointments(new AppointmentRepository.AppointmentLoadCallback() {
            @Override
            public void onAppointmentsLoaded(List<Appointment> appointments) {
                Appointment appointment = findAppointmentByTitle(appointments, spokenName);
                if (appointment == null) {
                    showVoiceItemNotFound();
                    return;
                }

                askDeleteConfirmation(appointment.getId(), appointment.getTitle());
            }

            @Override
            public void onError(String message) {
                showVoiceOperationFailed();
            }
        });
    }

    private void deleteExaminationByVoice(String spokenName) {
        examinationRepository.loadExaminations(new ExaminationRepository.ExaminationLoadCallback() {
            @Override
            public void onExaminationsLoaded(List<Examination> examinations) {
                Examination examination = findExaminationByTitle(examinations, spokenName);
                if (examination == null) {
                    showVoiceItemNotFound();
                    return;
                }

                askDeleteConfirmation(examination.getId(), examination.getTitle());
            }

            @Override
            public void onError(String message) {
                showVoiceOperationFailed();
            }
        });
    }

    private void askDeleteConfirmation(String itemId, String itemLabel) {
        pendingDeleteId = itemId;
        pendingVoiceStep = VoiceStep.CONFIRM_DELETE;
        listenForVoice(getString(R.string.voice_confirm_delete, itemLabel));
    }

    private void handleDeleteConfirmation(String answer) {
        if (isNegativeAnswer(answer)) {
            showVoiceSuccess(R.string.voice_cancelled);
            return;
        }

        if (!isPositiveAnswer(answer)) {
            showVoiceNotUnderstood();
            return;
        }

        if (pendingVoiceTarget == VoiceTarget.MEDICATION) {
            deleteConfirmedMedication();
        } else if (pendingVoiceTarget == VoiceTarget.APPOINTMENT) {
            deleteConfirmedAppointment();
        } else if (pendingVoiceTarget == VoiceTarget.EXAMINATION) {
            deleteConfirmedExamination();
        } else {
            showVoiceNotUnderstood();
        }
    }

    private void deleteConfirmedMedication() {
        Task<Void> deleteTask = medicationRepository.deleteMedication(pendingDeleteId);
        if (deleteTask == null) {
            showAuthNotReady();
            return;
        }

        deleteTask
                .addOnSuccessListener(unused -> {
                    reminderScheduler.cancel("medication", pendingDeleteId);
                    showVoiceSuccess(R.string.voice_deleted_medication);
                })
                .addOnFailureListener(exception -> showVoiceOperationFailed());
    }

    private void deleteConfirmedAppointment() {
        Task<Void> deleteTask = appointmentRepository.deleteAppointment(pendingDeleteId);
        if (deleteTask == null) {
            showAuthNotReady();
            return;
        }

        deleteTask
                .addOnSuccessListener(unused -> {
                    reminderScheduler.cancel("appointment", pendingDeleteId);
                    showVoiceSuccess(R.string.voice_deleted_appointment);
                })
                .addOnFailureListener(exception -> showVoiceOperationFailed());
    }

    private void deleteConfirmedExamination() {
        Task<Void> deleteTask = examinationRepository.deleteExamination(pendingDeleteId);
        if (deleteTask == null) {
            showAuthNotReady();
            return;
        }

        deleteTask
                .addOnSuccessListener(unused -> {
                    reminderScheduler.cancel("examination", pendingDeleteId);
                    showVoiceSuccess(R.string.voice_deleted_examination);
                })
                .addOnFailureListener(exception -> showVoiceOperationFailed());
    }

    private Medication findMedicationByName(List<Medication> medications, String spokenName) {
        String targetName = normalizeForMatching(spokenName);
        if (targetName.length() < 3) {
            return null;
        }
        for (Medication medication : medications) {
            String medicationName = normalizeForMatching(medication.getName());
            if (medicationName.equals(targetName) || medicationName.contains(targetName) || targetName.contains(medicationName)) {
                return medication;
            }
        }
        return null;
    }

    private Appointment findAppointmentByTitle(List<Appointment> appointments, String spokenName) {
        String targetTitle = normalizeForMatching(spokenName);
        if (targetTitle.length() < 3) {
            return null;
        }
        for (Appointment appointment : appointments) {
            String appointmentTitle = normalizeForMatching(appointment.getTitle());
            if (appointmentTitle.equals(targetTitle) || appointmentTitle.contains(targetTitle) || targetTitle.contains(appointmentTitle)) {
                return appointment;
            }
        }
        return null;
    }

    private Examination findExaminationByTitle(List<Examination> examinations, String spokenName) {
        String targetTitle = normalizeForMatching(spokenName);
        if (targetTitle.length() < 3) {
            return null;
        }
        for (Examination examination : examinations) {
            String examinationTitle = normalizeForMatching(examination.getTitle());
            if (examinationTitle.equals(targetTitle) || examinationTitle.contains(targetTitle) || targetTitle.contains(examinationTitle)) {
                return examination;
            }
        }
        return null;
    }

    private VoiceAction detectVoiceAction(String command) {
        String normalizedCommand = normalizeForMatching(command);
        if (normalizedCommand.contains("προσθ")
                || normalizedCommand.contains("βαλε")
                || normalizedCommand.contains("νεο")
                || normalizedCommand.contains("add")
                || normalizedCommand.contains("create")
                || normalizedCommand.contains("insert")
                || normalizedCommand.contains("new")) {
            return VoiceAction.ADD;
        }

        if (normalizedCommand.contains("επεξεργ")
                || normalizedCommand.contains("αλλαξ")
                || normalizedCommand.contains("τροποποι")
                || normalizedCommand.contains("edit")
                || normalizedCommand.contains("change")
                || normalizedCommand.contains("update")) {
            return VoiceAction.EDIT;
        }

        if (normalizedCommand.contains("διαγρα")
                || normalizedCommand.contains("διεγρα")
                || normalizedCommand.contains("σβησ")
                || normalizedCommand.contains("αφαιρε")
                || normalizedCommand.contains("delete")
                || normalizedCommand.contains("remove")
                || normalizedCommand.contains("erase")) {
            return VoiceAction.DELETE;
        }

        return VoiceAction.NONE;
    }

    private VoiceTarget detectVoiceTarget(String command) {
        String normalizedCommand = normalizeForMatching(command);
        if (normalizedCommand.contains("φαρμα")
                || normalizedCommand.contains("medication")
                || normalizedCommand.contains("medicine")
                || normalizedCommand.contains("pill")) {
            return VoiceTarget.MEDICATION;
        }

        if (normalizedCommand.contains("ραντεβ")
                || normalizedCommand.contains("appointment")
                || normalizedCommand.contains("visit")) {
            return VoiceTarget.APPOINTMENT;
        }

        if (normalizedCommand.contains("εξετα")
                || normalizedCommand.contains("examination")
                || normalizedCommand.contains("exam")
                || normalizedCommand.contains("test")
                || normalizedCommand.contains("checkup")) {
            return VoiceTarget.EXAMINATION;
        }

        return VoiceTarget.NONE;
    }

    private boolean isPositiveAnswer(String answer) {
        String normalizedAnswer = normalizeForMatching(answer);
        return normalizedAnswer.contains("ναι")
                || normalizedAnswer.contains("διαγραφ")
                || normalizedAnswer.contains("yes")
                || normalizedAnswer.contains("confirm")
                || normalizedAnswer.contains("delete")
                || normalizedAnswer.equals("ok")
                || normalizedAnswer.equals("okay");
    }

    private boolean isNegativeAnswer(String answer) {
        String normalizedAnswer = normalizeForMatching(answer);
        return normalizedAnswer.contains("οχι")
                || normalizedAnswer.contains("ακυρ")
                || normalizedAnswer.contains("no")
                || normalizedAnswer.contains("cancel")
                || normalizedAnswer.contains("stop");
    }

    private String normalizeSpokenTime(String spokenTime) {
        String cleanText = replaceSpokenNumberWords(normalizeForMatching(spokenTime))
                .replace(".", ":")
                .replace(",", ":")
                .replace("half past", ":30")
                .replace("και μιση", ":30")
                .replace("και μισή", ":30")
                .replace(" και ", ":")
                .replace("η ώρα", "")
                .replace("ωρα", "")
                .replace("o clock", "")
                .trim();

        String[] parts = cleanText.split(":");
        if (parts.length == 2) {
            return twoDigitNumber(parts[0]) + ":" + twoDigitNumber(parts[1]);
        }

        String digitsOnly = cleanText.replaceAll("[^0-9]", "");
        if (digitsOnly.length() == 3) {
            return "0" + digitsOnly.charAt(0) + ":" + digitsOnly.substring(1);
        }
        if (digitsOnly.length() == 4) {
            return digitsOnly.substring(0, 2) + ":" + digitsOnly.substring(2);
        }
        if (digitsOnly.length() == 1 || digitsOnly.length() == 2) {
            return twoDigitNumber(digitsOnly) + ":00";
        }

        return cleanText;
    }

    private String normalizeSpokenDate(String spokenDate) {
        Calendar today = Calendar.getInstance();
        String normalizedText = normalizeForMatching(spokenDate);
        // Support common relative dates before parsing numeric dates.
        if (normalizedText.contains("μεθαυριο") || normalizedText.contains("day after tomorrow")) {
            today.add(Calendar.DAY_OF_YEAR, 2);
            return formatDate(today);
        }
        if (normalizedText.contains("αυριο") || normalizedText.contains("tomorrow")) {
            today.add(Calendar.DAY_OF_YEAR, 1);
            return formatDate(today);
        }
        if (normalizedText.contains("σημερα") || normalizedText.contains("today")) {
            return formatDate(today);
        }

        String cleanText = replaceMonthNames(replaceSpokenNumberWords(normalizedText))
                .replace(".", "/")
                .replace("-", "/")
                .replace(" του ", "/")
                .replace(" of ", "/")
                .replace(" και ", "/")
                .replaceAll("[^0-9/ ]", " ")
                .trim();

        List<String> numbers = extractNumbers(cleanText);
        if (numbers.size() >= 3) {
            if (startsWithMonthName(normalizedText)) {
                return twoDigitNumber(numbers.get(1)) + "/" + twoDigitNumber(numbers.get(0)) + "/" + normalizeYear(numbers.get(2));
            }
            return twoDigitNumber(numbers.get(0)) + "/" + twoDigitNumber(numbers.get(1)) + "/" + normalizeYear(numbers.get(2));
        }
        if (numbers.size() == 2) {
            if (startsWithMonthName(normalizedText)) {
                return twoDigitNumber(numbers.get(1)) + "/" + twoDigitNumber(numbers.get(0)) + "/" + today.get(Calendar.YEAR);
            }
            return twoDigitNumber(numbers.get(0)) + "/" + twoDigitNumber(numbers.get(1)) + "/" + today.get(Calendar.YEAR);
        }

        String[] parts = cleanText.split("/");
        if (parts.length == 3) {
            return twoDigitNumber(parts[0]) + "/" + twoDigitNumber(parts[1]) + "/" + parts[2].replaceAll("[^0-9]", "");
        }

        return cleanText.replaceAll("\\s+", "/");
    }

    private String replaceMonthNames(String text) {
        return text
                .replace("ιανουαριου", " 01 ")
                .replace("ιανουαριο", " 01 ")
                .replace("january", " 01 ")
                .replace("φεβρουαριου", " 02 ")
                .replace("φεβρουαριο", " 02 ")
                .replace("february", " 02 ")
                .replace("μαρτιου", " 03 ")
                .replace("μαρτιο", " 03 ")
                .replace("march", " 03 ")
                .replace("απριλιου", " 04 ")
                .replace("απριλιο", " 04 ")
                .replace("april", " 04 ")
                .replace("μαιου", " 05 ")
                .replace("μαιο", " 05 ")
                .replace("may", " 05 ")
                .replace("ιουνιου", " 06 ")
                .replace("ιουνιο", " 06 ")
                .replace("june", " 06 ")
                .replace("ιουλιου", " 07 ")
                .replace("ιουλιο", " 07 ")
                .replace("july", " 07 ")
                .replace("αυγουστου", " 08 ")
                .replace("αυγουστο", " 08 ")
                .replace("august", " 08 ")
                .replace("σεπτεμβριου", " 09 ")
                .replace("σεπτεμβριο", " 09 ")
                .replace("september", " 09 ")
                .replace("οκτωβριου", " 10 ")
                .replace("οκτωβριο", " 10 ")
                .replace("october", " 10 ")
                .replace("νοεμβριου", " 11 ")
                .replace("νοεμβριο", " 11 ")
                .replace("november", " 11 ")
                .replace("δεκεμβριου", " 12 ")
                .replace("δεκεμβριο", " 12 ")
                .replace("december", " 12 ");
    }

    private boolean startsWithMonthName(String text) {
        String cleanText = normalizeForMatching(text).trim();
        String[] monthPrefixes = {
                "ιανουαρι", "january",
                "φεβρουαρι", "february",
                "μαρτι", "march",
                "απριλι", "april",
                "μαι", "may",
                "ιουνι", "june",
                "ιουλι", "july",
                "αυγουστ", "august",
                "σεπτεμβρι", "september",
                "οκτωβρι", "october",
                "νοεμβρι", "november",
                "δεκεμβρι", "december"
        };

        for (String monthPrefix : monthPrefixes) {
            if (cleanText.startsWith(monthPrefix)) {
                return true;
            }
        }
        return false;
    }

    private String replaceSpokenNumberWords(String text) {
        String result = " " + text + " ";
        String[][] replacements = {
                {" thirty one ", " 31 "}, {" τριαντα ενα ", " 31 "}, {" τριαντα μια ", " 31 "},
                {" thirty ", " 30 "}, {" τριαντα ", " 30 "},
                {" twenty nine ", " 29 "}, {" εικοσι εννεα ", " 29 "},
                {" twenty eight ", " 28 "}, {" εικοσι οκτω ", " 28 "},
                {" twenty seven ", " 27 "}, {" εικοσι επτα ", " 27 "},
                {" twenty six ", " 26 "}, {" εικοσι εξι ", " 26 "},
                {" twenty five ", " 25 "}, {" εικοσι πεντε ", " 25 "},
                {" twenty four ", " 24 "}, {" εικοσι τεσσερα ", " 24 "},
                {" twenty three ", " 23 "}, {" εικοσι τρια ", " 23 "},
                {" twenty two ", " 22 "}, {" εικοσι δυο ", " 22 "},
                {" twenty one ", " 21 "}, {" εικοσι ενα ", " 21 "}, {" εικοσι μια ", " 21 "},
                {" twenty ", " 20 "}, {" εικοσι ", " 20 "},
                {" nineteen ", " 19 "}, {" δεκαεννεα ", " 19 "},
                {" eighteen ", " 18 "}, {" δεκαοκτω ", " 18 "},
                {" seventeen ", " 17 "}, {" δεκαεπτα ", " 17 "},
                {" sixteen ", " 16 "}, {" δεκαεξι ", " 16 "},
                {" fifteen ", " 15 "}, {" δεκαπεντε ", " 15 "},
                {" fourteen ", " 14 "}, {" δεκατεσσερα ", " 14 "},
                {" thirteen ", " 13 "}, {" δεκατρια ", " 13 "},
                {" twelve ", " 12 "}, {" δωδεκα ", " 12 "},
                {" eleven ", " 11 "}, {" εντεκα ", " 11 "},
                {" ten ", " 10 "}, {" δεκα ", " 10 "},
                {" nine ", " 9 "}, {" εννεα ", " 9 "}, {" εννια ", " 9 "},
                {" eight ", " 8 "}, {" οκτω ", " 8 "},
                {" seven ", " 7 "}, {" επτα ", " 7 "},
                {" six ", " 6 "}, {" εξι ", " 6 "},
                {" five ", " 5 "}, {" πεντε ", " 5 "},
                {" four ", " 4 "}, {" τεσσερα ", " 4 "}, {" τεσσερις ", " 4 "},
                {" three ", " 3 "}, {" τρια ", " 3 "}, {" τρεις ", " 3 "},
                {" two ", " 2 "}, {" δυο ", " 2 "}, {" δυο ", " 2 "},
                {" one ", " 1 "}, {" ενα ", " 1 "}, {" μια ", " 1 "}
        };

        for (String[] replacement : replacements) {
            result = result.replace(replacement[0], replacement[1]);
        }
        return result.trim();
    }

    private List<String> extractNumbers(String text) {
        List<String> numbers = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\d+").matcher(text);
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
        return numbers;
    }

    private String normalizeYear(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() == 2) {
            return "20" + digits;
        }
        return digits;
    }

    private String formatDate(Calendar calendar) {
        return twoDigitNumber(String.valueOf(calendar.get(Calendar.DAY_OF_MONTH)))
                + "/"
                + twoDigitNumber(String.valueOf(calendar.get(Calendar.MONTH) + 1))
                + "/"
                + calendar.get(Calendar.YEAR);
    }

    private String twoDigitNumber(String value) {
        String digits = value.replaceAll("[^0-9]", "");
        if (digits.length() == 1) {
            return "0" + digits;
        }
        return digits;
    }

    private String cleanVoiceText(String text) {
        if (text == null) {
            return "";
        }
        return text.toLowerCase(Locale.ROOT).trim();
    }

    private String normalizeForMatching(String text) {
        return cleanVoiceText(text)
                .replace("ά", "α")
                .replace("έ", "ε")
                .replace("ή", "η")
                .replace("ί", "ι")
                .replace("ό", "ο")
                .replace("ύ", "υ")
                .replace("ώ", "ω")
                .replace("ϊ", "ι")
                .replace("ΐ", "ι")
                .replace("ϋ", "υ")
                .replace("ΰ", "υ");
    }

    private void openSection(Class<?> activityClass, int spokenTitleResId) {
        speak(getString(spokenTitleResId));
        startActivity(new Intent(this, activityClass));
    }

    private void showVoiceNotUnderstood() {
        Toast.makeText(this, R.string.voice_not_understood, Toast.LENGTH_SHORT).show();
        speak(getString(R.string.voice_not_understood));
        resetVoiceFlow();
    }

    private void showVoiceInvalidFields() {
        Toast.makeText(this, R.string.voice_invalid_fields, Toast.LENGTH_LONG).show();
        speak(getString(R.string.voice_invalid_fields));
        resetVoiceFlow();
    }

    private void showVoiceItemNotFound() {
        Toast.makeText(this, R.string.voice_item_not_found, Toast.LENGTH_LONG).show();
        speak(getString(R.string.voice_item_not_found));
        resetVoiceFlow();
    }

    private void showVoiceOperationFailed() {
        Toast.makeText(this, R.string.voice_operation_failed, Toast.LENGTH_LONG).show();
        speak(getString(R.string.voice_operation_failed));
        resetVoiceFlow();
    }

    private void showVoiceSuccess(int messageResId) {
        Toast.makeText(this, messageResId, Toast.LENGTH_LONG).show();
        speak(getString(messageResId));
        resetVoiceFlow();
    }

    private void showAuthNotReady() {
        Toast.makeText(this, R.string.anonymous_sign_in_error, Toast.LENGTH_SHORT).show();
        resetVoiceFlow();
    }

    private void speak(String message) {
        if (textToSpeech != null && textToSpeechReady) {
            textToSpeechReady = applyTextToSpeechSettings();
            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null, "caremate_voice");
        }
    }

    private void resetVoiceFlow() {
        pendingVoiceAction = VoiceAction.NONE;
        pendingVoiceTarget = VoiceTarget.NONE;
        pendingVoiceStep = VoiceStep.IDLE;
        voiceTitle = "";
        voiceDosage = "";
        voiceDate = "";
        voiceTime = "";
        pendingDeleteId = "";
        pendingEditId = "";
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == RECORD_AUDIO_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startVoiceCommand();
            } else {
                Toast.makeText(this, R.string.voice_permission_needed, Toast.LENGTH_SHORT).show();
            }
        } else if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE
                && (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED)) {
            Toast.makeText(this, R.string.notification_permission_needed, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
            textToSpeechReady = false;
        }
        super.onDestroy();
    }
}

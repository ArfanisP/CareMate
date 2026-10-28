package com.caremate.app.ui.examination;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.caremate.app.R;
import com.caremate.app.model.Examination;
import com.caremate.app.notification.ReminderScheduler;
import com.caremate.app.repository.ExaminationRepository;
import com.caremate.app.util.DateTimePickerHelper;
import com.caremate.app.util.ReminderDateTimeParser;
import com.caremate.app.viewmodel.ExaminationViewModel;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class ExaminationActivity extends AppCompatActivity {

    private static final String REMINDER_CATEGORY = "examination";
    private static final int REMINDER_MINUTES_BEFORE = 60;

    private ExaminationViewModel examinationViewModel;
    private ReminderScheduler reminderScheduler;
    private TextInputEditText examinationTitleEditText;
    private TextInputEditText examinationDateEditText;
    private TextInputEditText examinationTimeEditText;
    private TextInputEditText examinationNotesEditText;
    private TextView formTitleTextView;
    private TextView emptyExaminationsTextView;
    private LinearLayout examinationsListContainer;
    private MaterialButton saveExaminationButton;
    private MaterialButton cancelEditButton;
    private Examination selectedExamination;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_examination);

        examinationViewModel = new ExaminationViewModel();
        reminderScheduler = new ReminderScheduler(this);
        bindViews();
        setupInsets();
        setupPickers();
        setupActions();
        loadExaminations();
    }

    private void bindViews() {
        examinationTitleEditText = findViewById(R.id.examinationTitleEditText);
        examinationDateEditText = findViewById(R.id.examinationDateEditText);
        examinationTimeEditText = findViewById(R.id.examinationTimeEditText);
        examinationNotesEditText = findViewById(R.id.examinationNotesEditText);
        formTitleTextView = findViewById(R.id.formTitleTextView);
        emptyExaminationsTextView = findViewById(R.id.emptyExaminationsTextView);
        examinationsListContainer = findViewById(R.id.examinationsListContainer);
        saveExaminationButton = findViewById(R.id.saveExaminationButton);
        cancelEditButton = findViewById(R.id.cancelEditButton);
    }

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.examinationRoot), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupPickers() {
        DateTimePickerHelper.attachDatePicker(this, examinationDateEditText);
        DateTimePickerHelper.attachTimePicker(this, examinationTimeEditText);
    }

    private void setupActions() {
        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        saveExaminationButton.setOnClickListener(view -> saveExamination());
        cancelEditButton.setOnClickListener(view -> clearForm());
    }

    private void loadExaminations() {
        examinationViewModel.loadExaminations(new ExaminationRepository.ExaminationLoadCallback() {
            @Override
            public void onExaminationsLoaded(List<Examination> examinations) {
                renderExaminations(examinations);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(ExaminationActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderExaminations(List<Examination> examinations) {
        examinationsListContainer.removeAllViews();
        boolean hasExaminations = !examinations.isEmpty();
        examinationsListContainer.setVisibility(hasExaminations ? View.VISIBLE : View.GONE);
        emptyExaminationsTextView.setVisibility(hasExaminations ? View.GONE : View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Examination examination : examinations) {
            View examinationView = inflater.inflate(R.layout.item_examination, examinationsListContainer, false);
            bindExaminationView(examinationView, examination);
            examinationsListContainer.addView(examinationView);
        }
    }

    private void bindExaminationView(View examinationView, Examination examination) {
        TextView examinationTitleTextView = examinationView.findViewById(R.id.examinationTitleTextView);
        TextView examinationDetailsTextView = examinationView.findViewById(R.id.examinationDetailsTextView);
        TextView examinationNotesTextView = examinationView.findViewById(R.id.examinationNotesTextView);
        MaterialButton editExaminationButton = examinationView.findViewById(R.id.editExaminationButton);
        MaterialButton deleteExaminationButton = examinationView.findViewById(R.id.deleteExaminationButton);

        examinationTitleTextView.setText(examination.getTitle());
        examinationDetailsTextView.setText(getString(
                R.string.examination_details_format,
                examination.getDate(),
                examination.getTime()
        ));

        if (examination.getNotes() == null || examination.getNotes().trim().isEmpty()) {
            examinationNotesTextView.setVisibility(View.GONE);
        } else {
            examinationNotesTextView.setVisibility(View.VISIBLE);
            examinationNotesTextView.setText(examination.getNotes());
        }

        editExaminationButton.setOnClickListener(view -> showExaminationForEditing(examination));
        deleteExaminationButton.setOnClickListener(view -> confirmExaminationDelete(examination));
    }

    private void saveExamination() {
        String title = getTextValue(examinationTitleEditText);
        String date = getTextValue(examinationDateEditText);
        String time = getTextValue(examinationTimeEditText);
        String notes = getTextValue(examinationNotesEditText);

        if (title.isEmpty() || date.isEmpty() || time.isEmpty()) {
            Toast.makeText(this, R.string.examination_required_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        Examination examination = new Examination(
                selectedExamination == null ? null : selectedExamination.getId(),
                title,
                date,
                time,
                notes
        );

        saveExaminationButton.setEnabled(false);
        Task<Void> saveTask = examinationViewModel.saveExamination(examination);
        if (saveTask == null) {
            saveExaminationButton.setEnabled(true);
            Toast.makeText(this, R.string.anonymous_sign_in_error, Toast.LENGTH_SHORT).show();
            return;
        }

        saveTask
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, R.string.examination_saved, Toast.LENGTH_SHORT).show();
                    scheduleExaminationReminder(examination);
                    clearForm();
                    loadExaminations();
                })
                .addOnFailureListener(exception ->
                        Toast.makeText(this, R.string.examination_save_error, Toast.LENGTH_SHORT).show()
                )
                .addOnCompleteListener(task -> saveExaminationButton.setEnabled(true));
    }

    private void showExaminationForEditing(Examination examination) {
        selectedExamination = examination;
        formTitleTextView.setText(R.string.examination_form_edit_title);
        saveExaminationButton.setText(R.string.action_update_examination);
        cancelEditButton.setVisibility(View.VISIBLE);
        examinationTitleEditText.setText(examination.getTitle());
        examinationDateEditText.setText(examination.getDate());
        examinationTimeEditText.setText(examination.getTime());
        examinationNotesEditText.setText(examination.getNotes());
        examinationTitleEditText.requestFocus();
    }

    private void confirmExaminationDelete(Examination examination) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_examination_title)
                .setMessage(getString(R.string.delete_examination_message, examination.getTitle()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> deleteExamination(examination))
                .show();
    }

    private void deleteExamination(Examination examination) {
        Task<Void> deleteTask = examinationViewModel.deleteExamination(examination.getId());
        if (deleteTask == null) {
            Toast.makeText(this, R.string.anonymous_sign_in_error, Toast.LENGTH_SHORT).show();
            return;
        }

        deleteTask
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, R.string.examination_deleted, Toast.LENGTH_SHORT).show();
                    reminderScheduler.cancel(REMINDER_CATEGORY, examination.getId());
                    if (selectedExamination != null && selectedExamination.getId().equals(examination.getId())) {
                        clearForm();
                    }
                    loadExaminations();
                })
                .addOnFailureListener(exception ->
                        Toast.makeText(this, R.string.examination_delete_error, Toast.LENGTH_SHORT).show()
                );
    }

    private void scheduleExaminationReminder(Examination examination) {
        long reminderTime = ReminderDateTimeParser.dateTimeMillisBefore(
                examination.getDate(),
                examination.getTime(),
                REMINDER_MINUTES_BEFORE
        );
        boolean scheduled = reminderScheduler.scheduleOnce(
                REMINDER_CATEGORY,
                examination.getId(),
                reminderTime,
                getString(R.string.notification_examination_title),
                getString(R.string.notification_examination_message, examination.getTitle(), examination.getTime())
        );

        Toast.makeText(
                this,
                scheduled ? R.string.reminder_scheduled : R.string.reminder_not_scheduled,
                Toast.LENGTH_LONG
        ).show();
    }

    private void clearForm() {
        selectedExamination = null;
        formTitleTextView.setText(R.string.examination_form_add_title);
        saveExaminationButton.setText(R.string.action_save_examination);
        cancelEditButton.setVisibility(View.GONE);
        examinationTitleEditText.setText("");
        examinationDateEditText.setText("");
        examinationTimeEditText.setText("");
        examinationNotesEditText.setText("");
    }

    private String getTextValue(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}

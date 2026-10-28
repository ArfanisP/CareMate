package com.caremate.app.ui.medication;

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
import com.caremate.app.model.Medication;
import com.caremate.app.notification.ReminderScheduler;
import com.caremate.app.repository.MedicationRepository;
import com.caremate.app.util.DateTimePickerHelper;
import com.caremate.app.util.ReminderDateTimeParser;
import com.caremate.app.viewmodel.MedicationViewModel;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class MedicationActivity extends AppCompatActivity {

    private static final String REMINDER_CATEGORY = "medication";

    private MedicationViewModel medicationViewModel;
    private ReminderScheduler reminderScheduler;
    private TextInputEditText medicationNameEditText;
    private TextInputEditText medicationDosageEditText;
    private TextInputEditText medicationTimeEditText;
    private TextInputEditText medicationNotesEditText;
    private TextView formTitleTextView;
    private TextView emptyMedicationsTextView;
    private LinearLayout medicationsListContainer;
    private MaterialButton saveMedicationButton;
    private MaterialButton cancelEditButton;
    private Medication selectedMedication;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_medication);

        medicationViewModel = new MedicationViewModel();
        reminderScheduler = new ReminderScheduler(this);
        bindViews();
        setupInsets();
        setupPickers();
        setupActions();
        loadMedications();
    }

    private void bindViews() {
        medicationNameEditText = findViewById(R.id.medicationNameEditText);
        medicationDosageEditText = findViewById(R.id.medicationDosageEditText);
        medicationTimeEditText = findViewById(R.id.medicationTimeEditText);
        medicationNotesEditText = findViewById(R.id.medicationNotesEditText);
        formTitleTextView = findViewById(R.id.formTitleTextView);
        emptyMedicationsTextView = findViewById(R.id.emptyMedicationsTextView);
        medicationsListContainer = findViewById(R.id.medicationsListContainer);
        saveMedicationButton = findViewById(R.id.saveMedicationButton);
        cancelEditButton = findViewById(R.id.cancelEditButton);
    }

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.medicationRoot), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupPickers() {
        DateTimePickerHelper.attachTimePicker(this, medicationTimeEditText);
    }

    private void setupActions() {
        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        saveMedicationButton.setOnClickListener(view -> saveMedication());
        cancelEditButton.setOnClickListener(view -> clearForm());
    }

    private void loadMedications() {
        medicationViewModel.loadMedications(new MedicationRepository.MedicationLoadCallback() {
            @Override
            public void onMedicationsLoaded(List<Medication> medications) {
                renderMedications(medications);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(MedicationActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderMedications(List<Medication> medications) {
        medicationsListContainer.removeAllViews();
        boolean hasMedications = !medications.isEmpty();
        medicationsListContainer.setVisibility(hasMedications ? View.VISIBLE : View.GONE);
        emptyMedicationsTextView.setVisibility(hasMedications ? View.GONE : View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Medication medication : medications) {
            View medicationView = inflater.inflate(R.layout.item_medication, medicationsListContainer, false);
            bindMedicationView(medicationView, medication);
            medicationsListContainer.addView(medicationView);
        }
    }

    private void bindMedicationView(View medicationView, Medication medication) {
        TextView medicationNameTextView = medicationView.findViewById(R.id.medicationNameTextView);
        TextView medicationDetailsTextView = medicationView.findViewById(R.id.medicationDetailsTextView);
        TextView medicationNotesTextView = medicationView.findViewById(R.id.medicationNotesTextView);
        MaterialButton editMedicationButton = medicationView.findViewById(R.id.editMedicationButton);
        MaterialButton deleteMedicationButton = medicationView.findViewById(R.id.deleteMedicationButton);

        medicationNameTextView.setText(medication.getName());
        medicationDetailsTextView.setText(getString(
                R.string.medication_details_format,
                medication.getDosage(),
                medication.getTime()
        ));

        if (medication.getNotes() == null || medication.getNotes().trim().isEmpty()) {
            medicationNotesTextView.setVisibility(View.GONE);
        } else {
            medicationNotesTextView.setVisibility(View.VISIBLE);
            medicationNotesTextView.setText(medication.getNotes());
        }

        editMedicationButton.setOnClickListener(view -> showMedicationForEditing(medication));
        deleteMedicationButton.setOnClickListener(view -> confirmMedicationDelete(medication));
    }

    private void saveMedication() {
        String name = getTextValue(medicationNameEditText);
        String dosage = getTextValue(medicationDosageEditText);
        String time = getTextValue(medicationTimeEditText);
        String notes = getTextValue(medicationNotesEditText);

        if (name.isEmpty() || dosage.isEmpty() || time.isEmpty()) {
            Toast.makeText(this, R.string.medication_required_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        Medication medication = new Medication(
                selectedMedication == null ? null : selectedMedication.getId(),
                name,
                dosage,
                time,
                notes
        );

        saveMedicationButton.setEnabled(false);
        Task<Void> saveTask = medicationViewModel.saveMedication(medication);
        if (saveTask == null) {
            saveMedicationButton.setEnabled(true);
            Toast.makeText(this, R.string.anonymous_sign_in_error, Toast.LENGTH_SHORT).show();
            return;
        }

        saveTask
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, R.string.medication_saved, Toast.LENGTH_SHORT).show();
                    scheduleMedicationReminder(medication);
                    clearForm();
                    loadMedications();
                })
                .addOnFailureListener(exception ->
                        Toast.makeText(this, R.string.medication_save_error, Toast.LENGTH_SHORT).show()
                )
                .addOnCompleteListener(task -> saveMedicationButton.setEnabled(true));
    }

    private void showMedicationForEditing(Medication medication) {
        selectedMedication = medication;
        formTitleTextView.setText(R.string.medication_form_edit_title);
        saveMedicationButton.setText(R.string.action_update_medication);
        cancelEditButton.setVisibility(View.VISIBLE);
        medicationNameEditText.setText(medication.getName());
        medicationDosageEditText.setText(medication.getDosage());
        medicationTimeEditText.setText(medication.getTime());
        medicationNotesEditText.setText(medication.getNotes());
        medicationNameEditText.requestFocus();
    }

    private void confirmMedicationDelete(Medication medication) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_medication_title)
                .setMessage(getString(R.string.delete_medication_message, medication.getName()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> deleteMedication(medication))
                .show();
    }

    private void deleteMedication(Medication medication) {
        Task<Void> deleteTask = medicationViewModel.deleteMedication(medication.getId());
        if (deleteTask == null) {
            Toast.makeText(this, R.string.anonymous_sign_in_error, Toast.LENGTH_SHORT).show();
            return;
        }

        deleteTask
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, R.string.medication_deleted, Toast.LENGTH_SHORT).show();
                    reminderScheduler.cancel(REMINDER_CATEGORY, medication.getId());
                    if (selectedMedication != null && selectedMedication.getId().equals(medication.getId())) {
                        clearForm();
                    }
                    loadMedications();
                })
                .addOnFailureListener(exception ->
                        Toast.makeText(this, R.string.medication_delete_error, Toast.LENGTH_SHORT).show()
                );
    }

    private void scheduleMedicationReminder(Medication medication) {
        long reminderTime = ReminderDateTimeParser.nextDailyTimeMillis(medication.getTime());
        boolean scheduled = reminderScheduler.scheduleDaily(
                REMINDER_CATEGORY,
                medication.getId(),
                reminderTime,
                getString(R.string.notification_medication_title),
                getString(R.string.notification_medication_message, medication.getName(), medication.getDosage())
        );

        Toast.makeText(
                this,
                scheduled ? R.string.reminder_scheduled : R.string.reminder_not_scheduled,
                Toast.LENGTH_LONG
        ).show();
    }

    private void clearForm() {
        selectedMedication = null;
        formTitleTextView.setText(R.string.medication_form_add_title);
        saveMedicationButton.setText(R.string.action_save_medication);
        cancelEditButton.setVisibility(View.GONE);
        medicationNameEditText.setText("");
        medicationDosageEditText.setText("");
        medicationTimeEditText.setText("");
        medicationNotesEditText.setText("");
    }

    private String getTextValue(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}

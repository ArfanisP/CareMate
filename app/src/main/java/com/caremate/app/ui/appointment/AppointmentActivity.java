package com.caremate.app.ui.appointment;

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
import com.caremate.app.model.Appointment;
import com.caremate.app.notification.ReminderScheduler;
import com.caremate.app.repository.AppointmentRepository;
import com.caremate.app.util.DateTimePickerHelper;
import com.caremate.app.util.ReminderDateTimeParser;
import com.caremate.app.viewmodel.AppointmentViewModel;
import com.google.android.gms.tasks.Task;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class AppointmentActivity extends AppCompatActivity {

    private static final String REMINDER_CATEGORY = "appointment";
    private static final int REMINDER_MINUTES_BEFORE = 60;

    private AppointmentViewModel appointmentViewModel;
    private ReminderScheduler reminderScheduler;
    private TextInputEditText appointmentTitleEditText;
    private TextInputEditText appointmentDateEditText;
    private TextInputEditText appointmentTimeEditText;
    private TextInputEditText appointmentNotesEditText;
    private TextView formTitleTextView;
    private TextView emptyAppointmentsTextView;
    private LinearLayout appointmentsListContainer;
    private MaterialButton saveAppointmentButton;
    private MaterialButton cancelEditButton;
    private Appointment selectedAppointment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_appointment);

        appointmentViewModel = new AppointmentViewModel();
        reminderScheduler = new ReminderScheduler(this);
        bindViews();
        setupInsets();
        setupPickers();
        setupActions();
        loadAppointments();
    }

    private void bindViews() {
        appointmentTitleEditText = findViewById(R.id.appointmentTitleEditText);
        appointmentDateEditText = findViewById(R.id.appointmentDateEditText);
        appointmentTimeEditText = findViewById(R.id.appointmentTimeEditText);
        appointmentNotesEditText = findViewById(R.id.appointmentNotesEditText);
        formTitleTextView = findViewById(R.id.formTitleTextView);
        emptyAppointmentsTextView = findViewById(R.id.emptyAppointmentsTextView);
        appointmentsListContainer = findViewById(R.id.appointmentsListContainer);
        saveAppointmentButton = findViewById(R.id.saveAppointmentButton);
        cancelEditButton = findViewById(R.id.cancelEditButton);
    }

    private void setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.appointmentRoot), (view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private void setupPickers() {
        DateTimePickerHelper.attachDatePicker(this, appointmentDateEditText);
        DateTimePickerHelper.attachTimePicker(this, appointmentTimeEditText);
    }

    private void setupActions() {
        findViewById(R.id.backButton).setOnClickListener(view -> finish());
        saveAppointmentButton.setOnClickListener(view -> saveAppointment());
        cancelEditButton.setOnClickListener(view -> clearForm());
    }

    private void loadAppointments() {
        appointmentViewModel.loadAppointments(new AppointmentRepository.AppointmentLoadCallback() {
            @Override
            public void onAppointmentsLoaded(List<Appointment> appointments) {
                renderAppointments(appointments);
            }

            @Override
            public void onError(String message) {
                Toast.makeText(AppointmentActivity.this, message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void renderAppointments(List<Appointment> appointments) {
        appointmentsListContainer.removeAllViews();
        boolean hasAppointments = !appointments.isEmpty();
        appointmentsListContainer.setVisibility(hasAppointments ? View.VISIBLE : View.GONE);
        emptyAppointmentsTextView.setVisibility(hasAppointments ? View.GONE : View.VISIBLE);

        LayoutInflater inflater = LayoutInflater.from(this);
        for (Appointment appointment : appointments) {
            View appointmentView = inflater.inflate(R.layout.item_appointment, appointmentsListContainer, false);
            bindAppointmentView(appointmentView, appointment);
            appointmentsListContainer.addView(appointmentView);
        }
    }

    private void bindAppointmentView(View appointmentView, Appointment appointment) {
        TextView appointmentTitleTextView = appointmentView.findViewById(R.id.appointmentTitleTextView);
        TextView appointmentDetailsTextView = appointmentView.findViewById(R.id.appointmentDetailsTextView);
        TextView appointmentNotesTextView = appointmentView.findViewById(R.id.appointmentNotesTextView);
        MaterialButton editAppointmentButton = appointmentView.findViewById(R.id.editAppointmentButton);
        MaterialButton deleteAppointmentButton = appointmentView.findViewById(R.id.deleteAppointmentButton);

        appointmentTitleTextView.setText(appointment.getTitle());
        appointmentDetailsTextView.setText(getString(
                R.string.appointment_details_format,
                appointment.getDate(),
                appointment.getTime()
        ));

        if (appointment.getNotes() == null || appointment.getNotes().trim().isEmpty()) {
            appointmentNotesTextView.setVisibility(View.GONE);
        } else {
            appointmentNotesTextView.setVisibility(View.VISIBLE);
            appointmentNotesTextView.setText(appointment.getNotes());
        }

        editAppointmentButton.setOnClickListener(view -> showAppointmentForEditing(appointment));
        deleteAppointmentButton.setOnClickListener(view -> confirmAppointmentDelete(appointment));
    }

    private void saveAppointment() {
        String title = getTextValue(appointmentTitleEditText);
        String date = getTextValue(appointmentDateEditText);
        String time = getTextValue(appointmentTimeEditText);
        String notes = getTextValue(appointmentNotesEditText);

        if (title.isEmpty() || date.isEmpty() || time.isEmpty()) {
            Toast.makeText(this, R.string.appointment_required_fields, Toast.LENGTH_SHORT).show();
            return;
        }

        Appointment appointment = new Appointment(
                selectedAppointment == null ? null : selectedAppointment.getId(),
                title,
                date,
                time,
                notes
        );

        saveAppointmentButton.setEnabled(false);
        Task<Void> saveTask = appointmentViewModel.saveAppointment(appointment);
        if (saveTask == null) {
            saveAppointmentButton.setEnabled(true);
            Toast.makeText(this, R.string.anonymous_sign_in_error, Toast.LENGTH_SHORT).show();
            return;
        }

        saveTask
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, R.string.appointment_saved, Toast.LENGTH_SHORT).show();
                    scheduleAppointmentReminder(appointment);
                    clearForm();
                    loadAppointments();
                })
                .addOnFailureListener(exception ->
                        Toast.makeText(this, R.string.appointment_save_error, Toast.LENGTH_SHORT).show()
                )
                .addOnCompleteListener(task -> saveAppointmentButton.setEnabled(true));
    }

    private void showAppointmentForEditing(Appointment appointment) {
        selectedAppointment = appointment;
        formTitleTextView.setText(R.string.appointment_form_edit_title);
        saveAppointmentButton.setText(R.string.action_update_appointment);
        cancelEditButton.setVisibility(View.VISIBLE);
        appointmentTitleEditText.setText(appointment.getTitle());
        appointmentDateEditText.setText(appointment.getDate());
        appointmentTimeEditText.setText(appointment.getTime());
        appointmentNotesEditText.setText(appointment.getNotes());
        appointmentTitleEditText.requestFocus();
    }

    private void confirmAppointmentDelete(Appointment appointment) {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.delete_appointment_title)
                .setMessage(getString(R.string.delete_appointment_message, appointment.getTitle()))
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(R.string.action_delete, (dialog, which) -> deleteAppointment(appointment))
                .show();
    }

    private void deleteAppointment(Appointment appointment) {
        Task<Void> deleteTask = appointmentViewModel.deleteAppointment(appointment.getId());
        if (deleteTask == null) {
            Toast.makeText(this, R.string.anonymous_sign_in_error, Toast.LENGTH_SHORT).show();
            return;
        }

        deleteTask
                .addOnSuccessListener(unused -> {
                    Toast.makeText(this, R.string.appointment_deleted, Toast.LENGTH_SHORT).show();
                    reminderScheduler.cancel(REMINDER_CATEGORY, appointment.getId());
                    if (selectedAppointment != null && selectedAppointment.getId().equals(appointment.getId())) {
                        clearForm();
                    }
                    loadAppointments();
                })
                .addOnFailureListener(exception ->
                        Toast.makeText(this, R.string.appointment_delete_error, Toast.LENGTH_SHORT).show()
                );
    }

    private void scheduleAppointmentReminder(Appointment appointment) {
        long reminderTime = ReminderDateTimeParser.dateTimeMillisBefore(
                appointment.getDate(),
                appointment.getTime(),
                REMINDER_MINUTES_BEFORE
        );
        boolean scheduled = reminderScheduler.scheduleOnce(
                REMINDER_CATEGORY,
                appointment.getId(),
                reminderTime,
                getString(R.string.notification_appointment_title),
                getString(R.string.notification_appointment_message, appointment.getTitle(), appointment.getTime())
        );

        Toast.makeText(
                this,
                scheduled ? R.string.reminder_scheduled : R.string.reminder_not_scheduled,
                Toast.LENGTH_LONG
        ).show();
    }

    private void clearForm() {
        selectedAppointment = null;
        formTitleTextView.setText(R.string.appointment_form_add_title);
        saveAppointmentButton.setText(R.string.action_save_appointment);
        cancelEditButton.setVisibility(View.GONE);
        appointmentTitleEditText.setText("");
        appointmentDateEditText.setText("");
        appointmentTimeEditText.setText("");
        appointmentNotesEditText.setText("");
    }

    private String getTextValue(TextInputEditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString().trim();
    }
}

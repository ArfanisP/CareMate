package com.caremate.app.notification;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.caremate.app.R;
import com.caremate.app.model.Appointment;
import com.caremate.app.model.Examination;
import com.caremate.app.model.Medication;
import com.caremate.app.repository.AppointmentRepository;
import com.caremate.app.repository.ExaminationRepository;
import com.caremate.app.repository.MedicationRepository;
import com.caremate.app.util.ReminderDateTimeParser;

import java.util.List;

public class BootReminderReceiver extends BroadcastReceiver {

    private static final String MEDICATION_CATEGORY = "medication";
    private static final String APPOINTMENT_CATEGORY = "appointment";
    private static final String EXAMINATION_CATEGORY = "examination";
    private static final int EVENT_REMINDER_MINUTES_BEFORE = 60;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        PendingResult pendingResult = goAsync();
        ReminderScheduler reminderScheduler = new ReminderScheduler(context);
        int[] pendingLoads = {3};

        // Recreate alarms that Android removes after a device reboot.
        new MedicationRepository().loadMedications(new MedicationRepository.MedicationLoadCallback() {
            @Override
            public void onMedicationsLoaded(List<Medication> medications) {
                for (Medication medication : medications) {
                    reminderScheduler.scheduleDaily(
                            MEDICATION_CATEGORY,
                            medication.getId(),
                            ReminderDateTimeParser.nextDailyTimeMillis(medication.getTime()),
                            context.getString(R.string.notification_medication_title),
                            context.getString(R.string.notification_medication_message, medication.getName(), medication.getDosage())
                    );
                }
                finishOneLoad(pendingLoads, pendingResult);
            }

            @Override
            public void onError(String message) {
                finishOneLoad(pendingLoads, pendingResult);
            }
        });

        new AppointmentRepository().loadAppointments(new AppointmentRepository.AppointmentLoadCallback() {
            @Override
            public void onAppointmentsLoaded(List<Appointment> appointments) {
                for (Appointment appointment : appointments) {
                    reminderScheduler.scheduleOnce(
                            APPOINTMENT_CATEGORY,
                            appointment.getId(),
                            ReminderDateTimeParser.dateTimeMillisBefore(
                                    appointment.getDate(),
                                    appointment.getTime(),
                                    EVENT_REMINDER_MINUTES_BEFORE
                            ),
                            context.getString(R.string.notification_appointment_title),
                            context.getString(R.string.notification_appointment_message, appointment.getTitle(), appointment.getTime())
                    );
                }
                finishOneLoad(pendingLoads, pendingResult);
            }

            @Override
            public void onError(String message) {
                finishOneLoad(pendingLoads, pendingResult);
            }
        });

        new ExaminationRepository().loadExaminations(new ExaminationRepository.ExaminationLoadCallback() {
            @Override
            public void onExaminationsLoaded(List<Examination> examinations) {
                for (Examination examination : examinations) {
                    reminderScheduler.scheduleOnce(
                            EXAMINATION_CATEGORY,
                            examination.getId(),
                            ReminderDateTimeParser.dateTimeMillisBefore(
                                    examination.getDate(),
                                    examination.getTime(),
                                    EVENT_REMINDER_MINUTES_BEFORE
                            ),
                            context.getString(R.string.notification_examination_title),
                            context.getString(R.string.notification_examination_message, examination.getTitle(), examination.getTime())
                    );
                }
                finishOneLoad(pendingLoads, pendingResult);
            }

            @Override
            public void onError(String message) {
                finishOneLoad(pendingLoads, pendingResult);
            }
        });
    }

    private void finishOneLoad(int[] pendingLoads, PendingResult pendingResult) {
        pendingLoads[0]--;
        if (pendingLoads[0] <= 0) {
            pendingResult.finish();
        }
    }
}

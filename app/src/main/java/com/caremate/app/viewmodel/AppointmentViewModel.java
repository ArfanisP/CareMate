package com.caremate.app.viewmodel;

import com.caremate.app.model.Appointment;
import com.caremate.app.repository.AppointmentRepository;
import com.google.android.gms.tasks.Task;

public class AppointmentViewModel {

    private final AppointmentRepository appointmentRepository;

    public AppointmentViewModel() {
        appointmentRepository = new AppointmentRepository();
    }

    public void loadAppointments(AppointmentRepository.AppointmentLoadCallback callback) {
        appointmentRepository.loadAppointments(callback);
    }

    public Task<Void> saveAppointment(Appointment appointment) {
        return appointmentRepository.saveAppointment(appointment);
    }

    public Task<Void> deleteAppointment(String appointmentId) {
        return appointmentRepository.deleteAppointment(appointmentId);
    }
}

package com.caremate.app.repository;

import com.caremate.app.model.Appointment;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AppointmentRepository {

    public interface AppointmentLoadCallback {
        void onAppointmentsLoaded(List<Appointment> appointments);

        void onError(String message);
    }

    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;

    public AppointmentRepository() {
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    public void loadAppointments(AppointmentLoadCallback callback) {
        CollectionReference collectionReference = getAppointmentCollection();
        if (collectionReference == null) {
            callback.onError("Η εφαρμογή δεν είναι ακόμα έτοιμη.");
            return;
        }

        collectionReference
                .orderBy("date", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Appointment> appointments = new ArrayList<>();
                    for (QueryDocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                        appointments.add(mapAppointment(documentSnapshot));
                    }
                    callback.onAppointmentsLoaded(appointments);
                })
                .addOnFailureListener(exception ->
                        callback.onError("Δεν ήταν δυνατή η φόρτωση των ραντεβού.")
                );
    }

    public Task<Void> saveAppointment(Appointment appointment) {
        CollectionReference collectionReference = getAppointmentCollection();
        if (collectionReference == null) {
            return null;
        }

        Map<String, Object> appointmentData = new HashMap<>();
        appointmentData.put("title", appointment.getTitle());
        appointmentData.put("date", appointment.getDate());
        appointmentData.put("time", appointment.getTime());
        appointmentData.put("notes", appointment.getNotes());

        if (appointment.getId() == null || appointment.getId().isEmpty()) {
            DocumentReference documentReference = collectionReference.document();
            appointment.setId(documentReference.getId());
            return documentReference.set(appointmentData);
        }

        return collectionReference.document(appointment.getId()).set(appointmentData);
    }

    public Task<Void> deleteAppointment(String appointmentId) {
        CollectionReference collectionReference = getAppointmentCollection();
        if (collectionReference == null) {
            return null;
        }

        return collectionReference.document(appointmentId).delete();
    }

    private CollectionReference getAppointmentCollection() {
        if (firebaseAuth.getCurrentUser() == null) {
            return null;
        }

        String userId = firebaseAuth.getCurrentUser().getUid();
        return firestore
                .collection("users")
                .document(userId)
                .collection("appointments");
    }

    private Appointment mapAppointment(DocumentSnapshot documentSnapshot) {
        Appointment appointment = documentSnapshot.toObject(Appointment.class);
        if (appointment == null) {
            appointment = new Appointment();
        }
        appointment.setId(documentSnapshot.getId());
        return appointment;
    }
}

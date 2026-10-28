package com.caremate.app.repository;

import com.caremate.app.model.Medication;
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

public class MedicationRepository {

    public interface MedicationLoadCallback {
        void onMedicationsLoaded(List<Medication> medications);

        void onError(String message);
    }

    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;

    public MedicationRepository() {
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    public void loadMedications(MedicationLoadCallback callback) {
        CollectionReference collectionReference = getMedicationCollection();
        if (collectionReference == null) {
            callback.onError("Η εφαρμογή δεν είναι ακόμα έτοιμη.");
            return;
        }

        collectionReference
                .orderBy("name", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Medication> medications = new ArrayList<>();
                    for (QueryDocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                        medications.add(mapMedication(documentSnapshot));
                    }
                    callback.onMedicationsLoaded(medications);
                })
                .addOnFailureListener(exception ->
                        callback.onError("Δεν ήταν δυνατή η φόρτωση των φαρμάκων.")
                );
    }

    public Task<Void> saveMedication(Medication medication) {
        CollectionReference collectionReference = getMedicationCollection();
        if (collectionReference == null) {
            return null;
        }

        Map<String, Object> medicationData = new HashMap<>();
        medicationData.put("name", medication.getName());
        medicationData.put("dosage", medication.getDosage());
        medicationData.put("time", medication.getTime());
        medicationData.put("notes", medication.getNotes());

        if (medication.getId() == null || medication.getId().isEmpty()) {
            // Create the id before saving so reminders can use the same key.
            DocumentReference documentReference = collectionReference.document();
            medication.setId(documentReference.getId());
            return documentReference.set(medicationData);
        }

        return collectionReference.document(medication.getId()).set(medicationData);
    }

    public Task<Void> deleteMedication(String medicationId) {
        CollectionReference collectionReference = getMedicationCollection();
        if (collectionReference == null) {
            return null;
        }

        return collectionReference.document(medicationId).delete();
    }

    private CollectionReference getMedicationCollection() {
        if (firebaseAuth.getCurrentUser() == null) {
            return null;
        }

        String userId = firebaseAuth.getCurrentUser().getUid();
        return firestore
                .collection("users")
                .document(userId)
                .collection("medications");
    }

    private Medication mapMedication(DocumentSnapshot documentSnapshot) {
        Medication medication = documentSnapshot.toObject(Medication.class);
        if (medication == null) {
            medication = new Medication();
        }
        medication.setId(documentSnapshot.getId());
        return medication;
    }
}

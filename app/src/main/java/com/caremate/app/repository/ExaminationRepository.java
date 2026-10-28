package com.caremate.app.repository;

import com.caremate.app.model.Examination;
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

public class ExaminationRepository {

    public interface ExaminationLoadCallback {
        void onExaminationsLoaded(List<Examination> examinations);

        void onError(String message);
    }

    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;

    public ExaminationRepository() {
        firebaseAuth = FirebaseAuth.getInstance();
        firestore = FirebaseFirestore.getInstance();
    }

    public void loadExaminations(ExaminationLoadCallback callback) {
        CollectionReference collectionReference = getExaminationCollection();
        if (collectionReference == null) {
            callback.onError("Η εφαρμογή δεν είναι ακόμα έτοιμη.");
            return;
        }

        collectionReference
                .orderBy("date", Query.Direction.ASCENDING)
                .get()
                .addOnSuccessListener(queryDocumentSnapshots -> {
                    List<Examination> examinations = new ArrayList<>();
                    for (QueryDocumentSnapshot documentSnapshot : queryDocumentSnapshots) {
                        examinations.add(mapExamination(documentSnapshot));
                    }
                    callback.onExaminationsLoaded(examinations);
                })
                .addOnFailureListener(exception ->
                        callback.onError("Δεν ήταν δυνατή η φόρτωση των εξετάσεων.")
                );
    }

    public Task<Void> saveExamination(Examination examination) {
        CollectionReference collectionReference = getExaminationCollection();
        if (collectionReference == null) {
            return null;
        }

        Map<String, Object> examinationData = new HashMap<>();
        examinationData.put("title", examination.getTitle());
        examinationData.put("date", examination.getDate());
        examinationData.put("time", examination.getTime());
        examinationData.put("notes", examination.getNotes());

        if (examination.getId() == null || examination.getId().isEmpty()) {
            DocumentReference documentReference = collectionReference.document();
            examination.setId(documentReference.getId());
            return documentReference.set(examinationData);
        }

        return collectionReference.document(examination.getId()).set(examinationData);
    }

    public Task<Void> deleteExamination(String examinationId) {
        CollectionReference collectionReference = getExaminationCollection();
        if (collectionReference == null) {
            return null;
        }

        return collectionReference.document(examinationId).delete();
    }

    private CollectionReference getExaminationCollection() {
        if (firebaseAuth.getCurrentUser() == null) {
            return null;
        }

        String userId = firebaseAuth.getCurrentUser().getUid();
        return firestore
                .collection("users")
                .document(userId)
                .collection("examinations");
    }

    private Examination mapExamination(DocumentSnapshot documentSnapshot) {
        Examination examination = documentSnapshot.toObject(Examination.class);
        if (examination == null) {
            examination = new Examination();
        }
        examination.setId(documentSnapshot.getId());
        return examination;
    }
}

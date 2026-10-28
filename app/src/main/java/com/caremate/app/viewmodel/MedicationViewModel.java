package com.caremate.app.viewmodel;

import com.caremate.app.model.Medication;
import com.caremate.app.repository.MedicationRepository;
import com.google.android.gms.tasks.Task;

public class MedicationViewModel {

    private final MedicationRepository medicationRepository;

    public MedicationViewModel() {
        medicationRepository = new MedicationRepository();
    }

    public void loadMedications(MedicationRepository.MedicationLoadCallback callback) {
        medicationRepository.loadMedications(callback);
    }

    public Task<Void> saveMedication(Medication medication) {
        return medicationRepository.saveMedication(medication);
    }

    public Task<Void> deleteMedication(String medicationId) {
        return medicationRepository.deleteMedication(medicationId);
    }
}

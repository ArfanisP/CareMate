package com.caremate.app.viewmodel;

import com.caremate.app.model.Examination;
import com.caremate.app.repository.ExaminationRepository;
import com.google.android.gms.tasks.Task;

public class ExaminationViewModel {

    private final ExaminationRepository examinationRepository;

    public ExaminationViewModel() {
        examinationRepository = new ExaminationRepository();
    }

    public void loadExaminations(ExaminationRepository.ExaminationLoadCallback callback) {
        examinationRepository.loadExaminations(callback);
    }

    public Task<Void> saveExamination(Examination examination) {
        return examinationRepository.saveExamination(examination);
    }

    public Task<Void> deleteExamination(String examinationId) {
        return examinationRepository.deleteExamination(examinationId);
    }
}

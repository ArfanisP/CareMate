package com.caremate.app.model;

public class Medication {

    private String id;
    private String name;
    private String dosage;
    private String time;
    private String notes;

    public Medication() {
    }

    public Medication(String id, String name, String dosage, String time, String notes) {
        this.id = id;
        this.name = name;
        this.dosage = dosage;
        this.time = time;
        this.notes = notes;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDosage() {
        return dosage;
    }

    public void setDosage(String dosage) {
        this.dosage = dosage;
    }

    public String getTime() {
        return time;
    }

    public void setTime(String time) {
        this.time = time;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}

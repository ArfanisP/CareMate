package com.caremate.app.model;

public class Examination {

    private String id;
    private String title;
    private String date;
    private String time;
    private String notes;

    public Examination() {
    }

    public Examination(String id, String title, String date, String time, String notes) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.time = time;
        this.notes = notes;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
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

package com.moke.diary.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "diary_entries")
public class DiaryEntry {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String title;
    public String content;
    public String mood;
    public int backgroundColor;
    public boolean encrypted;
    public long createdAt;
    public long updatedAt;

    public DiaryEntry() {
        long now = System.currentTimeMillis();
        createdAt = now;
        updatedAt = now;
        mood = Mood.NEUTRAL.name();
        backgroundColor = 0xFFFFFFFF;
    }
}

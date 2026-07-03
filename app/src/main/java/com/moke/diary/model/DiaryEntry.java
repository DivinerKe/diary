package com.moke.diary.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

/**
 * 日记条目实体，对应表 diary_entries。
 */
@Entity(tableName = "diary_entries")
public class DiaryEntry {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String title;
    public String content;
    /** 心情枚举名，如 HAPPY、NEUTRAL */
    public String mood;
    public int backgroundColor;
    /** 正文是否 AES 加密存储 */
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

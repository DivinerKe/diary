package com.moke.diary.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "diary_revisions",
        foreignKeys = @ForeignKey(
                entity = DiaryEntry.class,
                parentColumns = "id",
                childColumns = "diaryId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("diaryId")}
)
public class DiaryRevision {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long diaryId;
    public String title;
    public String content;
    public String mood;
    public int backgroundColor;
    public long revisedAt;
    public String changeLog;

    public DiaryRevision() {
        revisedAt = System.currentTimeMillis();
        changeLog = "";
    }
}

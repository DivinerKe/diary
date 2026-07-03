package com.moke.diary.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(
        tableName = "media_attachments",
        foreignKeys = @ForeignKey(
                entity = DiaryEntry.class,
                parentColumns = "id",
                childColumns = "diaryId",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("diaryId")}
)
public class MediaAttachment {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public long diaryId;
    public String filePath;
    public String mediaType;
    public String fileName;
    public long addedAt;

    public MediaAttachment() {
        addedAt = System.currentTimeMillis();
    }
}

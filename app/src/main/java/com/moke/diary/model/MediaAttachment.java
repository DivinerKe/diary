package com.moke.diary.model;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * 日记媒体附件实体，对应 {@code media_attachments} 表。
 * 通过外键关联日记，删除日记时级联删除附件记录。
 */
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

package com.moke.diary.model;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.ArrayList;
import java.util.List;

/**
 * Room 联表查询结果：一条日记及其关联的媒体附件列表。
 */
public class DiaryWithMedia {

    @Embedded
    public DiaryEntry entry;

    @Relation(parentColumn = "id", entityColumn = "diaryId")
    public List<MediaAttachment> mediaList = new ArrayList<>();
}

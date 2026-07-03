package com.moke.diary.model;

import androidx.room.Embedded;
import androidx.room.Relation;

import java.util.ArrayList;
import java.util.List;

public class DiaryWithMedia {

    @Embedded
    public DiaryEntry entry;

    @Relation(parentColumn = "id", entityColumn = "diaryId")
    public List<MediaAttachment> mediaList = new ArrayList<>();
}

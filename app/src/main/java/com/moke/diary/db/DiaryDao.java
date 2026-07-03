package com.moke.diary.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Transaction;
import androidx.room.Update;

import com.moke.diary.model.DiaryEntry;
import com.moke.diary.model.DiaryRevision;
import com.moke.diary.model.DiaryWithMedia;
import com.moke.diary.model.MediaAttachment;

import java.util.List;

@Dao
public interface DiaryDao {

    @Transaction
    @Query("SELECT * FROM diary_entries ORDER BY updatedAt DESC")
    List<DiaryWithMedia> getAllDiaries();

    @Transaction
    @Query("SELECT * FROM diary_entries WHERE mood = :mood ORDER BY updatedAt DESC")
    List<DiaryWithMedia> getDiariesByMood(String mood);

    @Transaction
    @Query("SELECT * FROM diary_entries WHERE title LIKE '%' || :keyword || '%' OR content LIKE '%' || :keyword || '%' ORDER BY updatedAt DESC")
    List<DiaryWithMedia> searchDiaries(String keyword);

    @Transaction
    @Query("SELECT * FROM diary_entries WHERE (title LIKE '%' || :keyword || '%' OR content LIKE '%' || :keyword || '%') AND mood = :mood ORDER BY updatedAt DESC")
    List<DiaryWithMedia> searchDiariesByMood(String keyword, String mood);

    @Transaction
    @Query("SELECT * FROM diary_entries WHERE id = :id")
    DiaryWithMedia getDiaryById(long id);

    @Insert
    long insertDiary(DiaryEntry entry);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long upsertDiary(DiaryEntry entry);

    @Update
    void updateDiary(DiaryEntry entry);

    @Delete
    void deleteDiary(DiaryEntry entry);

    @Insert
    void insertMedia(MediaAttachment attachment);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertMedia(MediaAttachment attachment);

    @Query("DELETE FROM media_attachments WHERE diaryId = :diaryId")
    void deleteMediaByDiaryId(long diaryId);

    @Query("SELECT * FROM media_attachments WHERE diaryId = :diaryId")
    List<MediaAttachment> getMediaByDiaryId(long diaryId);

    @Insert
    void insertRevision(DiaryRevision revision);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertRevision(DiaryRevision revision);

    @Query("SELECT * FROM diary_revisions WHERE diaryId = :diaryId ORDER BY revisedAt DESC")
    List<DiaryRevision> getRevisions(long diaryId);

    @Query("SELECT * FROM diary_revisions ORDER BY diaryId, revisedAt DESC")
    List<DiaryRevision> getAllRevisions();

    @Query("SELECT COUNT(*) FROM diary_entries")
    int getDiaryCount();

    @Query("DELETE FROM diary_revisions")
    void deleteAllRevisions();

    @Query("DELETE FROM media_attachments")
    void deleteAllMedia();

    @Query("DELETE FROM diary_entries")
    void deleteAllEntries();
}

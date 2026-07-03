package com.moke.diary.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.moke.diary.model.DiaryEntry;
import com.moke.diary.model.DiaryRevision;
import com.moke.diary.model.MediaAttachment;

/**
 * Room 数据库单例。
 * 包含日记条目、媒体附件、修订历史三张表，当前版本 3。
 */
@Database(
        entities = {DiaryEntry.class, MediaAttachment.class, DiaryRevision.class},
        version = 3,
        exportSchema = false
)
public abstract class DiaryDatabase extends RoomDatabase {

    private static volatile DiaryDatabase INSTANCE;

    /** v1→v2：修订表增加 changeLog 字段 */
    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            db.execSQL("ALTER TABLE diary_revisions ADD COLUMN changeLog TEXT");
        }
    };

    /** v2→v3：修复 changeLog 被误设为 NOT NULL 的表结构（重建表） */
    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            recreateRevisionsTable(db);
        }
    };

    /** 通过临时表迁移数据，确保 changeLog 可为 NULL */
    private static void recreateRevisionsTable(@NonNull SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS diary_revisions_new ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, "
                + "diaryId INTEGER NOT NULL, "
                + "title TEXT, "
                + "content TEXT, "
                + "mood TEXT, "
                + "backgroundColor INTEGER NOT NULL, "
                + "revisedAt INTEGER NOT NULL, "
                + "changeLog TEXT, "
                + "FOREIGN KEY(diaryId) REFERENCES diary_entries(id) ON DELETE CASCADE)");
        db.execSQL("INSERT INTO diary_revisions_new "
                + "(id, diaryId, title, content, mood, backgroundColor, revisedAt, changeLog) "
                + "SELECT id, diaryId, title, content, mood, backgroundColor, revisedAt, changeLog "
                + "FROM diary_revisions");
        db.execSQL("DROP TABLE diary_revisions");
        db.execSQL("ALTER TABLE diary_revisions_new RENAME TO diary_revisions");
        db.execSQL("CREATE INDEX IF NOT EXISTS index_diary_revisions_diaryId "
                + "ON diary_revisions(diaryId)");
    }

    public abstract DiaryDao diaryDao();

    /** 双重检查锁获取全局唯一数据库实例 */
    public static DiaryDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (DiaryDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            DiaryDatabase.class,
                            "diary.db"
                    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build();
                }
            }
        }
        return INSTANCE;
    }
}

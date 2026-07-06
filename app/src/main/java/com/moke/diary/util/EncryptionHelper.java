package com.moke.diary.util;

import android.content.Context;

import com.moke.diary.db.DiaryDatabase;
import com.moke.diary.model.DiaryEntry;

import java.util.List;

/**
 * 加密日记批量重加密：改密码时用旧密码解密、新密码加密，避免改密后无法打开已加密日记。
 */
public final class EncryptionHelper {

    private EncryptionHelper() {
    }

    /** 将库中全部 encrypted=true 的日记用新密码重加密；任一条失败则中止并返回失败条目 id（&lt;0 表示成功）。 */
    public static long reEncryptAllEntries(Context context, String oldPassword, String newPassword) {
        List<DiaryEntry> entries = DiaryDatabase.getInstance(context).diaryDao().getEncryptedEntries();
        for (DiaryEntry entry : entries) {
            try {
                String plain = CryptoUtil.decrypt(entry.content, oldPassword);
                entry.content = CryptoUtil.encrypt(plain, newPassword);
                DiaryDatabase.getInstance(context).diaryDao().updateDiary(entry);
            } catch (Exception e) {
                MokeLog.e("[Crypto] 重加密失败 id=" + entry.id, e);
                return entry.id;
            }
        }
        MokeLog.i("[Crypto] 重加密完成，共 " + entries.size() + " 篇");
        return -1;
    }

    public static int countEncryptedEntries(Context context) {
        return DiaryDatabase.getInstance(context).diaryDao().countEncryptedEntries();
    }
}

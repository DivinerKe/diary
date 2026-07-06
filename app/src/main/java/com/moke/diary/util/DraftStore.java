package com.moke.diary.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 编辑页草稿：退后台自动上锁导致编辑页关闭时，保留未保存内容供恢复。
 */
public final class DraftStore {

    private static final String PREFS = "diary_draft";

    private DraftStore() {
    }

    public static void save(Context context,
                            long diaryId,
                            String title,
                            String content,
                            String mood,
                            int backgroundColor,
                            boolean encrypt) {
        getPrefs(context).edit()
                .putString(key(diaryId, "title"), title != null ? title : "")
                .putString(key(diaryId, "content"), content != null ? content : "")
                .putString(key(diaryId, "mood"), mood != null ? mood : "")
                .putInt(key(diaryId, "color"), backgroundColor)
                .putBoolean(key(diaryId, "encrypt"), encrypt)
                .putLong(key(diaryId, "saved_at"), System.currentTimeMillis())
                .apply();
    }

    public static Draft load(Context context, long diaryId) {
        SharedPreferences prefs = getPrefs(context);
        if (!prefs.contains(key(diaryId, "saved_at"))) {
            return null;
        }
        Draft draft = new Draft();
        draft.diaryId = diaryId;
        draft.title = prefs.getString(key(diaryId, "title"), "");
        draft.content = prefs.getString(key(diaryId, "content"), "");
        draft.mood = prefs.getString(key(diaryId, "mood"), "");
        draft.backgroundColor = prefs.getInt(key(diaryId, "color"), 0xFFFFFFFF);
        draft.encrypt = prefs.getBoolean(key(diaryId, "encrypt"), false);
        draft.savedAt = prefs.getLong(key(diaryId, "saved_at"), 0);
        return draft;
    }

    public static void clear(Context context, long diaryId) {
        SharedPreferences prefs = getPrefs(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.remove(key(diaryId, "title"));
        editor.remove(key(diaryId, "content"));
        editor.remove(key(diaryId, "mood"));
        editor.remove(key(diaryId, "color"));
        editor.remove(key(diaryId, "encrypt"));
        editor.remove(key(diaryId, "saved_at"));
        editor.apply();
    }

    public static boolean hasDraft(Context context, long diaryId) {
        return getPrefs(context).contains(key(diaryId, "saved_at"));
    }

    private static String key(long diaryId, String field) {
        String id = diaryId <= 0 ? "new" : String.valueOf(diaryId);
        return "draft_" + id + "_" + field;
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static final class Draft {
        public long diaryId;
        public String title;
        public String content;
        public String mood;
        public int backgroundColor;
        public boolean encrypt;
        public long savedAt;
    }
}

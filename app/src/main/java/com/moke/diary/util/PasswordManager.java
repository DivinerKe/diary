package com.moke.diary.util;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 应用锁屏密码与密保管理。
 * 密码以 SHA-256 哈希存储；支持密保找回；配置可随备份 zip 导入导出。
 */
public final class PasswordManager {

    public static final int PASSWORD_MIN_LENGTH = 8;

    private static final String PREFS = "diary_security";
    private static final String KEY_HASH = "password_hash";
    private static final String KEY_QUESTION = "security_question";
    private static final String KEY_ANSWER_HASH = "security_answer_hash";

    private PasswordManager() {
    }

    public static boolean hasPassword(Context context) {
        return getPrefs(context).contains(KEY_HASH);
    }

    public static boolean hasRecoverySetup(Context context) {
        SharedPreferences prefs = getPrefs(context);
        return prefs.contains(KEY_QUESTION) && prefs.contains(KEY_ANSWER_HASH);
    }

    public static boolean isValidLength(String password) {
        return password != null && password.length() >= PASSWORD_MIN_LENGTH;
    }

    /** 首次设置密码，同时绑定密保问题与答案 */
    public static void setPasswordWithRecovery(Context context, String password,
                                               String question, String answer) {
        if (!isValidLength(password)) {
            throw new IllegalArgumentException("Password too short");
        }
        getPrefs(context).edit()
                .putString(KEY_HASH, CryptoUtil.hashPassword(password))
                .putString(KEY_QUESTION, question)
                .putString(KEY_ANSWER_HASH, CryptoUtil.hashPassword(normalizeAnswer(answer)))
                .apply();
    }

    public static boolean changePassword(Context context, String oldPassword, String newPassword) {
        if (!verifyPassword(context, oldPassword)) {
            return false;
        }
        if (!isValidLength(newPassword)) {
            return false;
        }
        getPrefs(context).edit()
                .putString(KEY_HASH, CryptoUtil.hashPassword(newPassword))
                .apply();
        return true;
    }

    /** 通过密保验证后重置密码 */
    public static boolean resetPassword(Context context, String newPassword) {
        if (!isValidLength(newPassword)) {
            return false;
        }
        getPrefs(context).edit()
                .putString(KEY_HASH, CryptoUtil.hashPassword(newPassword))
                .apply();
        return true;
    }

    public static String getSecurityQuestion(Context context) {
        return getPrefs(context).getString(KEY_QUESTION, "");
    }

    public static boolean verifySecurityAnswer(Context context, String answer) {
        String stored = getPrefs(context).getString(KEY_ANSWER_HASH, null);
        if (stored == null) {
            return false;
        }
        return stored.equals(CryptoUtil.hashPassword(normalizeAnswer(answer)));
    }

    public static boolean verifyPassword(Context context, String password) {
        String stored = getPrefs(context).getString(KEY_HASH, null);
        if (stored == null) {
            return false;
        }
        return stored.equals(CryptoUtil.hashPassword(password));
    }

    /** 导出安全配置到 backup.json 的 security 字段 */
    public static org.json.JSONObject exportSecurityJson(Context context) throws Exception {
        SharedPreferences prefs = getPrefs(context);
        org.json.JSONObject json = new org.json.JSONObject();
        if (prefs.contains(KEY_HASH)) {
            json.put(KEY_HASH, prefs.getString(KEY_HASH, ""));
        }
        if (prefs.contains(KEY_QUESTION)) {
            json.put(KEY_QUESTION, prefs.getString(KEY_QUESTION, ""));
        }
        if (prefs.contains(KEY_ANSWER_HASH)) {
            json.put(KEY_ANSWER_HASH, prefs.getString(KEY_ANSWER_HASH, ""));
        }
        return json;
    }

    /** 从备份恢复锁屏密码与密保设置 */
    public static void importSecurityJson(Context context, org.json.JSONObject json) {
        if (json == null || json.length() == 0) {
            return;
        }
        SharedPreferences.Editor editor = getPrefs(context).edit();
        if (json.has(KEY_HASH)) {
            editor.putString(KEY_HASH, json.optString(KEY_HASH, ""));
        }
        if (json.has(KEY_QUESTION)) {
            editor.putString(KEY_QUESTION, json.optString(KEY_QUESTION, ""));
        }
        if (json.has(KEY_ANSWER_HASH)) {
            editor.putString(KEY_ANSWER_HASH, json.optString(KEY_ANSWER_HASH, ""));
        }
        editor.apply();
    }

    /** 密保答案统一转小写并去首尾空格后哈希 */
    private static String normalizeAnswer(String answer) {
        return answer == null ? "" : answer.trim().toLowerCase();
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

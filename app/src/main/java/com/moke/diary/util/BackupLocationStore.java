package com.moke.diary.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.List;

/**
 * 备份位置持久化存储。
 * 保存用户通过 SAF 授权的目录树 Uri 及手动选择的文件 Uri，供 BackupManager 读写备份。
 */
final class BackupLocationStore {

    private static final String PREFS = "backup_location";
    private static final String KEY_TREE_URI = "tree_uri";
    private static final String KEY_FILE_URI = "file_uri";

    private BackupLocationStore() {
    }

    /** 保存目录树 Uri 并申请持久读写权限 */
    static void saveTreeUri(Context context, Uri treeUri) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TREE_URI, treeUri.toString())
                .apply();
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        context.getContentResolver().takePersistableUriPermission(treeUri, flags);
        MokeLog.d("[Backup] 保存 SAF 目录：" + treeUri);
    }

    /** 记录用户手动选取的单个备份文件 Uri */
    static void saveFileUri(Context context, Uri fileUri) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_FILE_URI, fileUri.toString())
                .apply();
    }

    static Uri getFileUri(Context context) {
        String value = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_FILE_URI, null);
        if (value == null) {
            return null;
        }
        return Uri.parse(value);
    }

    /** 获取已授权且仍有效的 SAF 目录 Uri，权限失效时返回 null */
    static Uri getTreeUri(Context context) {
        String value = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TREE_URI, null);
        if (value == null) {
            return null;
        }
        Uri uri = Uri.parse(value);
        if (!hasPersistedPermission(context, uri)) {
            MokeLog.w("[Backup] SAF 目录权限已失效");
            return null;
        }
        return uri;
    }

    /** 检查 Uri 是否仍在系统持久授权列表中 */
    static boolean hasPersistedPermission(Context context, Uri uri) {
        List<android.content.UriPermission> permissions =
                context.getContentResolver().getPersistedUriPermissions();
        for (android.content.UriPermission permission : permissions) {
            if (uri.equals(permission.getUri()) && permission.isReadPermission()) {
                return true;
            }
        }
        return false;
    }
}

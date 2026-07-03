package com.moke.diary.util;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import java.util.List;

final class BackupLocationStore {

    private static final String PREFS = "backup_location";
    private static final String KEY_TREE_URI = "tree_uri";
    private static final String KEY_FILE_URI = "file_uri";

    private BackupLocationStore() {
    }

    static void saveTreeUri(Context context, Uri treeUri) {
        context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_TREE_URI, treeUri.toString())
                .apply();
        int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
        context.getContentResolver().takePersistableUriPermission(treeUri, flags);
    }

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

    static Uri getTreeUri(Context context) {
        String value = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_TREE_URI, null);
        if (value == null) {
            return null;
        }
        Uri uri = Uri.parse(value);
        if (!hasPersistedPermission(context, uri)) {
            return null;
        }
        return uri;
    }

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

package com.moke.diary.util;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.moke.diary.model.MediaType;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public final class MediaCaptureHelper {

    private MediaCaptureHelper() {
    }

    public static File createCaptureFile(Context context, MediaType type) throws IOException {
        File dir = new File(context.getCacheDir(), "capture");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String extension;
        switch (type) {
            case IMAGE:
                extension = ".jpg";
                break;
            case VIDEO:
                extension = ".mp4";
                break;
            case AUDIO:
                extension = ".m4a";
                break;
            default:
                extension = ".dat";
                break;
        }
        File file = new File(dir, UUID.randomUUID() + extension);
        if (!file.createNewFile() && !file.exists()) {
            throw new IOException("Cannot create capture file");
        }
        return file;
    }

    public static Uri getUriForFile(Context context, File file) {
        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file
        );
    }
}

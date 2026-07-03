package com.moke.diary.util;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import com.moke.diary.model.MediaType;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * 媒体采集辅助类。
 * 在缓存目录创建临时捕获文件，并通过 FileProvider 生成可供系统相机/录音组件写入的安全 URI。
 */
public final class MediaCaptureHelper {

    private MediaCaptureHelper() {
    }

    /**
     * 在应用缓存目录创建用于拍照、录像或录音的临时空文件。
     */
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

    /** 为捕获文件生成 FileProvider URI，供外部应用（相机、录音器等）写入数据。 */
    public static Uri getUriForFile(Context context, File file) {
        return FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                file
        );
    }
}

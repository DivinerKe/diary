package com.moke.diary.util;

import android.content.Context;
import android.net.Uri;
import android.webkit.MimeTypeMap;

import com.moke.diary.model.MediaAttachment;
import com.moke.diary.model.MediaType;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 日记媒体文件持久化工具。
 * 将 Content URI 或本地临时文件复制到应用私有目录，并生成 {@link MediaAttachment} 记录供数据库存储。
 */
public final class MediaStorage {

    private MediaStorage() {
    }

    /**
     * 从 Content URI（相册、文件选择器等）读取媒体并保存到按类型划分的私有目录。
     */
    public static MediaAttachment saveMedia(Context context, Uri uri, MediaType type, long diaryId)
            throws IOException {
        String extension = guessExtension(context, uri, type);
        File dir = new File(context.getFilesDir(), "media/" + type.name().toLowerCase());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String fileName = UUID.randomUUID() + extension;
        File dest = new File(dir, fileName);

        try (InputStream in = context.getContentResolver().openInputStream(uri);
             FileOutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                throw new IOException("Cannot open input stream");
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }

        MediaAttachment attachment = new MediaAttachment();
        attachment.diaryId = diaryId;
        attachment.filePath = dest.getAbsolutePath();
        attachment.mediaType = type.name();
        attachment.fileName = fileName;
        MokeLog.d("[Media] 保存 " + type + " -> " + fileName + " diaryId=" + diaryId);
        return attachment;
    }

    /**
     * 将已有本地文件（如相机/录音产生的缓存文件）复制到持久化目录并关联日记。
     */
    public static MediaAttachment saveMediaFile(Context context, File sourceFile, MediaType type, long diaryId)
            throws IOException {
        if (sourceFile == null || !sourceFile.exists() || sourceFile.length() == 0) {
            throw new IOException("Capture file is empty");
        }

        String extension;
        switch (type) {
            case IMAGE:
                extension = ".jpg";
                break;
            case AUDIO:
                extension = ".m4a";
                break;
            case VIDEO:
                extension = ".mp4";
                break;
            default:
                extension = ".dat";
                break;
        }

        File dir = new File(context.getFilesDir(), "media/" + type.name().toLowerCase());
        if (!dir.exists()) {
            dir.mkdirs();
        }
        String fileName = UUID.randomUUID() + extension;
        File dest = new File(dir, fileName);

        try (java.io.InputStream in = new java.io.FileInputStream(sourceFile);
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }

        MediaAttachment attachment = new MediaAttachment();
        attachment.diaryId = diaryId;
        attachment.filePath = dest.getAbsolutePath();
        attachment.mediaType = type.name();
        attachment.fileName = fileName;
        MokeLog.d("[Media] 复制 " + type + " -> " + fileName + " diaryId=" + diaryId);
        return attachment;
    }

    /** 根据 ContentResolver 的 MIME 类型或媒体类型推断文件扩展名。 */
    private static String guessExtension(Context context, Uri uri, MediaType type) {
        String mime = context.getContentResolver().getType(uri);
        if (mime != null) {
            String ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (ext != null) {
                return "." + ext;
            }
        }
        switch (type) {
            case IMAGE:
                return ".jpg";
            case AUDIO:
                return ".mp3";
            case VIDEO:
                return ".mp4";
            default:
                return ".dat";
        }
    }
}

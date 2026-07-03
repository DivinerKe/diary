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

public final class MediaStorage {

    private MediaStorage() {
    }

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
        return attachment;
    }

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
        return attachment;
    }

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

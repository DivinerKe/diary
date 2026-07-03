package com.moke.diary.util;

import android.content.Context;
import android.text.TextUtils;

import com.moke.diary.R;
import com.moke.diary.model.MediaAttachment;
import com.moke.diary.model.MediaType;
import com.moke.diary.model.Mood;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RevisionDiffHelper {

    private static final int SNIPPET_LENGTH = 60;

    private RevisionDiffHelper() {
    }

    public static String buildChangeLog(Context context,
                                        boolean isNew,
                                        String oldTitle,
                                        String newTitle,
                                        String oldContent,
                                        String newContent,
                                        String oldMood,
                                        String newMood,
                                        int oldColor,
                                        int newColor,
                                        List<MediaAttachment> oldMedia,
                                        List<MediaAttachment> newMedia,
                                        boolean encrypted) {
        StringBuilder log = new StringBuilder();

        if (isNew) {
            log.append(context.getString(R.string.revision_log_created)).append('\n');
            if (!TextUtils.isEmpty(newTitle)) {
                log.append(context.getString(R.string.revision_log_set_title, newTitle)).append('\n');
            }
            appendContentLog(context, log, null, newContent, encrypted);
            appendMediaList(context, log, newMedia, true);
            if (!TextUtils.isEmpty(newMood)) {
                log.append(context.getString(R.string.revision_log_set_mood,
                        Mood.fromName(newMood).display())).append('\n');
            }
            return trimLog(log);
        }

        if (!TextUtils.equals(oldTitle, newTitle)) {
            log.append(context.getString(R.string.revision_log_title_changed, oldTitle, newTitle))
                    .append('\n');
        }

        if (!TextUtils.equals(oldContent, newContent)) {
            appendContentLog(context, log, oldContent, newContent, encrypted);
        }

        if (!TextUtils.equals(oldMood, newMood)) {
            log.append(context.getString(R.string.revision_log_mood_changed,
                    Mood.fromName(oldMood).display(),
                    Mood.fromName(newMood).display())).append('\n');
        }

        if (oldColor != newColor) {
            log.append(context.getString(R.string.revision_log_color_changed)).append('\n');
        }

        appendMediaDiff(context, log, oldMedia, newMedia);

        if (log.length() == 0) {
            log.append(context.getString(R.string.revision_log_minor_edit)).append('\n');
        }

        return trimLog(log);
    }

    private static void appendContentLog(Context context,
                                         StringBuilder log,
                                         String oldContent,
                                         String newContent,
                                         boolean encrypted) {
        if (encrypted) {
            log.append(context.getString(R.string.revision_log_content_encrypted)).append('\n');
            return;
        }

        if (TextUtils.isEmpty(oldContent) && !TextUtils.isEmpty(newContent)) {
            log.append(context.getString(R.string.revision_log_content_added, truncate(newContent)))
                    .append('\n');
            return;
        }

        if (!TextUtils.isEmpty(oldContent) && TextUtils.isEmpty(newContent)) {
            log.append(context.getString(R.string.revision_log_content_cleared)).append('\n');
            return;
        }

        List<String> addedLines = findAddedLines(oldContent, newContent);
        List<String> removedLines = findRemovedLines(oldContent, newContent);

        if (!addedLines.isEmpty()) {
            for (String line : addedLines) {
                log.append(context.getString(R.string.revision_log_text_added, truncate(line)))
                        .append('\n');
            }
        }
        if (!removedLines.isEmpty()) {
            for (String line : removedLines) {
                log.append(context.getString(R.string.revision_log_text_removed, truncate(line)))
                        .append('\n');
            }
        }

        if (addedLines.isEmpty() && removedLines.isEmpty()
                && !TextUtils.equals(oldContent, newContent)) {
            log.append(context.getString(R.string.revision_log_content_updated, truncate(newContent)))
                    .append('\n');
        }
    }

    private static void appendMediaDiff(Context context,
                                        StringBuilder log,
                                        List<MediaAttachment> oldMedia,
                                        List<MediaAttachment> newMedia) {
        Set<String> oldKeys = mediaKeys(oldMedia);
        Set<String> newKeys = mediaKeys(newMedia);

        if (newMedia != null) {
            for (MediaAttachment attachment : newMedia) {
                String key = mediaKey(attachment);
                if (!oldKeys.contains(key)) {
                    log.append(context.getString(R.string.revision_log_media_added,
                            mediaLabel(context, attachment), attachment.fileName)).append('\n');
                }
            }
        }

        if (oldMedia != null) {
            for (MediaAttachment attachment : oldMedia) {
                String key = mediaKey(attachment);
                if (!newKeys.contains(key)) {
                    log.append(context.getString(R.string.revision_log_media_removed,
                            mediaLabel(context, attachment), attachment.fileName)).append('\n');
                }
            }
        }
    }

    private static void appendMediaList(Context context,
                                        StringBuilder log,
                                        List<MediaAttachment> media,
                                        boolean added) {
        if (media == null || media.isEmpty()) {
            return;
        }
        for (MediaAttachment attachment : media) {
            if (added) {
                log.append(context.getString(R.string.revision_log_media_added,
                        mediaLabel(context, attachment), attachment.fileName)).append('\n');
            }
        }
    }

    private static Set<String> mediaKeys(List<MediaAttachment> media) {
        Set<String> keys = new HashSet<>();
        if (media != null) {
            for (MediaAttachment attachment : media) {
                keys.add(mediaKey(attachment));
            }
        }
        return keys;
    }

    private static String mediaKey(MediaAttachment attachment) {
        if (!TextUtils.isEmpty(attachment.filePath)) {
            return attachment.filePath;
        }
        return attachment.fileName;
    }

    private static String mediaLabel(Context context, MediaAttachment attachment) {
        try {
            switch (MediaType.valueOf(attachment.mediaType)) {
                case IMAGE:
                    return context.getString(R.string.media_type_image);
                case AUDIO:
                    return context.getString(R.string.media_type_audio);
                case VIDEO:
                    return context.getString(R.string.media_type_video);
                default:
                    return context.getString(R.string.media_type_file);
            }
        } catch (IllegalArgumentException e) {
            return context.getString(R.string.media_type_file);
        }
    }

    private static List<String> findAddedLines(String oldText, String newText) {
        Set<String> oldLines = lineSet(oldText);
        List<String> added = new ArrayList<>();
        for (String line : newText.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !oldLines.contains(trimmed)) {
                added.add(trimmed);
            }
        }
        return added;
    }

    private static List<String> findRemovedLines(String oldText, String newText) {
        Set<String> newLines = lineSet(newText);
        List<String> removed = new ArrayList<>();
        for (String line : oldText.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !newLines.contains(trimmed)) {
                removed.add(trimmed);
            }
        }
        return removed;
    }

    private static Set<String> lineSet(String text) {
        Set<String> lines = new HashSet<>();
        if (text == null) {
            return lines;
        }
        for (String line : text.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty()) {
                lines.add(trimmed);
            }
        }
        return lines;
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace('\n', ' ').trim();
        if (normalized.length() <= SNIPPET_LENGTH) {
            return normalized;
        }
        return normalized.substring(0, SNIPPET_LENGTH) + "…";
    }

    private static String trimLog(StringBuilder log) {
        String result = log.toString().trim();
        return result.isEmpty() ? "" : result;
    }
}

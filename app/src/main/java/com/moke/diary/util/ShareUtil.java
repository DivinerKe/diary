package com.moke.diary.util;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import androidx.core.content.FileProvider;

import com.moke.diary.model.DiaryEntry;
import com.moke.diary.model.Mood;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * 日记分享工具。
 * 将日记标题、心情、时间与正文组装为分享文本，并渲染带背景色的卡片图片一并分享。
 */
public final class ShareUtil {

    private ShareUtil() {
    }

    /**
     * 调起系统分享面板，优先附带渲染后的日记卡片图片；图片生成失败时降级为纯文本分享。
     */
    public static void shareDiary(Context context, DiaryEntry entry, String decryptedContent) {
        String shareText = buildShareText(entry, decryptedContent);
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, entry.title);
        intent.putExtra(Intent.EXTRA_TEXT, shareText);

        try {
            File imageFile = createShareImage(context, entry, decryptedContent);
            Uri imageUri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    imageFile
            );
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_STREAM, imageUri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_TEXT, shareText);
            context.startActivity(Intent.createChooser(intent, "分享日记"));
        } catch (IOException e) {
            context.startActivity(Intent.createChooser(intent, "分享日记"));
        }
    }

    private static String buildShareText(DiaryEntry entry, String content) {
        Mood mood = Mood.fromName(entry.mood);
        return entry.title + "\n\n"
                + mood.display() + "\n"
                + DateUtil.formatFull(entry.updatedAt) + "\n\n"
                + content;
    }

    /** 将日记内容绘制为 PNG 图片，使用日记背景色作为画布底色。 */
    private static File createShareImage(Context context, DiaryEntry entry, String content)
            throws IOException {
        int width = 1080;
        int padding = 48;
        TextPaint titlePaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        titlePaint.setColor(Color.BLACK);
        titlePaint.setTextSize(48f);
        titlePaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        TextPaint bodyPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        bodyPaint.setColor(Color.DKGRAY);
        bodyPaint.setTextSize(36f);

        TextPaint metaPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        metaPaint.setColor(Color.GRAY);
        metaPaint.setTextSize(28f);

        String meta = Mood.fromName(entry.mood).display() + "  ·  "
                + DateUtil.formatFull(entry.updatedAt);

        StaticLayout titleLayout = buildLayout(entry.title, titlePaint, width - padding * 2);
        StaticLayout metaLayout = buildLayout(meta, metaPaint, width - padding * 2);
        StaticLayout bodyLayout = buildLayout(content, bodyPaint, width - padding * 2);

        int height = padding * 2
                + titleLayout.getHeight() + 24
                + metaLayout.getHeight() + 32
                + bodyLayout.getHeight();

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(entry.backgroundColor);

        float y = padding;
        canvas.save();
        canvas.translate(padding, y);
        titleLayout.draw(canvas);
        canvas.restore();
        y += titleLayout.getHeight() + 24;

        canvas.save();
        canvas.translate(padding, y);
        metaLayout.draw(canvas);
        canvas.restore();
        y += metaLayout.getHeight() + 32;

        canvas.save();
        canvas.translate(padding, y);
        bodyLayout.draw(canvas);
        canvas.restore();

        File dir = new File(context.getCacheDir(), "share");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        File file = new File(dir, "diary_share_" + entry.id + ".png");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        }
        bitmap.recycle();
        return file;
    }

    private static StaticLayout buildLayout(String text, TextPaint paint, int width) {
        return StaticLayout.Builder.obtain(text, 0, text.length(), paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, 1.2f)
                .setIncludePad(false)
                .build();
    }
}

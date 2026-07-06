package com.moke.diary.util;

import android.content.res.ColorStateList;
import android.graphics.Color;

import com.google.android.material.chip.Chip;

/**
 * 根据背景色亮度计算合适的前景色，保证文字可读性。
 */
public final class ColorUtil {

    private ColorUtil() {
    }

    /** 背景是否为浅色（亮度 &gt; 0.5） */
    public static boolean isLightColor(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color)
                + 0.114 * Color.blue(color)) / 255;
        return luminance > 0.5;
    }

    public static int primaryTextColor(int backgroundColor) {
        return isLightColor(backgroundColor) ? Color.BLACK : Color.WHITE;
    }

    public static int secondaryTextColor(int backgroundColor) {
        return isLightColor(backgroundColor) ? Color.GRAY : 0xFFB0BEC5;
    }

    public static int hintTextColor(int backgroundColor) {
        return isLightColor(backgroundColor) ? 0xFF757575 : 0xFF90A4AE;
    }

    /**
     * 为心情 Chip 设置与页面背景协调的底色与文字色，避免深色主题 Chip 配黑字不可读。
     */
    public static void applyMoodChipStyle(Chip chip,
                                          int pageBackground,
                                          int checkedBackground,
                                          int checkedText,
                                          float strokeWidthPx) {
        boolean lightPage = isLightColor(pageBackground);
        int uncheckedBg = lightPage ? 0xFFF5F5F5 : 0xFF32323E;
        int uncheckedText = lightPage ? Color.BLACK : Color.WHITE;
        int stroke = lightPage ? 0xFFBDBDBD : 0xFF546E7A;

        chip.setChipBackgroundColor(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{checkedBackground, uncheckedBg}));
        chip.setTextColor(new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_checked},
                        new int[]{}
                },
                new int[]{checkedText, uncheckedText}));
        chip.setChipStrokeColor(ColorStateList.valueOf(stroke));
        chip.setChipStrokeWidth(strokeWidthPx);
    }
}

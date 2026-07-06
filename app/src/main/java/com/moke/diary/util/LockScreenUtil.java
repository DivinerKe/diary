package com.moke.diary.util;

import android.content.Context;

import com.moke.diary.R;

import java.util.Calendar;

/**
 * 上锁界面相关工具：按当前时段与界面风格（经典 / 女生 / 男生）选择图标与文案。
 */
public final class LockScreenUtil {

    /** 白天起始小时（含），默认 6 点 */
    private static final int DAY_START_HOUR = 6;
    /** 白天结束小时（不含），默认 18 点 */
    private static final int DAY_END_HOUR = 18;

    private LockScreenUtil() {
    }

    /** 按主题与时段返回上锁图标 emoji */
    public static String getTimeBasedLockEmoji(Context context) {
        ThemeManager.AppTheme theme = ThemeManager.getTheme(context);
        boolean day = isDaytime();
        switch (theme) {
            case KAWAII:
                return day ? "🌞" : "🌙";
            case COOL:
                return day ? "🔆" : "🌃";
            default:
                return day ? "☀️" : "🌙";
        }
    }

    public static int getLockTitleRes(ThemeManager.AppTheme theme) {
        switch (theme) {
            case KAWAII:
                return R.string.lock_title_kawaii;
            case COOL:
                return R.string.lock_title_cool;
            default:
                return R.string.app_locked_title;
        }
    }

    public static int getLockMessageRes(ThemeManager.AppTheme theme) {
        switch (theme) {
            case KAWAII:
                return R.string.lock_message_kawaii;
            case COOL:
                return R.string.lock_message_cool;
            default:
                return R.string.app_locked_message;
        }
    }

    public static int getLockPasswordHintRes(ThemeManager.AppTheme theme) {
        switch (theme) {
            case KAWAII:
                return R.string.lock_password_hint_kawaii;
            case COOL:
                return R.string.lock_password_hint_cool;
            default:
                return R.string.lock_password_hint;
        }
    }

    public static boolean isDaytime() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        return hour >= DAY_START_HOUR && hour < DAY_END_HOUR;
    }
}

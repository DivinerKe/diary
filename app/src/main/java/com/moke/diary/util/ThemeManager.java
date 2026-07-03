package com.moke.diary.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.moke.diary.R;

public final class ThemeManager {

    public enum AppTheme {
        CLASSIC(R.style.Theme_Diary, R.string.theme_classic),
        KAWAII(R.style.Theme_Diary_Kawaii, R.string.theme_kawaii),
        COOL(R.style.Theme_Diary_Cool, R.string.theme_cool);

        public final int styleRes;
        public final int labelRes;

        AppTheme(int styleRes, int labelRes) {
            this.styleRes = styleRes;
            this.labelRes = labelRes;
        }

        public static AppTheme fromName(String name) {
            if (name == null) {
                return CLASSIC;
            }
            try {
                return valueOf(name);
            } catch (IllegalArgumentException e) {
                return CLASSIC;
            }
        }
    }

    private static final String PREFS = "diary_theme";
    private static final String KEY_THEME = "app_theme";

    private ThemeManager() {
    }

    public static AppTheme getTheme(Context context) {
        String name = getPrefs(context).getString(KEY_THEME, AppTheme.CLASSIC.name());
        return AppTheme.fromName(name);
    }

    public static void setTheme(Context context, AppTheme theme) {
        getPrefs(context).edit().putString(KEY_THEME, theme.name()).apply();
    }

    public static void applyTheme(Context context) {
        context.setTheme(getTheme(context).styleRes);
    }

    private static SharedPreferences getPrefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}

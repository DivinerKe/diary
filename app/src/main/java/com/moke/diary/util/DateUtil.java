package com.moke.diary.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class DateUtil {

    private static final SimpleDateFormat FULL =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat DATE =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private DateUtil() {
    }

    public static String formatFull(long timestamp) {
        return FULL.format(new Date(timestamp));
    }

    public static String formatDate(long timestamp) {
        return DATE.format(new Date(timestamp));
    }
}

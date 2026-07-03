package com.moke.diary.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 时间戳格式化工具，提供完整日期时间与仅日期两种展示格式。
 */
public final class DateUtil {

    private static final SimpleDateFormat FULL =
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
    private static final SimpleDateFormat DATE =
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private DateUtil() {
    }

    /** 格式化为 yyyy-MM-dd HH:mm:ss。 */
    public static String formatFull(long timestamp) {
        return FULL.format(new Date(timestamp));
    }

    /** 格式化为 yyyy-MM-dd。 */
    public static String formatDate(long timestamp) {
        return DATE.format(new Date(timestamp));
    }
}

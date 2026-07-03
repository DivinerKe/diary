package com.moke.diary.util;

import android.util.Log;

/**
 * 统一调试日志，TAG 固定为 {@link #TAG}（MokeDiary）。
 */
public final class MokeLog {

    public static final String TAG = "MokeDiary";

    private MokeLog() {
    }

    public static void d(String message) {
        Log.d(TAG, message);
    }

    public static void i(String message) {
        Log.i(TAG, message);
    }

    public static void w(String message) {
        Log.w(TAG, message);
    }

    public static void e(String message) {
        Log.e(TAG, message);
    }

    public static void e(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
    }
}

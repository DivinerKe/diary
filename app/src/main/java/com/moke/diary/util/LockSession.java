package com.moke.diary.util;

public final class LockSession {

    private static boolean unlocked;
    private static String sessionPassword;
    private static int externalCaptureCount;

    private LockSession() {
    }

    public static boolean isUnlocked() {
        return unlocked;
    }

    public static String getSessionPassword() {
        return sessionPassword;
    }

    public static void unlock(String password) {
        unlocked = true;
        sessionPassword = password;
    }

    public static void lock() {
        unlocked = false;
        sessionPassword = null;
    }

    /** 打开相机/相册等外部界面时暂停自动上锁 */
    public static void beginExternalCapture() {
        externalCaptureCount++;
    }

    public static void endExternalCapture() {
        if (externalCaptureCount > 0) {
            externalCaptureCount--;
        }
    }

    public static boolean shouldAutoLock() {
        return externalCaptureCount == 0;
    }
}

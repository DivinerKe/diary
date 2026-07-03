package com.moke.diary.util;

/**
 * 应用锁屏会话状态（内存级，不持久化）。
 * 解锁后缓存密码供加密日记读写；计数外部拍照/录像活动以延迟自动上锁。
 */
public final class LockSession {

    /** 当前是否已解锁 */
    private static boolean unlocked;
    /** 本次会话的明文密码，用于解密单篇加密日记 */
    private static String sessionPassword;
    /** 外部拍照/录像 Activity 嵌套计数，大于 0 时不自动上锁 */
    private static int externalCaptureCount;

    private LockSession() {
    }

    public static boolean isUnlocked() {
        return unlocked;
    }

    public static String getSessionPassword() {
        return sessionPassword;
    }

    /** 验证通过后解锁，并保存密码供 CryptoUtil 加解密使用 */
    public static void unlock(String password) {
        unlocked = true;
        sessionPassword = password;
        MokeLog.d("[Lock] 会话解锁");
    }

    /** 清除会话密码，回到锁屏状态 */
    public static void lock() {
        unlocked = false;
        sessionPassword = null;
        MokeLog.d("[Lock] 会话上锁");
    }

    /** 打开相机/相册等外部界面时暂停自动上锁 */
    public static void beginExternalCapture() {
        externalCaptureCount++;
        MokeLog.d("[Lock] 外部捕获开始 count=" + externalCaptureCount);
    }

    public static void endExternalCapture() {
        if (externalCaptureCount > 0) {
            externalCaptureCount--;
        }
        MokeLog.d("[Lock] 外部捕获结束 count=" + externalCaptureCount);
    }

    /** 无外部捕获活动时，退后台可触发自动上锁 */
    public static boolean shouldAutoLock() {
        return externalCaptureCount == 0;
    }
}

package com.moke.diary.ui;

import android.content.Intent;
import android.os.Bundle;

import com.moke.diary.util.LockSession;
import com.moke.diary.util.MokeLog;
import com.moke.diary.util.PasswordManager;

/**
 * 带锁屏检查的 Activity 基类。
 * 若已设密码且当前未解锁，强制跳回 MainActivity 显示锁屏界面。
 */
public abstract class BaseLockActivity extends BaseThemedActivity {

    @Override
    protected void onResume() {
        super.onResume();
        if (PasswordManager.hasPassword(this) && !LockSession.isUnlocked()) {
            MokeLog.d("[Lock] 未解锁，跳转 MainActivity：" + getClass().getSimpleName());
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        }
    }
}

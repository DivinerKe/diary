package com.moke.diary;

import android.app.Application;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.moke.diary.util.BackupManager;
import com.moke.diary.util.LockSession;
import com.moke.diary.util.MokeLog;
import com.moke.diary.util.PasswordManager;

/**
 * 应用入口。
 * 监听进程级生命周期：退到后台时自动备份，并在非外部拍照/录像期间自动上锁。
 */
public class DiaryApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        MokeLog.d("[App] 启动");
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStop(LifecycleOwner owner) {
                MokeLog.d("[App] 进入后台");
                // App 进入后台：防抖触发备份
                BackupManager.exportBackupAsync(DiaryApplication.this);
                // 已设密码且不在相机/相册等外部界面时，自动锁定会话
                if (PasswordManager.hasPassword(DiaryApplication.this)
                        && LockSession.shouldAutoLock()) {
                    LockSession.lock();
                    MokeLog.d("[App] 自动上锁");
                }
            }
        });
    }
}

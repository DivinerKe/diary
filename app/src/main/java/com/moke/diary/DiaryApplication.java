package com.moke.diary;

import android.app.Application;

import androidx.lifecycle.DefaultLifecycleObserver;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ProcessLifecycleOwner;

import com.moke.diary.util.LockSession;
import com.moke.diary.util.PasswordManager;
import com.moke.diary.util.BackupManager;

public class DiaryApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ProcessLifecycleOwner.get().getLifecycle().addObserver(new DefaultLifecycleObserver() {
            @Override
            public void onStop(LifecycleOwner owner) {
                BackupManager.exportBackupAsync(DiaryApplication.this);
                if (PasswordManager.hasPassword(DiaryApplication.this)
                        && LockSession.shouldAutoLock()) {
                    LockSession.lock();
                }
            }
        });
    }
}

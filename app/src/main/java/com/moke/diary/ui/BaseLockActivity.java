package com.moke.diary.ui;

import android.content.Intent;
import android.os.Bundle;

import com.moke.diary.util.LockSession;
import com.moke.diary.util.PasswordManager;

public abstract class BaseLockActivity extends BaseThemedActivity {

    @Override
    protected void onResume() {
        super.onResume();
        if (PasswordManager.hasPassword(this) && !LockSession.isUnlocked()) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(intent);
            finish();
        }
    }
}

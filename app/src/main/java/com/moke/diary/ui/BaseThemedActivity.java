package com.moke.diary.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.moke.diary.util.ThemeManager;

/**
 * 带主题支持的 Activity 基类。
 * 在 super.onCreate 之前应用用户选择的主题。
 */
public abstract class BaseThemedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
    }
}

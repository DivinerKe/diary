package com.moke.diary.ui;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.moke.diary.util.ThemeManager;

public abstract class BaseThemedActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemeManager.applyTheme(this);
        super.onCreate(savedInstanceState);
    }
}

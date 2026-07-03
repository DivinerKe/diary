package com.moke.diary.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.moke.diary.R;
import com.moke.diary.util.MediaCaptureHelper;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public class AudioRecordActivity extends BaseThemedActivity {

    private static final int PERMISSION_REQUEST = 1001;
    public static final String EXTRA_OUTPUT_PATH = "output_path";

    private TextView recordStatus;
    private TextView recordTimer;
    private MaterialButton btnRecordToggle;
    private MediaRecorder mediaRecorder;
    private File outputFile;
    private boolean recording;
    private long startTime;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            long elapsed = System.currentTimeMillis() - startTime;
            int seconds = (int) (elapsed / 1000);
            recordTimer.setText(String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_audio_record);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finishCancelled());

        recordStatus = findViewById(R.id.recordStatus);
        recordTimer = findViewById(R.id.recordTimer);
        btnRecordToggle = findViewById(R.id.btnRecordToggle);
        btnRecordToggle.setOnClickListener(v -> toggleRecording());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, R.string.permission_record_audio_denied, Toast.LENGTH_SHORT).show();
                finishCancelled();
            }
        }
    }

    private void toggleRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    PERMISSION_REQUEST
            );
            return;
        }

        if (recording) {
            stopRecording(true);
        } else {
            startRecording();
        }
    }

    private void startRecording() {
        try {
            outputFile = MediaCaptureHelper.createCaptureFile(this, com.moke.diary.model.MediaType.AUDIO);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mediaRecorder = new MediaRecorder(this);
            } else {
                mediaRecorder = new MediaRecorder();
            }
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();

            recording = true;
            startTime = System.currentTimeMillis();
            recordStatus.setText(R.string.recording);
            btnRecordToggle.setText(R.string.stop_record);
            handler.post(timerRunnable);
        } catch (IOException e) {
            Toast.makeText(this, R.string.record_failed, Toast.LENGTH_SHORT).show();
            releaseRecorder();
        }
    }

    private void stopRecording(boolean save) {
        handler.removeCallbacks(timerRunnable);
        if (mediaRecorder != null) {
            try {
                mediaRecorder.stop();
            } catch (RuntimeException ignored) {
                save = false;
            }
            releaseRecorder();
        }
        recording = false;
        recordStatus.setText(R.string.record_ready);
        btnRecordToggle.setText(R.string.start_record);

        if (save && outputFile != null && outputFile.exists() && outputFile.length() > 0) {
            Intent data = new Intent();
            data.putExtra(EXTRA_OUTPUT_PATH, outputFile.getAbsolutePath());
            setResult(RESULT_OK, data);
            finish();
        } else if (save) {
            Toast.makeText(this, R.string.record_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private void releaseRecorder() {
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private void finishCancelled() {
        if (recording) {
            stopRecording(false);
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    public void onBackPressed() {
        finishCancelled();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacks(timerRunnable);
        if (recording) {
            stopRecording(false);
        } else {
            releaseRecorder();
        }
        super.onDestroy();
    }
}

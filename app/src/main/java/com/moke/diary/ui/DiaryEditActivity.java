package com.moke.diary.ui;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.chip.Chip;
import com.moke.diary.R;
import com.moke.diary.adapter.MediaAdapter;
import com.moke.diary.databinding.ActivityDiaryEditBinding;
import com.moke.diary.db.DiaryDao;
import com.moke.diary.db.DiaryDatabase;
import com.moke.diary.model.DiaryEntry;
import com.moke.diary.model.DiaryRevision;
import com.moke.diary.model.DiaryWithMedia;
import com.moke.diary.model.MediaAttachment;
import com.moke.diary.model.MediaType;
import com.moke.diary.model.Mood;
import com.moke.diary.util.CryptoUtil;
import com.moke.diary.util.LockSession;
import com.moke.diary.util.BackupManager;
import com.moke.diary.util.MediaCaptureHelper;
import com.moke.diary.util.MediaStorage;
import com.moke.diary.util.PasswordManager;
import com.moke.diary.util.RevisionDiffHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DiaryEditActivity extends BaseLockActivity {

    private enum PendingCapture {
        NONE, TAKE_PHOTO, RECORD_VIDEO, RECORD_AUDIO
    }

    private static final String EXTRA_DIARY_ID = "diary_id";
    private static final int[] BACKGROUND_COLORS = {
            0xFFFFFFFF, 0xFFFFF3E0, 0xFFE8F5E9, 0xFFE3F2FD,
            0xFFF3E5F5, 0xFFFFEBEE, 0xFFE0F7FA, 0xFFFFFDE7
    };

    private ActivityDiaryEditBinding binding;
    private DiaryDao diaryDao;
    private ExecutorService executor;
    private MediaAdapter mediaAdapter;
    private long diaryId = -1;
    private DiaryEntry existingEntry;
    private int selectedColor = BACKGROUND_COLORS[0];
    private Mood selectedMood = Mood.NEUTRAL;
    private final List<MediaAttachment> pendingMedia = new ArrayList<>();
    private final List<MediaAttachment> snapshotMedia = new ArrayList<>();
    private String sessionPassword;
    private String snapshotTitle = "";
    private String snapshotContent = "";
    private String snapshotMood = Mood.NEUTRAL.name();
    private int snapshotColor = BACKGROUND_COLORS[0];

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<String> pickAudioLauncher;
    private ActivityResultLauncher<String> pickVideoLauncher;
    private ActivityResultLauncher<Uri> takePictureLauncher;
    private ActivityResultLauncher<Uri> captureVideoLauncher;
    private ActivityResultLauncher<Intent> recordAudioLauncher;
    private ActivityResultLauncher<String[]> permissionLauncher;
    private PendingCapture pendingCapture = PendingCapture.NONE;
    private File pendingCaptureFile;

    public static Intent createIntent(Context context, long diaryId) {
        Intent intent = new Intent(context, DiaryEditActivity.class);
        intent.putExtra(EXTRA_DIARY_ID, diaryId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDiaryEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        diaryId = getIntent().getLongExtra(EXTRA_DIARY_ID, -1);
        diaryDao = DiaryDatabase.getInstance(this).diaryDao();
        executor = Executors.newSingleThreadExecutor();

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        setupMediaPickers();
        setupMediaList();
        setupMoodSelector();
        setupColorPicker();
        setupButtons();

        if (diaryId > 0) {
            binding.toolbar.setTitle(R.string.edit_diary);
            loadExistingDiary();
        } else {
            binding.toolbar.setTitle(R.string.new_diary);
            applyBackgroundColor(selectedColor);
        }
    }

    private void setupMediaPickers() {
        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    try {
                        handlePickedMedia(uri, MediaType.IMAGE);
                    } finally {
                        endExternalCaptureSafely();
                    }
                });
        pickAudioLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    try {
                        handlePickedMedia(uri, MediaType.AUDIO);
                    } finally {
                        endExternalCaptureSafely();
                    }
                });
        pickVideoLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    try {
                        handlePickedMedia(uri, MediaType.VIDEO);
                    } finally {
                        endExternalCaptureSafely();
                    }
                });

        takePictureLauncher = registerForActivityResult(
                new ActivityResultContracts.TakePicture(),
                success -> {
                    try {
                        handleCaptureResult(success, MediaType.IMAGE);
                    } finally {
                        endExternalCaptureSafely();
                    }
                });

        captureVideoLauncher = registerForActivityResult(
                new ActivityResultContracts.CaptureVideo(),
                success -> {
                    try {
                        handleCaptureResult(success, MediaType.VIDEO);
                    } finally {
                        endExternalCaptureSafely();
                    }
                });

        recordAudioLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    try {
                        if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                            String path = result.getData().getStringExtra(AudioRecordActivity.EXTRA_OUTPUT_PATH);
                            if (path != null) {
                                handleCapturedFile(new File(path), MediaType.AUDIO);
                            }
                        }
                    } finally {
                        endExternalCaptureSafely();
                    }
                });

        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestMultiplePermissions(),
                this::onPermissionsResult);
    }

    private void beginExternalCaptureSafely() {
        LockSession.beginExternalCapture();
    }

    private void endExternalCaptureSafely() {
        LockSession.endExternalCapture();
    }

    private void launchPickImage() {
        beginExternalCaptureSafely();
        pickImageLauncher.launch("image/*");
    }

    private void launchPickVideo() {
        beginExternalCaptureSafely();
        pickVideoLauncher.launch("video/*");
    }

    private void launchPickAudio() {
        beginExternalCaptureSafely();
        pickAudioLauncher.launch("audio/*");
    }

    private void onPermissionsResult(Map<String, Boolean> results) {
        PendingCapture action = pendingCapture;
        pendingCapture = PendingCapture.NONE;

        if (action == PendingCapture.NONE) {
            return;
        }

        String[] required = getRequiredPermissions(action);
        for (String permission : required) {
            if (!Boolean.TRUE.equals(results.get(permission))) {
                showPermissionDeniedMessage(action);
                return;
            }
        }
        launchCapture(action);
    }

    private void showPhotoOptions() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_photo_source)
                .setItems(new CharSequence[]{
                        getString(R.string.take_photo),
                        getString(R.string.pick_from_gallery)
                }, (dialog, which) -> {
                    if (which == 0) {
                        requestCapture(PendingCapture.TAKE_PHOTO);
                    } else {
                        launchPickImage();
                    }
                })
                .show();
    }

    private void showVideoOptions() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_video_source)
                .setItems(new CharSequence[]{
                        getString(R.string.record_video),
                        getString(R.string.pick_from_gallery)
                }, (dialog, which) -> {
                    if (which == 0) {
                        requestCapture(PendingCapture.RECORD_VIDEO);
                    } else {
                        launchPickVideo();
                    }
                })
                .show();
    }

    private void showAudioOptions() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_audio_source)
                .setItems(new CharSequence[]{
                        getString(R.string.record_audio),
                        getString(R.string.pick_audio_file)
                }, (dialog, which) -> {
                    if (which == 0) {
                        requestCapture(PendingCapture.RECORD_AUDIO);
                    } else {
                        launchPickAudio();
                    }
                })
                .show();
    }

    private void requestCapture(PendingCapture action) {
        String[] permissions = getRequiredPermissions(action);
        if (hasPermissions(permissions)) {
            launchCapture(action);
            return;
        }
        pendingCapture = action;
        permissionLauncher.launch(permissions);
    }

    private void launchCapture(PendingCapture action) {
        beginExternalCaptureSafely();
        try {
            switch (action) {
                case TAKE_PHOTO:
                    pendingCaptureFile = MediaCaptureHelper.createCaptureFile(this, MediaType.IMAGE);
                    takePictureLauncher.launch(MediaCaptureHelper.getUriForFile(this, pendingCaptureFile));
                    break;
                case RECORD_VIDEO:
                    pendingCaptureFile = MediaCaptureHelper.createCaptureFile(this, MediaType.VIDEO);
                    captureVideoLauncher.launch(MediaCaptureHelper.getUriForFile(this, pendingCaptureFile));
                    break;
                case RECORD_AUDIO:
                    recordAudioLauncher.launch(new Intent(this, AudioRecordActivity.class));
                    break;
                default:
                    endExternalCaptureSafely();
                    break;
            }
        } catch (Exception e) {
            endExternalCaptureSafely();
            Toast.makeText(this, R.string.capture_failed, Toast.LENGTH_SHORT).show();
        }
    }

    private static String[] getRequiredPermissions(PendingCapture action) {
        switch (action) {
            case TAKE_PHOTO:
                return new String[]{Manifest.permission.CAMERA};
            case RECORD_VIDEO:
                return new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
            case RECORD_AUDIO:
                return new String[]{Manifest.permission.RECORD_AUDIO};
            default:
                return new String[0];
        }
    }

    private boolean hasPermissions(String[] permissions) {
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void showPermissionDeniedMessage(PendingCapture action) {
        int message;
        switch (action) {
            case RECORD_AUDIO:
                message = R.string.permission_record_audio_denied;
                break;
            default:
                message = R.string.permission_camera_denied;
                break;
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void handleCaptureResult(boolean success, MediaType type) {
        if (success && pendingCaptureFile != null) {
            handleCapturedFile(pendingCaptureFile, type);
        } else if (!success) {
            Toast.makeText(this, R.string.capture_failed, Toast.LENGTH_SHORT).show();
        }
        pendingCaptureFile = null;
    }

    private void handleCapturedFile(File file, MediaType type) {
        executor.execute(() -> {
            try {
                MediaAttachment attachment = MediaStorage.saveMediaFile(
                        this, file, type, diaryId > 0 ? diaryId : 0);
                if (file.getParentFile() != null
                        && file.getParentFile().equals(new File(getCacheDir(), "capture"))) {
                    file.delete();
                }
                runOnUiThread(() -> {
                    pendingMedia.add(attachment);
                    mediaAdapter.setItems(pendingMedia);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, R.string.capture_failed, Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void setupMediaList() {
        mediaAdapter = new MediaAdapter(true);
        mediaAdapter.setRemoveListener((position, attachment) -> {
            pendingMedia.remove(attachment);
            mediaAdapter.setItems(pendingMedia);
        });
        binding.mediaRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.mediaRecyclerView.setAdapter(mediaAdapter);
    }

    private void setupMoodSelector() {
        for (Mood mood : Mood.values()) {
            Chip chip = new Chip(this);
            chip.setText(mood.display());
            chip.setCheckable(true);
            chip.setTag(mood);
            if (mood == selectedMood) {
                chip.setChecked(true);
            }
            chip.setOnClickListener(v -> selectedMood = (Mood) chip.getTag());
            binding.moodGroup.addView(chip);
        }
    }

    private void setupColorPicker() {
        int size = (int) (40 * getResources().getDisplayMetrics().density);
        int margin = (int) (8 * getResources().getDisplayMetrics().density);

        for (int color : BACKGROUND_COLORS) {
            View circle = new View(this);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(size, size);
            params.setMarginEnd(margin);
            circle.setLayoutParams(params);

            GradientDrawable drawable = new GradientDrawable();
            drawable.setShape(GradientDrawable.OVAL);
            drawable.setColor(color);
            drawable.setStroke(4, color == selectedColor ? Color.BLACK : Color.TRANSPARENT);
            circle.setBackground(drawable);

            circle.setOnClickListener(v -> {
                selectedColor = color;
                applyBackgroundColor(color);
                refreshColorPickerSelection();
            });
            circle.setTag(color);
            binding.colorPicker.addView(circle);
        }
    }

    private void refreshColorPickerSelection() {
        for (int i = 0; i < binding.colorPicker.getChildCount(); i++) {
            View child = binding.colorPicker.getChildAt(i);
            int color = (int) child.getTag();
            GradientDrawable drawable = (GradientDrawable) child.getBackground();
            drawable.setStroke(4, color == selectedColor ? Color.BLACK : Color.TRANSPARENT);
        }
    }

    private void applyBackgroundColor(int color) {
        binding.editContainer.setBackgroundColor(color);
    }

    private void setupButtons() {
        binding.btnAddPhoto.setOnClickListener(v -> showPhotoOptions());
        binding.btnAddAudio.setOnClickListener(v -> showAudioOptions());
        binding.btnAddVideo.setOnClickListener(v -> showVideoOptions());
        binding.btnSave.setOnClickListener(v -> saveDiary());
    }

    private void handlePickedMedia(Uri uri, MediaType type) {
        if (uri == null) {
            return;
        }
        executor.execute(() -> {
            try {
                MediaAttachment attachment = MediaStorage.saveMedia(this, uri, type, diaryId > 0 ? diaryId : 0);
                runOnUiThread(() -> {
                    pendingMedia.add(attachment);
                    mediaAdapter.setItems(pendingMedia);
                });
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(this, "媒体文件保存失败", Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void loadExistingDiary() {
        executor.execute(() -> {
            DiaryWithMedia diary = diaryDao.getDiaryById(diaryId);
            runOnUiThread(() -> {
                if (diary == null) {
                    finish();
                    return;
                }
                existingEntry = diary.entry;
                if (existingEntry.encrypted) {
                    decryptAndFill(diary);
                } else {
                    fillForm(diary, existingEntry.content);
                }
            });
        });
    }

    private void decryptAndFill(DiaryWithMedia diary) {
        String password = LockSession.getSessionPassword();
        if (password == null) {
            Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        sessionPassword = password;
        try {
            String content = CryptoUtil.decrypt(existingEntry.content, password);
            fillForm(diary, content);
        } catch (Exception e) {
            Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void fillForm(DiaryWithMedia diary, String content) {
        binding.titleInput.setText(existingEntry.title);
        binding.contentInput.setText(content);
        binding.encryptSwitch.setChecked(existingEntry.encrypted);
        selectedColor = existingEntry.backgroundColor;
        selectedMood = Mood.fromName(existingEntry.mood);
        applyBackgroundColor(selectedColor);
        refreshColorPickerSelection();

        for (int i = 0; i < binding.moodGroup.getChildCount(); i++) {
            Chip chip = (Chip) binding.moodGroup.getChildAt(i);
            chip.setChecked(chip.getTag() == selectedMood);
        }

        pendingMedia.clear();
        if (diary.mediaList != null) {
            pendingMedia.addAll(diary.mediaList);
        }
        mediaAdapter.setItems(pendingMedia);
        takeSnapshot(existingEntry.title, content, existingEntry.mood,
                existingEntry.backgroundColor, pendingMedia);
    }

    private void takeSnapshot(String title,
                              String content,
                              String mood,
                              int backgroundColor,
                              List<MediaAttachment> media) {
        snapshotTitle = title != null ? title : "";
        snapshotContent = content != null ? content : "";
        snapshotMood = mood != null ? mood : Mood.NEUTRAL.name();
        snapshotColor = backgroundColor;
        snapshotMedia.clear();
        if (media != null) {
            snapshotMedia.addAll(media);
        }
    }

    private void saveDiary() {
        String title = binding.titleInput.getText() != null
                ? binding.titleInput.getText().toString().trim() : "";
        String content = binding.contentInput.getText() != null
                ? binding.contentInput.getText().toString().trim() : "";

        if (TextUtils.isEmpty(title)) {
            Toast.makeText(this, R.string.title_required, Toast.LENGTH_SHORT).show();
            return;
        }

        boolean encrypt = binding.encryptSwitch.isChecked();
        if (encrypt && !PasswordManager.hasPassword(this)) {
            Toast.makeText(this, "请先在主页设置密码", Toast.LENGTH_SHORT).show();
            return;
        }
        if (encrypt) {
            sessionPassword = LockSession.getSessionPassword();
            if (sessionPassword == null) {
                Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        performSave(title, content, encrypt);
    }

    private void performSave(String title, String content, boolean encrypt) {
        executor.execute(() -> {
            long now = System.currentTimeMillis();
            DiaryEntry entry;
            boolean isNew = diaryId <= 0;

            if (isNew) {
                entry = new DiaryEntry();
            } else {
                entry = existingEntry;
            }

            entry.title = title;
            entry.mood = selectedMood.name();
            entry.backgroundColor = selectedColor;
            entry.encrypted = encrypt;
            entry.updatedAt = now;

            if (encrypt) {
                entry.content = CryptoUtil.encrypt(content, sessionPassword);
            } else {
                entry.content = content;
            }

            if (isNew) {
                diaryId = diaryDao.insertDiary(entry);
                entry.id = diaryId;
            } else {
                diaryDao.updateDiary(entry);
            }

            String changeLog = RevisionDiffHelper.buildChangeLog(
                    this,
                    isNew,
                    snapshotTitle,
                    title,
                    snapshotContent,
                    content,
                    snapshotMood,
                    selectedMood.name(),
                    snapshotColor,
                    selectedColor,
                    snapshotMedia,
                    pendingMedia,
                    encrypt
            );
            DiaryRevision revision = createRevision(entry, now, changeLog);
            diaryDao.insertRevision(revision);

            diaryDao.deleteMediaByDiaryId(diaryId);
            for (MediaAttachment attachment : pendingMedia) {
                attachment.diaryId = diaryId;
                diaryDao.insertMedia(attachment);
            }

            runOnUiThread(() -> {
                Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show();
                BackupManager.exportBackupAsync(this);
                finish();
            });
        });
    }

    private DiaryRevision createRevision(DiaryEntry entry, long revisedAt, String changeLog) {
        DiaryRevision revision = new DiaryRevision();
        revision.diaryId = entry.id;
        revision.title = entry.title;
        revision.content = entry.content;
        revision.mood = entry.mood;
        revision.backgroundColor = entry.backgroundColor;
        revision.revisedAt = revisedAt;
        revision.changeLog = changeLog != null ? changeLog : "";
        return revision;
    }

    @Override
    protected void onDestroy() {
        endExternalCaptureSafely();
        super.onDestroy();
        executor.shutdown();
    }
}

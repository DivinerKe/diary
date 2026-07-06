package com.moke.diary.ui;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.graphics.drawable.DrawableCompat;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;
import com.moke.diary.R;
import com.moke.diary.adapter.DiaryAdapter;
import com.moke.diary.databinding.ActivityMainBinding;
import com.moke.diary.db.DiaryDao;
import com.moke.diary.db.DiaryDatabase;
import com.moke.diary.model.DiaryWithMedia;
import com.moke.diary.model.Mood;
import com.moke.diary.util.BackupManager;
import com.moke.diary.util.LockSession;
import com.moke.diary.util.LockScreenUtil;
import com.moke.diary.util.MokeLog;
import com.moke.diary.util.PasswordManager;
import com.moke.diary.util.ThemeManager;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 应用主界面：日记列表、搜索筛选、锁屏、备份恢复、主题与密码管理。
 */
public class MainActivity extends BaseThemedActivity {

    private ActivityMainBinding binding;
    private DiaryDao diaryDao;
    private DiaryAdapter adapter;
    private ExecutorService executor;
    /** 当前选中的心情筛选，null 表示全部 */
    private String selectedMood = null;
    private View lockScreen;
    private TextInputEditText lockPasswordInput;
    private TextView lockEmojiText;
    private TextView lockTitleText;
    private TextView lockMessageText;
    /** 选择备份目录后是否立即执行一次备份 */
    private boolean pendingBackupAfterFolder;

    /** 用户授权备份写入目录（SAF OPEN_DOCUMENT_TREE） */
    private final ActivityResultLauncher<Intent> pickBackupFolderLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    pendingBackupAfterFolder = false;
                    return;
                }
                Uri treeUri = result.getData().getData();
                if (treeUri == null) {
                    pendingBackupAfterFolder = false;
                    return;
                }
                BackupManager.saveTreeUri(this, treeUri);
                Toast.makeText(this, R.string.backup_folder_saved, Toast.LENGTH_SHORT).show();
                if (pendingBackupAfterFolder) {
                    pendingBackupAfterFolder = false;
                    runBackup();
                }
            });

    /** 用户选择「我的日记」文件夹后触发合并恢复 */
    private final ActivityResultLauncher<Intent> pickRestoreFolderLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) {
                    return;
                }
                Uri treeUri = result.getData().getData();
                if (treeUri != null) {
                    BackupManager.saveTreeUri(this, treeUri);
                    performRestoreFromFolder(treeUri);
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setSupportActionBar(binding.toolbar);
        setupToolbar();
        diaryDao = DiaryDatabase.getInstance(this).diaryDao();
        executor = Executors.newSingleThreadExecutor();

        lockScreen = binding.lockScreen.getRoot();
        lockPasswordInput = lockScreen.findViewById(R.id.lockPasswordInput);
        lockEmojiText = lockScreen.findViewById(R.id.lockEmojiText);
        lockTitleText = lockScreen.findViewById(R.id.lockTitleText);
        lockMessageText = lockScreen.findViewById(R.id.lockMessageText);
        MaterialButton btnUnlock = lockScreen.findViewById(R.id.btnUnlock);
        MaterialButton btnForgot = lockScreen.findViewById(R.id.btnForgotPassword);

        btnUnlock.setOnClickListener(v -> attemptUnlock());
        btnForgot.setOnClickListener(v -> showRecoverPasswordDialog());

        setupRecyclerView();
        setupMoodFilter();
        setupSearch();
        setupFab();
        checkRestoreOnLaunch();
        updateLockState();
        MokeLog.d("[Main] onCreate 完成");
    }

    /** 启动时若本地无日记但检测到备份，提示用户恢复 */
    private void checkRestoreOnLaunch() {
        executor.execute(() -> {
            int count = diaryDao.getDiaryCount();
            boolean hasBackup = BackupManager.detectBackupHint(MainActivity.this);
            boolean manualPick = BackupManager.needsManualRestorePick(MainActivity.this);
            MokeLog.d("[Main] 启动检查：日记=" + count + "，有备份=" + hasBackup + "，需手动恢复=" + manualPick);
            runOnUiThread(() -> {
                if (count == 0 && hasBackup) {
                    showRestoreDialog(false, manualPick);
                }
            });
        });
    }

    /** 设置工具栏溢出菜单为温馨圆点图标，并按主题着色 */
    private void setupToolbar() {
        android.graphics.drawable.Drawable overflowIcon =
                AppCompatResources.getDrawable(this, R.drawable.ic_menu_overflow_warm);
        if (overflowIcon != null) {
            overflowIcon = DrawableCompat.wrap(overflowIcon.mutate());
            TypedValue typedValue = new TypedValue();
            if (getTheme().resolveAttribute(com.google.android.material.R.attr.colorOnPrimary, typedValue, true)) {
                DrawableCompat.setTint(overflowIcon, typedValue.data);
            }
            binding.toolbar.setOverflowIcon(overflowIcon);
        }
    }

    private void setupRecyclerView() {
        adapter = new DiaryAdapter(this::openDiaryDetail);
        binding.diaryRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.diaryRecyclerView.setAdapter(adapter);
    }

    private void setupMoodFilter() {
        Chip allChip = new Chip(this);
        allChip.setText(R.string.all_moods);
        allChip.setCheckable(true);
        allChip.setChecked(true);
        allChip.setOnClickListener(v -> {
            selectedMood = null;
            loadDiaries();
        });
        binding.moodFilterGroup.addView(allChip);

        for (Mood mood : Mood.values()) {
            Chip chip = new Chip(this);
            chip.setText(mood.display());
            chip.setCheckable(true);
            chip.setOnClickListener(v -> {
                selectedMood = mood.name();
                loadDiaries();
            });
            binding.moodFilterGroup.addView(chip);
        }
    }

    private void setupSearch() {
        binding.searchInput.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                loadDiaries();
            }

            @Override
            public void afterTextChanged(android.text.Editable s) {
            }
        });
        binding.searchInput.setOnEditorActionListener((v, actionId, event) -> {
            loadDiaries();
            return true;
        });
    }

    private void setupFab() {
        binding.fabAdd.setOnClickListener(v ->
                startActivity(DiaryEditActivity.createIntent(this, -1)));
    }

    /** 是否处于锁屏状态（已设密码且当前会话未解锁） */
    private boolean isLocked() {
        return PasswordManager.hasPassword(this) && !LockSession.isUnlocked();
    }

    /** 根据锁屏状态切换主内容与锁屏层，解锁后刷新列表 */
    private void updateLockState() {
        boolean locked = isLocked();
        lockScreen.setVisibility(locked ? View.VISIBLE : View.GONE);
        binding.contentLayout.setVisibility(locked ? View.GONE : View.VISIBLE);

        applyLockScreenStyle();

        if (locked) {
            adapter.setItems(null);
            lockPasswordInput.setText("");
        } else {
            loadDiaries();
        }
        invalidateOptionsMenu();
    }

    /** 按当前主题与时段刷新上锁界面的图标与文案 */
    private void applyLockScreenStyle() {
        ThemeManager.AppTheme theme = ThemeManager.getTheme(this);
        if (lockEmojiText != null) {
            lockEmojiText.setText(LockScreenUtil.getTimeBasedLockEmoji(this));
        }
        if (lockTitleText != null) {
            lockTitleText.setText(LockScreenUtil.getLockTitleRes(theme));
        }
        if (lockMessageText != null) {
            lockMessageText.setText(LockScreenUtil.getLockMessageRes(theme));
        }
        if (lockPasswordInput != null) {
            lockPasswordInput.setHint(LockScreenUtil.getLockPasswordHintRes(theme));
        }
    }

    private void attemptUnlock() {
        String password = lockPasswordInput.getText() != null
                ? lockPasswordInput.getText().toString() : "";
        if (TextUtils.isEmpty(password)) {
            Toast.makeText(this, R.string.password_hint, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!PasswordManager.verifyPassword(this, password)) {
            Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show();
            return;
        }
        LockSession.unlock(password);
        MokeLog.d("[Main] 解锁成功");
        updateLockState();
    }

    /** 后台查询日记，支持关键词 + 心情组合筛选 */
    private void loadDiaries() {
        if (isLocked()) {
            return;
        }

        String keyword = binding.searchInput.getText() != null
                ? binding.searchInput.getText().toString().trim() : "";

        executor.execute(() -> {
            List<DiaryWithMedia> diaries;
            if (!TextUtils.isEmpty(keyword) && selectedMood != null) {
                diaries = diaryDao.searchDiariesByMood(keyword, selectedMood);
            } else if (!TextUtils.isEmpty(keyword)) {
                diaries = diaryDao.searchDiaries(keyword);
            } else if (selectedMood != null) {
                diaries = diaryDao.getDiariesByMood(selectedMood);
            } else {
                diaries = diaryDao.getAllDiaries();
            }

            runOnUiThread(() -> {
                if (isLocked()) {
                    return;
                }
                adapter.setItems(diaries, keyword);
                boolean empty = diaries.isEmpty();
                binding.emptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
                binding.diaryRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
                MokeLog.d("[Main] 加载列表：" + diaries.size() + " 篇");
            });
        });
    }

    private void openDiaryDetail(DiaryWithMedia diary) {
        startActivity(DiaryDetailActivity.createIntent(this, diary.entry.id));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        setMenuIconsVisible(menu);
        boolean hasPassword = PasswordManager.hasPassword(this);
        boolean unlocked = LockSession.isUnlocked();

        menu.findItem(R.id.action_set_password).setVisible(!hasPassword);
        menu.findItem(R.id.action_change_password).setVisible(hasPassword && unlocked);
        menu.findItem(R.id.action_lock_now).setVisible(hasPassword && unlocked);
        return true;
    }

    /** 强制在溢出菜单中显示各项图标（Android 默认可能隐藏） */
    private void setMenuIconsVisible(Menu menu) {
        if (menu instanceof androidx.appcompat.view.menu.MenuBuilder) {
            ((androidx.appcompat.view.menu.MenuBuilder) menu).setOptionalIconsVisible(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_set_password) {
            showSetPasswordDialog();
            return true;
        } else if (id == R.id.action_change_password) {
            showChangePasswordDialog();
            return true;
        } else if (id == R.id.action_lock_now) {
            LockSession.lock();
            MokeLog.d("[Main] 手动上锁");
            updateLockState();
            Toast.makeText(this, R.string.locked_success, Toast.LENGTH_SHORT).show();
            return true;
        } else if (id == R.id.action_theme) {
            showThemePicker();
            return true;
        } else if (id == R.id.action_backup) {
            performBackup();
            return true;
        } else if (id == R.id.action_restore) {
            showRestoreDialog(true, BackupManager.needsManualRestorePick(this));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void performBackup() {
        if (!BackupManager.hasWritableLocation(this)) {
            promptBackupFolder(true);
            return;
        }
        runBackup();
    }

    private void promptBackupFolder(boolean backupAfterSelect) {
        pendingBackupAfterFolder = backupAfterSelect;
        new AlertDialog.Builder(this)
                .setTitle(R.string.backup_pick_folder)
                .setMessage(R.string.backup_pick_folder_hint)
                .setPositiveButton(R.string.confirm, (dialog, which) ->
                        pickBackupFolderLauncher.launch(BackupManager.createOpenTreeIntent()))
                .setNegativeButton(R.string.cancel, (dialog, which) -> pendingBackupAfterFolder = false)
                .show();
    }

    private void runBackup() {
        MokeLog.d("[Main] 开始手动备份");
        executor.execute(() -> {
            if (diaryDao.getDiaryCount() == 0) {
                runOnUiThread(() -> Toast.makeText(this, R.string.backup_empty, Toast.LENGTH_SHORT).show());
                return;
            }
            boolean success = BackupManager.exportBackup(this);
            runOnUiThread(() -> {
                if (success) {
                    Toast.makeText(this, R.string.backup_success, Toast.LENGTH_SHORT).show();
                } else if (!BackupManager.hasWritableLocation(this)) {
                    promptBackupFolder(true);
                } else {
                    Toast.makeText(this, R.string.backup_failed, Toast.LENGTH_SHORT).show();
                }
            });
        });
    }

    private void showRestoreDialog(boolean manual, boolean manualPickRequired) {
        if (manualPickRequired) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.restore_backup)
                    .setMessage(R.string.restore_after_clear_prompt)
                    .setPositiveButton(R.string.restore_pick_folder, (dialog, which) -> launchPickBackupForRestore())
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(R.string.restore_backup)
                .setMessage(manual ? R.string.restore_confirm : R.string.restore_prompt)
                .setPositiveButton(R.string.confirm, (dialog, which) -> performRestore())
                .setNegativeButton(R.string.cancel, null);
        builder.setNeutralButton(R.string.restore_pick_folder, (dialog, which) -> launchPickBackupForRestore());
        builder.show();
    }

    private void launchPickBackupForRestore() {
        pickRestoreFolderLauncher.launch(BackupManager.createOpenTreeIntent());
    }

    /** 扫描并合并恢复全部备份；无自动权限时引导选手动选文件夹 */
    private void performRestore() {
        MokeLog.d("[Main] 开始自动恢复");
        if (BackupManager.needsManualRestorePick(this)) {
            launchPickBackupForRestore();
            return;
        }
        executor.execute(() -> {
            BackupManager.RestoreResult result = BackupManager.importAllBackups(this, null, null);
            runOnUiThread(() -> showRestoreResult(result));
        });
    }

    private void performRestoreFromFolder(Uri treeUri) {
        MokeLog.d("[Main] 从文件夹恢复：" + treeUri);
        executor.execute(() -> {
            BackupManager.RestoreResult result = BackupManager.importAllBackups(this, treeUri, null);
            runOnUiThread(() -> showRestoreResult(result));
        });
    }

    private void showRestoreResult(BackupManager.RestoreResult result) {
        MokeLog.i("[Main] 恢复结果：success=" + result.success
                + "，files=" + result.backupFileCount + "，diaries=" + result.diaryCount);
        if (result.success) {
            String message = result.backupFileCount > 1
                    ? getString(R.string.restore_success_merge, result.backupFileCount, result.diaryCount)
                    : getString(R.string.restore_success_count, result.diaryCount);
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            updateLockState();
        } else {
            Toast.makeText(this, R.string.restore_pick_hint, Toast.LENGTH_LONG).show();
            launchPickBackupForRestore();
        }
    }

    private void showThemePicker() {
        ThemeManager.AppTheme current = ThemeManager.getTheme(this);
        ThemeManager.AppTheme[] themes = ThemeManager.AppTheme.values();
        CharSequence[] labels = new CharSequence[themes.length];
        int checkedItem = 0;
        for (int i = 0; i < themes.length; i++) {
            labels[i] = getString(themes[i].labelRes);
            if (themes[i] == current) {
                checkedItem = i;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.choose_theme)
                .setSingleChoiceItems(labels, checkedItem, (dialog, which) -> {
                    ThemeManager.AppTheme selected = themes[which];
                    if (selected != ThemeManager.getTheme(this)) {
                        ThemeManager.setTheme(this, selected);
                        recreate();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showSetPasswordDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_set_password, null);
        TextInputEditText passwordInput = view.findViewById(R.id.passwordInput);
        TextInputEditText confirmInput = view.findViewById(R.id.confirmPasswordInput);
        TextInputEditText answerInput = view.findViewById(R.id.answerInput);
        Spinner questionSpinner = view.findViewById(R.id.questionSpinner);

        ArrayAdapter<CharSequence> spinnerAdapter = ArrayAdapter.createFromResource(
                this, R.array.security_questions, android.R.layout.simple_spinner_item);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        questionSpinner.setAdapter(spinnerAdapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.set_password)
                .setView(view)
                .setPositiveButton(R.string.confirm, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = textOf(passwordInput);
            String confirm = textOf(confirmInput);
            String answer = textOf(answerInput);
            String question = questionSpinner.getSelectedItem().toString();

            if (TextUtils.isEmpty(password)) {
                Toast.makeText(this, R.string.password_hint, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!password.equals(confirm)) {
                Toast.makeText(this, R.string.password_mismatch, Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(answer)) {
                Toast.makeText(this, R.string.answer_required, Toast.LENGTH_SHORT).show();
                return;
            }

            PasswordManager.setPasswordWithRecovery(this, password, question, answer);
            LockSession.unlock(password);
            Toast.makeText(this, R.string.password_set_success, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            updateLockState();
        }));
        dialog.show();
    }

    private void showChangePasswordDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_change_password, null);
        TextInputEditText oldInput = view.findViewById(R.id.oldPasswordInput);
        TextInputEditText newInput = view.findViewById(R.id.newPasswordInput);
        TextInputEditText confirmInput = view.findViewById(R.id.confirmPasswordInput);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.change_password)
                .setView(view)
                .setPositiveButton(R.string.confirm, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String oldPassword = textOf(oldInput);
            String newPassword = textOf(newInput);
            String confirm = textOf(confirmInput);

            if (TextUtils.isEmpty(newPassword)) {
                Toast.makeText(this, R.string.password_hint, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPassword.equals(confirm)) {
                Toast.makeText(this, R.string.password_mismatch, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!PasswordManager.changePassword(this, oldPassword, newPassword)) {
                Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show();
                return;
            }

            LockSession.unlock(newPassword);
            Toast.makeText(this, R.string.password_changed_success, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        }));
        dialog.show();
    }

    private void showRecoverPasswordDialog() {
        if (!PasswordManager.hasRecoverySetup(this)) {
            Toast.makeText(this, R.string.wrong_answer, Toast.LENGTH_SHORT).show();
            return;
        }

        View view = LayoutInflater.from(this).inflate(R.layout.dialog_recover_password, null);
        TextView questionText = view.findViewById(R.id.securityQuestionText);
        TextInputEditText answerInput = view.findViewById(R.id.answerInput);
        TextInputEditText newInput = view.findViewById(R.id.newPasswordInput);
        TextInputEditText confirmInput = view.findViewById(R.id.confirmPasswordInput);

        questionText.setText(PasswordManager.getSecurityQuestion(this));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.recover_password)
                .setView(view)
                .setPositiveButton(R.string.confirm, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String answer = textOf(answerInput);
            String newPassword = textOf(newInput);
            String confirm = textOf(confirmInput);

            if (!PasswordManager.verifySecurityAnswer(this, answer)) {
                Toast.makeText(this, R.string.wrong_answer, Toast.LENGTH_SHORT).show();
                return;
            }
            if (TextUtils.isEmpty(newPassword)) {
                Toast.makeText(this, R.string.password_hint, Toast.LENGTH_SHORT).show();
                return;
            }
            if (!newPassword.equals(confirm)) {
                Toast.makeText(this, R.string.password_mismatch, Toast.LENGTH_SHORT).show();
                return;
            }

            PasswordManager.resetPassword(this, newPassword);
            LockSession.unlock(newPassword);
            Toast.makeText(this, R.string.password_reset_success, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
            updateLockState();
        }));
        dialog.show();
    }

    private static String textOf(TextInputEditText input) {
        return input.getText() != null ? input.getText().toString() : "";
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateLockState();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}

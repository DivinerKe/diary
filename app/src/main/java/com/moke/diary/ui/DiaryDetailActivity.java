package com.moke.diary.ui;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.moke.diary.R;
import com.moke.diary.adapter.MediaAdapter;
import com.moke.diary.adapter.RevisionAdapter;
import com.moke.diary.databinding.ActivityDiaryDetailBinding;
import com.moke.diary.db.DiaryDao;
import com.moke.diary.db.DiaryDatabase;
import com.moke.diary.model.DiaryEntry;
import com.moke.diary.model.DiaryRevision;
import com.moke.diary.model.DiaryWithMedia;
import com.moke.diary.model.Mood;
import com.moke.diary.util.CryptoUtil;
import com.moke.diary.util.DateUtil;
import com.moke.diary.util.LockSession;
import com.moke.diary.util.MokeLog;
import com.moke.diary.util.ShareUtil;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 日记详情页。
 * 展示日记正文、媒体附件与修订历史时间线，支持编辑、分享与删除操作；
 * 加密日记需借助当前会话密码解密后展示。
 */
public class DiaryDetailActivity extends BaseLockActivity {

    private static final String EXTRA_DIARY_ID = "diary_id";

    private ActivityDiaryDetailBinding binding;
    private DiaryDao diaryDao;
    private ExecutorService executor;
    private long diaryId;
    private DiaryEntry currentEntry;
    private String decryptedContent;
    private String sessionPassword;

    /** 创建打开指定日记详情的 Intent。 */
    public static Intent createIntent(Context context, long diaryId) {
        Intent intent = new Intent(context, DiaryDetailActivity.class);
        intent.putExtra(EXTRA_DIARY_ID, diaryId);
        return intent;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityDiaryDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        diaryId = getIntent().getLongExtra(EXTRA_DIARY_ID, -1);
        diaryDao = DiaryDatabase.getInstance(this).diaryDao();
        executor = Executors.newSingleThreadExecutor();

        setSupportActionBar(binding.toolbar);
        binding.toolbar.setNavigationOnClickListener(v -> finish());

        loadDiary();
    }

    /** 后台加载日记及修订记录，加密条目走解密流程后再渲染 UI。 */
    private void loadDiary() {
        executor.execute(() -> {
            DiaryWithMedia diary = diaryDao.getDiaryById(diaryId);
            List<DiaryRevision> revisions = diaryDao.getRevisions(diaryId);

            runOnUiThread(() -> {
                if (diary == null) {
                    MokeLog.w("[Detail] 日记不存在 id=" + diaryId);
                    finish();
                    return;
                }
                currentEntry = diary.entry;
                if (currentEntry.encrypted) {
                    decryptAndDisplay(diary, revisions);
                } else {
                    decryptedContent = currentEntry.content;
                    displayDiary(diary, revisions);
                }
            });
        });
    }

    /** 使用会话密码解密日记正文，解密失败则提示并关闭页面。 */
    private void decryptAndDisplay(DiaryWithMedia diary, List<DiaryRevision> revisions) {
        String password = LockSession.getSessionPassword();
        if (password == null) {
            Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        sessionPassword = password;
        try {
            decryptedContent = CryptoUtil.decrypt(currentEntry.content, password);
            displayDiary(diary, revisions);
        } catch (Exception e) {
            Toast.makeText(this, R.string.wrong_password, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    /** 将日记基本信息、附件列表（只读）与修订时间线绑定到界面。 */
    private void displayDiary(DiaryWithMedia diary, List<DiaryRevision> revisions) {
        binding.detailContainer.setBackgroundColor(currentEntry.backgroundColor);
        binding.titleText.setText(currentEntry.title);
        binding.moodText.setText(Mood.fromName(currentEntry.mood).display());
        binding.timeText.setText(getString(R.string.created_at, DateUtil.formatFull(currentEntry.createdAt))
                + "\n" + getString(R.string.updated_at, DateUtil.formatFull(currentEntry.updatedAt)));
        binding.contentText.setText(decryptedContent);

        MediaAdapter mediaAdapter = new MediaAdapter(false);
        binding.mediaRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.mediaRecyclerView.setAdapter(mediaAdapter);
        mediaAdapter.setItems(diary.mediaList);

        RevisionAdapter revisionAdapter = new RevisionAdapter();
        binding.timelineRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        binding.timelineRecyclerView.setAdapter(revisionAdapter);
        revisionAdapter.setItems(revisions);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_detail, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_edit) {
            startActivity(DiaryEditActivity.createIntent(this, diaryId));
            return true;
        } else if (id == R.id.action_share) {
            if (decryptedContent != null) {
                ShareUtil.shareDiary(this, currentEntry, decryptedContent);
            }
            return true;
        } else if (id == R.id.action_delete) {
            confirmDelete();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.delete_confirm)
                .setPositiveButton(R.string.delete, (dialog, which) -> deleteDiary())
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** 弹出确认对话框后于后台线程删除日记并关闭页面。 */
    private void deleteDiary() {
        MokeLog.d("[Detail] 删除日记 id=" + diaryId);
        executor.execute(() -> {
            diaryDao.deleteDiary(currentEntry);
            runOnUiThread(() -> {
                Toast.makeText(this, "已删除", Toast.LENGTH_SHORT).show();
                finish();
            });
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
    }
}

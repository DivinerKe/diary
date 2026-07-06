package com.moke.diary.util;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.provider.MediaStore;

import androidx.documentfile.provider.DocumentFile;

import com.moke.diary.db.DiaryDao;
import com.moke.diary.db.DiaryDatabase;
import com.moke.diary.model.DiaryEntry;
import com.moke.diary.model.DiaryRevision;
import com.moke.diary.model.DiaryWithMedia;
import com.moke.diary.model.MediaAttachment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 日记备份与恢复管理器。
 * <p>
 * 负责将 Room 数据库中的日记、修订记录、媒体及安全配置导出为 zip，
 * 并支持从 SAF 目录、传统文件路径、MediaStore 等多来源扫描并合并恢复。
 * Android 11+ 优先使用 SAF 树 URI 写入，避免 MediaStore 孤儿文件问题。
 */
public final class BackupManager {

    /** 标准备份文件名 */
    public static final String BACKUP_FILE_NAME = "diary_backup.zip";
    /** 写入过程中的临时文件名，校验通过后再替换正式文件 */
    private static final String BACKUP_TEMP_NAME = "diary_backup.tmp.zip";
    /** 备份所在文件夹名（位于 Documents 下） */
    private static final String BACKUP_FOLDER = "我的日记";
    private static final String BACKUP_RELATIVE_PATH = Environment.DIRECTORY_DOCUMENTS + "/" + BACKUP_FOLDER + "/";
    /** backup.json 格式版本号 */
    private static final int BACKUP_VERSION = 1;
    /** 扫描 diary_backup (N).zip 编号变体的上限 */
    private static final int MAX_NUMBERED_VARIANT = 20;
    /** 异步备份防抖延迟：连续保存时合并为一次备份 */
    private static final long BACKUP_DEBOUNCE_MS = 2000L;

    /** 主线程 Handler，用于延迟触发异步备份 */
    private static final Handler BACKUP_HANDLER = new Handler(Looper.getMainLooper());
    private static Runnable pendingBackup;

    private BackupManager() {
    }

    /** 外部存储 DocumentsProvider 的 authority */
    private static final String EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents";

    /** 创建 SAF 目录选择 Intent，用于授权备份写入位置 */
    public static Intent createOpenTreeIntent() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        applyInitialDocumentsUri(intent);
        return intent;
    }

    /** 创建文件选择 Intent，支持多选备份 zip（兼容部分 MTK 文件选择器） */
    public static Intent createOpenBackupIntent() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/zip",
                "application/x-zip-compressed",
                "application/octet-stream"
        });
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        applyInitialBackupFolderUri(intent);
        return intent;
    }

    /** 从 GET_CONTENT / 多选结果中收集所有备份 Uri */
    public static List<Uri> collectUrisFromIntent(Intent data) {
        List<Uri> uris = new ArrayList<>();
        if (data == null) {
            return uris;
        }
        if (data.getClipData() != null) {
            android.content.ClipData clip = data.getClipData();
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) {
                    uris.add(uri);
                }
            }
        } else if (data.getData() != null) {
            uris.add(data.getData());
        }
        return uris;
    }

    /** 预览指定备份 zip 中包含的日记条数，失败返回 -1 */
    public static int countEntriesInBackup(Context context, Uri uri) {
        File tempZip = new File(context.getCacheDir(), "peek_" + BACKUP_FILE_NAME);
        try {
            if (!copyUriToFile(context, uri, tempZip) || !isValidZip(tempZip)) {
                return -1;
            }
            return countEntriesInZip(tempZip);
        } catch (Exception e) {
            return -1;
        } finally {
            tempZip.delete();
        }
    }

    /**
     * 清除应用数据后是否必须手动选文件夹恢复。
     * 无 SAF 写入权限但检测到备份文件时为 true。
     */
    public static boolean needsManualRestorePick(Context context) {
        return !hasWritableLocation(context) && detectBackupHint(context);
    }

    /** 尝试持久化用户所选备份文件的读取权限 */
    public static void persistBackupReadPermission(Context context, Uri uri) {
        if (uri == null) {
            return;
        }
        try {
            context.getContentResolver().takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // GET_CONTENT 部分机型不支持持久授权，单次读取仍可用
        }
        BackupLocationStore.saveFileUri(context, uri);
    }

    /** 保存用户授权的 SAF 目录 Uri，供后续备份写入 */
    public static void saveTreeUri(Context context, Uri treeUri) {
        if (treeUri == null) {
            return;
        }
        BackupLocationStore.saveTreeUri(context, treeUri);
    }

    /** 是否存在可写入的备份位置（SAF 树或 Android 10 及以下直接写文件） */
    public static boolean hasWritableLocation(Context context) {
        if (BackupLocationStore.getTreeUri(context) != null) {
            return true;
        }
        return canWriteLegacyBackupDir();
    }

    public static boolean backupExists(Context context) {
        return findLatestBackupFile() != null
                || BackupLocationStore.getTreeUri(context) != null
                || findNewestBackupUri(context) != null;
    }

    public static boolean detectBackupHint(Context context) {
        if (findLatestBackupFile() != null) {
            return true;
        }
        File dir = getBackupDir();
        for (int i = 1; i <= MAX_NUMBERED_VARIANT; i++) {
            if (new File(dir, "diary_backup (" + i + ").zip").exists()) {
                return true;
            }
        }
        return findNewestBackupUri(context) != null;
    }

    /** 统计可参与合并恢复的备份 zip 数量（不执行导入） */
    public static int countBackupSources(Context context, Uri restoreTreeUri) {
        try {
            return collectAllBackupSources(context, restoreTreeUri, null).size();
        } catch (Exception e) {
            MokeLog.e("[Backup] countBackupSources 异常", e);
            return 0;
        }
    }

    /** 导出全部日记到 zip 并写入备份目录，无日记时返回 false */
    public static boolean exportBackup(Context context) {
        try {
            DiaryDao dao = DiaryDatabase.getInstance(context).diaryDao();
            List<DiaryWithMedia> diaries = dao.getAllDiaries();
            if (diaries.isEmpty()) {
                MokeLog.d("[Backup] export 跳过：无日记");
                return false;
            }
            List<DiaryRevision> revisions = dao.getAllRevisions();
            MokeLog.d("[Backup] export 开始，日记=" + diaries.size() + "，修订=" + revisions.size());

            File tempZip = new File(context.getCacheDir(), BACKUP_FILE_NAME);
            writeZip(tempZip, diaries, revisions, context);
            if (!isValidZip(tempZip)) {
                MokeLog.e("[Backup] export 失败：zip 校验无效");
                tempZip.delete();
                return false;
            }

            boolean saved = saveBackupFile(context, tempZip);
            tempZip.delete();
            MokeLog.d("[Backup] export " + (saved ? "成功" : "失败"));
            return saved;
        } catch (Exception e) {
            MokeLog.e("[Backup] export 异常", e);
            return false;
        }
    }

    public static boolean importBackup(Context context) {
        return importAllBackups(context, null, null).success;
    }

    public static boolean importBackup(Context context, Uri backupUri) {
        List<Uri> uris = new ArrayList<>();
        uris.add(backupUri);
        return importAllBackups(context, null, uris).success;
    }

    public static boolean importBackupMerge(Context context, List<Uri> backupUris) {
        return importAllBackups(context, null, backupUris).success;
    }

    /**
     * 合并恢复：扫描所有可用来源的备份 zip，按时间从旧到新依次导入。
     * 先清空本地数据库，再用 upsert 合并，避免多份历史备份各含部分日记的问题。
     */
    public static RestoreResult importAllBackups(Context context, Uri restoreTreeUri, List<Uri> extraUris) {
        try {
            if (extraUris != null) {
                for (Uri uri : extraUris) {
                    persistBackupReadPermission(context, uri);
                }
            }
            List<BackupSource> sources = collectAllBackupSources(context, restoreTreeUri, extraUris);
            if (sources.isEmpty()) {
                MokeLog.w("[Backup] restore 失败：未找到备份来源");
                return RestoreResult.failed();
            }
            MokeLog.d("[Backup] restore 开始，发现 " + sources.size() + " 个备份文件");
            sources.sort((a, b) -> Long.compare(a.lastModified, b.lastModified));

            DiaryDatabase db = DiaryDatabase.getInstance(context);
            DiaryDao dao = db.diaryDao();
            int[] importedFiles = {0};
            db.runInTransaction(() -> {
                try {
                    dao.deleteAllRevisions();
                    dao.deleteAllMedia();
                    dao.deleteAllEntries();
                    int index = 0;
                    for (BackupSource source : sources) {
                        File tempZip = new File(context.getCacheDir(),
                                "restore_all_" + index++ + "_" + BACKUP_FILE_NAME);
                        if (!source.copyTo(context, tempZip) || !isValidZip(tempZip)) {
                            MokeLog.w("[Backup] restore 跳过无效文件：" + source.dedupeKey);
                            tempZip.delete();
                            continue;
                        }
                        importFromZip(tempZip, dao, context, false);
                        importedFiles[0]++;
                        MokeLog.d("[Backup] restore 已导入：" + source.dedupeKey);
                        tempZip.delete();
                    }
                    resetAutoIncrement(db, "diary_entries");
                    resetAutoIncrement(db, "media_attachments");
                    resetAutoIncrement(db, "diary_revisions");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            int diaryCount = dao.getAllDiaries().size();
            MokeLog.i("[Backup] restore 完成，文件=" + importedFiles[0] + "，日记=" + diaryCount);
            return new RestoreResult(diaryCount > 0, importedFiles[0], diaryCount);
        } catch (Exception e) {
            MokeLog.e("[Backup] restore 异常", e);
            return RestoreResult.failed();
        }
    }

    /** 恢复操作的结果：是否成功、合并的备份文件数、最终日记条数 */
    public static final class RestoreResult {
        public final boolean success;
        public final int backupFileCount;
        public final int diaryCount;

        RestoreResult(boolean success, int backupFileCount, int diaryCount) {
            this.success = success;
            this.backupFileCount = backupFileCount;
            this.diaryCount = diaryCount;
        }

        static RestoreResult failed() {
            return new RestoreResult(false, 0, 0);
        }
    }

    /** 防抖异步备份：App 退到后台或保存日记后延迟触发 */
    public static void exportBackupAsync(Context context) {
        Context app = context.getApplicationContext();
        synchronized (BackupManager.class) {
            if (pendingBackup != null) {
                BACKUP_HANDLER.removeCallbacks(pendingBackup);
            }
            pendingBackup = () -> {
                MokeLog.d("[Backup] 异步备份触发");
                new Thread(() -> exportBackup(app)).start();
            };
            BACKUP_HANDLER.postDelayed(pendingBackup, BACKUP_DEBOUNCE_MS);
        }
    }

    /** 从 SAF 树、传统目录、MediaStore、用户额外选择的 Uri 收集全部备份来源 */
    private static List<BackupSource> collectAllBackupSources(
            Context context, Uri restoreTreeUri, List<Uri> extraUris) {
        Map<String, BackupSource> byKey = new LinkedHashMap<>();

        for (File file : findAllLegacyBackupFiles()) {
            putBackupSource(byKey, file.getName(), BackupSource.fromFile(file));
        }

        Uri treeUri = restoreTreeUri != null
                ? restoreTreeUri
                : BackupLocationStore.getTreeUri(context);
        if (treeUri != null) {
            collectSafBackupSources(context, treeUri, byKey);
        }

        for (BackupSource source : findAllMediaStoreBackupSources(context)) {
            putBackupSource(byKey, source.dedupeKey, source);
        }

        if (extraUris != null) {
            for (Uri uri : extraUris) {
                String name = queryDisplayName(context, uri);
                String key = name != null ? name : uri.toString();
                long modified = queryLastModified(context, uri);
                putBackupSource(byKey, key, BackupSource.fromUri(uri, modified, key));
            }
        }

        MokeLog.d("[Backup] 扫描来源：" + byKey.keySet());
        return new ArrayList<>(byKey.values());
    }

    private static void collectSafBackupSources(
            Context context, Uri treeUri, Map<String, BackupSource> byKey) {
        DocumentFile folder = resolveBackupFolder(context, treeUri);
        if (folder == null) {
            return;
        }
        for (DocumentFile child : folder.listFiles()) {
            if (child.isFile() && isBackupFileName(child.getName())) {
                putBackupSource(byKey, child.getName(),
                        BackupSource.fromUri(child.getUri(), child.lastModified(), child.getName()));
            }
        }
    }

    private static List<File> findAllLegacyBackupFiles() {
        List<File> result = new ArrayList<>();
        File dir = getBackupDir();
        if (!dir.exists()) {
            return result;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return result;
        }
        for (File file : files) {
            if (file.isFile() && isBackupFileName(file.getName())) {
                result.add(file);
            }
        }
        return result;
    }

    private static List<BackupSource> findAllMediaStoreBackupSources(Context context) {
        List<BackupSource> result = new ArrayList<>();
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return result;
        }
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Files.getContentUri("external");
        String[] projection = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_MODIFIED
        };
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        String[] args = {"diary_backup%.zip", BACKUP_RELATIVE_PATH};
        String sort = MediaStore.MediaColumns.DATE_MODIFIED + " ASC";
        try (Cursor cursor = resolver.query(collection, projection, selection, args, sort)) {
            if (cursor == null) {
                return result;
            }
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                String name = cursor.getString(1);
                long modified = cursor.getLong(2) * 1000L;
                Uri uri = ContentUris.withAppendedId(collection, id);
                result.add(BackupSource.fromUri(uri, modified, name));
            }
        }
        return result;
    }

    private static void putBackupSource(
            Map<String, BackupSource> byKey, String key, BackupSource source) {
        BackupSource existing = byKey.get(key);
        if (existing == null || source.lastModified > existing.lastModified) {
            byKey.put(key, source);
        }
    }

    private static String queryDisplayName(Context context, Uri uri) {
        if (uri == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return null;
        }
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getString(0);
            }
        } catch (Exception ignored) {
            // 部分 URI 不支持查询
        }
        return null;
    }

    private static long queryLastModified(Context context, Uri uri) {
        if (uri == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            return 0L;
        }
        try (Cursor cursor = context.getContentResolver().query(
                uri, new String[]{DocumentsContract.Document.COLUMN_LAST_MODIFIED},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getLong(0);
            }
        } catch (Exception ignored) {
            // 部分 URI 不支持查询
        }
        return System.currentTimeMillis();
    }

    private static final class BackupSource {
        final Uri uri;
        final File file;
        final long lastModified;
        final String dedupeKey;

        static BackupSource fromFile(File file) {
            return new BackupSource(null, file, file.lastModified(), file.getName());
        }

        static BackupSource fromUri(Uri uri, long lastModified, String name) {
            return new BackupSource(uri, null, lastModified, name);
        }

        private BackupSource(Uri uri, File file, long lastModified, String dedupeKey) {
            this.uri = uri;
            this.file = file;
            this.lastModified = lastModified;
            this.dedupeKey = dedupeKey;
        }

        boolean copyTo(Context context, File dest) {
            try {
                if (file != null) {
                    return copyFile(file, dest);
                }
                return copyUriToFile(context, uri, dest);
            } catch (Exception e) {
                return false;
            }
        }
    }

    /** 备份写入策略：SAF 树 → 直接写文件 → MediaStore（仅 Android 10 及以下） */
    private static boolean saveBackupFile(Context context, File source) {
        Uri treeUri = BackupLocationStore.getTreeUri(context);
        if (treeUri != null && writeViaSafTree(context, treeUri, source)) {
            MokeLog.d("[Backup] 写入成功：SAF 树");
            return true;
        }
        if (writeViaFileSafe(source)) {
            cleanupStaleMediaStoreEntries(context);
            MokeLog.d("[Backup] 写入成功：传统文件路径");
            return true;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MokeLog.w("[Backup] 写入失败：Android 11+ 且无 SAF 权限");
            return false;
        }
        boolean viaMediaStore = writeViaMediaStoreReplace(context, source);
        MokeLog.d("[Backup] 写入" + (viaMediaStore ? "成功" : "失败") + "：MediaStore");
        return viaMediaStore;
    }

    private static boolean writeViaSafTree(Context context, Uri treeUri, File source) {
        try {
            DocumentFile folder = resolveBackupFolder(context, treeUri);
            if (folder == null || !folder.canWrite()) {
                return false;
            }
            removeSafBackupFiles(folder);
            DocumentFile backup = folder.createFile("application/zip", BACKUP_FILE_NAME);
            if (backup == null) {
                return false;
            }
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = context.getContentResolver().openOutputStream(backup.getUri())) {
                if (out == null) {
                    return false;
                }
                copyStream(in, out);
            }
            return isValidSafFile(context, backup.getUri());
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean writeViaFileSafe(File source) {
        if (!canWriteLegacyBackupDir()) {
            return false;
        }
        File dir = getBackupDir();
        if (!dir.exists() && !dir.mkdirs()) {
            return false;
        }
        File temp = new File(dir, BACKUP_TEMP_NAME);
        try {
            copyFile(source, temp);
            if (!isValidZip(temp)) {
                temp.delete();
                return false;
            }
            removeLegacyBackupFiles(dir, temp);
            File dest = new File(dir, BACKUP_FILE_NAME);
            if (dest.exists() && !dest.delete()) {
                return copyFile(temp, dest) && temp.delete();
            }
            if (temp.renameTo(dest)) {
                return true;
            }
            if (copyFile(temp, dest)) {
                temp.delete();
                return true;
            }
            return false;
        } catch (Exception e) {
            temp.delete();
            return false;
        }
    }

    private static boolean writeViaMediaStoreReplace(Context context, File source) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false;
        }
        try {
            Uri existing = findNewestBackupUri(context);
            if (existing != null) {
                try (InputStream in = new FileInputStream(source);
                     OutputStream out = context.getContentResolver().openOutputStream(existing, "wt")) {
                    if (out != null) {
                        copyStream(in, out);
                        return true;
                    }
                } catch (Exception ignored) {
                    // 旧条目可能已 orphan，改走 insert
                }
            }
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, BACKUP_FILE_NAME);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "application/zip");
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, BACKUP_RELATIVE_PATH);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);
            Uri uri = context.getContentResolver().insert(
                    MediaStore.Files.getContentUri("external"), values);
            if (uri == null) {
                return false;
            }
            try (InputStream in = new FileInputStream(source);
                 OutputStream out = context.getContentResolver().openOutputStream(uri)) {
                if (out == null) {
                    context.getContentResolver().delete(uri, null, null);
                    return false;
                }
                copyStream(in, out);
            }
            ContentValues publish = new ContentValues();
            publish.put(MediaStore.MediaColumns.IS_PENDING, 0);
            context.getContentResolver().update(uri, publish, null, null);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void applyInitialBackupFolderUri(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, buildBackupFolderInitialUri());
        }
    }

    private static void applyInitialDocumentsUri(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, buildDocumentsInitialUri());
        }
    }

    private static Uri buildBackupFolderInitialUri() {
        return DocumentsContract.buildDocumentUri(
                EXTERNAL_STORAGE_AUTHORITY,
                "primary:" + Environment.DIRECTORY_DOCUMENTS + "/" + BACKUP_FOLDER);
    }

    private static Uri buildDocumentsInitialUri() {
        return DocumentsContract.buildDocumentUri(
                EXTERNAL_STORAGE_AUTHORITY,
                "primary:" + Environment.DIRECTORY_DOCUMENTS);
    }

    private static boolean loadBackupFile(Context context, File dest) throws Exception {
        Uri savedFileUri = BackupLocationStore.getFileUri(context);
        if (savedFileUri != null && copyUriToFile(context, savedFileUri, dest)) {
            return true;
        }
        Uri treeUri = BackupLocationStore.getTreeUri(context);
        if (treeUri != null) {
            DocumentFile backup = findSafBackupFile(context, treeUri);
            if (backup != null && copyUriToFile(context, backup.getUri(), dest)) {
                return true;
            }
        }
        File latest = findLatestBackupFile();
        if (latest != null && copyFile(latest, dest)) {
            return true;
        }
        Uri mediaUri = findNewestBackupUri(context);
        if (mediaUri != null) {
            return copyUriToFile(context, mediaUri, dest);
        }
        return false;
    }

    private static DocumentFile findSafBackupFile(Context context, Uri treeUri) {
        DocumentFile folder = resolveBackupFolder(context, treeUri);
        if (folder == null) {
            return null;
        }
        DocumentFile newest = null;
        for (DocumentFile child : folder.listFiles()) {
            if (!child.isFile() || !isBackupFileName(child.getName())) {
                continue;
            }
            if (newest == null || child.lastModified() > newest.lastModified()) {
                newest = child;
            }
        }
        return newest;
    }

    private static DocumentFile findOrCreateFolder(DocumentFile root, String name) {
        DocumentFile existing = root.findFile(name);
        if (existing != null && existing.isDirectory()) {
            return existing;
        }
        return root.createDirectory(name);
    }

    private static DocumentFile resolveBackupFolder(Context context, Uri treeUri) {
        DocumentFile root = DocumentFile.fromTreeUri(context, treeUri);
        if (root == null) {
            return null;
        }
        if (BACKUP_FOLDER.equals(root.getName())) {
            return root;
        }
        return findOrCreateFolder(root, BACKUP_FOLDER);
    }

    private static void removeSafBackupFiles(DocumentFile folder) {
        for (DocumentFile child : folder.listFiles()) {
            if (child.isFile() && isBackupFileName(child.getName())) {
                child.delete();
            }
        }
    }

    private static void removeLegacyBackupFiles(File dir, File keep) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.equals(keep)) {
                continue;
            }
            if (file.isFile() && isBackupFileName(file.getName())) {
                file.delete();
            }
        }
    }

    private static void cleanupStaleMediaStoreEntries(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return;
        }
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Files.getContentUri("external");
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        String[] args = {"diary_backup%.zip", BACKUP_RELATIVE_PATH};
        try (Cursor cursor = resolver.query(collection,
                new String[]{MediaStore.Files.FileColumns._ID}, selection, args, null)) {
            if (cursor == null) {
                return;
            }
            while (cursor.moveToNext()) {
                long id = cursor.getLong(0);
                resolver.delete(ContentUris.withAppendedId(collection, id), null, null);
            }
        }
    }

    private static Uri findNewestBackupUri(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return null;
        }
        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Files.getContentUri("external");
        String[] projection = {
                MediaStore.Files.FileColumns._ID,
                MediaStore.MediaColumns.DATE_MODIFIED
        };
        String selection = MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ? AND "
                + MediaStore.MediaColumns.RELATIVE_PATH + "=?";
        String[] args = {"diary_backup%.zip", BACKUP_RELATIVE_PATH};
        String sort = MediaStore.MediaColumns.DATE_MODIFIED + " DESC";
        try (Cursor cursor = resolver.query(collection, projection, selection, args, sort)) {
            if (cursor != null && cursor.moveToFirst()) {
                long id = cursor.getLong(0);
                return ContentUris.withAppendedId(collection, id);
            }
        }
        return null;
    }

    private static File findLatestBackupFile() {
        File dir = getBackupDir();
        File newest = null;
        File canonical = new File(dir, BACKUP_FILE_NAME);
        if (canonical.exists()) {
            newest = canonical;
        }
        for (int i = 1; i <= MAX_NUMBERED_VARIANT; i++) {
            File candidate = new File(dir, "diary_backup (" + i + ").zip");
            if (!candidate.exists()) {
                continue;
            }
            if (newest == null || candidate.lastModified() > newest.lastModified()) {
                newest = candidate;
            }
        }
        File[] listed = dir.listFiles();
        if (listed != null) {
            for (File file : listed) {
                if (!file.isFile() || !isBackupFileName(file.getName())) {
                    continue;
                }
                if (newest == null || file.lastModified() > newest.lastModified()) {
                    newest = file;
                }
            }
        }
        return newest;
    }

    private static boolean isBackupFileName(String name) {
        if (name == null) {
            return false;
        }
        if (BACKUP_FILE_NAME.equals(name) || BACKUP_TEMP_NAME.equals(name)) {
            return true;
        }
        return name.startsWith("diary_backup") && name.endsWith(".zip");
    }

    private static boolean canWriteLegacyBackupDir() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return false;
        }
        File dir = getBackupDir();
        if (!dir.exists() && !dir.mkdirs()) {
            return false;
        }
        File probe = new File(dir, ".write_probe");
        try {
            if (!probe.createNewFile()) {
                return probe.exists() && probe.canWrite();
            }
            probe.delete();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isValidZip(File file) {
        if (file == null || !file.exists() || file.length() < 32) {
            return false;
        }
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(file)))) {
            ZipEntry entry = zis.getNextEntry();
            return entry != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isValidSafFile(Context context, Uri uri) {
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) {
                return false;
            }
            ZipInputStream zis = new ZipInputStream(new BufferedInputStream(in));
            ZipEntry entry = zis.getNextEntry();
            return entry != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean copyUriToFile(Context context, Uri uri, File dest) {
        try (InputStream in = context.getContentResolver().openInputStream(uri);
             OutputStream out = new FileOutputStream(dest)) {
            if (in == null) {
                return false;
            }
            copyStream(in, out);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static File getBackupDir() {
        return new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), BACKUP_FOLDER);
    }

    private static void writeZip(File zipFile,
                                 List<DiaryWithMedia> diaries,
                                 List<DiaryRevision> revisions,
                                 Context context) throws Exception {
        JSONObject root = new JSONObject();
        root.put("version", BACKUP_VERSION);
        root.put("exportedAt", System.currentTimeMillis());

        JSONArray entryArray = new JSONArray();
        JSONArray mediaArray = new JSONArray();
        for (DiaryWithMedia diary : diaries) {
            entryArray.put(entryToJson(diary.entry));
            if (diary.mediaList != null) {
                for (MediaAttachment media : diary.mediaList) {
                    mediaArray.put(mediaToJson(media));
                }
            }
        }
        root.put("entries", entryArray);
        root.put("media", mediaArray);

        JSONArray revisionArray = new JSONArray();
        for (DiaryRevision revision : revisions) {
            revisionArray.put(revisionToJson(revision));
        }
        root.put("revisions", revisionArray);
        root.put("security", PasswordManager.exportSecurityJson(context));

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            addZipEntry(zos, "backup.json", root.toString(2).getBytes(StandardCharsets.UTF_8));
            if (diaries != null) {
                for (DiaryWithMedia diary : diaries) {
                    if (diary.mediaList == null) {
                        continue;
                    }
                    for (MediaAttachment media : diary.mediaList) {
                        File file = new File(media.filePath);
                        if (file.exists()) {
                            addZipFileEntry(zos, "media/" + mediaArchiveName(media), file);
                        }
                    }
                }
            }
        }
    }

    private static int countEntriesInZip(File zipFile) {
        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("backup.json".equals(entry.getName())) {
                    JSONObject root = new JSONObject(readStream(zis));
                    return root.getJSONArray("entries").length();
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            return -1;
        }
        return -1;
    }

    /**
     * 从 zip 导入数据。
     *
     * @param replaceAll true 时先清空表再插入；false 时用 upsert 合并（多备份恢复）
     */
    private static void importFromZip(File zipFile, DiaryDao dao, Context context, boolean replaceAll)
            throws Exception {
        JSONObject root = null;
        java.util.Map<String, byte[]> mediaFiles = new java.util.HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(new FileInputStream(zipFile)))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("backup.json".equals(entry.getName())) {
                    root = new JSONObject(readStream(zis));
                } else if (entry.getName() != null && entry.getName().startsWith("media/")) {
                    mediaFiles.put(entry.getName(), readStreamBytes(zis));
                }
                zis.closeEntry();
            }
        }

        if (root == null) {
            throw new IllegalStateException("Invalid backup file");
        }

        if (replaceAll) {
            dao.deleteAllRevisions();
            dao.deleteAllMedia();
            dao.deleteAllEntries();
        }

        JSONArray entries = root.getJSONArray("entries");
        for (int i = 0; i < entries.length(); i++) {
            DiaryEntry diaryEntry = jsonToEntry(entries.getJSONObject(i));
            if (replaceAll) {
                dao.insertDiary(diaryEntry);
            } else {
                dao.upsertDiary(diaryEntry);
            }
        }

        JSONArray revisions = root.getJSONArray("revisions");
        for (int i = 0; i < revisions.length(); i++) {
            DiaryRevision revision = jsonToRevision(revisions.getJSONObject(i));
            if (replaceAll) {
                dao.insertRevision(revision);
            } else {
                dao.upsertRevision(revision);
            }
        }

        JSONArray mediaItems = root.getJSONArray("media");
        for (int i = 0; i < mediaItems.length(); i++) {
            MediaAttachment media = jsonToMedia(mediaItems.getJSONObject(i));
            byte[] bytes = mediaFiles.get("media/" + mediaArchiveName(media));
            if (bytes != null) {
                restoreMediaFile(context, media, bytes);
            }
            if (replaceAll) {
                dao.insertMedia(media);
            } else {
                dao.upsertMedia(media);
            }
        }

        if (root.has("security")) {
            PasswordManager.importSecurityJson(context, root.getJSONObject("security"));
        }
    }

    private static void restoreMediaFile(Context context, MediaAttachment media, byte[] bytes) throws Exception {
        File dest = new File(media.filePath);
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (FileOutputStream out = new FileOutputStream(dest)) {
            out.write(bytes);
        }
    }

    private static String mediaArchiveName(MediaAttachment media) {
        return media.diaryId + "_" + media.id + "_" + media.fileName;
    }

    private static JSONObject entryToJson(DiaryEntry entry) throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", entry.id);
        json.put("title", entry.title);
        json.put("content", entry.content);
        json.put("mood", entry.mood);
        json.put("backgroundColor", entry.backgroundColor);
        json.put("encrypted", entry.encrypted);
        json.put("createdAt", entry.createdAt);
        json.put("updatedAt", entry.updatedAt);
        return json;
    }

    private static DiaryEntry jsonToEntry(JSONObject json) throws Exception {
        DiaryEntry entry = new DiaryEntry();
        entry.id = json.getLong("id");
        entry.title = json.optString("title", "");
        entry.content = json.optString("content", "");
        entry.mood = json.optString("mood", "NEUTRAL");
        entry.backgroundColor = json.optInt("backgroundColor", 0xFFFFFFFF);
        entry.encrypted = json.optBoolean("encrypted", false);
        entry.createdAt = json.optLong("createdAt", System.currentTimeMillis());
        entry.updatedAt = json.optLong("updatedAt", entry.createdAt);
        return entry;
    }

    private static JSONObject mediaToJson(MediaAttachment media) throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", media.id);
        json.put("diaryId", media.diaryId);
        json.put("filePath", media.filePath);
        json.put("mediaType", media.mediaType);
        json.put("fileName", media.fileName);
        json.put("addedAt", media.addedAt);
        return json;
    }

    private static MediaAttachment jsonToMedia(JSONObject json) throws Exception {
        MediaAttachment media = new MediaAttachment();
        media.id = json.getLong("id");
        media.diaryId = json.getLong("diaryId");
        media.filePath = json.optString("filePath", "");
        media.mediaType = json.optString("mediaType", "IMAGE");
        media.fileName = json.optString("fileName", "file.dat");
        media.addedAt = json.optLong("addedAt", System.currentTimeMillis());
        return media;
    }

    private static JSONObject revisionToJson(DiaryRevision revision) throws Exception {
        JSONObject json = new JSONObject();
        json.put("id", revision.id);
        json.put("diaryId", revision.diaryId);
        json.put("title", revision.title);
        json.put("content", revision.content);
        json.put("mood", revision.mood);
        json.put("backgroundColor", revision.backgroundColor);
        json.put("revisedAt", revision.revisedAt);
        json.put("changeLog", revision.changeLog);
        return json;
    }

    private static DiaryRevision jsonToRevision(JSONObject json) throws Exception {
        DiaryRevision revision = new DiaryRevision();
        revision.id = json.getLong("id");
        revision.diaryId = json.getLong("diaryId");
        revision.title = json.optString("title", "");
        revision.content = json.optString("content", "");
        revision.mood = json.optString("mood", "NEUTRAL");
        revision.backgroundColor = json.optInt("backgroundColor", 0xFFFFFFFF);
        revision.revisedAt = json.optLong("revisedAt", System.currentTimeMillis());
        revision.changeLog = json.optString("changeLog", "");
        return revision;
    }

    private static void addZipEntry(ZipOutputStream zos, String name, byte[] data) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(data);
        zos.closeEntry();
    }

    private static void addZipFileEntry(ZipOutputStream zos, String name, File file) throws Exception {
        zos.putNextEntry(new ZipEntry(name));
        try (InputStream in = new FileInputStream(file)) {
            copyStream(in, zos);
        }
        zos.closeEntry();
    }

    private static String readStream(InputStream in) throws Exception {
        return new String(readStreamBytes(in), StandardCharsets.UTF_8);
    }

    private static byte[] readStreamBytes(InputStream in) throws Exception {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        copyStream(in, buffer);
        return buffer.toByteArray();
    }

    private static void copyStream(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private static boolean copyFile(File source, File dest) throws Exception {
        File parent = dest.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (InputStream in = new FileInputStream(source);
             OutputStream out = new FileOutputStream(dest)) {
            copyStream(in, out);
        }
        return true;
    }

    /** 恢复后重置 SQLite 自增序列，避免新插入 ID 与已恢复 ID 冲突 */
    private static void resetAutoIncrement(DiaryDatabase db, String tableName) {
        db.getOpenHelper().getWritableDatabase().execSQL(
                "INSERT OR REPLACE INTO sqlite_sequence (name, seq) "
                        + "VALUES ('" + tableName + "', "
                        + "(SELECT IFNULL(MAX(id), 0) FROM " + tableName + "))");
    }
}

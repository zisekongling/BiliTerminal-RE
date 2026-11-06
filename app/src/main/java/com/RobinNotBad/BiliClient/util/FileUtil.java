package com.RobinNotBad.BiliClient.util;

import com.RobinNotBad.BiliClient.util.UiText;
import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import com.RobinNotBad.BiliClient.BiliTerminal;
import com.RobinNotBad.BiliClient.R;
import com.RobinNotBad.BiliClient.service.download.DownloadPathSpec;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.Objects;

//用于清除缓存，因为glide实际上会往本地存不少缩略图，时间一长就会爆炸
//清除调用我放在了每次刷新推荐页

public class FileUtil {

    /** 成品备份后缀，与 DownloadService.safeReplaceTemp 里拼接的 ".bak" 必须一致 */
    private static final String BAK_SUFFIX = ".bak";

    /**
     * 进程级降级标记：正式目录写不进去时，真正写入落到应用专属目录（见 [resolveVideoDownloadDir]）。
     *
     * 为什么必须是进程级而不是每次现算：下载服务、缓存列表、元数据读写都各自调
     * [getVideoDownloadPath]，它们必须看到同一个根目录，否则文件会一半在正式目录、
     * 一半在降级目录，列表与删除逻辑全部对不上。
     */
    private static volatile boolean videoFallbackActive = false;

    /** 图片下载（公共 Pictures/）的同类标记。 */
    private static volatile boolean pictureFallbackActive = false;

    public static void clearCache(Context context) {
        File cacheDir = context.getCacheDir();
        if (cacheDir.exists() && Objects.requireNonNull(cacheDir.listFiles()).length != 0)
            deleteFolder(cacheDir);
        Log.e("debug", "清除了缓存");
    }

    /** 递归删除的深度上限：缓存目录最多「根/标题/分P/字幕」四层，32 层只是防符号链接套娃死循环。 */
    private static final int DELETE_MAX_DEPTH = 32;

    /**
     * 递归删除目录/文件，兼容旧调用点、**忽略删除结果**。
     *
     * 新代码（尤其"用户点了删除要立刻知道成不成"的地方）请用 [deleteFolderRecursively]：
     * 本方法存在只是为了让旧调用点（视频详情页清缓存 / 下载列表 / 分 P 选择页等）不改签名，
     * 语义已换成同一套可靠实现。
     */
    public static void deleteFolder(File folder) {
        deleteFolderRecursively(folder);
    }

    /**
     * 递归删除目录/文件，并**返回是否真的删干净**（反馈 #58 的核心之一）。
     *
     * 旧实现有三个坑，合并起来就是「点了删除、提示成功、文件还在」：
     *
     * 1. **空子目录永远删不掉**：子目录只在 `listFiles().length != 0` 时才递归，空目录被跳过，
     *    于是父目录 `delete()` 必然失败（目录非空），整个任务目录原地留下。缓存视频目录里
     *    出现空子目录很常见（`subtitles/` 建了没下到字幕、删到一半中断）；
     * 2. **失败被吞掉**：所有 `delete()` 的 false 返回值都没人看，`listFiles()` 返回 null 时
     *    还会 NPE（`assert` 在 ART 上默认关闭，等于没有），异常被线程池兜住只留日志；
     * 3. **调用方只能猜结果**：签名是 void，所以界面永远提示「删除成功」——用户看到的正是
     *    「视频没消失、文件也没删」。
     *
     * 现在：逐层删、空目录也删、单层失败不影响继续清理其它子项，最后按「目标是否已不存在」
     * 给结论；删不掉的项（Android 11+ 公共目录文件不属于本应用、SD 卡只读、文件系统错误）
     * 会原样留着，由调用方明确提示用户。
     *
     * @param folder 文件或目录；null / 不存在都视为「已经删掉」
     * @return true 表示调用后该路径确实不存在了
     */
    public static boolean deleteFolderRecursively(File folder) {
        return deletePath(folder, 0);
    }

    private static boolean deletePath(File file, int depth) {
        if (file == null) return false;
        try {
            if (!file.exists()) return true;   // 并发删掉了 / 本来就没有
            if (depth > DELETE_MAX_DEPTH) {
                logDeleteFailure(file, "目录层级超过 " + DELETE_MAX_DEPTH + " 层，疑似符号链接成环");
                return false;
            }

            boolean childrenOk = true;
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        // 关键：不管这一项是不是空目录都要进去删，否则父目录永远删不掉
                        if (!deletePath(child, depth + 1)) childrenOk = false;
                    }
                } else if (file.exists()) {
                    // listFiles() 为 null 说明目录不可读（权限/IO 错误），里面的内容删不掉
                    childrenOk = false;
                }
            }

            boolean deleted = file.delete();
            if (!deleted) {
                // delete() 返回 false 但目标已经不在了（被别的线程删掉）也算成功
                deleted = !file.exists();
            }
            if (!deleted) logDeleteFailure(file, childrenOk ? "delete() 返回 false" : "目录不可读或子项删不掉");
            return deleted;
        } catch (Throwable t) {
            // SecurityException / 只读文件系统 / 路径被并发改掉都按「删不掉」处理，不向上抛
            logDeleteFailure(file, t.toString());
            return false;
        }
    }

    /** 删除失败时记一条日志。**日志本身失败不能影响删除结果**（单测跑在 JVM 上，android.util.Log 是桩）。 */
    private static void logDeleteFailure(File file, String reason) {
        try {
            Log.w("file-delete", "删除失败：" + file + "（" + reason + "）");
        } catch (Throwable ignored) {
        }
    }

    /**
     * 只清理下载过程中的临时产物，绝不删除已下好的成品。
     *
     * 背景：下载服务退出且批次非正常结束时，此前调用 deleteFolder(整个任务目录)，
     * 而单 P 任务的目录就是 &lt;下载根&gt;/&lt;标题&gt; 本身，会把上一次成功下载好的
     * video.mp4 / audio.m4a / 封面 / 弹幕一起删掉 —— 真实数据丢失。
     *
     * 因此这里语义收紧为"只收拾本次下载自己产生的中间文件"：
     * - video_new.mp4 / audio_new.m4a：safeReplaceTemp 的下载临时文件，直接删；
     * - video_merging.tmp：DASH 音视频合并的临时产物（见 MediaMerger），合并成功才替换 video.mp4，
     *   中断留下的半成品直接删（原始 video.mp4 / audio.m4a 还在，双文件仍可播放）；
     * - *.bak：safeReplaceTemp 替换正式文件前的备份。**正式文件存在**时它只是替换成功后没删掉的
     *   备份，可以删；**正式文件不存在而 .bak 存在**说明进程在"旧成品改名成 .bak"之后、
     *   "临时文件改名成正式文件"之前被回收了，此时 .bak 就是当前唯一的成品，必须改名回正式文件名，
     *   绝不能删（否则重新下载失败一次就永久丢掉旧视频）；
     * - .DOWNLOADING：本任务的"下载中"标记，任务已确认为非正常结束，必须摘掉，
     *   否则本地列表会把残留的旧成品误判为"正在下载"，用户也就看不到本地缓存的视频。
     * 一旦任务目录中的上述文件都不存在（下载尚未进入写临时文件阶段就失败/退出），本方法不会留任何痕迹。
     *
     * 只处理任务目录的直接子项、不递归：合集视频的每个分 P 是独立子目录、各有自己的任务记录，
     * 递归会误伤其他任务正在写的文件。
     *
     * @param folder 任务目录（即 DownloadSection.getPath() 的返回值）
     */
    public static void cleanDownloadTempFiles(File folder) {
        if (folder == null || !folder.exists()) return;
        File[] children = folder.listFiles();
        if (children == null) return;
        for (File file : children) {
            if (!file.isFile()) continue;   // 只认文件，不递归，避免误伤同名子目录
            String name = file.getName();
            if (name.endsWith(BAK_SUFFIX)) {
                String finalName = name.substring(0, name.length() - BAK_SUFFIX.length());
                if (finalName.isEmpty()) continue;
                File finalFile = new File(folder, finalName);
                if (!finalFile.exists()) {
                    // 替换在两次 rename 之间被打断：.bak 是唯一成品，改名回正式文件名（恢复）
                    file.renameTo(finalFile);
                } else {
                    // 正式文件在，.bak 只是替换成功后没删掉的备份
                    file.delete();
                }
                continue;
            }
            if (name.equals(DownloadPathSpec.FILE_VIDEO_TMP) || name.equals(DownloadPathSpec.FILE_AUDIO_TMP)
                    || name.equals(DownloadPathSpec.FILE_VIDEO_MERGING)) {
                file.delete();
            }
        }
        File downloadingMark = new File(folder, DownloadPathSpec.FILE_DOWNLOADING);
        if (downloadingMark.exists()) downloadingMark.delete();
    }

    public static String readString(File file) {
        if (file == null || !file.exists() || !file.canRead() || !file.isFile()) return null;
        // try-with-resources：旧实现手工 new FileInputStream 从不关闭，readString 是配置/主题/JSON 的
        // 通用读取入口，热路径反复调用会持续泄漏 FileDescriptor（minSdk 19 的老手表 fd 上限很紧）。
        // 返回语义与异常吞掉策略保持不变（失败仍返回 null），编码仍走 outputStream.toString() 的平台默认。
        try (FileInputStream inputStream = new FileInputStream(file)) {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            FileChannel channel = inputStream.getChannel();
            ByteBuffer buffer = ByteBuffer.allocate(1 << 13);
            int i;
            while ((i = channel.read(buffer)) != -1) {
                buffer.flip();
                outputStream.write(buffer.array(), 0, i);
                buffer.clear();
            }
            return outputStream.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 真的到目标目录里试写一个临时文件，用「能不能写」代替「有没有授权」。
     *
     * <p>为什么不再只看 `Environment.isExternalStorageManager()`（26.10.18 改）：
     *
     * <ul>
     *   <li>默认下载目录是 `&lt;外部存储&gt;/Android/media/&lt;包名&gt;/`，属**应用专属外部存储**，
     *       Android 10+ 写这里本来就不需要任何权限，却被授权判据一律判成「没权限」——等于每次下载都
     *       先把用户顶到系统设置页；</li>
     *   <li>手表 ROM 普遍没有「所有文件访问」设置页（issue #42：小米手表 M2505W1 / SDK 34），
     *       用户**永远授不到**这个权限，于是默认路径下的下载被永久挡死。26.10.14 只修了「点设置页崩」
     *       （见 [requestStoragePermission]），挡死的语义没有动。</li>
     * </ul>
     *
     * <p>改成试写后两种情形都对：默认目录直接放行；用户把保存路径改到公共目录、或下载图片到
     * `Pictures/` 时，试写会失败，才去要权限。API 29 及以下同理——旧运行时权限没给时试写也失败。
     *
     * @param dir 目标目录（不存在则尝试创建）
     * @return 该目录眼下是否真的能创建文件
     */
    public static boolean canWriteDirectory(File dir) {
        if (dir == null) return false;
        try {
            if (!dir.exists() && !dir.mkdirs()) return false;
            if (!dir.isDirectory()) return false;
            // 用 createTempFile 而不是固定文件名：并发下载时多个任务可能同时探测同一个根目录，
            // 固定名会互相把对方刚建的文件当成「已存在」误判成不可写。
            File probe = File.createTempFile(".write_probe", null, dir);
            boolean deleted = probe.delete();
            if (!deleted) Log.w("storage-perm", "探测文件删不掉：" + probe);
            return true;
        } catch (Throwable t) {
            // SecurityException / 只读文件系统 / 目录被并发删除 都归为「写不了」
            return false;
        }
    }

    /**
     * 保证目标目录可写：能写就直接放行，写不了才去要权限。
     *
     * @param activity 用于拉起授权页的 Activity
     * @param dir      调用方真正要写入的目录（**必须传实际目标**，别一律传下载根目录）
     * @return true 表示现在就能写；false 表示已经尝试拉起授权页/弹过提示，调用方应当停下，
     * 等用户授权后重新点一次
     */
    public static boolean ensureDirectoryWritable(Activity activity, File dir) {
        if (canWriteDirectory(dir)) return true;
        requestStoragePermission(activity);
        return false;
    }

    /**
     * 跳系统设置申请「所有文件访问权限」（Android 11+）。
     *
     * <p>26.10.14 线上崩溃：小米手表（M2505W1 / SDK 34）上 `ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION`
     * **没有对应的 Activity**，`startActivity` 抛 `ActivityNotFoundException`，异常冒到 `ActivityThread`
     * 把发起页整个带崩（现场：`Unable to start activity ComponentInfo{…}`）。
     * 手表 ROM 普遍不带完整设置应用，这个 action 缺失是常态，所以必须兜底而不是直接 startActivity。
     *
     * <p>三级降级：① 带包名直达本应用页面 → ② 通用的「所有文件访问」列表页 → ③ 只弹提示。
     * 详见 docs/review/fix-progress.md §3.15。
     */
    public static void requestStoragePermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (startSettings(activity, new Intent(
                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName())))) {
                return;
            }
            if (startSettings(activity, new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))) {
                return;
            }
            MsgUtil.showMsgLong(UiText.of("此设备打不开系统的「所有文件访问」设置页，请手动到 设置 → 应用 → 权限 里授予本应用存储权限"));
        } else {
            // Android 10 及以下：请求旧存储权限
            ActivityCompat.requestPermissions(activity, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 100);
        }
    }

    /**
     * 试着拉起一个系统设置页。
     *
     * <p>吞掉所有异常而不是只吞 `ActivityNotFoundException`：部分 ROM 该 Activity 存在但未导出，
     * 抛的是 `SecurityException`；两类都属于「这台设备去不了这个页面」，处理方式相同。
     *
     * @return 是否成功拉起
     */
    private static boolean startSettings(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
            return true;
        } catch (Exception e) {
            Log.w("storage-perm", "设置页拉不起来：" + intent.getAction() + " -> " + e.getClass().getSimpleName());
            return false;
        }
    }

    /**
     * 当前进程**实际写入**的视频缓存根目录。
     *
     * 正常情况下等于用户配置/按 API 分档的默认目录（见 [getConfiguredVideoDownloadPath]）；
     * 一旦 [resolveVideoDownloadDir] 判定正式目录不可写并降级，这里返回应用专属降级目录。
     * 读列表、读写元数据、下载落盘全部走这个方法，所以降级后所有链路看到的是同一个根目录。
     */
    public static File getVideoDownloadPath() {
        File path = videoFallbackActive ? getFallbackVideoDownloadPath() : getConfiguredVideoDownloadPath();
        applyNoMedia(path);
        return path;
    }

    public static File getVideoDownloadPath(String title, String child) {
        File parentFolder = new File(getVideoDownloadPath(), stringToFile(title));
        if (child == null || child.isEmpty()) return parentFolder;
        return new File(parentFolder, stringToFile(child));
    }

    /**
     * 用户配置的视频缓存目录（没配置过就是按 API 分档的默认目录），**不含运行时降级**。
     *
     * 设置页展示、以及 [resolveVideoDownloadDir] 做「正式目录可不可写」判断时用它；
     * 具体写入请用 [getVideoDownloadPath]。
     */
    public static File getConfiguredVideoDownloadPath() {
        String mediaDir = legacyMediaDir();
        String defaultPath = StoragePathPolicy.defaultVideoDir(
                Build.VERSION.SDK_INT, mediaDir, appExternalPath("video"), internalPath("video"));
        String configured = SharedPreferencesUtil.getString(SettingsKeys.SAVE_PATH_VIDEO, "");
        boolean legacyHasContent = StoragePathPolicy.hasRealContent(listNames(new File(mediaDir)));
        return new File(StoragePathPolicy.chooseVideoDir(configured, mediaDir, legacyHasContent, defaultPath));
    }

    /**
     * 视频缓存的降级目录：应用专属外部目录（任何版本、任何设备都免权限），
     * 取不到外部存储时退到内部 `filesDir/video`。
     */
    public static File getFallbackVideoDownloadPath() {
        if (BiliTerminal.context == null) return new File(legacyMediaDir());
        File external = BiliTerminal.context.getExternalFilesDir("video");
        if (external != null) return external;
        return new File(BiliTerminal.context.getFilesDir(), "video");
    }

    /** 当前是否正在使用降级目录（缓存列表用它决定「目录切换后要不要重新加载」）。 */
    public static boolean isVideoFallbackActive() {
        return videoFallbackActive;
    }

    /**
     * 决定本次该往哪个视频目录写：正式目录能写就用它；写不进去就降级到应用专属目录，**不再挡死下载**。
     *
     * 这是 issue #42 / Wear OS 的核心修正：以前「目录不可写」就直接跳系统设置页并返回 false，
     * 而手表 ROM 根本没有「所有文件访问」设置页，用户永远授不到权限 → 下载永久失败。
     * 现在：
     * - 默认目录本身免权限（API 21+ 的 Android/media/&lt;包名&gt;、低版本的 getExternalFilesDir）；
     * - 用户把路径改到公共目录、或下载图片到公共 Pictures/ 时，写不进去只降级不阻塞；
     * - 正式目录恢复可写（例如用户去手机设置里授了权）后下一次调用会自动切回，并把降级期间
     *   写下的缓存搬回正式目录，避免「缓存突然消失」。
     *
     * @return 实际可写的目录；连应用专属目录都写不进去（存储满/只读）时返回 null
     */
    public static File resolveVideoDownloadDir(Context context) {
        File configured = getConfiguredVideoDownloadPath();
        File fallback = getFallbackVideoDownloadPath();
        boolean wasFallback = videoFallbackActive;

        if (canWriteDirectory(configured)) {
            if (wasFallback) {
                videoFallbackActive = false;
                int moved = mergeMoveDirectoryContents(fallback, configured);
                Logu.d("storage-perm", "正式目录恢复可写，搬回 " + moved + " 项缓存 -> " + configured);
            }
            return configured;
        }

        if (canWriteDirectory(fallback)) {
            videoFallbackActive = true;
            if (!wasFallback) showStorageMsg(context, R.string.msg_storage_fallback, fallback.getAbsolutePath());
            return fallback;
        }

        videoFallbackActive = false;
        showStorageMsg(context, R.string.msg_storage_unavailable);
        return null;
    }

    /**
     * 用户配置的图片下载目录（设置页、图片查看器把目标传给下载页时用）。
     *
     * <p>语义与 [getConfiguredVideoDownloadPath] 对齐：**只回答配置/默认，不含运行时降级**——
     * 设置页展示用这个；真正写入前还要经过 [resolvePictureDownloadDir] 的可写性判断。
     *
     * <p>26.10.24 安全审计 P2-2：默认目录不再无条件取公共 `Pictures/`。Android 10+ 上
     * 裸 File 写公共目录必须拿「所有文件访问」权限（手表 ROM 根本没有这个设置页，
     * issue #42），为了一张图要求全盘权限不成比例；所以 10+ 默认落应用专属目录
     * （免权限），下载完成后再由 [PictureMediaStore.publishToGallery] 发布进相册。
     */
    public static File getPicturePath() {
        return getConfiguredPictureDownloadPath();
    }

    /** 用户配置的图片目录；没配置（或配成空串）时返回按 API 分档的默认目录。 */
    public static File getConfiguredPictureDownloadPath() {
        String configured = PictureSavePolicy.cleanConfiguredPath(
                SharedPreferencesUtil.getString(SettingsKeys.SAVE_PATH_PICTURES, ""));
        if (configured != null) return new File(configured);
        return getDefaultPictureDownloadPath();
    }

    /**
     * 默认图片目录：Android 10（API 29）及以上用应用专属目录（免权限），更老的系统沿用
     * 公共 `Pictures/哔哩终端/`（旧系统的存储权限安装即授予，不存在全盘权限问题）。
     */
    @SuppressWarnings("deprecation")
    public static File getDefaultPictureDownloadPath() {
        if (PictureSavePolicy.usesAppPrivateDefault(Build.VERSION.SDK_INT)) {
            return getFallbackPictureDownloadPath();
        }
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                PictureSavePolicy.PUBLIC_PICTURE_SUBDIR);
    }

    /** 图片下载的降级目录（应用专属，免权限）。 */
    public static File getFallbackPictureDownloadPath() {
        if (BiliTerminal.context == null) {
            return new File(Environment.getExternalStorageDirectory(), "Android/media/com.RobinNotBad.BiliClient/Pictures");
        }
        File external = BiliTerminal.context.getExternalFilesDir("Pictures");
        if (external != null) return external;
        return new File(BiliTerminal.context.getFilesDir(), "Pictures");
    }

    /** 当前图片是否写进了降级目录（下载完成提示据它换文案：降级目录不在系统相册里）。 */
    public static boolean isPictureFallbackActive() {
        return pictureFallbackActive;
    }

    /**
     * 图片下载目标目录的降级判断，语义与 [resolveVideoDownloadDir] 一致。
     *
     * @param configured 调用方原本要写的目录（一般是 [getPicturePath]）
     * @return 实际可写的目录；都写不进去时返回 null
     */
    public static File resolvePictureDownloadDir(Context context, File configured) {
        File fallback = getFallbackPictureDownloadPath();
        boolean wasFallback = pictureFallbackActive;

        if (canWriteDirectory(configured)) {
            if (wasFallback) {
                pictureFallbackActive = false;
                int moved = mergeMoveDirectoryContents(fallback, configured);
                Logu.d("storage-perm", "图片正式目录恢复可写，搬回 " + moved + " 项 -> " + configured);
            }
            return configured;
        }

        if (canWriteDirectory(fallback)) {
            pictureFallbackActive = true;
            if (!wasFallback) showStorageMsg(context, R.string.msg_picture_fallback, fallback.getAbsolutePath());
            return fallback;
        }

        pictureFallbackActive = false;
        showStorageMsg(context, R.string.msg_storage_unavailable);
        return null;
    }

    /** 老默认目录：`<外部存储>/Android/media/<包名>/`（API 21+ 免权限，也是历史默认值）。 */
    private static String legacyMediaDir() {
        String packageName = BiliTerminal.context != null
                ? BiliTerminal.context.getPackageName()
                : "com.RobinNotBad.BiliClient";
        return Environment.getExternalStorageDirectory() + "/Android/media/" + packageName + "/";
    }

    private static String appExternalPath(String child) {
        if (BiliTerminal.context == null) return null;
        File dir = BiliTerminal.context.getExternalFilesDir(child);
        return dir != null ? dir.getAbsolutePath() : null;
    }

    private static String internalPath(String child) {
        if (BiliTerminal.context == null) return null;
        return new File(BiliTerminal.context.getFilesDir(), child).getAbsolutePath();
    }

    private static java.util.List<String> listNames(File dir) {
        try {
            String[] names = dir.isDirectory() ? dir.list() : null;
            return names != null ? java.util.Arrays.asList(names) : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 与相册隔离用的 `.nomedia` 维护。
     *
     * 注意旧实现是 `if (ban && !exists) create; else if (exists) delete;` —— ban 开着且文件已存在时
     * 会把它删掉，等于每次调用都在「建/删」之间来回切。这里按语义写成两个独立分支。
     */
    private static void applyNoMedia(File path) {
        try {
            boolean banGallery = SharedPreferencesUtil.getBoolean(SettingsKeys.SAVE_BAN_GALLERY, true);
            File nomedia = new File(path, ".nomedia");
            if (banGallery && !nomedia.exists()) nomedia.createNewFile();
            else if (!banGallery && nomedia.exists()) nomedia.delete();
        } catch (Exception ignored) {
        }
    }

    private static void showStorageMsg(Context context, int resId, Object... args) {
        if (context == null) return;
        try {
            MsgUtil.showMsgLong(context.getString(resId, args));
        } catch (Throwable t) {
            Log.w("storage-perm", "提示文案获取失败：" + t);
        }
    }

    /**
     * 把 `from` 目录里的内容**合并搬进** `to`，绝不覆盖 `to` 里已有文件。
     *
     * 只用于「降级目录 → 正式目录」这一个恢复动作：降级期间写下的缓存不能因为目录切回正式目录
     * 就从列表里消失。同名冲突时给来源改名成 `name (1).ext`；搬不动（跨文件系统且复制失败）
     * 就留在原处，不删除任何已有数据。
     *
     * @return 实际搬动的条目数
     */
    public static int mergeMoveDirectoryContents(File from, File to) {
        if (from == null || to == null || from.equals(to)) return 0;
        File[] children;
        try {
            children = from.listFiles();
        } catch (Throwable t) {
            return 0;
        }
        if (children == null || children.length == 0) return 0;
        if (!to.exists() && !to.mkdirs()) return 0;

        int moved = 0;
        for (File child : children) {
            if (".nomedia".equals(child.getName())) continue;
            File target = new File(to, child.getName());
            if (!target.exists()) {
                if (moveFileOrDirectory(child, target)) moved++;
            } else if (child.isDirectory() && target.isDirectory()) {
                moved += mergeMoveDirectoryContents(child, target);
            } else {
                File alt = new File(to, uniqueName(to, child.getName()));
                if (moveFileOrDirectory(child, alt)) moved++;
            }
        }
        return moved;
    }

    /** 先试改名（同卷），失败再退回复制+删除。 */
    private static boolean moveFileOrDirectory(File from, File to) {
        try {
            if (from.renameTo(to)) return true;
        } catch (Throwable ignored) {
        }
        if (from.isDirectory()) {
            if (!copyDirectory(from, to)) return false;
            deleteFolder(from);
            return true;
        }
        if (!copyFile(from, to)) return false;
        //noinspection ResultOfMethodCallIgnored
        from.delete();
        return true;
    }

    private static boolean copyFile(File from, File to) {
        FileInputStream in = null;
        FileOutputStream out = null;
        try {
            File parent = to.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) return false;
            in = new FileInputStream(from);
            out = new FileOutputStream(to);
            byte[] buffer = new byte[8192];
            int len;
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
            return true;
        } catch (Throwable t) {
            Logu.w("storage-perm", "搬运文件失败：" + from + " -> " + to + "：" + t);
            //noinspection ResultOfMethodCallIgnored
            to.delete();
            return false;
        } finally {
            closeQuietly(in);
            closeQuietly(out);
        }
    }

    private static boolean copyDirectory(File from, File to) {
        if (!to.exists() && !to.mkdirs()) return false;
        File[] children;
        try {
            children = from.listFiles();
        } catch (Throwable t) {
            return false;
        }
        if (children == null) return true;
        for (File child : children) {
            File target = new File(to, child.getName());
            if (child.isDirectory()) {
                if (!copyDirectory(child, target)) return false;
            } else if (!copyFile(child, target)) {
                return false;
            }
        }
        return true;
    }

    private static void closeQuietly(Closeable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Throwable ignored) {
        }
    }

    private static String uniqueName(File dir, String name) {
        String base = name;
        String ext = "";
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            base = name.substring(0, dot);
            ext = name.substring(dot);
        }
        for (int i = 1; i < 1000; i++) {
            String candidate = base + " (" + i + ")" + ext;
            if (!new File(dir, candidate).exists()) return candidate;
        }
        return name + "." + System.currentTimeMillis();
    }

    public static File getDownloadPath() {
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
    }

    public static void requireTFCardPermission() {

    }

    public static String stringToFile(String str) {
        return str.substring(0, Math.min(85, str.length()))    //防止长度溢出
                .replace("|", "｜")
                .replace(":", "：")
                .replace("*", "﹡")
                .replace("?", "？")
                .replace("\"", "”")
                .replace("<", "＜")
                .replace(">", "＞")
                .replace("/", "／")
                .replace("\\", "＼");    //文件名里不能包含非法字符
    }

    public static String getFileNameFromLink(String link) {
        int length = link.length();
        for (int i = length - 1; i > 0; i--) {
            if (link.charAt(i) == '/') {
                return link.substring(i + 1);
            }
        }
        return "fail";
    }

    public static String getFileFirstName(String file) {
        for (int i = 0; i < file.length(); i++) {
            if (file.charAt(i) == '.') {
                return file.substring(0, i);
            }
        }
        return "fail";
    }

    public static boolean isVideoCached(String title) {
        File videoFolder = new File(getVideoDownloadPath(), stringToFile(title));
        if (!videoFolder.exists() || !videoFolder.isDirectory()) {
            return false;
        }
        
        File videoFile = new File(videoFolder, "video.mp4");
        File audioFile = new File(videoFolder, "audio.m4a");
        File downloadingMark = new File(videoFolder, ".DOWNLOADING");
        
        if (downloadingMark.exists()) {
            return false;
        }
        
        return videoFile.exists() || audioFile.exists();
    }
}

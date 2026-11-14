/*
 * 弹幕时钟与「真实媒体时间」的同步计算（纯 Java，无 Android 依赖，可直接 JVM 单测）
 *
 * 背景（26.10.22 实测）：DFM 原生的 `DrawHandler.syncTimer()` 用
 * `SystemClock.uptimeMillis() - mTimeBase` 当作媒体时间。这只在「视频恰好按 1.0x 实时播放」时成立；
 * 一旦解码跟不上（弱机、高清、后台）、或用户切到 2 倍速、或缓冲停顿时，媒体位置与墙钟的
 * **速率就不同了**，差距会线性增长：
 *
 *   视频=3743  弹幕=4249  差=-506ms
 *   视频=7754  弹幕=8722  差=-968ms
 *   视频=11773 弹幕=13217 差=-1444ms   ← 每 4 秒多跑约 490ms（约 12%）
 *
 * 于是上层只能周期性 `seekTo` 拉回，而 `seekTo` 本身会清空屏上弹幕 —— 用户看到的就是
 * 「弹幕闪一下又没了」。
 *
 * 正确做法：**以播放器位置为唯一时间源**。上层每 250ms 采样一次位置，本类负责
 * ① 两次采样之间按倍速外推，让弹幕平滑滚动（不出现"每 250ms 跳一下"）；
 * ② 每帧只朝目标值蠕动有限步长，让采样带来的小抖动被摊平（不出现"画面回退"）；
 * ③ 位置"不连续"时（seek / 换源 / 首次采样）直接硬对齐，不做蠕动。
 */
package master.flame.danmaku.controller;

public final class MediaClockSync {

    /**
     * 每帧最多把弹幕时钟朝目标值挪多少毫秒（1.0x 下的基准值）。
     *
     * 取值依据：采样间隔 250ms 内解码器抖动一般不超过几十毫秒；40ms/帧 ≈ 2.4 倍帧间隔，
     * 既能在约 6 帧（~100ms）内消化一次 250ms 的采样跳变，又不会让弹幕肉眼可见地"窜一下"。
     *
     * **必须按倍速放大**（见 {@link #stepFor(float)}）：3x 时媒体时间每秒推进 3000ms，固定 40ms/帧在
     * 60fps 下只能追 2400ms/s，永远追不上（真机 30fps 更差）。
     */
    public static final long MAX_STEP_MS = 40L;

    /**
     * 误差超过这个值就直接吸附到理想值，不再蠕动。
     *
     * 蠕动是给「采样抖动」用的；偏差已经过半秒说明时间轴真的脱节了（暂停恢复、换源、
     * 倍速切换、长时间卡顿），这时候慢慢爬过去只会让弹幕长时间错位。
     */
    public static final long SNAP_ERROR_MS = 500L;

    /**
     * 相邻两次采样之间位置差超过这个值，判定为"不是正常播放推进"（用户拖动/快进/换源/恢复进度），
     * 必须直接硬对齐，否则蠕动收敛要等几十秒。
     *
     * 正常推进下两次采样（250ms）最多差 250ms × 2.0 倍速 = 500ms，取 {@link #DISCONTINUITY_MS} 留余量。
     * 3x 下相邻采样差 750ms，仍在阈值内，所以**不能只靠这条判据兜底**，大误差吸附由
     * {@link #SNAP_ERROR_MS} 负责。
     */
    public static final long DISCONTINUITY_MS = 1000L;

    /**
     * 保守下限之外的余量：按真实帧间隔给步进时留 25%，让误差逐步收敛而不是贴着 0 卡住。
     */
    private static final float FRAME_INTERVAL_MARGIN = 1.25f;

    /** 当前倍速下每帧允许的最大步进（1.0x 为 {@link #MAX_STEP_MS}，倍速越高按比例放大）。 */
    public static long stepFor(float speed) {
        if (speed <= 1.0f) return MAX_STEP_MS;
        return (long) (MAX_STEP_MS * speed);
    }

    /**
     * 按**真实帧间隔**给出的每帧步进上限（26.11.14）。
     *
     * 为什么需要它：[stepFor(float)] 的 40ms/帧是「60fps 下」的基准值。弹幕渲染帧率一旦
     * 掉到 25fps 以下（手表上叠加 15 行弹幕很常见，短视频页还要同时解视频帧），每帧真实过去的
     * 媒体时间就超过 40ms，而蠕动上限还卡在 40ms —— 时钟每帧必然落后一截，误差线性累积，
     * 攒到 {@link #SNAP_ERROR_MS} 就硬吸一次。表现就是：**弹幕整体每 1~2 秒「跳」一下**
     * （用户反馈原话「弹幕依旧会一跳一跳的」）。
     *
     * 这一版把上限抬到「本帧真实过去的媒体时间」（`帧间隔 × 倍速`）以上，蠕动就只用来抹平
     * 采样抖动，不再承担「限制追赶速度」的职责——后者本来就是错的：媒体时间过去多少，
     * 弹幕时钟就该走多少，DFM 原生（墙钟）分支里的 `timer.add(d)` 也是这个口径。
     *
     * @param speed            播放倍速
     * @param frameIntervalMs  距上一帧的真实墙钟间隔；≤0 或未知时退回 [stepFor(float)]
     */
    public static long stepFor(float speed, long frameIntervalMs) {
        long speedStep = stepFor(speed);
        if (frameIntervalMs <= 0) return speedStep;
        float rate = speed < 1.0f ? 1.0f : speed;
        long minStep = (long) (frameIntervalMs * rate * FRAME_INTERVAL_MARGIN);
        return Math.max(speedStep, minStep);
    }

    private MediaClockSync() {
    }

    /** 相邻两次采样的位置是否"不连续"；`prevPosMs < 0`（尚无采样）视为不连续。 */
    public static boolean isDiscontinuity(long prevPosMs, long curPosMs) {
        if (prevPosMs < 0) return true;
        long diff = curPosMs - prevPosMs;
        return diff > DISCONTINUITY_MS || diff < -DISCONTINUITY_MS;
    }

    /**
     * 由「最近一次采样到的位置 + 其后经过的墙钟时间」推算此刻的理想媒体时间。
     *
     * 两次采样之间必须外推，否则弹幕每 250ms 才会动一次（肉眼就是一顿一顿）。
     *
     * @param samplePosMs   最近一次采样到的播放位置（毫秒）
     * @param sampleAtMs    那次采样的墙钟时刻（`SystemClock.uptimeMillis()`）
     * @param nowMs         当前墙钟时刻
     * @param speed         播放倍速（1.0 / 1.5 / 2.0 …）；倍速下位置推进得更快
     */
    public static long project(long samplePosMs, long sampleAtMs, long nowMs, float speed) {
        long elapsed = nowMs - sampleAtMs;
        if (elapsed < 0) elapsed = 0;   // 采样时刻晚于当前（时钟回拨/乱序）时按 0 处理
        if (elapsed > 1000) elapsed = 1000; // 超过一个完整采样周期的空档说明采样已停（暂停/卡死），不再外推
        return samplePosMs + (long) (elapsed * speed);
    }

    /**
     * 本帧应当把 `DanmakuTimer.currMillisecond` 设成多少。
     *
     * 不是直接跳到 idealMs，而是**最多挪 {@link #stepFor(float)} 步**：采样点位置一更新，目标值会出现
     * 最多一个采样间隔的跳变，直接赋值会让屏上弹幕整体前移/后退一下。
     * 但误差超过 {@link #SNAP_ERROR_MS} 说明时间轴真的脱节了（暂停恢复、换源、切倍速、长时间卡顿），
     * 这时直接吸附，别让弹幕长时间错位。
     *
     * @param currentMs 弹幕时钟当前值
     * @param idealMs   理想媒体时间（见 {@link #project(long, long, long, float)}）
     * @param speed     播放倍速；步进上限随它放大（3x 下媒体时间每秒推进 3000ms，40ms/帧追不上）
     */
    public static long nextTimerValue(long currentMs, long idealMs, float speed) {
        return nextTimerValue(currentMs, idealMs, speed, 0L);
    }

    /**
     * 同 [nextTimerValue(long, long, float)]，但步进上限按**真实帧间隔**推算。
     *
     * 调用方（`DrawHandler.syncTimerByMediaClock`）应当传入「距上一帧的墙钟间隔」：
     * 帧率掉到 25fps 以下时，固定 40ms/帧追不上媒体时间流逝，误差会周期性撞上
     * {@link #SNAP_ERROR_MS} 变成肉眼可见的「一跳一跳」（见 [stepFor(float, long)]）。
     *
     * @param frameIntervalMs 距上一帧的墙钟间隔；≤0 或未知时按原行为处理
     */
    public static long nextTimerValue(long currentMs, long idealMs, float speed, long frameIntervalMs) {
        long err = idealMs - currentMs;
        if (err > SNAP_ERROR_MS || err < -SNAP_ERROR_MS) return idealMs;
        long step = stepFor(speed, frameIntervalMs);
        if (err > step) err = step;
        else if (err < -step) err = -step;
        return currentMs + err;
    }
}

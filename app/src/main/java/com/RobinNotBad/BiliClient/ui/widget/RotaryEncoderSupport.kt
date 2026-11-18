package com.RobinNotBad.BiliClient.ui.widget

import android.graphics.Rect
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.ScrollView
import androidx.core.view.ViewConfigurationCompat
import androidx.core.widget.NestedScrollView
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.SettingsKeys
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

/**
 * 表冠（旋转编码器）滚动的公共实现。
 *
 * 合并自原先三份近乎逐行的实现：[RotaryRecyclerView]、[RotaryScrollView]、[RotaryNestedScrollView]。
 * 三者只有「读哪个灵敏度设置键」与「滚动后是否抢焦点」两处差异，但基类分别是
 * `RecyclerView` / `ScrollView` / `NestedScrollView`，无法用共同父类归并，
 * 因此这里用**组合**：控件各自保留自己的事件接入方式（监听器 / dispatch 覆写），
 * 把「判定 + 换轴值 + 滚动」这段真正重复的逻辑收进来。纯判定部分在 [RotaryEventPolicy]。
 *
 * 两类入口：
 * 1. 控件级——[handle]，由三个 `Rotary*` 控件自己的监听器调用（只在该控件拿到事件时触发）；
 * 2. 页面级兜底——[handlePage]，由 `BaseActivity` 挂在 **DecorView** 的 generic motion 监听器上。
 *
 * **为什么必须有页面级兜底**：旋转编码器事件只沿焦点路径分发，页面上没有任何控件获焦时，
 * 事件会一路退到 DecorView 而没人消费。列表页的 `RecyclerView` 默认不抢焦点，于是
 * 「设置页里能用、退出就失灵」——设置页能用只是因为点过输入框，`EditText` 拿了焦点，
 * 事件恰好沿焦点链经过外层的 `RotaryRecyclerView`。DecorView 监听器只在**所有子 View
 * 都没处理**时才会被调用，所以不会和控件级监听器重复滚动。
 *
 * **26.10.17 起开关与灵敏度改成「每次事件现读」**（原先只在 `onAttachedToWindow` 读一次）：
 * 手表上改完设置通常不会重进页面（返回栈里的页面只是不可见，`onAttachedToWindow` 不会再触发），
 * 旧写法等于「改完不生效」。SharedPreferences 的读取是内存 Map，热路径上没有性能问题。
 *
 * **26.11.07 修反馈 #103/#107（「视频详情-简介里表冠不适配」）做了三件事**，都是能静态指认的缺陷：
 * 1. 位移算出 0 时**不再吞事件**（[RotaryEventPolicy.shouldConsume]）。原先控件级 `handle()`
 *    不论算出多少都 return true，事件被吃掉、画面不动，父级和 DecorView 兜底也接不到；
 * 2. 滚动量在 `AXIS_SCROLL` 为 0 时**回落 `AXIS_VSCROLL`**（[RotaryEventPolicy.rawScrollRatio]）。
 *    只读单轴时，把量放在另一轴上的 ROM 会恒算出 0，与第 1 条叠加就是「彻底没反应」；
 * 3. 页面级兜底找目标时**要求与窗口有实际交集**（[isCandidateVisible]），不再只看 `isShown()`。
 *    `isShown()` 只看 VISIBILITY 位，而视频详情页 `viewPager.offscreenPageLimit = 页数`，
 *    离屏页始终 attached + VISIBLE，照旧写法可能滚到用户看不见的那一页。
 *
 * 另外加了 W 级诊断日志（[probe]）：内存环形缓冲只留 W/E/F，反馈页勾「附带应用日志」会把
 * 它一起带上来。同一个进程最多 [PROBE_MAX] 条，正常用户看不到任何变化，但下次「转不动」的
 * 反馈里就能直接读出事发时的 action / source / 两个轴值 / 命中目标——这比再猜一轮省事。
 *
 * **26.11.07.1 起总开关关闭时也接管，但顺序反过来：系统优先、App 补位**（issue #21：
 * Wear OS 5 / OneUI 6 上「几乎所有界面失去了系统滚动，只有视频详情、评论区正常」）。
 * 原先开关关闭等于「App 什么都不做」，只能吃框架的默认行为：`NestedScrollView` /
 * `ScrollView` / `RecyclerView` 三种容器各自的 `onGenericMotionEvent` 只认 `AXIS_SCROLL`
 * （`RecyclerView.java:3961-3967` 与 `NestedScrollView.java:1337-1344`），且前两者的
 * 命中依赖事件沿焦点链经过它们——页面里没有可获焦控件时事件根本到不了容器，于是
 * 「原版能用、这里转不动」。现在的顺序是：先让框架处理（[handleSystemFirst]），
 * 框架动了就**把事件吃掉**（防止冒泡到页面级兜底再滚一遍 = 「叠加」），框架没动才由 App
 * 按双轴补位。开关开启（[isEnabled]）仍是老的「App 先接管」，灵敏度可调的手感不变。
 *
 * **26.11.09 修反馈 #132（「关闭表冠适配后详情页滑动速率小，得一直转」）**：系统优先模式原先
 * 是「框架一动就整段吞掉」，可框架各容器的换算基准并不相同，而且都小于 App 的
 * [RotaryEventPolicy.pixelDelta] 口径——`NestedScrollView`（详情页）用主题的
 * `listPreferredItemHeight`（Material/AppCompat 默认 48dp，见 androidx 的
 * `getVerticalScrollFactorCompat()`），平台 `ScrollView` 与 `RecyclerView` 用
 * `config_verticalScrollFactor`（64dp），而 App 口径是 64dp × 2。于是同一格表冠，详情页只走
 * 约三分之一的距离、列表页约一半——**只有详情页慢**，与用户描述一致。现在吞之前先补足差额
 * （[RotaryEventPolicy.topUpDelta]）：框架滚了多少就抵扣多少，不够的由 App 补到与「开启表冠
 * 适配」一致的距离。框架本来就给得够的机型（issue #21 那一类）分毫不动，同一次手势也不会
 * 被算两次。
 *
 * **26.11.18 修反馈 #228（小米手表「主界面正常，进视频详细页转得很慢，像 0.5 倍速」）与
 * #197（OPPO Watch 4 Pro「视频详情页简介部分，表冠无法精确控制滑动」）**，两条的原因不同：
 * 1. #197 是**来源**问题：这台表把表冠交付成鼠标滚轮（反馈日志 `src=0x2002` =
 *    `SOURCE_MOUSE`、量放在 `AXIS_VSCROLL` 上，相邻几条里既有 -1.0 也有 -16.0），而
 *    [RotaryEventPolicy.isRotaryEvent] 只认 `SOURCE_ROTARY_ENCODER`，于是三条入口全部在
 *    门口退回框架（见 [handleSystemFirst] 的第二行），只剩框架自己的换算——`NestedScrollView`
 *    把 -16 当 16 行算就是**一次跳一整页**，手感即「无法精确控制」。放宽的判据与换算在
 *    [CrownSourcePolicy]：只放宽「表把表冠报成滚轮」这一种来源。接管必须放在**框架之前**：
 *    [RotaryEventPolicy.topUpDelta] 只补不足，收不回框架已经跳出去的那一段。
 * 2. #228 是**落点与开销**问题，且是详情页独有。详情页 `viewPager.offscreenPageLimit = 页数`，
 *    三个页始终 attached + VISIBLE：兜底第一轮靠 [isCandidateVisible] 还能滤掉离屏页，
 *    第二轮（`requireOnScreen = false`）就会选中**用户看不见那一页**的容器并把事件吃掉；
 *    焦点同理可能停在别页的容器上（本工程的 `Rotary*` 控件靠焦点路径拿事件）。于是「一直在转，
 *    看得见的那页不动」。现在兜底排除 `ViewPager` 非当前页（[isInsideOffscreenPagerPage]），
 *    并把命中目标缓存起来（[resolveScrollTarget]）：详情页是「三页同时 attached」的最坏情况，
 *    原先每个表冠事件都要跑两轮全树遍历、每个候选问一次 `canScrollVertically`，低端手表上
 *    这就是「转起来发涩」的叠加项。找不到目标时的日志也补上扫描统计（[ScanStats]），
 *    下次再报「转不动」能直接分辨「整页没内容」与「被可见性判断挡掉」。
 *
 * **26.11.18.1 修反馈 #234（同一个 OPPO Watch 4 Pro 用户：「表冠失效，调极高的灵敏度一卡一卡」）**：
 * 上一版把滚轮整格量一律归一成 ±1（抹掉量级），而这台表**快转一格报 -16.0**——改前框架给 16 行、
 * 改后只给 1 行，速度掉到 1/16，用户的体感就是「转不动」。现在改成 [CrownSourcePolicy.wheelScrollDelta]
 * 保留量级，只用 `maxSteps` 封顶，且封顶按容器分：列表（`RecyclerView` / `ScrollView`）按量级原样
 * （与 26.11.17 完全一致），阅读容器（`NestedScrollView`）单次封顶 2 格，兼顾 #197 的「一转过整页」。
 * 那一次修法的教训：**「收成一格」等于把用户唯一的调速手段抹掉了**，再靠灵敏度补偿只会变成
 * 「一卡一卡」——灵敏度是乘数，救不回量级信息。
 *
 * **26.11.18.2 修反馈 #235（同一台 OPPO Watch 4 Pro 回过一份带日志的「几乎每个页面都用表冠
 * 滑动测试了」）**：日志把这两条硬伤一次说清了——
 * 1. **闸门找错了容器**：每条都是 `view=RotaryRecyclerView ratio=1.0 scrollable=false`。消息页是
 *    `activity_message.xml:47` 的 `RotaryNestedScrollView` 里再套 `:358` 一个
 *    `layout_height="match_parent"` 的 `RotaryRecyclerView`，内层被 UNSPECIFIED 测成「刚好装下
 *    全部会话」，自己永远滚不动（#166 的老结构）。老代码「容器滚不动就交回框架」，而框架不换算
 *    mouse 源的轴值，这一格就白扔了。现在 [resolveWidgetTarget] 沿 parent 链找可滚祖先再接管
 *    （[handle] 与 [handleSystemFirst] 两条入口都走它）。
 * 2. **基准被 ROM 改坏了**：兜底那条是 `dy=-1`，当时灵敏度 1.0、ratio 1.0，按
 *    `-ratio × factor × multiple` 反推 **factor = 1px**（正常机 `config_verticalScrollFactor`
 *    = 64dp ≈ 128px）。于是 1.0 灵敏度 = 每格 1px（用户眼里的「完全动不了」），调到 700 又是每格
 *    700px、超过 378×496 屏幕上那 496px 可视高（「一卡一卡、粘住」）。现在统一走
 *    [verticalScrollFactorOf]，基准不低于 [SCROLL_FACTOR_FLOOR_DP] × density：正常机取到原值，
 *    异常 ROM 拉回正常量级。
 * 3. 单次位移另外加了**一屏封顶**（[clampToOneViewport]）：极端灵敏度下拒绝「一次跳过一屏以上」，
 *    不改灵敏度语义（乘数照旧生效），只是不让 `smoothScrollBy` 每 34ms 就撞一次端点。
 */
internal object RotaryEncoderSupport {

    /**
     * 灵敏度默认值。
     *
     * 与设置页 `SettingPrefActivity` 里 `input_float` 的 `defaultValue` 必须一致（单一来源）。
     * 1.0 = 系统原始滚动量 ×2（`pixelDelta` 里那个 `* 2`），是「不用调手感就对」的经验值；
     * 用户反馈过的可用值也是 1.0（issue：三星表圈只能在设置页内用）。
     */
    const val DEFAULT_MULTIPLE = "1.0"

    /** 表冠滚动总开关（`SettingsKeys.UI_ROTATORY_ENABLE`，且需要 Android 8.0+）。 */
    fun isEnabled(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                SharedPreferencesUtil.getBoolean(SettingsKeys.UI_ROTATORY_ENABLE, false)

    /**
     * 读取某个灵敏度设置键。
     *
     * 总开关关闭时恒返回 0——各控件原本都是 `rotaryEnabled && scrollMultiple > 0` 双条件，
     * 归并成一个「倍数 ≤ 0 即不生效」的判断。用户显式把灵敏度填 0 也就等于关掉该类控件。
     */
    fun multipleOf(key: String): Float =
        if (isEnabled()) RotaryEventPolicy.clampSensitivity(storedMultiple(key)) else 0f

    /**
     * 补位/兜底用的倍率：开关关闭时是 [DEFAULT_MULTIPLE]，开启时是用户填的灵敏度。
     *
     * 与 [multipleOf] 的区别只在开关关闭时——[multipleOf] 返回 0 表示「App 先接管」这条路不走，
     * 而补位这条路关闭时也要走（否则就是 issue #21 的「大部分界面没有表冠滚动」）。
     * 用户显式填 0 仍是「这类页面不响应表冠」的逃生口。
     */
    fun sensitivityOf(key: String): Float =
        if (isEnabled()) RotaryEventPolicy.clampSensitivity(storedMultiple(key))
        else DEFAULT_MULTIPLE.toFloat()

    /**
     * 把存下来的灵敏度**就地收回** [RotaryEventPolicy.SENSITIVITY_MIN]..[SENSITIVITY_MAX]。
     *
     * 只在设置页建列表时调一次：读取侧（[multipleOf] / [sensitivityOf]）已经收过了，所以即便
     * 这里没跑到（用户从不进设置页），换算也用不到越界的值；这一步的目的是让**设置页显示的
     * 数字等于真正生效的数字**，否则用户看到 700、手感却是 8，只会当成又一个 bug。
     *
     * 反馈 #235 的复测里，这一条对应「之前能正常滑动的页面变得一滑到底」：那些页面本身没变，
     * 是基准从 1px 修到 128px 之后，他们为旧基准填的几百倍被原样乘了上去。
     */
    fun normalizeSensitivity(key: String) {
        val stored = storedMultiple(key)
        val clamped = RotaryEventPolicy.clampSensitivity(stored)
        if (clamped != stored) SharedPreferencesUtil.putFloat(key, clamped)
    }

    /** 总开关关闭时走「系统优先、App 补位」，开启时走老的「App 先接管」。 */
    fun isSystemFirstMode(): Boolean = !isEnabled()

    /**
     * 处理一次 generic motion 事件（控件级）。
     *
     * 命中表冠滚动则滚动并返回 `true`；否则返回 `false`，由调用方继续走自己的默认分发。
     * **位移为 0 也返回 `false`**——见 [RotaryEventPolicy.shouldConsume] 与类注释第 1 条。
     *
     * @param multiple    灵敏度倍率，≤ 0 直接不处理
     * @param requestFocus 是否在滚动后抢焦点。`RecyclerView`/`ScrollView` 原本会抢，
     *        `NestedScrollView` 原本不抢——这个差异原样保留，避免改变现有手感。
     * @param smoothScrollBy 竖直平滑滚动动作。`View` 本身没有可访问的 `smoothScrollBy`，
     *        只有各具体子类有，所以由调用方传进来（`{ smoothScrollBy(0, it) }`）。
     */
    fun handle(
        view: View,
        event: MotionEvent,
        multiple: Float,
        requestFocus: Boolean,
        smoothScrollBy: (Int) -> Unit
    ): Boolean {
        if (multiple <= 0f) return false
        if (isOffscreenPageWidget(view)) return false
        val crown = CrownSourcePolicy.kindOf(event.action, event.source)
        if (crown == CrownSourcePolicy.NONE) {
            if (RotaryEventPolicy.isScrollEvent(event.action)) {
                probe("控件级：非表冠源，未处理", event, "view=${view.javaClass.simpleName}")
            }
            return false
        }
        // 开启模式下也要把「模式 + 两类灵敏度 + factor」记一次：反馈 #234 的复现步骤里用户会
        // 先关开关再打开，两条路各打一条，才能看出是哪条路上的量级不对。
        probeMode(view, event)
        // 落点自己滚不动时**先向上找可滚祖先**（反馈 #235），找到就由 App 滚它。
        //
        // 这条针对 #166 那种「外层滚、内层撑满」的结构：#235 的消息页是
        // activity_message.xml:47 的 `RotaryNestedScrollView` 套 :358 的 `RotaryRecyclerView`
        // （`layout_height="match_parent"`，被 `NestedScrollView` 的 UNSPECIFIED 量成「刚好装下
        // 全部会话」），内层自己永远 `canScrollVertically() == false`。老代码在这里一律交回框架
        // （理由是「别吃掉事件却没人动」），可把表冠报成鼠标的 ROM 上框架根本不换算轴值，交回
        // 等于整段交给一个不处理的人——#235 的日志里每格只剩 DecorView 兜底那一次最慢的滚动。
        //
        // 26.11.18-beta.3 起**不再按「量够不够粗」让路**（反馈 #234）：细量照旧按
        // [CrownSourcePolicy.wheelScrollDelta] 换算，只有位移真的算成 0（轴值本来就是 0）时才由
        // 下面的 shouldConsume 交回框架。
        val scrollTarget = resolveWidgetTarget(view)
        if (scrollTarget == null) {
            probe(
                "控件级：落点与祖先都滚不动，交回框架", event,
                "view=" + view.javaClass.simpleName + " crown=" + crown +
                    " ratio=" + rawScrollRatio(event)
            )
            return false
        }

        val dy = crownPixelDelta(scrollTarget, event, multiple) ?: return false
        if (!RotaryEventPolicy.shouldConsume(dy)) {
            probe("控件级：位移为 0，不吞事件", event, "view=${view.javaClass.simpleName} multiple=$multiple")
            return false
        }
        if (scrollTarget === view) smoothScrollBy(dy) else smoothScrollVertically(scrollTarget, dy)
        if (requestFocus) view.requestFocus()
        return true
    }

    /**
     * 「系统优先、App 补位」的控件级入口（总开关关闭时走这条，见类注释最后两段）。
     *
     * 顺序是先让框架自己处理（`runFramework` 里就是 `super.onGenericMotionEvent` /
     * `super.dispatchGenericMotionEvent`），再用**滚动位置的变化量**量出框架到底滚了多少：
     *
     * - 框架动了（或框架自己说处理了）→ 按 [RotaryEventPolicy.topUpDelta] 补上差额，再
     *   `return true` 把事件吃掉。吃掉是「不叠加」的关键：`RecyclerView.onGenericMotionEvent`
     *   滚完仍然 `return false`，不吞就会一路冒泡到 DecorView 的页面级兜底，兜底再把同一个
     *   容器滚一遍。补差额而不是「一动不动」是 26.11.09 修反馈 #132 的改动——框架的换算
     *   基准比 App 口径小（详情页用 `listPreferredItemHeight`、列表用 `config_verticalScrollFactor`，
     *   而 App 是后者 × 2），不补的话详情页每格只走三分之一的距离。
     * - 没动 → 按双轴换算整段位移补位（`AXIS_VSCROLL` 的 ROM 就是靠这一步才有反应）。
     *
     * 用**带符号差值**而不是「有没有变」做判定：同一个读数既要回答「动没动」，又要拿来抵扣
     * 补位量，一次读值两用，也就不会出现「前后读了两次偏移、两次之间框架又滚了一下」的偏差。
     *
     * @param multiple 补位倍率（开关关闭时用 [DEFAULT_MULTIPLE]，用户填 0 则不补位）
     * @param scrollOffset 竖直滚动位置的读数，用来量框架滚了多少
     * @param runFramework 交给框架处理，返回框架自己的「是否已处理」
     * @param smoothScrollBy 补位 / 补差额时的平滑滚动动作
     */
    fun handleSystemFirst(
        view: View,
        event: MotionEvent,
        multiple: Float,
        scrollOffset: () -> Int,
        runFramework: () -> Boolean,
        smoothScrollBy: (Int) -> Unit
    ): Boolean {
        if (multiple <= 0f) return runFramework()
        if (isOffscreenPageWidget(view)) return false
        val crown = CrownSourcePolicy.kindOf(event.action, event.source)
        if (crown == CrownSourcePolicy.NONE) return runFramework()

        probeMode(view, event)

        // 落点自己滚不动时先向上找可滚祖先（反馈 #235），与 [handle] 里同一段同因，见那里的注释。
        //
        // 这条路**不走「框架先跑、App 补差额」**，而是直接整段滚祖先：`runFramework` / `scrollOffset` /
        // `smoothScrollBy` 三个 λ 都绑在**落点**上，而落点此刻滚不动，`super` 那条路什么也动不了；
        // 祖先自己的 `onGenericMotionEvent` 要等子控件返回 false 之后才跑，此刻还没轮到它。也就是说
        // 祖先的框架处理尚未发生，直接接管不会与它叠加。
        val scrollTarget = resolveWidgetTarget(view)
        if (scrollTarget == null) {
            probePerView(
                "滚轮：落点与祖先都滚不动，交回框架", view, event,
                "crown=" + crown + " ratio=" + rawScrollRatio(event)
            )
            return runFramework()
        }
        if (scrollTarget !== view) {
            val dy = crownPixelDelta(scrollTarget, event, multiple) ?: return runFramework()
            if (!RotaryEventPolicy.shouldConsume(dy)) {
                probePerView(
                    "接管祖先：位移为 0，交回框架", view, event,
                    "ratio=" + rawScrollRatio(event) + " multiple=" + multiple
                )
                return runFramework()
            }
            smoothScrollVertically(scrollTarget, dy)
            probePerView(
                "接管祖先：落点滚不动，改滚外层容器", view, event,
                "target=" + scrollTarget.javaClass.simpleName + " dy=" + dy + " multiple=" + multiple
            )
            return true
        }

        // 滚轮类来源（反馈 #197）一格报多行时，必须在**框架动手之前**接管：框架按行数换算
        // （-16 就是 16 行 = 一次整页，见 [RotaryEventPolicy.systemFirstTargetDelta] 的注释），
        // 而 [RotaryEventPolicy.topUpDelta] 只补不足、收不回已经跳出去的那一段距离。
        //
        // 细量（|量| < 0.9，触控板式 / 部分 ROM 的连续小量）反过来：一格只有几像素，先让框架跑
        // 不会「跳出去收不回」，再按同一套滚轮口径补足差额即可。26.11.18-beta.3 之前这里是
        // 「细量整段交回框架」，而在 OPPO Watch 4 Pro 那类把表冠报成鼠标的 ROM 上框架根本不换算
        // 轴值——低灵敏度下表现为「完全动不了」，正是反馈 #234；高灵敏度下又只剩框架那一份最慢
        // 的基准，表现为「一卡一卡」。
        if (crown == CrownSourcePolicy.WHEEL) {
            val ratio = rawScrollRatio(event)
            val cap = wheelMaxStepsOf(view)
            val factor = verticalScrollFactorOf(view)

            if (CrownSourcePolicy.isCoarseWheelStep(ratio)) {
                val dy = clampToOneViewport(
                    view, CrownSourcePolicy.wheelScrollDelta(ratio, factor, multiple, cap)
                )
                if (!RotaryEventPolicy.shouldConsume(dy)) {
                    probePerView("滚轮：整格量位移为 0，交回框架", view, event, "ratio=$ratio multiple=$multiple")
                    return runFramework()
                }
                smoothScrollBy(dy)
                probePerView(
                    "滚轮：整格量按量级换算（列表保量级、阅读容器封顶）", view, event,
                    "ratio=$ratio dy=$dy multiple=$multiple cap=$cap factor=$factor"
                )
                return true
            }

            val target = clampToOneViewport(
                view, CrownSourcePolicy.wheelScrollDelta(ratio, factor, multiple, cap)
            )
            val before = scrollOffset()
            val frameworkHandled = runFramework()
            val frameworkDelta = scrollOffset() - before
            if (RotaryEventPolicy.shouldConsumeAfterFramework(frameworkHandled, frameworkDelta != 0)) {
                val topUp = RotaryEventPolicy.topUpDelta(target, frameworkDelta)
                if (topUp != 0) smoothScrollBy(topUp)
                probePerView(
                    "滚轮：细量框架先跑后补差额", view, event,
                    "ratio=$ratio target=$target frameworkDelta=$frameworkDelta topUp=$topUp multiple=$multiple"
                )
                return true
            }
            if (!RotaryEventPolicy.shouldConsume(target)) {
                probePerView(
                    "滚轮：细量、框架没动且目标为 0，不吞事件", view, event,
                    "ratio=$ratio multiple=$multiple"
                )
                return false
            }
            smoothScrollBy(target)
            probePerView(
                "滚轮：细量框架没动，App 接管", view, event,
                "ratio=$ratio target=$target multiple=$multiple factor=$factor"
            )
            return true
        }

        val before = scrollOffset()
        val frameworkHandled = runFramework()
        val frameworkDelta = scrollOffset() - before
        // 这一格「应该」走多远。**必须在 event 被回收之前算出来**：系统在 dispatch 返回后会
        // recycle 这个 MotionEvent，延后使用它的 axis 值只会读到 0。
        val target = clampToOneViewport(view, systemFirstTargetDelta(view, event, multiple))

        if (RotaryEventPolicy.shouldConsumeAfterFramework(frameworkHandled, frameworkDelta != 0)) {
            // 框架说自己处理了、偏移却没变：可能是异步滚动（惯性/动画还没落到 scrollY 上），
            // 也可能已经到顶/到底。此时一律不补——补了会和随后到来的框架滚动叠加，
            // 正是 1789a853 要防的「同一次手势被算两次」。
            if (frameworkDelta == 0) {
                probePerView(
                    "系统优先：框架说处理了但没位移，吞掉不补", view, event,
                    "target=$target multiple=$multiple"
                )
                return true
            }

            val topUp = RotaryEventPolicy.topUpDelta(target, frameworkDelta)
            if (topUp == 0) {
                probePerView(
                    "系统优先：框架已滚够", view, event,
                    "target=$target frameworkDelta=$frameworkDelta multiple=$multiple"
                )
                return true
            }
            smoothScrollBy(topUp)
            probePerView(
                "系统优先：框架滚得不够，补足差额", view, event,
                "target=$target frameworkDelta=$frameworkDelta topUp=$topUp multiple=$multiple"
            )
            return true
        }

        if (!RotaryEventPolicy.shouldConsume(target)) {
            probePerView(
                "系统优先：框架没动、补位位移为 0，不吞事件", view, event,
                "multiple=$multiple"
            )
            return false
        }
        smoothScrollBy(target)
        probePerView("补位：框架没动，App 接管", view, event, "target=$target multiple=$multiple")
        return true
    }

    /**
     * 页面级兜底：挂在 DecorView 的 generic motion 监听器上，谁都没处理时才被调用。
     *
     * 找出本页第一个「可见、且当前确实能上下滚」的竖直滚动容器并滚它。
     * 横向容器（如 `ViewPager2` 内部、横向 `RecyclerView`）的 `canScrollVertically` 恒为
     * false，天然被排除，不会把翻页手势抢成竖滚。
     *
     * 26.11.18 为反馈 #228 收紧了两处，见 [resolveScrollTarget]：不再选 ViewPager 离屏页里的
     * 容器（详情页三页常驻，宽松轮会捞到看不见的那一页并吞掉事件），以及命中缓存时不遍历。
     *
     * @return 是否消费了这次事件
     */
    fun handlePage(root: View, event: MotionEvent): Boolean {
        val crown = CrownSourcePolicy.kindOf(event.action, event.source)
        if (crown == CrownSourcePolicy.NONE) {
            if (RotaryEventPolicy.isScrollEvent(event.action)) {
                probe("兜底：非表冠源，未处理", event, "root=${root.javaClass.simpleName}")
            }
            return false
        }

        val stats = ScanStats()
        val target = resolveScrollTarget(root, stats)
        if (target == null) {
            // 反馈 #228 就卡在这一句上：原先它只能说「本页没有可滚容器」，说不出是哪种原因。
            // 现在把扫描统计一起打出来——scrollable > 0 而 hidden / noOverlap / offscreenPage
            // 也 > 0，说明目标被可见性或页位判断挡掉了（详情页三页常驻时最可能是 offscreenPage）；
            // scrollable 为 0 说明页面上当时**根本没有可滚内容**（详情页取数没回来、页面根还
            // 是 GONE 的那段时间，正好对上 #228 那种满屏 DNS 失败、半天加载不出的场景）。
            probe("兜底：本页找不到可滚容器", event, stats.describe(root))
            return false
        }
        val multiple = if (target is RecyclerView)
            sensitivityOf(SettingsKeys.UI_ROTATORY_RECYCLER)
        else
            sensitivityOf(SettingsKeys.UI_ROTATORY_SCROLL)
        if (multiple <= 0f) return false

        val dy = crownPixelDelta(target, event, multiple) ?: return false
        if (!RotaryEventPolicy.shouldConsume(dy)) {
            probe("兜底：位移为 0，不吞事件", event, "target=${target.javaClass.simpleName} multiple=$multiple")
            return false
        }
        if (!smoothScrollVertically(target, dy)) return false
        if (successProbed.compareAndSet(false, true)) {
            probe("兜底：滚动成功（本进程首次）", event, "dy=$dy target=${target.javaClass.simpleName}")
        }
        return true
    }

    /** 本次事件的滚动量：优先 `AXIS_SCROLL`，为 0 时回落 `AXIS_VSCROLL`。 */
    private fun rawScrollRatio(event: MotionEvent): Float =
        RotaryEventPolicy.rawScrollRatio(
            event.getAxisValue(MotionEvent.AXIS_SCROLL),
            event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        )

    /**
     * 本机 `ViewConfiguration` 报上来的竖直滚动缩放值（`config_verticalScrollFactor`，正常机 = 64dp × density）。
     *
     * 这是**原值**，不保证可用：反馈 #235 的 OPPO Watch 4 Pro 报的是 **1px**（正常手表 64dp ≈ 128px，
     * 差两个数量级），于是灵敏度 1.0 时每格只滚 1px（用户眼里就是「完全动不了」）、灵敏度 700 时
     * 每格 700px 又一格撞穿一屏（「一卡一卡粘住」）。要算位移请用 [verticalScrollFactorOf]。
     */
    private fun scaledVerticalScrollFactorOf(view: View): Float =
        ViewConfigurationCompat.getScaledVerticalScrollFactor(
            ViewConfiguration.get(view.context), view.context
        )

    /**
     * 真正用于换算的基准：`max(config_verticalScrollFactor, [SCROLL_FACTOR_FLOOR_DP] × density)`。
     *
     * 这是反馈 #235 的第二条根因。ROM 把 `config_verticalScrollFactor` 调成 1px 之后，所有按它换算的
     * 路径都只剩百分之一的距离。这个值由 ROM 决定、AOSP 的默认值是 64dp，正常机器不会低于它，所以
     * 设一个下限既不会改变正常机型（`max` 取到原值），又能把异常 ROM 拉回正常量级。
     *
     * 框架各容器自己算表冠滚动量时未必用这个值（`NestedScrollView` 用的是主题的
     * `listPreferredItemHeight`），所以系统优先模式下的补位要按 App 口径重新算一遍目标距离，
     * 见 [RotaryEventPolicy.systemFirstTargetDelta]。
     */
    private fun verticalScrollFactorOf(view: View): Float {
        val floor = SCROLL_FACTOR_FLOOR_DP * view.resources.displayMetrics.density
        val config = scaledVerticalScrollFactorOf(view)
        return if (config >= floor) config else floor
    }

    /**
     * 这一次滚轮事件最多折算几格（封顶值）。
     *
     * 判据是**容器类型**而不是页面：同一个视频详情页里，简介是 `NestedScrollView`（阅读，要精确）、
     * 外层是 `RecyclerView`（列表，要快），两类容器的手感要求本来就相反。封顶值见
     * [CrownSourcePolicy.WHEEL_MAX_STEPS_READING] 与 [CrownSourcePolicy.WHEEL_MAX_STEPS_LIST]。
     */
    private fun wheelMaxStepsOf(view: View): Float =
        if (view is NestedScrollView) {
            CrownSourcePolicy.WHEEL_MAX_STEPS_READING
        } else {
            CrownSourcePolicy.WHEEL_MAX_STEPS_LIST
        }

    /**
     * 本容器上这一格该滚多少像素；返回 null 表示「这次不接管」。
     *
     * 编码器来源走 [RotaryEventPolicy.pixelDelta]（原口径，含那个 ×2）；滚轮来源走
     * [CrownSourcePolicy.wheelScrollDelta]（保留量级、阅读容器封顶、不乘 2，理由见那里的注释）。
     * 两条都过 [verticalScrollFactorOf] 的下限与 [clampToOneViewport] 的一屏封顶（反馈 #235）。
     * 三条入口都已经提前挡掉了 CrownSourcePolicy.NONE，所以 null 在正常路径上不会出现。
     */
    private fun crownPixelDelta(view: View, event: MotionEvent, multiple: Float): Int? {
        val factor = verticalScrollFactorOf(view)
        val delta = when (CrownSourcePolicy.kindOf(event.action, event.source)) {
            CrownSourcePolicy.ENCODER ->
                RotaryEventPolicy.pixelDelta(rawScrollRatio(event), factor, multiple)

            CrownSourcePolicy.WHEEL -> CrownSourcePolicy.wheelScrollDelta(
                rawScrollRatio(event), factor, multiple, wheelMaxStepsOf(view)
            )

            else -> return null
        }
        return clampToOneViewport(view, delta)
    }

    /** 系统优先模式下这一格「应该」走的距离（与开启表冠适配 + 灵敏度 1.0 一致）。 */
    private fun systemFirstTargetDelta(view: View, event: MotionEvent, multiple: Float): Int =
        RotaryEventPolicy.systemFirstTargetDelta(
            rawScrollRatio(event), verticalScrollFactorOf(view), multiple
        )

    // ==================== 诊断 ====================
    //
    // 只在「本该处理却没处理」「本进程第一次真的滚动成功」「本进程第一次补差额」时打日志，
    // 同一进程最多 PROBE_MAX 条。用 W 级：Logu 的内存环形缓冲只记 W/E/F，反馈页勾
    // 「附带应用日志」带的就是它。
    //
    // 补差额那条特意带 target / frameworkDelta / topUp 三个数：反馈 #132 这类「手感偏慢」
    // 的问题没有这三个数就只能靠猜（维护者手上没有表冠设备，density 与各容器的换算基准
    // 都得从日志反推），下次再报慢时一眼就能算出框架给少了多少。

    private const val PROBE_MAX = 6
    private val probeCount = AtomicInteger(0)
    private val successProbed = AtomicBoolean(false)

    // ---------- 反馈 #205（「有的页面表冠快有的慢」）专用埋点 ----------
    //
    // 这条反馈靠读代码定不了论：三种容器的目标距离是**同一套口径**（[systemFirstTargetDelta]，
    // 64dp × 2 × 灵敏度），差异只可能来自「框架自己先滚了多少」——而框架各容器的换算基准并不
    // 相同（NestedScrollView 用主题 listPreferredItemHeight ≈48dp，ScrollView / RecyclerView 用
    // config_verticalScrollFactor = 64dp），补差额补得对不对，全看 frameworkDelta 这一个数。
    // 原先的 [probe] 只在本进程第一次打，第一条几乎总是来自最先被转的那个页面，后面所有页面的
    // 数都被 [PROBE_MAX] 挤掉，正好看不见「快慢不同」。
    // 现在给三种容器**各留 [PER_VIEW_PROBE_MAX] 条**，并在本进程第一次转表冠时把「当前模式 +
    // 两类容器的灵敏度 + 本机竖直滚动基准」一起打出来——「快慢不同」有一半是两类容器填了不同
    // 灵敏度造成的，这几个数一摆出来就能区分开。

    private const val PER_VIEW_PROBE_MAX = 4
    private val perViewProbeCount = ConcurrentHashMap<String, AtomicInteger>()
    private val modeProbed = AtomicBoolean(false)

    /** 本进程第一次转表冠时记一条：模式、两个灵敏度、竖直滚动基准。 */
    private fun probeMode(view: View, event: MotionEvent) {
        if (!modeProbed.compareAndSet(false, true)) return
        // 系统优先模式下 [sensitivityOf] 恒返回 1.0（开关关着时灵敏度不参与），所以把用户
        // **实际填的**值也一起打出来：用户看到「两类页面快慢不同」时，很可能是当初给两类容器
        // 填了不同灵敏度，而那几个值在系统优先模式下并不会生效——不摆出来就会误判。
        logLine(
            "模式与灵敏度（本进程首次）", event,
            "enabled=${isEnabled()} systemFirst=${isSystemFirstMode()}" +
                    " storedRecycler=${storedMultiple(SettingsKeys.UI_ROTATORY_RECYCLER)}" +
                    " storedScroll=${storedMultiple(SettingsKeys.UI_ROTATORY_SCROLL)}" +
                    " effectiveRecycler=${sensitivityOf(SettingsKeys.UI_ROTATORY_RECYCLER)}" +
                    " effectiveScroll=${sensitivityOf(SettingsKeys.UI_ROTATORY_SCROLL)}" +
                    " factor=${verticalScrollFactorOf(view)}" +
                    " factorRaw=${scaledVerticalScrollFactorOf(view)}"
        )
    }

    /** 用户存下来的灵敏度原值（不受总开关影响）。 */
    private fun storedMultiple(key: String): Float =
        SharedPreferencesUtil.getFloat(key, DEFAULT_MULTIPLE.toFloat())

    /** 按容器类名各记 [PER_VIEW_PROBE_MAX] 条；超出后只计数不打日志。 */
    private fun probePerView(reason: String, view: View, event: MotionEvent, extra: String = "") {
        val key = view.javaClass.simpleName
        val counter = perViewProbeCount.computeIfAbsent(key) { AtomicInteger(0) }
        if (counter.getAndIncrement() >= PER_VIEW_PROBE_MAX) return
        logLine(reason, event, "view=$key $extra")
    }

    private fun probe(reason: String, event: MotionEvent, extra: String = "") {
        if (probeCount.getAndIncrement() >= PROBE_MAX) return
        logLine(reason, event, extra)
    }

    private fun logLine(reason: String, event: MotionEvent, extra: String = "") {
        val source = event.source
        val text = buildString {
            append(reason)
            append(" action=").append(event.action)
            append(" src=0x").append(source.toString(16))
            append(" rotary=").append(RotaryEventPolicy.isRotaryEvent(event.action, source))
            // crown 是放宽后的判据（1=编码器 2=滚轮 0=不接管），排查 #197 这类「ROM 把表冠报成
            // 鼠标」时，rotary=false 只能说明没走老路径，crown 才能说明到底认没认出来。
            append(" crown=").append(CrownSourcePolicy.kindOf(event.action, source))
            append(" axisScroll=").append(event.getAxisValue(MotionEvent.AXIS_SCROLL))
            append(" axisVScroll=").append(event.getAxisValue(MotionEvent.AXIS_VSCROLL))
            if (extra.isNotEmpty()) {
                append(' ').append(extra)
            }
        }
        Logu.w("RotaryEncoder", text)
    }

    // ==================== 找目标 ====================

    /**
     * 上一次兜底命中的容器。
     *
     * 一格的代价是「ViewPager → 三个常驻 fragment → 每个 fragment 几十个 View 的子树」全遍历，
     * 而 [isScrollable] 内部的 canScrollVertically 是虚调用；表冠转得快时一帧可能来好几格，
     * 累计起来就是「发涩 / 像 0.5 倍速」的体感（反馈 #228）。缓存只解决「同一个容器连续命中」
     * 这个绝大多数情况，命中条件每次都重新校验（不在离屏页 + 能滚 + 可见），所以容器被回收、
     * 切页、滚到内容不满屏时都会自动失效。
     *
     * 用弱引用：owner 是 DecorView，活到 Activity 销毁，强引用会把已经离开的 fragment 的
     * 整棵 View 树一起摁住。
     */
    private var cachedTargetRef: WeakReference<View>? = null

    /**
     * 找本页第一个「可见且当前确实能上下滚」的竖直滚动容器。
     *
     * 顺序：先试 [cachedTargetRef]，未命中再单轮深度优先扫全树（[scanScrollTarget]）。
     * 26.11.18 为反馈 #228 改了两点：
     * 1. 两轮遍历合成一轮——原来「严格一轮 + 宽松一轮」在详情页就是两遍全树；
     * 2. 扫描时**跳过 ViewPager 非当前页里的容器**。视频详情页把 offscreenPageLimit 设成页数
     *    （VideoInfoActivity.kt:125 / :92），简介 / 评论 / 推荐三页同时 attached 且 VISIBLE，
     *    只是被摆到屏幕外，`isShown()` 对它们仍为真：老代码的严格轮被 getGlobalVisibleRect
     *    挡掉之后，宽松轮会捞到离屏页的容器，于是表冠滚的是用户看不见的那一页，事件还被吞掉
     *    （返回 true 后不再有别的处理者）。
     */
    private fun resolveScrollTarget(root: View, stats: ScanStats): View? {
        val rect = Rect()
        val cached = cachedTargetRef?.get()
        if (cached != null && !isInsideOffscreenPagerPage(cached) &&
            isScrollable(cached) && isCandidateVisible(cached, rect, requireOnScreen = true)
        ) {
            return cached
        }

        val hit = scanScrollTarget(root, rect, stats) ?: stats.fallback
        cachedTargetRef = hit?.let { WeakReference(it) }
        return hit
    }

    /**
     * 单轮深度优先：优先返回「可见、与窗口有实际交集、且不在 ViewPager 离屏页里」的容器；
     * 一个都没有时退回第一个「`isShown()` 为真」的容器（26.11.07 之前的行为：布局还没完成、
     * 尺寸都是 0 的瞬间严格条件会全灭，此时按老办法至少不会比原来更差）。
     *
     * 顺路把统计记进 [stats]：反馈 #228 的日志只有一句「本页找不到可滚容器」，没法区分
     * 「页面上真的没有可滚内容」和「有容器但被可见性判断挡掉了」，只能靠这几组数字下次判死。
     */
    private fun scanScrollTarget(view: View, rect: Rect, stats: ScanStats): View? {
        if (view is ViewGroup) stats.groups++
        if (isScrollable(view)) {
            stats.scrollable++
            when {
                // 先判几何可见：只要与窗口有交集就一定是用户看得见的那个（含翻页过程中的两页），
                // 不受 ViewPager 子序号与页号对不对得上的影响。
                isCandidateVisible(view, rect, requireOnScreen = true) -> return view
                // 再排除离屏页：宽松轮（只看 isShown）正是 #228 / #205 那类「滚了没反应」的来源。
                isInsideOffscreenPagerPage(view) -> stats.offscreenPage++
                !view.isShown -> stats.hidden++
                else -> {
                    stats.noOverlap++
                    if (stats.fallback == null) stats.fallback = view
                }
            }
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                scanScrollTarget(view.getChildAt(i), rect, stats)?.let { return it }
            }
        }
        return null
    }

    /**
     * 控件级入口的落点闸门：自己要嘛在 ViewPager 的离屏页里，要嘛根本不在屏幕上，就别消费这一格。
     *
     * 典型现场（反馈 #228 的详情页）：焦点停在切走的那一页上，ViewPager 又不会搬焦点，于是编码器
     * 事件顺着**旧焦点路径**先落到那一页的滚动控件上。老代码在这里照旧接管，用户看到的是
     * 「在当前页转表冠，看不见的那一页在后台滚，当前页纹丝不动」——事件还被吞掉。返回 false 之后
     * 事件冒泡到 DecorView 的兜底（[handlePage]），由它按「当前页」重新选目标。
     *
     * 两个条件缺一不可：只有**同时**满足「在离屏页里」和「与窗口无交集」才让路。只判前者会受
     * ViewPager 子序号与页号对不对得上的影响（对不上时会把当前页当离屏页，直接滚不动）；
     * 只判后者会把「布局还没完成、尺寸还是 0」的正常情况也挡掉。
     */
    internal fun isOffscreenPageWidget(view: View): Boolean =
        isInsideOffscreenPagerPage(view) && !isCandidateVisible(view, Rect(), requireOnScreen = true)

    /**
     * 这个 View 是否躺在 ViewPager 的非当前页里。
     *
     * 只认 v1 的 androidx ViewPager（本工程的 SafeViewPager / ViewPagerFragmentAdapter 就是它）：
     * 页面是它的直接子 View，页号能用 indexOfChild 直接对上 currentItem。沿 parent 链找最近的
     * ViewPager，比的是「装我的那个直接子 View」的序号——离屏页可以有任意多个
     * （offscreenPageLimit = 页数），所以不能只判「差一页」。
     */
    private fun isInsideOffscreenPagerPage(view: View): Boolean {
        var child = view
        while (true) {
            val parent = child.parent ?: return false
            if (parent !is ViewGroup) return false
            if (parent is ViewPager) return parent.indexOfChild(child) != parent.currentItem
            child = parent
        }
    }

    /**
     * 一次兜底扫描的统计，只在「找不到目标」时由 [handlePage] 打进日志。
     *
     * [groups] 走过的 ViewGroup 数；[scrollable] 能滚的容器数；[hidden] 能滚但整条祖先链不可见
     * （含还在 GONE 的 fragment 根）；[noOverlap] 能滚且 isShown 但与窗口无交集或尺寸为 0；
     * [offscreenPage] 在 ViewPager 离屏页里；[fallback] 是宽松轮的退路。
     */
    private class ScanStats {
        var groups = 0
        var scrollable = 0
        var hidden = 0
        var noOverlap = 0
        var offscreenPage = 0
        var fallback: View? = null

        fun describe(root: View): String =
            "scanned=$groups scrollable=$scrollable hidden=$hidden " +
                "noOverlap=$noOverlap offscreenPage=$offscreenPage " +
                "root=${root.javaClass.simpleName}"
    }

    /** 「向上找可滚祖先」最多走几层。正常手表页面 10~20 层，32 只是防超深树。 */
    private const val MAX_ANCESTOR_DEPTH = 32

    /** [verticalScrollFactorOf] 的下限（dp）。AOSP 的 `config_verticalScrollFactor` 默认值就是 64dp。 */
    private const val SCROLL_FACTOR_FLOOR_DP = 64f

    /**
     * 控件级入口的落点解析：自己滚得动就用自己，滚不动就沿 `parent` 链向上找第一个滚得动的容器。
     *
     * 反馈 #235 的消息页就是这个结构：`RotaryNestedScrollView`（activity_message.xml:47）里再套一个
     * `RotaryRecyclerView`（activity_message.xml:358，`layout_height="match_parent"`），后者被
     * `NestedScrollView` 的 UNSPECIFIED 测成「刚好装下全部会话」，自己永远滚不动。于是事件落在内层时
     * 老代码「容器滚不动就交回框架」→ 框架不换算 mouse 源的轴值 → 每格只剩 DecorView 兜底那一次。
     * 找到祖先之后由 App 直接滚它，量与其它页面同一口径。
     *
     * 不会越过 DecorView 继续往上（`parent` 走到 `ViewRootImpl` 就不是 `View` 了），也选不到横向容器
     * （[isScrollable] 只认竖直）。
     */
    private fun resolveWidgetTarget(view: View): View? {
        if (isScrollable(view)) return view
        var child = view
        for (i in 1..MAX_ANCESTOR_DEPTH) {
            val parent = child.parent
            if (parent !is View) return null
            if (isScrollable(parent)) return parent
            child = parent
        }
        return null
    }

    /**
     * 按容器类型竖直平滑滚动 [view]。
     *
     * 控件级入口平时用调用方传进来的 λ（子类可能覆写 `smoothScrollBy`），只有「改滚祖先」时才走这里
     * ——祖先的类型是运行时才确定的，只能自己分发。[isScrollable] 已把范围限定在这三个类，所以返回
     * false 只是防御（类型判断与滚动能力判断不同步的瞬间）。
     */
    private fun smoothScrollVertically(view: View, dy: Int): Boolean = when (view) {
        is RecyclerView -> {
            view.smoothScrollBy(0, dy)
            true
        }

        is NestedScrollView -> {
            view.smoothScrollBy(0, dy)
            true
        }

        is ScrollView -> {
            view.smoothScrollBy(0, dy)
            true
        }

        else -> false
    }

    /**
     * 单次事件的位移封顶**一屏**（反馈 #235 的第三条改动）。
     *
     * #235 里用户把灵敏度一路调到 700：`700 × 基准` 远超 378×496 屏幕上那 496px 的可视高度，
     * `smoothScrollBy` 每隔 34ms 就被下一格打断、每次都撞到端点，看到的就是「一卡一卡、粘住」。
     * 一屏封顶不改灵敏度语义（乘数照旧生效，慢转该走多远还是多远），只是拒绝「一次跳过一屏以上」
     * ——那一段距离用户根本看不见，只会把容器的位置感和动画一起弄坏。
     *
     * 布局还没完成（`height == 0`）时不封顶：那时量不出可视高，封顶反而会误伤。
     */
    private fun clampToOneViewport(view: View, dy: Int): Int {
        val viewport = view.height - view.paddingTop - view.paddingBottom
        return RotaryEventPolicy.clampToViewport(dy, viewport)
    }

    private fun isScrollable(view: View): Boolean = when (view) {
        is RecyclerView, is ScrollView -> view.canScrollVertically(1) || view.canScrollVertically(-1)
        is NestedScrollView -> canScrollNestedVertically(view)
        else -> false
    }

    /**
     * `androidx.core.widget.NestedScrollView` 没有覆写 `canScrollVertically()`（它本质是 FrameLayout），
     * 继承自 View 的默认实现恒返回 false——于是页面级兜底永远挑不到它，日志里就是
     * 「兜底：本页找不到可滚容器」。反馈 #166 的消息页正是这个结构：外层 NestedScrollView 负责滚，
     * 里面的 RecyclerView 被撑成整页高、自己滚不动，于是两条路一起灭，表冠怎么转都没反应。
     *
     * 这里改成自己比「内容高 vs 视口高」。不看当前位置（已经到底也算可滚容器），
     * 因为调用方的语义是「这个容器能不能滚」，滚到头该由滚动的实现去 clamp。
     */
    private fun canScrollNestedVertically(view: NestedScrollView): Boolean {
        if (view.childCount == 0) return false
        val child = view.getChildAt(0) ?: return false
        val viewport = view.height - view.paddingTop - view.paddingBottom
        return viewport > 0 && child.height > viewport
    }

    private fun isCandidateVisible(view: View, rect: Rect, requireOnScreen: Boolean): Boolean {
        if (!view.isShown) return false
        if (!requireOnScreen) return true
        // isShown() 只看 VISIBILITY 位与 attach 状态，不看「是否真的落在可视区域内」：
        // ViewPager 的离屏页、被上层盖住的页都仍算 shown。这里再要求与窗口有实际交集。
        if (view.width <= 0 || view.height <= 0) return false
        return view.getGlobalVisibleRect(rect) && rect.height() > 0
    }
}

/**
 * 表冠来源的放宽判据：把「ROM 把表冠报成鼠标滚轮」这一档认出来（反馈 #197）。
 *
 * 背景：OPPO Watch 4 Pro 把表冠报成**鼠标滚轮**——action 仍是 ACTION_SCROLL，但 source 为
 * 0x2002（SOURCE_MOUSE），位移落在 AXIS_VSCROLL 上，量既有 ±1.0 也有 ±16.0。而
 * [RotaryEventPolicy.isRotaryEvent] 只认 SOURCE_ROTARY_ENCODER，于是三个入口全部在源闸门
 * 就 return，页面完全交给框架：框架对 NestedScrollView 用主题的 listPreferredItemHeight
 * （48dp）当一行，-16 一步就是十几行，用户看到的就是「一转过整页、没法精确控制」。
 *
 * 为什么不直接把 [RotaryEventPolicy.isRotaryEvent] 放宽：
 * 1. 它的 KDoc 写明「pointer 类来源判 false 是有意的保守」，且 RotaryEventPolicyTest 里已有
 *    「指针类来源不算表冠事件」的断言——改语义等于推翻既有用例；
 * 2. 判据和接管分开：这里只回答「这次事件像不像表冠」，接不接管由 [RotaryEncoderSupport]
 *    拿到容器、再看步长与容器状态决定（见 handle / handleSystemFirst 的 WHEEL 分支）。
 *
 * 本对象只吃 int 与 float，不 import android.*，可以直接上 JVM 单测。
 */
object CrownSourcePolicy {

    /** 不是表冠事件（触屏、摇杆、未知来源）。 */
    const val NONE = 0

    /** ROM 如实报成旋转编码器（绝大多数手表）。 */
    const val ENCODER = 1

    /** ROM 把表冠报成鼠标滚轮（OPPO Watch 4 Pro、部分 Wear 机型）。 */
    const val WHEEL = 2

    /** MotionEvent.ACTION_SCROLL。写字面量是为了不在本文件 import android.*，与 RotaryEventPolicy 同一套写法。 */
    private const val ACTION_SCROLL = 8

    /**
     * InputDevice.SOURCE_MOUSE（0x00002002，pointer 类 | bit 13）：鼠标，以及把表冠报成滚轮的 ROM
     * ——反馈 #197 的日志里就是 `src=0x2002` 这一个。
     *
     * 数值必须写对：`0x00020002` 是 SOURCE_MOUSE_RELATIVE（另一个常量），拿它当掩码去与 `0x2002`
     * 只会剩 pointer 类那一位 0x2，永远判不出来，等于这条修复完全失效。两个常量都要认——
     * 有的 ROM / 输入栈报相对位移时给的是 MOUSE_RELATIVE。
     */
    private const val SOURCE_MOUSE = 0x00002002

    /** InputDevice.SOURCE_MOUSE_RELATIVE（0x00020002，pointer 类 | bit 17）：相对位移鼠标。 */
    private const val SOURCE_MOUSE_RELATIVE = 0x00020002

    /**
     * 判定这次 generic motion 属于哪一类。
     *
     * 只看 ACTION_SCROLL（别的 action 不是滚动）。编码器优先：真实编码器即使同时带轴值也按
     * 编码器口径（每格 ×2）算，不让手感回退。鼠标源判成 [WHEEL] 之后仍要在调用方过「步长是
     * 不是够粗」「容器还能不能滚」两道闸，所以触控板那种连续小量不会被抢走。
     */
    fun kindOf(action: Int, source: Int): Int = when {
        action != ACTION_SCROLL -> NONE
        RotaryEventPolicy.isRotaryEvent(action, source) -> ENCODER
        (source and SOURCE_MOUSE) == SOURCE_MOUSE -> WHEEL
        (source and SOURCE_MOUSE_RELATIVE) == SOURCE_MOUSE_RELATIVE -> WHEEL
        // 触屏（0x1002）也带 pointer 类那一位，但它 and 上面两个掩码都只剩 0x2，落不到 WHEEL；
        // 触屏拖拽本来也不该被这里接管，保持 NONE 与既有单测一致。
        else -> NONE
    }

    /**
     * 这一格的轴值是不是「一格一跳」的滚轮量（|ratio| >= 0.9）。
     *
     * 实测数据（#197 日志）：OPPO Watch 4 Pro 的表冠一格是 AXIS_VSCROLL = ±1.0，部分 ROM 报
     * ±16.0；两者都是整格量。触控板 / 精细滚轮给的是 0.1 这种连续小量。
     *
     * **它现在只决定「谁先动手」，不再决定「接不接管」**（26.11.18-beta.3，反馈 #234）：
     * - 整格量 → App 在框架之前接管，否则框架按「行数」换算会一次跳掉整页（-16 就是 16 行）；
     * - 细量 → 先让框架跑，再用 [RotaryEventPolicy.topUpDelta] 补到滚轮口径，谁也不叠加。
     *
     * 以前细量是整段让给框架的，这个判据因此变成了「要不要处理这一格」；在把表冠报成鼠标、
     * 框架又根本不换算轴值的 ROM 上，让路等于整段交给一个不处理的人（#234「默认灵敏度完全
     * 动不了」）。阈值 0.9 本身没变，只是用途变了。
     */
    fun isCoarseWheelStep(ratio: Float): Boolean = ratio >= 0.9f || ratio <= -0.9f

    /**
     * 列表容器单次滚轮事件最多折算几格（26.11.18-beta.5 从 32 收到 2，反馈 #235 复测）。
     *
     * 32 是配「基准被 ROM 压成 1px」那版定的：那时一格只算 1px，倍数再大也不过几十像素。
     * 基准被 [RotaryEncoderSupport.verticalScrollFactorOf] 拉回 64dp 之后，一格 = 128px
     * （378×496 的表上约 1.3 个列表项），而快转一格报的 `AXIS_VSCROLL` 可达 -32.0：
     * 32 × 128 = 4096px，即便有「单次封顶一屏」也是一次跳满屏，用户看到的就是「一滑到底」。
     *
     * 所以速度**不再由单事件量级承担，而由事件数承担**：慢转一格 128px，快转连来几个事件就
     * 叠几格，与编码器机型的观感一致。留 2 格（256px ≈ 半个屏高）给「稍微快一点」的余量。
     */
    const val WHEEL_MAX_STEPS_LIST = 2f

    /**
     * 阅读容器（`NestedScrollView` 及其子类：视频详情简介、消息页这类）单次滚轮事件最多折算几格。
     *
     * **26.11.18-beta.5 从 2 收到 1**（反馈 #235 复测），理由与 [WHEEL_MAX_STEPS_LIST] 相同：
     * 这类容器拿主题的 `listPreferredItemHeight`（≈48dp ≈ 96px）当一行，基准修好后一格 128px
     * 已经接近 1.4 行，再乘 2 就是「转一格跳三行」——#197 原话「一转过整页、没法精确控制」。
     *
     * 这里原先写着「不能封到 1 格」，那条结论的前提是 **1px 基准**（1 格 = 1px，封到 1 就是
     * 完全不动的 #234）。基准修好后 1 格 = 128px，两个前提不是一回事。这个数字是手感参数，
     * 只能真机判，改动要记 L2。
     */
    const val WHEEL_MAX_STEPS_READING = 1f

    /**
     * 一次滚轮事件该滚多少像素。
     *
     * **保留量级、只封顶**（封到 [WHEEL_MAX_STEPS_LIST] / [WHEEL_MAX_STEPS_READING] 那点格子），
     * 不要写成「只取方向」：26.11.18-beta.1 用 stepOf 把量级整个抹掉，OPPO Watch 4 Pro 快转一格
     * 报 `AXIS_VSCROLL = -16.0`，框架给 16 行、App 只给 1 行，用户（#234）体感就是「表冠失效」。
     *
     * **但「保留量级」不等于「量级不设上限」**：26.11.18-beta.5 之前上限是 32 格，那是配 1px 基准
     * 定的；基准修回 128px 之后，一个 -32.0 的事件就是 4096px，用户（#235）直接报「一滑到底」。
     * 现在 32 → 2（列表）/ 1（阅读），量级只用来区分「一格」和「不到一格」，快转的加速交给事件数。
     *
     * **不乘 2**：`pixelDelta` 里那个 ×2 是 26.10.17 为「编码器一格」定的手感基准；滚轮与框架的
     * `config_verticalScrollFactor × 1` 同口径——#197 用户说「其他地方都正常」，那些地方走的正是它。
     *
     * @param ratio 这一格的轴值（带量级）
     * @param maxSteps 单次事件最多折算几格，见 [WHEEL_MAX_STEPS_LIST] / [WHEEL_MAX_STEPS_READING]
     */
    fun wheelScrollDelta(ratio: Float, scrollFactor: Float, multiple: Float, maxSteps: Float): Int {
        if (ratio == 0f || multiple <= 0f || maxSteps <= 0f) return 0
        val capped = ratio.coerceIn(-maxSteps, maxSteps)
        return (-capped * scrollFactor * multiple).roundToInt()
    }
}

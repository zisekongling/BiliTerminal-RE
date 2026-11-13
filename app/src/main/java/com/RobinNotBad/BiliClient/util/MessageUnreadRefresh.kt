package com.RobinNotBad.BiliClient.util

import android.os.SystemClock
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.api.MessageApi
import org.json.JSONException
import java.io.IOException

/**
 * 菜单未读角标的「回菜单增量刷新」（未读提示补齐，E）。
 *
 * <p>要解决的事：未读数原来只在 [BiliTerminal] 冷启动时算一次，之后一直躺在
 * `SharedPreferences` 里。进程常驻的手表上，用户从消息页返回菜单、或者待机时刚来了新私信，
 * 「消息 (N)」都得等下次冷启动才变，看着像坏了。
 *
 * <p>刻意**不做**的事：
 * - **不起后台定时器**。兄弟分支那套 60 秒轮询不抄：手表上后台唤醒的耗电代价远大于
 *   「晚几秒看到角标」的收益。触发点只有两个——冷启动（[BiliTerminal]）和回到菜单
 *   （`MenuActivity.onResume`）。
 * - **不管 IO**。这个类只回答两个问题：「这次该不该刷」「这条回来的响应还算不算数」。
 *   调接口 / 写 SP / 弹通知都在同文件的 [MessageUnreadRefresher] 里。
 *
 * <p>判据全在这里、且是纯 Kotlin（无 Context、不读资源），所以能直接在 JVM 单测里跑，
 * 见 `MessageUnreadRefreshTest`。错判的后果都不报错（多打接口被发现风控 / 少刷新只是角标不动），
 * 只能靠单测锁死。
 */
class MessageUnreadRefresh(private val minIntervalMs: Long = MIN_REFRESH_MS) {

    /**
     * 一次刷新请求的凭据：发出请求时抓一份，回来时原样交回。
     *
     * <p>三个字段缺一不可——[account] 挡住「切号后旧响应」，[revision] 挡住「中间发生过
     * reset / 别的响应已落账」，[sequence] 挡住「同一账号连续两次请求的回包乱序」。
     */
    data class Ticket(val account: Long, val revision: Long, val sequence: Long)

    /** 当前认可的账号（0 = 未登录）。 */
    private var account = 0L

    /** 每当有一次响应被接受、或账号状态被重置，就自增；旧凭据据此失效。 */
    private var revision = 0L

    /** 每发出一次请求自增，用来区分同一 revision 下的多张凭据。 */
    private var sequence = 0L

    /** 是否已有一次请求在飞（单飞判据）。 */
    private var inFlight = false

    /** 最近一次被接受的总未读数。 */
    private var total = 0

    /** [total] 是不是「本次会话真的拉回来的」——否则不能拿它当角标（可能属于上个账号）。 */
    private var fresh = false

    /** 上次真正发出请求的时间（单调时钟）；[NONE] = 从没发过。 */
    private var lastAttemptAt = NONE

    /** 上次请求对应的账号：换号后的第一次刷新不受最小间隔限制。 */
    private var lastAttemptAccount = 0L

    /**
     * 尝试领一张刷新凭据；返回 null 表示**这次不刷**。
     *
     * <p>四种不刷的理由，顺序即优先级：
     * 1. 未登录（[account] ≤ 0）——接口必然空跑，还会多一次风控机会；
     * 2. 已经有一次在飞（单飞）——菜单连续 onResume（切页回来、弹窗关闭）不该叠请求；
     * 3. 距上次请求不足 [minIntervalMs]——用户狂按返回键时别把接口当心跳；
     * 4. 不是 force 且被 3 挡住（[force] 给冷启动用：启动那一次永远要拉新的）。
     *
     * @param account 发起时的登录 mid（从 SP 读）
     * @param nowMs   单调时钟毫秒（调用方传 `SystemClock.elapsedRealtime()`）
     * @param force   true = 忽略最小间隔（冷启动）
     */
    @Synchronized
    fun begin(account: Long, nowMs: Long, force: Boolean = false): Ticket? {
        if (account <= 0L) return null
        // 换号：上一账号的未读数不能留在界面上，重置（同时让在飞的旧凭据作废）
        if (account != this.account) resetLocked(account)
        if (inFlight) return null
        if (!force && account == lastAttemptAccount && lastAttemptAt != NONE &&
            nowMs - lastAttemptAt < minIntervalMs
        ) return null
        lastAttemptAt = nowMs
        lastAttemptAccount = account
        inFlight = true
        return Ticket(account, revision, ++sequence)
    }

    /**
     * 交回一张凭据与拉到的未读数；返回本次认可的总数，**null = 这条响应已过期，必须丢弃**。
     *
     * <p>丢弃的典型场景：请求在飞的时候用户切了号 / 登出了。此时既不能写 SP（会把别人的
     * 未读数记到当前账号头上），也不能弹通知。
     *
     * @param currentAccount 响应回来**之后**重新读的 mid
     */
    @Synchronized
    fun complete(
        ticket: Ticket?,
        currentAccount: Long,
        messageUnread: Int,
        privateUnread: Int
    ): Int? {
        if (!valid(ticket, currentAccount)) return null
        revision++
        // 接口理论上不会给负数；真给了也不能让角标变成负的
        val sum = messageUnread.coerceAtLeast(0).toLong() + privateUnread.coerceAtLeast(0).toLong()
        total = sum.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        fresh = true
        inFlight = false
        return total
    }

    /**
     * 交回一张**失败**的凭据（超时 / 解析错）；返回 true 表示这次失败被采纳（凭据仍有效）。
     *
     * <p>和兄弟实现一个道理：请求失败不能证明用户读过了，所以**旧值原样保留、绝不清零**
     * （清零会让下一次成功把「老未读」当成新增未读，网络抖一次就重复弹通知——这条语义
     * 原本写在 [BiliTerminal] 的启动检查里）。失败只负责把单飞标记放掉。
     */
    @Synchronized
    fun fail(ticket: Ticket?, currentAccount: Long): Boolean {
        if (!valid(ticket, currentAccount)) return false
        revision++
        inFlight = false
        return true
    }

    /** 重置到某个账号（登出传 0）：丢弃已确认的未读数与在飞状态。 */
    @Synchronized
    fun reset(account: Long) = resetLocked(account)

    /** 当前可用的角标数字；账号不符或还没成功拉过一律 0。 */
    @Synchronized
    fun count(currentAccount: Long): Int =
        if (currentAccount > 0L && currentAccount == account && fresh) total else 0

    /** 是否有请求在飞。调用方据此判断「这次被挡掉后，还要不要等某次回包」。 */
    @Synchronized
    fun isInFlight(): Boolean = inFlight

    private fun resetLocked(newAccount: Long) {
        account = newAccount
        revision++
        sequence++
        total = 0
        fresh = false
        // 在飞的旧请求由 revision 作废；这里放掉单飞标记，免得换号后一直刷不动
        inFlight = false
    }

    private fun valid(ticket: Ticket?, currentAccount: Long): Boolean =
        ticket != null && currentAccount > 0L &&
                ticket.account == currentAccount && account == currentAccount &&
                ticket.revision == revision && ticket.sequence == sequence

    companion object {
        /** 同一账号两次刷新的最小间隔，沿用兄弟分支 MessageUnreadCounter 的取值（5 秒）。 */
        const val MIN_REFRESH_MS = 5000L

        /** 「从没发生过」的时间戳哨兵。单调时钟不会给出这个值。 */
        private const val NONE = Long.MIN_VALUE

        /** 该不该做未读检查：开关打开 **并且** 已登录。未登录时接口必然空跑。 */
        fun shouldCheck(checkEnabled: Boolean, account: Long): Boolean = checkEnabled && account > 0L

        /**
         * 按钮文案：没有未读时就是原名，有未读时按 [format] 拼成「消息 (3)」。
         *
         * <p>纯函数：[format] 由调用方从资源里取（`social_menu_unread_label`）再传进来，
         * 这样 util 层不必持有 Context，也能被 JVM 单测覆盖。
         */
        fun formatCountLabel(base: String, count: Int, format: String): String =
            if (count > 0) String.format(format, base, count) else base
    }
}

/**
 * [MessageUnreadRefresh] 的 Android 侧胶水：调接口、写 SP、弹通知、把结果送回界面。
 *
 * <p>没有 DI 也没有 ViewModel，所以这里用「进程内单例 + 观察者回调」的静态写法，
 * 与 [BangumiUpdateChecker] / [MsgNotifier] 同一套风格。观察者必须由界面在
 * `onPause` 里 [unobserve] 掉，否则静态表会留住已销毁的 Activity。
 *
 * <p>全流程都在 [CenterThreadPool] 的 IO 线程上跑，回调统一切回主线程；
 * 全程**没有**任何定时器 / Handler.postDelayed，刷新只由入口方法触发。
 */
object MessageUnreadRefresher {

    private const val TAG = "MessageUnreadRefresher"

    /** 进程内唯一的一份判据状态：冷启动检查与菜单刷新共用，避免刚启动就再发一遍。 */
    private val state = MessageUnreadRefresh()

    private val lock = Any()

    /** 等这次刷新结果回写按钮文案的界面回调（主线程执行）。 */
    private val observers = ArrayList<(Int) -> Unit>()

    /**
     * 回到菜单时调用：注册一个「未读刷新好了」的回调，并在允许时发起**一次**轻量刷新。
     *
     * <p>三种结局：
     * - 未登录 / 关了「消息数量检查」：不注册、不请求（按钮文案由调用方从 SP 读）；
     * - 发起了请求：回包（成功才回调）时通知；
     * - 被单飞或最小间隔挡掉：只注册；如果在飞的那次收尾会一并回调，否则立刻撤掉注册。
     *
     * @param onUpdated 主线程收到本次确认的总未读数
     */
    fun refreshOnMenuResume(onUpdated: (Int) -> Unit) {
        val account = account()
        if (!MessageUnreadRefresh.shouldCheck(checkEnabled(), account)) {
            // 关开关 / 登出后回来：别让上一次的角标继续挂着
            synchronized(lock) { observers.remove(onUpdated) }
            return
        }
        synchronized(lock) {
            observers.remove(onUpdated)
            observers.add(onUpdated)
        }
        val ticket = state.begin(account, SystemClock.elapsedRealtime())
        if (ticket == null) {
            // 刚刷过（没有在飞的请求）：调用方在 onResume 里已经读过 SP，不需要回调了
            if (!state.isInFlight()) synchronized(lock) { observers.remove(onUpdated) }
            return
        }
        CenterThreadPool.run { fetch(ticket) }
    }

    /** 撤销回调（界面 onPause 时调用）。 */
    fun unobserve(onUpdated: (Int) -> Unit) {
        synchronized(lock) { observers.remove(onUpdated) }
    }

    /**
     * 冷启动时的一次性检查（[BiliTerminal] 调，跑在 IO 线程里）。
     *
     * <p>force：启动那一次必须拉新的，不受最小间隔限制；但仍守单飞与登录态。
     */
    fun checkOnStartup() {
        val account = account()
        if (!MessageUnreadRefresh.shouldCheck(checkEnabled(), account)) return
        val ticket = state.begin(account, SystemClock.elapsedRealtime(), force = true) ?: return
        fetch(ticket)
    }

    /**
     * 拉一次未读并写回 SP。返回本次总数；失败或被判为过期返回 null。
     *
     * <p>必须在 IO 线程调用。
     */
    private fun fetch(ticket: MessageUnreadRefresh.Ticket) {
        // 先记下上次的未读数：只有"变多了"才弹通知（见 MsgNotifier.shouldNotify）
        val previousUnread = SharedPreferencesUtil.getInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, 0)
        val result = try {
            val messageUnread = MessageApi.checkMessageUnread()
            val privateUnread = MessageApi.checkPrivateMsgUnread()
            // 用**回来之后**的登录态校验这条响应：请求在飞时用户可能已经切号或登出
            val total = state.complete(ticket, account(), messageUnread, privateUnread)
            if (total != null) {
                SharedPreferencesUtil.putInt(SharedPreferencesUtil.MESSAGE_UPDATE_NUM, total)
                val notifyEnabled =
                    SharedPreferencesUtil.getBoolean(SettingsKeys.PRIVATE_MSG_NOTIFY_ENABLE, true)
                if (MsgNotifier.shouldNotify(previousUnread, total, notifyEnabled)) {
                    BiliTerminal.context?.let {
                        MsgNotifier.notifyNewMessages(it, privateUnread, messageUnread)
                    }
                }
            }
            total
        } catch (e: IOException) {
            state.fail(ticket, account())
            // 失败保留旧值不清零：清零会让下一次成功把"老未读"当成新增，网络抖一次就重复弹通知
            Logu.w(TAG, "未读检查失败: ${e.message}")
            null
        } catch (e: JSONException) {
            state.fail(ticket, account())
            Logu.w(TAG, "未读检查失败: ${e.message}")
            null
        }

        val callbacks: List<(Int) -> Unit> = synchronized(lock) {
            if (observers.isEmpty()) {
                emptyList()
            } else {
                // 取一份快照并清空：本次失败/过期时这些回调也一并丢弃（旧值本来就没变）
                ArrayList(observers).also { observers.clear() }
            }
        }
        if (result != null && callbacks.isNotEmpty()) {
            val accepted = result
            CenterThreadPool.runOnUiThread {
                for (callback in callbacks) callback(accepted)
            }
        }
    }

    private fun account(): Long = SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0)

    private fun checkEnabled(): Boolean =
        SharedPreferencesUtil.getBoolean(SharedPreferencesUtil.MESSAGE_UPDATE_CHECK_ENABLE, true)
}

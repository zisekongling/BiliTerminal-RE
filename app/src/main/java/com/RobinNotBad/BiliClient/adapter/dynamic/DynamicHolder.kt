package com.RobinNotBad.BiliClient.adapter.dynamic

import com.RobinNotBad.BiliClient.ui.appearance.ThemeTint
import com.RobinNotBad.BiliClient.util.UiText

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.SystemClock
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.RobinNotBad.BiliClient.BiliTerminal
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.api.ApiErrorText
import com.RobinNotBad.BiliClient.activity.ImageViewerActivity
import com.RobinNotBad.BiliClient.activity.base.BaseActivity
import com.RobinNotBad.BiliClient.activity.dynamic.send.SendDynamicActivity
import com.RobinNotBad.BiliClient.adapter.article.ArticleCardHolder
import com.RobinNotBad.BiliClient.adapter.video.VideoCardHolder
import com.RobinNotBad.BiliClient.api.DynamicApi
import com.RobinNotBad.BiliClient.model.ArticleCard
import com.RobinNotBad.BiliClient.model.Dynamic
import com.RobinNotBad.BiliClient.model.LiveRoom
import com.RobinNotBad.BiliClient.model.VoteInfo
import com.RobinNotBad.BiliClient.model.VoteOption
import com.RobinNotBad.BiliClient.model.VideoCard
import com.RobinNotBad.BiliClient.api.VoteApi
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.GlideUtil
import com.RobinNotBad.BiliClient.util.Logu
import com.RobinNotBad.BiliClient.util.LongPressPrefs
import com.RobinNotBad.BiliClient.util.SharedPreferencesUtil
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.StringUtil
import com.RobinNotBad.BiliClient.util.TerminalContext
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.ui.appearance.ColorScheme
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DecodeFormat
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.bumptech.glide.request.RequestOptions
import java.io.IOException
import java.util.ArrayList
import java.util.Collections
import java.util.HashSet

/**
 * 动态管理菜单里的一项。
 *
 * 以前这里是 `Pair<String, () -> Unit>`，菜单项的**身份**只能靠显示文案反查
 * （比较 "复制文字" / startsWith("删除")）。文案一旦迁进 strings.xml 或被本地化，反查就会静默失效：
 * 单项菜单不再直连复制界面、破坏性项也丢掉危险色。现在身份放进 [kind]，[text] 只用于展示。
 */
private data class ManageAction(
    val kind: ManageActionKind,
    val text: String,
    val danger: Boolean,
    val action: () -> Unit
)

/** 动态管理菜单的动作种类；只用于判定身份，不要拿来当显示文案。 */
private enum class ManageActionKind { COPY, EDIT, TOP, DELETE }

class DynamicHolder(itemView: View, val mActivity: BaseActivity, val isChild: Boolean) :
    RecyclerView.ViewHolder(itemView) {

    companion object {
        const val GO_TO_INFO_REQUEST = 71

        /**
         * 同一次手势内 click/longClick 去重窗口（毫秒）。
         *
         * 见 [lastManageAt]：400ms 足够覆盖「长按抬起后补发 click」，
         * 又明显短于用户有意连点两次的间隔，不会误吞正常操作。
         */
        private const val MANAGE_DEDUP_MS = 400L

        /**
         * 「评论」入口跳动态详情页时传给 `seekReply` 的值：0 表示**只切到评论页、不定位具体评论**。
         *
         * <p>为什么是 0：`OpusInfoActivity` / `DynamicInfoActivity` 都用 `seek_reply != -1` 决定
         * 是否把 ViewPager 切到评论页（-1 = 停在正文页），而 `ReplyApi.getRepliesLazy` 只在
         * `rpid > 0` 时才带 `seek_rpid` 参数——于是 0 正好等于「切页但不 seek」。
         * 这个约定在 `TerminalContext.enterDynamicDetailPage(context, id, position, seekReply)`
         * 里已有对应参数，直接复用、不新写页面。
         */
        private const val COMMENT_PAGE_ONLY = 0L

        // 动态点赞去重：记录正在请求中的 dynamicId。
        // 放在 companion 里而不是实例字段，是因为 ViewHolder 会被回收复用，
        // 实例字段会随着复用被重置而失去去重效果。用同步 Set 是因为它会被点击线程和请求线程同时访问。
        private val likingDynamicIds: MutableSet<Long> = Collections.synchronizedSet(HashSet<Long>())

        @JvmStatic
        fun removeDynamicFromList(
            dynamicList: List<Dynamic>, finalPosition: Int,
            adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
        ) {
            removeDynamicFromList(dynamicList, finalPosition, adapter, false)
        }

        @JvmStatic
        fun removeDynamicFromList(
            dynamicList: List<Dynamic>, finalPosition: Int,
            adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>, showRecentUp: Boolean
        ) {
            val mutableList = dynamicList as MutableList<Dynamic>
            mutableList.removeAt(finalPosition)
            val offset = if (showRecentUp) 2 else 1
            adapter.notifyItemRemoved(finalPosition + offset)
            adapter.notifyItemRangeChanged(finalPosition + offset, dynamicList.size - finalPosition)
        }

        /**
         * 列表版「管理」动作（**首选**入口）。
         *
         * <p>返回一个普通 lambda 而不是 `View.OnLongClickListener`，是为了让调用方用
         * [setManageAction] 把它挂给 holder，从而「点击」和「长按」共用同一条路径。
         * 直接用 `setOnLongClickListener` 覆盖 holder 内部入口的话，点击那条路会指向别处，
         * 两套行为就会分叉。
         */
        @JvmStatic
        fun getManageAction(
            activity: BaseActivity, dynamicList: List<Dynamic>,
            finalPosition: Int, adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
        ): () -> Unit = getManageAction(activity, dynamicList, finalPosition, adapter, false)

        @JvmStatic
        fun getManageAction(
            activity: BaseActivity, dynamicList: List<Dynamic>,
            finalPosition: Int, adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>, showRecentUp: Boolean
        ): () -> Unit {
            val offset = if (showRecentUp) 2 else 1
            return {
                showManageMenu(
                    activity, dynamicList[finalPosition],
                    onEdited = { newText ->
                        // 编辑接口只回 code、不回新动态，所以本地先把正文换掉。
                        // 只换正文（不重建 Span）是因为正文之外的东西本来也没法只靠一段字符串还原，
                        // 下次刷新会从服务端取回真正的内容。
                        dynamicList[finalPosition].content = newText
                        adapter.notifyItemChanged(finalPosition + offset)
                    },
                    onChanged = { adapter.notifyItemChanged(finalPosition + offset) },
                    onDeleted = { removeDynamicFromList(dynamicList, finalPosition, adapter, showRecentUp) }
                )
            }
        }

        /** 详情页版「管理」动作（**首选**入口），见 [getManageAction] 的说明。 */
        @JvmStatic
        fun getManageAction(
            activity: BaseActivity, dynamic: Dynamic,
            onEdited: ((String) -> Unit)?, onChanged: (() -> Unit)?
        ): () -> Unit = {
            showManageMenu(
                activity, dynamic, onEdited, onChanged,
                onDeleted = {
                    // 详情页沿用「改动完就带着结果退出去」的既有约定
                    activity.setResult(
                        Activity.RESULT_OK,
                        if (activity.intent.extras != null) Intent().putExtras(activity.intent.extras!!)
                        else Intent()
                    )
                    activity.finish()
                }
            )
        }

        @JvmStatic
        fun getManageAction(activity: BaseActivity, dynamic: Dynamic): () -> Unit =
            getManageAction(activity, dynamic, null, null)

        @JvmStatic
        fun getManageAction(
            activity: BaseActivity, dynamic: Dynamic, onEdited: ((String) -> Unit)?
        ): () -> Unit = getManageAction(activity, dynamic, onEdited, null)

        /**
         * 兼容入口：返回 `View.OnLongClickListener`。
         *
         * <p>保留是因为 `TopicDynamicAdapter`（不属于本次修改范围）仍在用它。
         * 新代码请用 [getManageAction] + [setManageAction]，这样点击和长按才同源。
         */
        @JvmStatic
        fun getManageListener(
            activity: BaseActivity, dynamicList: List<Dynamic>,
            finalPosition: Int, adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>
        ): View.OnLongClickListener {
            return getManageListener(activity, dynamicList, finalPosition, adapter, false)
        }

        /** 兼容入口，见 [getManageListener] 的说明。 */
        @JvmStatic
        fun getManageListener(
            activity: BaseActivity, dynamicList: List<Dynamic>,
            finalPosition: Int, adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>, showRecentUp: Boolean
        ): View.OnLongClickListener {
            val action = getManageAction(activity, dynamicList, finalPosition, adapter, showRecentUp)
            return View.OnLongClickListener {
                action()
                true
            }
        }

        /** 兼容入口，见 [getManageListener] 的说明。 */
        @JvmStatic
        fun getManageListener(activity: BaseActivity, dynamic: Dynamic): View.OnLongClickListener {
            return getManageListener(activity, dynamic, null)
        }

        /** 兼容入口，见 [getManageListener] 的说明。 */
        @JvmStatic
        fun getManageListener(
            activity: BaseActivity, dynamic: Dynamic, onEdited: ((String) -> Unit)?
        ): View.OnLongClickListener {
            return getManageListener(activity, dynamic, onEdited, null)
        }

        /** 兼容入口，见 [getManageListener] 的说明。 */
        @JvmStatic
        fun getManageListener(
            activity: BaseActivity, dynamic: Dynamic,
            onEdited: ((String) -> Unit)?, onChanged: (() -> Unit)?
        ): View.OnLongClickListener {
            val action = getManageAction(activity, dynamic, onEdited, onChanged)
            return View.OnLongClickListener {
                action()
                true
            }
        }

        /**
         * 动态的操作面板（「管理」按钮点击/长按、长按正文，都走这里）。
         *
         * <p>「复制」也是面板里的一项：长按正文不再直接把用户送进复制界面，而是先让他看见
         * 这一条动态能做哪些事。复制项只受 `copy_enable` 约束，与
         * `long_press_panel_enable`（决定长按走面板还是走复制）无关。
         *
         * <p>其余项只在服务端下发的三点菜单允许时才给出：`canEdit` 对应 THREE_POINT_EDIT、
         * `canDelete` 对应 THREE_POINT_DELETE，两个开关是独立的，不能互相顶替。
         * 「置顶 / 取消置顶」只对自己的动态有意义，而「自己的动态」在客户端能拿到的唯一可靠信号
         * 就是 `canDelete`（别人的动态不会下发 THREE_POINT_DELETE），所以用它当门槛。
         *
         * @param onEdited  编辑成功后怎么把新正文刷到界面上（可为 null，表示只弹提示）
         * @param onChanged 置顶状态变掉后怎么刷新这一条（可为 null）
         * @param onDeleted 删除成功后怎么从界面里拿掉这条动态
         */
        @JvmStatic
        fun showManageMenu(
            activity: BaseActivity, dynamic: Dynamic,
            onEdited: ((String) -> Unit)?, onChanged: (() -> Unit)?, onDeleted: () -> Unit
        ) {
            val actions = ArrayList<ManageAction>()
            // 「复制」收进面板：长按正文时用户先看见有哪些操作，而不是被直接送进复制界面。
            // 受 copy_enable 约束，正文为空时也不列（点了只会看到一个空界面）。
            // 注意本方法同时服务「管理」按钮和长按正文两个入口，所以复制项只按 copy_enable
            // 决定——`long_press_panel_enable` 管的是「长按走哪条路」，不是「面板里有什么」。
            val content = dynamic.content?.toString().orEmpty()
            if (LongPressPrefs.shouldOfferCopy(StringUtil.isCopyEnabled(), content)) {
                actions.add(
                    ManageAction(ManageActionKind.COPY, UiText.of("复制文字"), false) {
                        StringUtil.openCopyPage(activity, content)
                    }
                )
            }
            if (dynamic.canEdit) {
                actions.add(
                    ManageAction(ManageActionKind.EDIT, UiText.of("编辑动态"), false) {
                        launchEdit(activity, dynamic, onEdited)
                    }
                )
            }
            if (dynamic.canDelete) {
                actions.add(
                    ManageAction(
                        ManageActionKind.TOP,
                        if (dynamic.isTop) UiText.of("取消置顶") else UiText.of("置顶动态"),
                        false
                    ) { toggleTop(activity, dynamic, onChanged) }
                )
            }
            if (dynamic.canDelete) {
                actions.add(
                    ManageAction(ManageActionKind.DELETE, UiText.of("删除动态"), true) {
                        confirmDelete(activity, dynamic, onDeleted)
                    }
                )
            }
            if (actions.isEmpty()) {
                MsgUtil.showMsg(UiText.of("没有可操作的项"))
                return
            }
            // 只剩「复制文字」一项时，不必弹一个只有单选项的菜单——直接进复制界面。
            // 别人的动态（都是 canEdit=false / canDelete=false）就是这种情况：以前长按会弹一个
            // 孤零零的「复制文字」，多一次点击才拿到本来就该直接给的东西。
            if (actions.size == 1 && actions[0].kind == ManageActionKind.COPY) {
                actions[0].action()
                return
            }
            // 破坏性项由动作类型标出危险色，不再匹配文案前缀
            TerminalDialog.menu(
                context = activity,
                items = actions.map { it.text },
                danger = actions.indices.filter { actions[it].danger }.toSet()
            ) { which -> actions[which].action() }.show()
        }

        private fun launchEdit(activity: BaseActivity, dynamic: Dynamic, onEdited: ((String) -> Unit)?) {
            val text = dynamic.content?.toString().orEmpty()
            if (text.isEmpty()) {
                MsgUtil.showMsg(UiText.of("这条动态没有能编辑的正文"))
                return
            }
            // ActivityResultLauncher 只能在 Activity 上注册一次，没有 per-holder 的回调注册点，
            // 所以借用 Activity 上留的一个槽位，把「改完怎么刷新」传进去，由 Launcher 回调取走
            activity.pendingDynamicEdit = onEdited
            val intent = Intent(activity, SendDynamicActivity::class.java)
            intent.putExtra("edit_dyn_id", dynamic.dynamicId)
            intent.putExtra("edit_text", text)
            activity.editDynamicLauncher.launch(intent)
        }

        /**
         * 置顶 / 取消置顶。
         *
         * <p>服务端在空间里只保留一条置顶（再置顶会顶掉原来那条），所以这里只需按当前状态取反。
         * 成功后本地立刻翻 `isTop` 并刷新——首页/空间列表的「置顶」标记来自服务端下发的
         * `module_tag`，不刷新的话用户下一次长按菜单还是旧文案。
         */
        private fun toggleTop(activity: BaseActivity, dynamic: Dynamic, onChanged: (() -> Unit)?) {
            val top = !dynamic.isTop
            CenterThreadPool.run {
                try {
                    val code = DynamicApi.setDynamicTop(dynamic.dynamicId, top)
                    activity.runOnUiThread {
                        if (code == 0) {
                            dynamic.isTop = top
                            onChanged?.invoke()
                            MsgUtil.showMsg(DynamicApi.topSuccessMsg(top))
                        } else {
                            MsgUtil.showMsg(DynamicApi.topErrorMsg(code).ifEmpty { ApiErrorText.opFailedBrief(code) })
                        }
                    }
                } catch (e: Exception) {
                    activity.runOnUiThread { MsgUtil.err(e) }
                }
            }
        }

        private fun confirmDelete(activity: BaseActivity, dynamic: Dynamic, onDeleted: () -> Unit) {
            TerminalDialog.confirm(
                context = activity,
                title = UiText.of("删除动态"),
                message = UiText.of("删除后无法恢复，确定删除这条动态吗？"),
                confirmText = UiText.of("删除")
            ) {
                CenterThreadPool.run {
                    try {
                        val result = DynamicApi.deleteDynamic(dynamic.dynamicId)
                        if (result == 0) {
                            activity.runOnUiThread {
                                onDeleted()
                                MsgUtil.showMsg(UiText.of("删除成功~"))
                            }
                        } else {
                            var msg = ApiErrorText.opFailedWithDetail(result)
                            when (result) {
                                500404 -> msg = UiText.of("已经删除过了哦~")
                                500406 -> msg = UiText.of("不是自己的动态！")
                            }
                            val finalMsg = msg
                            activity.runOnUiThread { MsgUtil.showMsg(finalMsg) }
                        }
                    } catch (e: IOException) {
                        activity.runOnUiThread { MsgUtil.err(e) }
                    }
                }
            }.show()
        }
    }

    val username: TextView
    val content: TextView
    val title: TextView
    var pubdate: TextView? = null
    val avatar: ImageView
    val extraCard: LinearLayout
    val cell_dynamic_video: View
    val cell_dynamic_image: View
    val cell_dynamic_article: View
    var item_dynamic_share: TextView? = null
    var item_dynamic_delete: TextView? = null
    var likeCount: TextView? = null

    /** 卡片操作栏的「评论」入口（只有父卡片有：子转发卡片 `cell_dynamic_child` 没有操作栏）。 */
    var item_dynamic_comment: TextView? = null
    lateinit var cell_dynamic_child: View
    var relayDynamicLauncher: ActivityResultLauncher<Intent>? = null
    var childDynamicHolder: DynamicHolder? = null

    /**
     * 「管理」这一条动作的**唯一实现**，由适配器/详情页在绑定时挂上来。
     *
     * <p>之所以存成一个回调而不是直接存 `View.OnLongClickListener`：点一下和长按要走到
     * **完全相同**的一段代码，且必须能被"去重"（见 [lastManageAt]）。如果把
     * `OnLongClickListener` 存下来、点击时用 `performLongClick()` 转发，长按手势在结束时
     * 常会再补一次 click，于是会弹出**两个**菜单。这里存成普通 lambda，再用时间戳守卫拦掉
     * 紧邻的第二次触发。
     */
    private var manageAction: (() -> Unit)? = null

    /**
     * 上一次弹管理菜单的时间戳，用来拦掉同一次手势里的重复触发。
     *
     * <p>场景：长按按钮时 Android 会先派发 longClick，手势抬起后部分机型/父容器
     * 还会再补一个 click；「点一下」和「长按」既然都绑在同一个动作上，就必须保证
     * 这一对事件只弹一次菜单。[MANAGE_DEDUP_MS] 是同一个手势内两次事件的最大间隔，
     * 取 400ms：足够覆盖 click 紧随 longClick 的补发，又远小于用户有意连点两次的间隔。
     */
    private var lastManageAt = 0L
    private var videoCardHolder: VideoCardHolder? = null
    private var articleCardHolder: ArticleCardHolder? = null
    private var lastAvatarUrl: String? = null
    private var lastImageUrl: String? = null

    // 投票相关视图
    var cell_dynamic_vote: View? = null
    var voteTitle: TextView? = null
    var voteOptionsContainer: LinearLayout? = null
    var voteJoinNum: TextView? = null
    var voteStatus: TextView? = null
    private var currentVoteInfo: VoteInfo? = null

    /**
     * 复用前清掉「同 URL 跳过加载」的缓存，供 Adapter.onViewRecycled 调用。
     *
     * 不清的话，被子 ViewHolder 回收复用时 lastAvatarUrl / lastImageUrl 还留着上一条动态的值，
     * 新动态如果恰好用了同一个头像或配图 URL 会被跳过加载，于是显示上一条的残留图。
     */
    fun clearImageCache() {
        lastAvatarUrl = null
        lastImageUrl = null
    }

    /**
     * 挂上「管理」动作，并保证它的入口只有这一个。
     *
     * <p>由适配器/详情页在绑定时调用（替代过去直接 `item_dynamic_delete.setOnLongClickListener`）。
     * 挂上之后 [itemView] 上的「管理」按钮与动态正文的长按都走这里，**点击与长按同一条路径**。
     */
    fun setManageAction(action: (() -> Unit)?) {
        manageAction = action
        // 复用时旧的时间戳要清掉，否则连续复用同一个 holder 绑定两条动态时，
        // 第二条动态的第一次点击可能被上一条留下的时间戳误判成重复触发而吞掉。
        lastManageAt = 0L
    }

    /**
     * 触发管理菜单（点击 / 长按共用入口）。
     *
     * <p>带手势去重：同一次长按手势里 longClick 与随后的 click 只会真正执行一次。
     * 有意连点两次（间隔 > [MANAGE_DEDUP_MS]）不受影响。
     *
     * @return 是否真的执行了（供 OnLongClickListener 判断要不要消费事件）
     */
    fun showManage(): Boolean {
        val action = manageAction ?: return false
        val now = SystemClock.uptimeMillis()
        if (now - lastManageAt < MANAGE_DEDUP_MS) return true
        lastManageAt = now
        action()
        return true
    }

    init {
        if (isChild) {
            username = itemView.findViewById(R.id.child_username)
            content = itemView.findViewById(R.id.child_content)
            avatar = itemView.findViewById(R.id.child_avatar)
            title = itemView.findViewById(R.id.child_title)
            extraCard = itemView.findViewById(R.id.child_extraCard)
            this.cell_dynamic_video = extraCard.findViewById(R.id.dynamic_video_child)
            this.cell_dynamic_article = extraCard.findViewById(R.id.dynamic_article_child)
            this.cell_dynamic_image = extraCard.findViewById(R.id.dynamic_image_child)
            this.cell_dynamic_child = itemView
            // 投票视图（子布局）
            val voteView = extraCard.findViewById<View>(R.id.dynamic_vote_child)
            this.cell_dynamic_vote = voteView
            if (voteView != null) {
                this.voteTitle = voteView.findViewById(R.id.vote_title)
                this.voteOptionsContainer = voteView.findViewById(R.id.vote_options_container)
                this.voteJoinNum = voteView.findViewById(R.id.vote_join_num)
                this.voteStatus = voteView.findViewById(R.id.vote_status)
            }
        } else {
            username = itemView.findViewById(R.id.username)
            pubdate = itemView.findViewById(R.id.pubdate)
            content = itemView.findViewById(R.id.content)
            avatar = itemView.findViewById(R.id.avatar)
            title = itemView.findViewById(R.id.title)
            extraCard = itemView.findViewById(R.id.extraCard)
            item_dynamic_share = itemView.findViewById(R.id.item_dynamic_share)
            likeCount = itemView.findViewById(R.id.likes)
            item_dynamic_delete = itemView.findViewById(R.id.item_dynamic_delete)
            item_dynamic_comment = itemView.findViewById(R.id.item_dynamic_comment)
            relayDynamicLauncher = mActivity.relayDynamicLauncher
            this.cell_dynamic_child = extraCard.findViewById(R.id.dynamic_child)
            this.cell_dynamic_video = extraCard.findViewById(R.id.dynamic_video_extra)
            this.cell_dynamic_article = extraCard.findViewById(R.id.dynamic_article_extra)
            this.cell_dynamic_image = extraCard.findViewById(R.id.dynamic_image_extra)
            // 投票视图（父布局）
            val voteView = extraCard.findViewById<View>(R.id.dynamic_vote_extra)
            this.cell_dynamic_vote = voteView
            if (voteView != null) {
                this.voteTitle = voteView.findViewById(R.id.vote_title)
                this.voteOptionsContainer = voteView.findViewById(R.id.vote_options_container)
                this.voteJoinNum = voteView.findViewById(R.id.vote_join_num)
                this.voteStatus = voteView.findViewById(R.id.vote_status)
            }
        }
    }

    /**
     * 渲染投票卡片
     */
    @SuppressLint("SetTextI18n")
    private fun showVoteCard(context: Context, voteInfo: VoteInfo) {
        currentVoteInfo = voteInfo
        val voteView = cell_dynamic_vote ?: return
        val titleView = voteTitle ?: return
        val container = voteOptionsContainer ?: return
        val joinNumView = voteJoinNum ?: return
        val statusView = voteStatus ?: return

        // 设置标题
        titleView.text = if (voteInfo.title.isNotEmpty()) voteInfo.title else UiText.of("投票")

        // 设置参与人数
        joinNumView.text = UiText.fmt("%1\$s人参与", voteInfo.join_num)

        // 清空并重新填充选项
        container.removeAllViews()

        val isExpired = voteInfo.isExpired()
        val hasVoted = voteInfo.hasVoted()
        val isSingleChoice = voteInfo.choice_cnt <= 1

        // 设置状态文字
        statusView.text = when {
            isExpired -> UiText.of("已结束")
            hasVoted -> UiText.of("已投票")
            else -> UiText.of("投票")
        }

        // 动态 feed 里的投票信息通常不带 options，需异步拉取完整投票信息
        if (voteInfo.options.isEmpty()) {
            fetchFullVoteInfo(context, voteInfo)
            voteView.visibility = View.VISIBLE
            return
        }

        for (option in voteInfo.options) {
            val optionView = createVoteOptionView(context, option, voteInfo, isExpired, hasVoted)
            container.addView(optionView)
        }

        voteView.visibility = View.VISIBLE
    }

    /**
     * 异步拉取完整投票信息（动态 feed 的 vote 只有 vote_id，无 options）
     */
    private fun fetchFullVoteInfo(context: Context, voteInfo: VoteInfo) {
        CenterThreadPool.run {
            try {
                val full = VoteApi.getVoteInfo(voteInfo.vote_id)
                if (full != null) {
                    // 合并完整信息
                    voteInfo.title = full.title
                    voteInfo.desc = full.desc
                    voteInfo.join_num = full.join_num
                    voteInfo.type = full.type
                    voteInfo.choice_cnt = full.choice_cnt
                    voteInfo.end_time = full.end_time
                    voteInfo.status = full.status
                    voteInfo.my_votes.clear()
                    voteInfo.my_votes.addAll(full.my_votes)
                    voteInfo.options.clear()
                    voteInfo.options.addAll(full.options)
                    // 用 mActivity 稳定回主线程，避免 context 非 Activity 时强转失败导致不刷新
                    mActivity.runOnUiThread {
                        showVoteCard(context, voteInfo)
                    }
                }
            } catch (e: Exception) {
                Logu.d("DynamicVote", "获取投票详情失败: " + e.message)
            }
        }
    }

    /**
     * 创建单个投票选项视图
     */
    @SuppressLint("SetTextI18n")
    private fun createVoteOptionView(
        context: Context,
        option: VoteOption,
        voteInfo: VoteInfo,
        isExpired: Boolean,
        hasVoted: Boolean
    ): View {
        val optionView = LayoutInflater.from(context).inflate(R.layout.item_vote_option, null)
        val optionText = optionView.findViewById<TextView>(R.id.option_text)
        val optionIndicator = optionView.findViewById<TextView>(R.id.option_indicator)

        // 图片投票 - 裁剪缩略图与文字并排
        if (!option.img_url.isNullOrEmpty()) {
            val thumb = optionView.findViewById<ImageView>(R.id.option_thumb)
            thumb.visibility = View.VISIBLE
            Glide.with(context)
                .load(option.img_url)
                .apply(RequestOptions().format(DecodeFormat.PREFER_RGB_565).centerCrop())
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(thumb)
        }

        optionText.text = option.opt_desc

        // 已投票或投票结束后显示各选项得票数和比例
        if (isExpired || hasVoted) {
            val totalCnt = voteInfo.options.sumOf { it.cnt }
            if (totalCnt > 0) {
                val percent = (option.cnt * 100f / totalCnt).toInt()
                optionText.text = UiText.fmt("%1\$s  %2\$s票 (%3\$s%%)", option.opt_desc, option.cnt, percent)
            }
        }

        // 判断是否已选此选项
        val isSelected = voteInfo.my_votes.contains(option.opt_idx)

        if (isSelected) {
            // 原 bg_vote_option_selected 写死 #FE679A，这里按主题主色现建（0.5dp 描边 + 12% 淡底 + 6dp 圆角，与原件一致）
            optionView.background = ThemeTint.outlinedBox(context, ColorScheme.PRIMARY, 6f, 0.5f, 0x20)
            optionIndicator.text = "✓"
            optionIndicator.setTextColor(ColorScheme.PRIMARY)
        } else {
            optionView.setBackgroundResource(R.drawable.bg_vote_option)
            optionIndicator.text = if (isSingleChoice(voteInfo)) "○" else "□"
            optionIndicator.setTextColor(ColorScheme.TEXT_SECONDARY)
        }

        // 投票结束或已投票时禁止点击
        if (isExpired || hasVoted) {
            optionView.isEnabled = false
            optionView.alpha = 0.7f
            return optionView
        }

        // 点击选项进行投票
        optionView.setOnClickListener { v ->
            // 未登录时提示
            if (SharedPreferencesUtil.getLong(SharedPreferencesUtil.mid, 0) == 0L) {
                MsgUtil.showMsg(UiText.of("请先登录"))
                return@setOnClickListener
            }
            v.isEnabled = false
            CenterThreadPool.run {
                try {
                    val result = VoteApi.doVote(voteInfo.vote_id, listOf(option.opt_idx))
                    if (result == 0) {
                        // 投票成功，刷新投票信息
                        try {
                            val updatedVote = VoteApi.getVoteInfo(voteInfo.vote_id)
                            if (updatedVote != null) {
                                voteInfo.join_num = updatedVote.join_num
                                // 本地先标记已投（加入本次选中的选项），确保无论接口是否返回 my_votes 都锁定不可重复投
                                voteInfo.my_votes.clear()
                                if (!voteInfo.my_votes.contains(option.opt_idx)) {
                                    voteInfo.my_votes.add(option.opt_idx)
                                }
                                if (!updatedVote.my_votes.isEmpty()) {
                                    for (v in updatedVote.my_votes) {
                                        if (!voteInfo.my_votes.contains(v)) voteInfo.my_votes.add(v)
                                    }
                                }
                                // 同步更新各选项得票数
                                voteInfo.options.clear()
                                voteInfo.options.addAll(updatedVote.options)
                                (context as? Activity)?.runOnUiThread {
                                    showVoteCard(context, voteInfo)
                                    MsgUtil.showMsg(UiText.of("投票成功~"))
                                }
                            }
                        } catch (e: Exception) {
                            (context as? Activity)?.runOnUiThread {
                                MsgUtil.err(e)
                                v.isEnabled = true
                            }
                        }
                    } else {
                        val msg = when (result) {
                            -111 -> UiText.of("需要重新登录")
                            else -> UiText.fmt("投票失败：%1\$s", result)
                        }
                        (context as? Activity)?.runOnUiThread {
                            MsgUtil.showMsg(msg)
                            v.isEnabled = true
                        }
                    }
                } catch (e: Exception) {
                    (context as? Activity)?.runOnUiThread {
                        MsgUtil.err(e)
                        v.isEnabled = true
                    }
                }
            }
        }

        return optionView
    }

    private fun isSingleChoice(voteInfo: VoteInfo): Boolean {
        return voteInfo.choice_cnt <= 1
    }

    @SuppressLint("SetTextI18n", "ClickableViewAccessibility")
    fun showDynamic(context: Context, dynamic: Dynamic, clickable: Boolean) {
        if (!TextUtils.isEmpty(dynamic.title)) {
            title.visibility = View.VISIBLE
            title.text = dynamic.title
        } else
            title.visibility = View.GONE

        username.text = dynamic.userInfo.name
        if (!dynamic.userInfo.vip_nickname_color.isEmpty()) {
            username.setTextColor(Color.parseColor(dynamic.userInfo.vip_nickname_color))
        } else {
            username.setTextColor(ColorScheme.TEXT_PRIMARY)
        }
        if (pubdate != null)
            pubdate!!.text = dynamic.pubTime
        if (dynamic.content != null && !TextUtils.isEmpty(dynamic.content)) {
            content.visibility = View.VISIBLE
            content.text = dynamic.content
            // 长按正文：开关打开时弹操作面板，「复制」就在面板里（见 [showManageMenu]）；
            // 开关关闭时才退回旧行为——直接打开复制界面。
            // 之前是「可管理就 showManage、不可管理交给 setCopy」，同一个手势两种结果，
            // 用户在自己动态上长按时根本猜不到会发生哪一件。
            if (LongPressPrefs.isEnabled()) {
                content.setOnLongClickListener { showManage() }
            } else {
                StringUtil.setCopy(content)
            }
            content.setOnTouchListener(StringUtil.ClickableSpanTouchListener.getInstance())
        } else
            content.visibility = View.GONE

        if (dynamic.userInfo.avatar != lastAvatarUrl) {
            lastAvatarUrl = dynamic.userInfo.avatar
            GlideUtil.with(avatar).asDrawable().load(GlideUtil.url(dynamic.userInfo.avatar))
                .transition(GlideUtil.getTransitionOptions())
                .placeholder(R.mipmap.akari)
                .error(R.mipmap.akari)
                .apply(RequestOptions.circleCropTransform())
                .format(DecodeFormat.PREFER_RGB_565)
                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                .override(80, 80)
                .into(avatar)
        }

        avatar.setOnClickListener {
            BiliTerminal.jumpToUser(context, dynamic.userInfo.mid)
        }

        var isPgc = false
        if (!isChild) cell_dynamic_child.visibility = View.GONE
        cell_dynamic_video.visibility = View.GONE
        cell_dynamic_image.visibility = View.GONE
        cell_dynamic_article.visibility = View.GONE
        cell_dynamic_vote?.visibility = View.GONE
        if (dynamic.major_type != null)
            when (dynamic.major_type) {
                "MAJOR_TYPE_PGC" -> isPgc = true
                "MAJOR_TYPE_ARCHIVE", "MAJOR_TYPE_UGC_SEASON" -> {
                    val childVideoCard = dynamic.major_object as VideoCard
                    if (videoCardHolder == null) {
                        videoCardHolder = VideoCardHolder(cell_dynamic_video)
                    }
                    videoCardHolder!!.showVideoCard(childVideoCard, context)
                    val finalIsPgc = isPgc
                    cell_dynamic_video.setOnClickListener {
                        TerminalContext.getInstance()
                            .enterVideoDetailPage(context, childVideoCard.aid, "", if (finalIsPgc) "media" else null)
                    }
                    cell_dynamic_video.visibility = View.VISIBLE
                }

                "MAJOR_TYPE_LIVE", "MAJOR_TYPE_LIVE_RCMD" -> {
                    val liveRoom = dynamic.major_object as LiveRoom
                    val childLiveCard = VideoCard()
                    childLiveCard.title = liveRoom.title
                    childLiveCard.cover = liveRoom.cover
                    childLiveCard.upName = liveRoom.uname
                    childLiveCard.view = ""
                    childLiveCard.type = "live"

                    if (videoCardHolder == null) {
                        videoCardHolder = VideoCardHolder(cell_dynamic_video)
                    }
                    videoCardHolder!!.showVideoCard(childLiveCard, context)
                    cell_dynamic_video.setOnClickListener {
                        TerminalContext.getInstance().enterLiveDetailPage(context, liveRoom.roomid)
                    }
                    cell_dynamic_video.visibility = View.VISIBLE
                }

                "MAJOR_TYPE_ARTICLE" -> {
                    val articleCard = dynamic.major_object as ArticleCard
                    if (articleCardHolder == null) {
                        articleCardHolder = ArticleCardHolder(cell_dynamic_article)
                    }
                    articleCardHolder!!.showArticleCard(articleCard, context)
                    cell_dynamic_article.setOnClickListener {
                        TerminalContext.getInstance().enterArticleDetailPage(context, articleCard.id)
                    }
                    cell_dynamic_article.visibility = View.VISIBLE
                }

                "MAJOR_TYPE_DRAW", "MAJOR_TYPE_OPUS" -> {
                    val pictureList: ArrayList<String> = if (dynamic.major_object is ArrayList<*>) {
                        dynamic.major_object as ArrayList<String>
                    } else {
                        ArrayList()
                    }

                    if (!pictureList.isEmpty()) {
                        val imageView = cell_dynamic_image.findViewById<ImageView>(R.id.imageView)
                        val firstImageUrl = pictureList[0]
                        if (firstImageUrl != lastImageUrl) {
                            lastImageUrl = firstImageUrl
                            // 动态配图：列表封面级尺寸，用 url_hq 且不写死 override，
                            // 否则 400×400 的绝对像素在大屏上被放大发虚
                            GlideUtil.with(imageView).asDrawable().load(GlideUtil.url_hq(firstImageUrl))
                                .transition(GlideUtil.getTransitionOptions())
                                .placeholder(R.mipmap.placeholder)
                                .error(R.mipmap.placeholder)
                                .centerCrop()
                                .format(DecodeFormat.PREFER_ARGB_8888)
                                .diskCacheStrategy(DiskCacheStrategy.AUTOMATIC)
                                .into(imageView)
                        }
                        val textView = cell_dynamic_image.findViewById<TextView>(R.id.imageCount)
                        textView.text = UiText.fmt(UiText.of("共%1\$s张图片"), pictureList.size)
                        imageView.setOnClickListener {
                            val intent = Intent()
                            intent.setClass(context, ImageViewerActivity::class.java)
                            intent.putExtra("imageList", pictureList)
                            context.startActivity(intent)
                        }
                        cell_dynamic_image.visibility = View.VISIBLE
                    }
                }
            }

        // 渲染投票卡片
        if (dynamic.additional_type == "ADDITIONAL_TYPE_VOTE" && dynamic.vote != null) {
            showVoteCard(context, dynamic.vote!!)
        } else {
            cell_dynamic_vote?.visibility = View.GONE
        }

        if (dynamic.major_object == null && dynamic.dynamic_forward == null && dynamic.vote == null)
            extraCard.visibility = View.GONE
        else
            extraCard.visibility = View.VISIBLE

        if (clickable) {
            content.maxLines = 5
            if (dynamic.dynamicId != 0L) {
                (if (isChild) itemView.findViewById<View>(R.id.dynamic_child) else itemView)
                    .setOnClickListener {
                        if (context is Activity) {
                            TerminalContext.getInstance().enterDynamicDetailPageForResult(
                                context as Activity,
                                dynamic.dynamicId, adapterPosition, GO_TO_INFO_REQUEST
                            )
                        } else {
                            TerminalContext.getInstance().enterDynamicDetailPage(
                                context, dynamic.dynamicId,
                                adapterPosition
                            )
                        }
                    }
                content.setOnClickListener {
                    val targetView =
                        (if (isChild) itemView.findViewById<View>(R.id.dynamic_child) else itemView)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH_MR1) {
                        targetView.callOnClick()
                    } else {
                        targetView.performClick()
                    }
                }
            }
        } else {
            content.maxLines = 999
        }
        content.ellipsize = TextUtils.TruncateAt.END

        val onRelayClick = View.OnClickListener {
            if (relayDynamicLauncher == null) {
                return@OnClickListener
            }
            val intent = Intent()
            intent.setClass(mActivity, SendDynamicActivity::class.java)
            intent.putExtra("dynamicId", dynamic.dynamicId)
            // 转发自动引用所需的信息：SendDynamicActivity 完成时会把启动时的 extras 原样回传，
            // 由 DynamicActivity.getRelayDynamicLauncher 取出并拼成 //@原作者:原内容
            if (dynamic.userInfo != null) {
                intent.putExtra("forwardAuthorName", dynamic.userInfo.name)
                intent.putExtra("forwardAuthorMid", dynamic.userInfo.mid)
            }
            if (dynamic.content != null) intent.putExtra("forwardContentText", dynamic.content.toString())
            TerminalContext.getInstance().setForwardContent(dynamic)
            relayDynamicLauncher!!.launch(intent)
        }
        if (item_dynamic_share != null && clickable)
            item_dynamic_share!!.setOnClickListener(onRelayClick)

        // 「管理」入口：点一下或长按都弹同一个菜单，最终都进 [showManage]。
        // 动作本身由适配器/详情页通过 [setManageAction] 挂上来——菜单要的回调
        // （怎么刷新列表、怎么退出页面）只有它们知道。这里不另存一份 OnLongClickListener，
        // 免得点一下和长按弹出两套行为；[showManage] 内部会拦掉同一次手势的重复触发。
        if (item_dynamic_delete != null) {
            item_dynamic_delete!!.visibility = View.GONE
            item_dynamic_delete!!.setOnClickListener { showManage() }
            item_dynamic_delete!!.setOnLongClickListener { showManage() }
        }

        if (likeCount != null) {
            if (dynamic.stats != null) {
                if (dynamic.stats.liked) {
                    likeCount!!.setTextColor(ColorScheme.LIKE_COLOR)
                    likeCount!!.setCompoundDrawablesWithIntrinsicBounds(
                        ThemeTint.tinted(context, R.drawable.icon_reply_like1, ColorScheme.LIKE_COLOR), null, null, null
                    )
                } else {
                    likeCount!!.setTextColor(ColorScheme.TEXT_PRIMARY)
                    likeCount!!.setCompoundDrawablesWithIntrinsicBounds(
                        ContextCompat.getDrawable(context, R.drawable.icon_reply_like0), null, null, null
                    )
                }
                likeCount!!.text = StringUtil.toWan(dynamic.stats.like.toLong())
            } else {
                likeCount!!.visibility = View.GONE
            }
            likeCount!!.setOnClickListener {
                // 客户端去重：同一条动态的点赞请求还没回来之前，后续连点直接忽略。
                // 不加的话用户连点会连发好几次点赞/取消，服务端只认最后一次，
                // 而界面每次点击都改一次计数，结果就是点赞状态与服务端对不上
                if (!likingDynamicIds.add(dynamic.dynamicId)) {
                    MsgUtil.showMsg(UiText.of("正在处理中"))
                    return@setOnClickListener
                }
                CenterThreadPool.run {
                    try {
                        if (!dynamic.stats.liked) {
                            try {
                                if (DynamicApi.likeDynamic(dynamic.dynamicId, true) == 0) {
                                    dynamic.stats.liked = true
                                    (context as Activity).runOnUiThread {
                                        MsgUtil.showMsg(UiText.of("点赞成功"))
                                        likeCount!!.text = StringUtil.toWan((++dynamic.stats.like).toLong())
                                        likeCount!!.setTextColor(ColorScheme.LIKE_COLOR)
                                        likeCount!!.setCompoundDrawablesWithIntrinsicBounds(
                                            ThemeTint.tinted(context, R.drawable.icon_reply_like1, ColorScheme.LIKE_COLOR), null, null,
                                            null
                                        )
                                    }
                                } else
                                    (context as Activity).runOnUiThread { MsgUtil.showMsg(UiText.of("点赞失败")) }
                            } catch (e: IOException) {
                                MsgUtil.err(e)
                            }
                        } else {
                            try {
                                if (DynamicApi.likeDynamic(dynamic.dynamicId, false) == 0) {
                                    dynamic.stats.liked = false
                                    (context as Activity).runOnUiThread {
                                        MsgUtil.showMsg(UiText.of("取消成功"))
                                        likeCount!!.text = StringUtil.toWan((--dynamic.stats.like).toLong())
                                        likeCount!!.setTextColor(ColorScheme.TEXT_PRIMARY)
                                        likeCount!!.setCompoundDrawablesWithIntrinsicBounds(
                                            ContextCompat.getDrawable(context, R.drawable.icon_reply_like0), null, null,
                                            null
                                        )
                                    }
                                } else
                                    (context as Activity).runOnUiThread { MsgUtil.showMsg(UiText.of("取消失败")) }
                            } catch (e: IOException) {
                                e.printStackTrace()
                            }
                        }
                    } finally {
                        // 成功、失败、抛异常都必须摘掉标志，
                        // 否则这条动态会永久卡在"正在处理中"，再也点不动
                        likingDynamicIds.remove(dynamic.dynamicId)
                    }
                }
            }
        }

        // 「评论」入口：0 条显示「评论」标签，有数显示 万/亿 数字；点击进详情页的评论页。
        if (item_dynamic_comment != null) {
            val emptyLabel = context.getString(R.string.dynamic_port_comment_entry)
            val commentText = DynamicCommentText.build(dynamic.stats?.reply?.toLong() ?: 0L, emptyLabel)
            item_dynamic_comment!!.text = commentText
            item_dynamic_comment!!.contentDescription = if (commentText == emptyLabel) {
                emptyLabel
            } else {
                context.getString(R.string.dynamic_port_comment_desc, commentText)
            }
            // icon_reply 固有尺寸 24dp，比这一行的 20dp 高，直接当 compound drawable 会被裁；
            // 按 14dp 重设边界，与同行 icon_reply_like0（14dp）视觉一致（不新增 drawable）。
            val icon = ContextCompat.getDrawable(context, R.drawable.icon_reply)
            if (icon != null) {
                val side = (context.resources.displayMetrics.density * 14f).toInt()
                icon.setBounds(0, 0, side, side)
                item_dynamic_comment!!.setCompoundDrawables(icon, null, null, null)
            }
            item_dynamic_comment!!.isEnabled = dynamic.dynamicId > 0L
            item_dynamic_comment!!.setOnClickListener {
                if (dynamic.dynamicId <= 0L) return@setOnClickListener
                // 复用现成跳法（不新写页面）：seekReply=0 表示只切到评论页、不定位具体评论，
                // 见 [COMMENT_PAGE_ONLY]。
                TerminalContext.getInstance().enterDynamicDetailPage(
                    context, dynamic.dynamicId, adapterPosition, COMMENT_PAGE_ONLY
                )
            }
        }
    }
}
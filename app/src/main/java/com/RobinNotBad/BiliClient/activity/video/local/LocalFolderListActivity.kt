package com.RobinNotBad.BiliClient.activity.video.local

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.SimpleItemAnimator
import com.RobinNotBad.BiliClient.R
import com.RobinNotBad.BiliClient.activity.InputDialogActivity
import com.RobinNotBad.BiliClient.activity.base.RefreshListActivity
import com.RobinNotBad.BiliClient.adapter.video.LocalFolderAdapter
import com.RobinNotBad.BiliClient.adapter.video.LocalFolderAdapter.FolderRow
import com.RobinNotBad.BiliClient.service.download.DownloadPathSpec
import com.RobinNotBad.BiliClient.util.CachePathResolver
import com.RobinNotBad.BiliClient.util.CenterThreadPool
import com.RobinNotBad.BiliClient.util.FileUtil
import com.RobinNotBad.BiliClient.util.FolderManager
import com.RobinNotBad.BiliClient.util.MsgUtil
import com.RobinNotBad.BiliClient.util.TerminalDialog
import com.RobinNotBad.BiliClient.util.UiText
import java.io.File

/**
 * 独立的本地缓存文件夹列表页（复刻「我的 → 收藏」的收藏夹列表页）。
 *
 * <h3>为什么单独开一页</h3>
 * 缓存页以前把文件夹铺在列表里（下载列表入口 → 文件夹分区 → 新建文件夹 → 未分类视频），
 * 文件夹一多，光文件夹就占满一屏，未分类视频要滑很久才看得见。设置里打开
 * `util/FolderPagePrefs.ENABLE`（「缓存页文件夹入口」）后，缓存页最上面只留一行
 * 「文件夹 N」，点它进本页；文件夹的增删改排序全在本页做，关掉开关则一切照旧。
 *
 * <h3>数据全部本地</h3>
 * 文件夹本身来自 `FolderManager.getAllFolders()`（`.folders_info.json`），每个夹的视频数走
 * `FolderManager.getFolderVideos(name).size`，封面取夹内**第一条**视频目录里的 `cover.png`。
 * 都在 [loadFolders] 的后台线程里算好，再一次性交给 adapter——`bindViewHolder` 里不读盘。
 *
 * <h3>刷新口径</h3>
 * 每次加载都**换一个全新 adapter**（与 `FavoriteFolderListActivity` 同一口径）：原地改已挂在
 * RecyclerView 上的那份列表会撞 `IndexOutOfBoundsException: Inconsistency detected`（控制台崩溃 #160）。
 * 从某个夹回来无条件重算（夹内可能删了视频，视频数与封面都会变）。
 * 本页改过东西就带着 `RESULT_OK` 退出，缓存页收到后重扫（缓存页自己的未分类分区也受影响）。
 */
class LocalFolderListActivity : RefreshListActivity() {

    /** 当前显示的行（文件夹层改过东西时用来判断「有没有东西可选」）。 */
    private var rows: List<FolderRow> = emptyList()

    private var adapter: LocalFolderAdapter? = null

    /** 挂上去的拖动 helper：换 adapter 时先 detach 旧的，否则同一个 RecyclerView 会叠多个手势监听。 */
    private var touchHelper: ItemTouchHelper? = null

    // 复用 activity_simple_refresh 的 manageBar 三个槽做本页的操作条：
    // 常态 [多选][排序]、多选态 [退出][全选/全不选][操作]、排序态 [退出]
    private lateinit var manageBar: View
    private lateinit var manageToggle: TextView
    private lateinit var manageSelectAll: TextView
    private lateinit var manageOperate: TextView

    private var sortMode = false
    private var selectionMode = false

    /** 已选文件夹名；与 adapter 共用同一份集合，adapter 只读。 */
    private val selectedNames = LinkedHashSet<String>()

    /** 文件夹层改过东西：退回缓存页时要让它重扫（夹内的改动由 `LocalFolderActivity` 自己标）。 */
    private var dataChanged = false

    private var pendingInputCallback: ((String) -> Unit)? = null

    private val inputLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val callback = pendingInputCallback
            pendingInputCallback = null
            if (result.resultCode == RESULT_OK) {
                callback?.invoke(result.data?.getStringExtra("input_text") ?: "")
            }
        }

    /**
     * 从某个夹回来：夹里可能删过视频（视频数 / 封面变）、也可能把视频移回未分类
     * （那连缓存页的未分类分区都要重扫），所以无条件重算一遍；只在真的改过时标脏。
     */
    private val folderLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) dataChanged = true
            loadFolders()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setPageName(getString(R.string.pagename_localfolderlist))

        manageBar = findViewById(R.id.manageBar)
        manageToggle = findViewById(R.id.manageToggle)
        manageSelectAll = findViewById(R.id.manageSelectAll)
        manageOperate = findViewById(R.id.manageDelete)

        manageToggle.setOnClickListener { onManageToggleClick() }
        manageSelectAll.setOnClickListener { onManageSelectAllClick() }
        manageOperate.setOnClickListener { showBatchMenu() }

        // 本页数据在本地，下拉刷新 = 重新读一遍文件夹信息（夹里的改动、外部下载完成都能刷出来）
        setOnRefreshListener { loadFolders() }
        loadFolders()
    }

    // ===================== 加载 =====================

    private fun loadFolders() {
        if (isFinishing) return
        CenterThreadPool.run {
            val computed = buildRows()
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                rows = computed
                bindList(computed)
                setRefreshing(false)
            }
        }
    }

    /** 算好每一行的数据（文件夹名 / 视频数 / 封面路径）。 */
    private fun buildRows(): List<FolderRow> {
        val folders = FolderManager.getAllFolders()
        val root = FileUtil.getVideoDownloadPath()
        val result = ArrayList<FolderRow>(folders.size)
        for (folder in folders) {
            val titles = FolderManager.getFolderVideos(folder.name)
            result.add(FolderRow(folder.name, titles.size, firstCover(root, titles)))
        }
        return result
    }

    /**
     * 夹内第一条视频目录里的 `cover.png`。
     *
     * 视频目录按标题反查，候选取 `CachePathResolver` 那三条（由媒体文件反推 / 清洗后的标题 /
     * 原始标题），与缓存页找条目目录用的是同一套规则，不做第二套拼法。
     */
    private fun firstCover(root: File, titles: List<String>): String? {
        val first = titles.firstOrNull() ?: return null
        for (path in CachePathResolver.videoDirCandidates(root.path, first)) {
            val cover = File(path, DownloadPathSpec.FILE_COVER)
            if (cover.isFile) return cover.absolutePath
        }
        return null
    }

    private fun bindList(computed: List<FolderRow>) {
        val newAdapter = LocalFolderAdapter(this)
        newAdapter.sortMode = sortMode
        newAdapter.selectionMode = selectionMode
        newAdapter.selectedNames = selectedNames
        newAdapter.onFolderClick = { row -> openFolder(row.name) }
        newAdapter.onFolderMenu = { row -> showFolderMenu(row.name) }
        newAdapter.onCreateClick = { showNewFolderDialog() }
        newAdapter.onFolderToggle = { row -> toggleSelection(row.name) }
        newAdapter.onOrderDragFinished = { order ->
            // 拖动落盘的入口只此一处：clearView 里已经 post 过，这里可以安全写盘
            FolderManager.saveFolderOrder(order)
            dataChanged = true
        }
        newAdapter.setData(computed)

        adapter = newAdapter
        setAdapter(newAdapter)

        // 勾选态靠引导符 + 主色，变更动画会让引导符从旧标题上「跑」过去（与缓存页同一口径）
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false

        touchHelper?.attachToRecyclerView(null)
        val helper = ItemTouchHelper(newAdapter.dragCallback)
        helper.attachToRecyclerView(recyclerView)
        newAdapter.touchHelper = helper
        touchHelper = helper

        pruneSelection()
        updateBars()
    }

    // ===================== 文件夹操作 =====================

    /**
     * 打开某个夹。
     *
     * 用 [LocalFolderActivity]（`LocalListActivity` 的子类）自己撑一页，返回栈独立；
     * `from` 透传下去——菜单页名靠它决定「这还是在缓存链里」。
     */
    private fun openFolder(name: String) {
        if (sortMode) exitSortMode()
        if (selectionMode) exitSelectionMode()
        val intent = Intent(this, LocalFolderActivity::class.java)
            .putExtra(LocalFolderActivity.EXTRA_FOLDER_NAME, name)
        getIntent().getStringExtra("from")?.let { intent.putExtra("from", it) }
        folderLauncher.launch(intent)
    }

    /** 与缓存页同一个截断口径：弹窗标题不铺满一行。 */
    private fun menuTitle(title: String): String =
        if (title.length <= 12) title else title.take(12) + "…"

    /** 长按文件夹：重命名 / 拆散（文案与缓存页一致）。 */
    private fun showFolderMenu(name: String) {
        val items = listOf(UiText.of("重命名"), UiText.of("拆散文件夹"))
        TerminalDialog.menu(this, menuTitle(name), items) { which ->
            when (which) {
                0 -> showRenameDialog(name)
                1 -> showDisbandConfirmDialog(name)
            }
        }.show()
    }

    private fun showNewFolderDialog() {
        val intent = Intent(this, InputDialogActivity::class.java)
            .putExtra("title", UiText.of("新建文件夹"))
            .putExtra("hint", UiText.of("请输入文件夹名称"))
        pendingInputCallback = { name ->
            if (!FolderManager.isValidFolderName(name)) {
                MsgUtil.showMsg(UiText.of("名称无效（不能为空、超过30字符或包含特殊字符）"))
            } else if (FolderManager.createFolder(name)) {
                dataChanged = true
                MsgUtil.showMsg(UiText.of("文件夹创建成功"))
                loadFolders()
            } else {
                MsgUtil.showMsg(UiText.of("文件夹名称已存在或创建失败"))
            }
        }
        inputLauncher.launch(intent)
    }

    private fun showRenameDialog(oldName: String) {
        val intent = Intent(this, InputDialogActivity::class.java)
            .putExtra("title", UiText.of("重命名文件夹"))
            .putExtra("initial_text", oldName)
        pendingInputCallback = { newName ->
            if (!FolderManager.isValidFolderName(newName)) {
                MsgUtil.showMsg(UiText.of("名称无效"))
            } else if (FolderManager.renameFolder(oldName, newName)) {
                dataChanged = true
                MsgUtil.showMsg(UiText.of("重命名成功"))
                loadFolders()
            } else {
                MsgUtil.showMsg(UiText.of("重命名失败（名称可能已存在）"))
            }
        }
        inputLauncher.launch(intent)
    }

    private fun showDisbandConfirmDialog(name: String) {
        TerminalDialog.confirm(
            this,
            UiText.of("拆散文件夹"),
            UiText.of("视频会移回未分类区域。"),
            UiText.of("拆散")
        ) {
            disbandFolder(name)
        }.show()
    }

    private fun disbandFolder(name: String) {
        CenterThreadPool.run {
            if (FolderManager.deleteFolder(name)) {
                runOnUiThread {
                    dataChanged = true
                    MsgUtil.showMsg(UiText.of("文件夹已拆散"))
                    loadFolders()
                }
            }
        }
    }

    // ===================== 多选 / 排序 =====================

    private fun onManageToggleClick() {
        when {
            selectionMode -> exitSelectionMode()
            sortMode -> exitSortMode()
            else -> enterSelectionMode()
        }
    }

    /** 常态下这一格是「排序」，多选态下是「全选 / 全不选」。 */
    private fun onManageSelectAllClick() {
        if (selectionMode) {
            if (rows.isNotEmpty() && selectedNames.size >= rows.size) {
                selectedNames.clear()
            } else {
                selectedNames.clear()
                rows.forEach { selectedNames.add(it.name) }
            }
            adapter?.notifyDataSetChanged()
            updateBars()
        } else {
            enterSortMode()
        }
    }

    private fun enterSelectionMode() {
        if (rows.isEmpty()) return
        if (sortMode) exitSortMode()
        selectionMode = true
        adapter?.selectionMode = true
        adapter?.selectedNames = selectedNames
        adapter?.notifyDataSetChanged()
        updateBars()
    }

    private fun exitSelectionMode() {
        selectionMode = false
        selectedNames.clear()
        adapter?.selectionMode = false
        adapter?.notifyDataSetChanged()
        updateBars()
    }

    private fun toggleSelection(name: String) {
        if (!selectedNames.remove(name)) selectedNames.add(name)
        adapter?.notifyRowChanged(name)
        updateBars()
    }

    /** 重新加载后勾选集合跟着收缩，底部计数才不会虚高。 */
    private fun pruneSelection() {
        if (selectedNames.isEmpty()) return
        selectedNames.retainAll(rows.map { it.name }.toSet())
    }

    private fun enterSortMode() {
        if (rows.size < 2) {
            // 一个文件夹没有可排的东西；直接进排序态反而让人找不到「退出」
            MsgUtil.showMsg(UiText.of("至少要有两个文件夹才能排序"))
            return
        }
        if (selectionMode) exitSelectionMode()
        sortMode = true
        adapter?.sortMode = true
        updateBars()
        MsgUtil.showMsg(getString(R.string.text_cache_sort_hint))
    }

    private fun exitSortMode() {
        sortMode = false
        adapter?.sortMode = false
        updateBars()
    }

    /**
     * 操作条三格的内容按当前模式切换。
     *
     * 没有文件夹时整条藏起来：本页唯一可点的就是「新建文件夹」，留着一条点不动的工具条
     * 只会让人以为界面坏了。
     */
    private fun updateBars() {
        if (rows.isEmpty()) {
            manageBar.visibility = View.GONE
            return
        }
        manageBar.visibility = View.VISIBLE
        when {
            selectionMode -> {
                manageToggle.text = getString(R.string.uitext_locallistactivity_025) // 退出
                manageSelectAll.visibility = View.VISIBLE
                manageSelectAll.text = if (selectedNames.size >= rows.size) {
                    getString(R.string.btn_unselect_all) // 全不选
                } else {
                    getString(R.string.btn_select_all) // 全选
                }
                manageOperate.visibility = View.VISIBLE
                manageOperate.text = getString(R.string.uitext_locallistactivity_023) // 操作
                manageOperate.alpha = if (selectedNames.isEmpty()) 0.5f else 1f
            }

            sortMode -> {
                manageToggle.text = getString(R.string.uitext_locallistactivity_025) // 退出
                manageSelectAll.visibility = View.GONE
                manageOperate.visibility = View.GONE
            }

            else -> {
                manageToggle.text = getString(R.string.uitext_favoritevideolistactivity_009) // 多选
                manageSelectAll.visibility = View.VISIBLE
                manageSelectAll.text = getString(R.string.btn_cache_sort) // 排序
                manageOperate.visibility = View.GONE
            }
        }
    }

    // ===================== 批量操作 =====================

    private fun showBatchMenu() {
        val count = selectedNames.size
        if (count == 0) {
            MsgUtil.showMsg(getString(R.string.uitext_locallistactivity_026)) // 点条目勾选，再点「操作」
            return
        }
        // 只有「拆散」一项：删除文件夹的语义就是拆散（视频回未分类，本页不删视频文件），
        // 再列一条「删除文件夹」会让人以为能连夹里的视频一起删
        val items = listOf(UiText.of("拆散文件夹"))
        TerminalDialog.menu(this, UiText.fmt("已选 %1\$d 项", count), items) {
            confirmBatchDisband()
        }.show()
    }

    private fun confirmBatchDisband() {
        val count = selectedNames.size
        if (count == 0) return
        TerminalDialog.confirm(
            this,
            UiText.of("拆散文件夹"),
            UiText.fmt("确定拆散选中的 %1\$d 个文件夹吗？视频会移回未分类区域。", count),
            UiText.of("拆散")
        ) {
            performBatchDisband()
        }.show()
    }

    private fun performBatchDisband() {
        val names = selectedNames.toList()
        if (names.isEmpty()) return
        CenterThreadPool.run {
            for (name in names) FolderManager.deleteFolder(name)
            runOnUiThread {
                dataChanged = true
                exitSelectionMode()
                loadFolders()
                MsgUtil.showMsg(UiText.of("文件夹已拆散"))
            }
        }
    }

    // ===================== 返回 / 退出 =====================

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 排序 / 多选态下按返回只退出该模式，不直接离开页面（拖到一半误按返回不该直接退出）
        if (sortMode) {
            exitSortMode()
            return
        }
        if (selectionMode) {
            exitSelectionMode()
            return
        }
        super.onBackPressed()
    }

    override fun finish() {
        // 文件夹层改过东西才让缓存页重扫：只是进来看看再退出去，没必要整表重绑一遍封面
        if (dataChanged) setResult(RESULT_OK)
        super.finish()
    }
}

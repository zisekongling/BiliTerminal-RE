package com.RobinNotBad.BiliClient.player

import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDanmakus
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.model.android.Danmakus
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser

/**
 * 弹幕屏蔽词的**解析层**实现（G01）。
 *
 * 为什么必须包在 parser 这一层、而不是「prepare 完再遍历删」：
 * 首窗口的弹幕是交给 `BiliProtobufDanmakuParser` / `BiliDanmukuParser` 解析的
 * （`DrawTask.loadDanmakus()` 在渲染线程里懒执行），解析结果直接进了 `DrawTask` 的时间轴；
 * 此时再逐条 `removeItem` 等价于在渲染线程上改一个正在被遍历的集合，
 * 既可能 `ConcurrentModificationException`，也已经白算了一遍测量。
 * 在 `parse()` 里就地过滤，命中词条从头到尾不进入弹幕队列——内存与耗时都更省。
 *
 * 与 [ForceScrollParser] 的关系：两者都是包装器，可以叠加。
 * 顺序是「先强制滚动（改类型）→ 再屏蔽词（按文本丢）」——反过来的话被丢掉的条目
 * 会先被重建成滚动弹幕再扔掉，白分配一次。两者都转发 `setConfig`/`setDisplayer`/`setTimer`，
 * 见 [ForceScrollParser] 里那段关于 DFM 调用链的说明。
 *
 * 只在快照非空时由 [DanmakuManager.configureAndPrepare] 包一层，没填词时零开销。
 */
internal class DanmakuBlockWordParser(
    private val delegate: BaseDanmakuParser,
    private val blockWords: DanmakuBlockWordPolicy.Snapshot
) : BaseDanmakuParser() {

    override fun setConfig(config: DanmakuContext?): BaseDanmakuParser {
        delegate.setConfig(config)
        return super.setConfig(config)
    }

    override fun setDisplayer(disp: IDisplayer): BaseDanmakuParser {
        delegate.setDisplayer(disp)
        return super.setDisplayer(disp)
    }

    override fun setTimer(timer: DanmakuTimer?): BaseDanmakuParser {
        delegate.setTimer(timer)
        return super.setTimer(timer)
    }

    override fun release() {
        delegate.release()
        super.release()
    }

    override fun parse(): IDanmakus {
        // 走公开的 getDanmakus()：protected parse() 不允许跨实例调用（同 ForceScrollParser 的说明）
        val parsed: IDanmakus = delegate.danmakus ?: return Danmakus()
        val filtered = Danmakus()
        val iterator = parsed.iterator()
        while (iterator.hasNext()) {
            val danmaku = iterator.next() ?: continue
            if (!blockWords.isBlocked(danmaku.text)) filtered.addItem(danmaku)
        }
        return filtered
    }
}

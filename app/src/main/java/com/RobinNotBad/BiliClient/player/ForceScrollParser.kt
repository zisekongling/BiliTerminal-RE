package com.RobinNotBad.BiliClient.player

import master.flame.danmaku.danmaku.model.BaseDanmaku
import master.flame.danmaku.danmaku.model.DanmakuTimer
import master.flame.danmaku.danmaku.model.IDanmakus
import master.flame.danmaku.danmaku.model.IDisplayer
import master.flame.danmaku.danmaku.model.android.DanmakuContext
import master.flame.danmaku.danmaku.model.android.Danmakus
import master.flame.danmaku.danmaku.parser.BaseDanmakuParser

/**
 * 「强制为滚动弹幕」的解析层实现（E2）。
 *
 * 为什么是**包装另一个 parser**而不是直接改 type：
 * DFM 的弹幕类型由具体子类的 `getType()` 常量决定（`FTDanmaku`/`FBDanmaku` 的布局、测量、
 * 绘制都按固定弹幕走），只把 `type` 字段改成滚动值不会改变类行为。所以这里在解析结果上
 * 把顶部/底部弹幕**重新生成**为 `R2LDanmaku`（用 `DanmakuFactory.createDanmaku(TYPE_SCROLL_RL)`），
 * 保留文本、颜色、时间、字号、阴影、背景等可见属性。
 *
 * 该类的 `setConfig` / `setDisplayer` / `setTimer` 必须转发给被包装的 parser：
 * `DrawTask.loadDanmakus()` 是把这几个调用链在被包装对象上的（DFM 源码
 * `DrawTask.java` 的 `parser.setConfig(mContext).setDisplayer(mDisp).setTimer(mTimer).getDanmakus()`），
 * 内层 parser 拿不到 context/displayer 就没法创建弹幕。
 *
 * 只在设置开启时由 [DanmakuManager.configureAndPrepare] 包一层，关闭时零开销。
 */
internal class ForceScrollParser(
    private val delegate: BaseDanmakuParser
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
        // 用公开的 getDanmakus() 而不是受保护的 parse()：本类与被包装的 parser 不是同一个类，
        // Java/Kotlin 的 protected 规则都不允许跨实例直接调 `protected parse()`。
        // 转发过的 setConfig/setDisplayer/setTimer 已经让内层 parser 拿到它需要的上下文。
        val parsed: IDanmakus = delegate.danmakus ?: return Danmakus()
        val context: DanmakuContext = mContext ?: return parsed
        val iterator = parsed.iterator()
        val converted = Danmakus()
        while (iterator.hasNext()) {
            val danmaku = iterator.next() ?: continue
            if (!DanmakuStylePolicy.shouldForceScroll(true, danmaku.type)) {
                converted.addItem(danmaku)
                continue
            }
            val scroll = context.mDanmakuFactory.createDanmaku(BaseDanmaku.TYPE_SCROLL_RL, context)
            if (scroll == null) {
                // 视口还没就绪时拿不到可创建的实例：宁可保留固定弹幕，也不能丢弹幕
                converted.addItem(danmaku)
                continue
            }
            scroll.text = danmaku.text
            scroll.time = danmaku.time
            scroll.textSize = danmaku.textSize
            scroll.textColor = danmaku.textColor
            scroll.textShadowColor = danmaku.textShadowColor
            scroll.backgroundColor = danmaku.backgroundColor
            scroll.padding = danmaku.padding
            scroll.priority = danmaku.priority
            scroll.isLive = danmaku.isLive
            scroll.userHash = danmaku.userHash
            scroll.isGuest = danmaku.isGuest
            scroll.index = danmaku.index
            converted.addItem(scroll)
        }
        return converted
    }
}

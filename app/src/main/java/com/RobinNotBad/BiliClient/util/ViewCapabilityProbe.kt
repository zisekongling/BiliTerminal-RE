package com.RobinNotBad.BiliClient.util

import java.lang.reflect.InvocationTargetException

/**
 * 探测框架里那些「理论上有、部分杂牌设备上却没有」的 View 方法，并做降级。
 *
 * 起因是真实崩溃：某手表上 `android.view.View.hasOnLongClickListeners()` 不存在，
 * 直接抛 `NoSuchMethodError: No virtual method hasOnLongClickListeners()Z` 闪退。
 * 这两个方法明明是 API 15 就有的公开 API，class 文件里也确实有（javap 验证过），
 * 但部分手表/定制 ROM 的框架把它裁掉了 —— 编译期完全查不出来，只有真机运行到才炸。
 *
 * 所以这里用**反射探测**而不是直接调用：
 * - 方法存在 → 正常反射调用，行为与直接调用一致；
 * - 方法不存在 → 返回 null，调用方走降级分支，并且**整个进程只探测一次**，
 *   后续调用直接命中缓存，不再进反射。
 *
 * 按项目「一切功能优先考虑手表端」的约定，凡是给杂牌手表用的框架方法都该走这里，
 * 别在业务代码里裸调 —— 裸调一旦碰上缺方法的设备就是启动即崩，且极难定位。
 */
object ViewCapabilityProbe {

    /** 探测结果缓存；true = 可用，false = 该设备没有这个方法。探测过一次即常驻。 */
    private val availableCache = HashMap<String, Boolean>()

    /** 探测失败的日志只打一次，避免每个页面重复刷屏。 */
    private val loggedFailures = HashSet<String>()

    /**
     * 反射调用 [target] 上的无参 boolean 方法 [method]，并缓存「这个方法在本机存不存在」。
     *
     * @return 方法返回值；该方法在本机不存在时返回 null，调用方应走降级分支
     */
    fun probeBoolean(
        target: Any,
        method: String,
        onFailure: ((String, Throwable) -> Unit)? = null
    ): Boolean? {
        when (availableCache[method]) {
            false -> return null
            // 缓存命中也要包 try/catch：目标方法自己抛异常时，两条路径的行为必须一致，
            // 否则「第一次调用不崩、第二次调用崩」这种鬼问题就没法解释
            true -> return try {
                invokeBoolean(target, method)
            } catch (t: Throwable) {
                handleProbeFailure(method, t, onFailure)
            }
            null -> Unit // 没探测过：往下走首次探测
        }

        return try {
            val result = invokeBoolean(target, method)
            availableCache[method] = true
            result
        } catch (t: Throwable) {
            handleProbeFailure(method, t, onFailure)
        }
    }

    /**
     * 一次调用失败的统一处理。
     *
     * 关键：捕获的是 [Throwable] 而不是 [Error] —— 方法缺失时抛的是 [NoSuchMethodError]（Error），
     * 但框架方法自己炸时抛出来的是普通异常（Exception），只 catch Error 会让后者直接穿透，
     * 那正是「该降级却没降级」的漏洞。
     *
     * 但**不能什么都吞**：普通异常（Exception）多是入参/状态问题，可以降级返回 null；
     * [Error] 里除了「方法不存在」这一类链接错误，其余（StackOverflowError、OOM 等）说明是真实
     * 缺陷，必须原样抛出，免得把真 bug 静默掩盖掉。
     */
    private fun handleProbeFailure(
        method: String,
        t: Throwable,
        onFailure: ((String, Throwable) -> Unit)?
    ): Nothing? {
        if (t is LinkageError) {
            // NoSuchMethodError / AbstractMethodError / NoClassDefFoundError：本机确实没有这个能力
            availableCache[method] = false
            reportFailure(method, t, onFailure)
            return null
        }
        if (t is Exception) {
            reportFailure(method, t, onFailure)
            return null
        }
        throw t
    }

    private fun reportFailure(method: String, t: Throwable, onFailure: ((String, Throwable) -> Unit)?) {
        if (loggedFailures.add(method)) onFailure?.invoke(method, t)
    }

    /**
     * 反射调用目标方法。
     *
     * 用 `getMethod` 而不是直接写调用：方法不存在时它抛 [NoSuchMethodException]（走 Exception
     * 通道，不依赖 catch Error），这里统一转成 [NoSuchMethodError] 交给调用方当「能力缺失」处理，
     * 避免把反射的包装类型漏到业务层。若框架方法本身炸了，反射会包一层
     * [InvocationTargetException]，这里把原始 cause 原样抛出，不让它被降级逻辑吞掉。
     */
    private fun invokeBoolean(target: Any, method: String): Boolean {
        return try {
            target.javaClass.getMethod(method).invoke(target) as Boolean
        } catch (e: NoSuchMethodException) {
            throw NoSuchMethodError("${target.javaClass.name}.$method()")
        } catch (e: InvocationTargetException) {
            throw (e.targetException ?: e)
        }
    }

    /** 仅测试用：重置探测缓存，避免用例之间互相污染。 */
    internal fun resetForTest() {
        availableCache.clear()
        loggedFailures.clear()
    }
}

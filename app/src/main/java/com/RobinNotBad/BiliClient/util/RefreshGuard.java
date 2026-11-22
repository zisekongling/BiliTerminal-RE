package com.RobinNotBad.BiliClient.util;

/**
 * 登录凭证刷新链路的全局互斥锁（26.11.22「老是掉登录」返工，Step 1）。
 *
 * <h3>为什么必须有这把锁</h3>
 * B 站的 refresh_token 是**一次性轮换**口令：谁先用谁拿到新的，另一个拿旧口令的请求必然被判
 * -101。而本工程有两条刷新入口：
 * <ul>
 *   <li>启动页每次冷启动刷一次（SplashActivity）；</li>
 *   <li>进程级调度器每 30 分钟刷一次（CookieRefreshScheduler）。</li>
 * </ul>
 * 两条链路原先各自 synchronized 在自己的类上，等于没有互斥——冷启动撞上调度 tick，
 * 一个请求轮换了口令、另一个拿着刚作废的口令去刷，结果 -101；而启动页原来收到 -101 就
 * resetLogin()，用户看到的就是「一进 App 就掉登录」。
 *
 * <p>把两条链路（以及 confirm/refresh 的补发）全部套进同一把锁之后，同一时刻只有一个刷新请求
 * 在飞；后进来的那个会先看到前一个已经轮换好的新口令，正常续期。
 *
 * <h3>用法约定</h3>
 * <ul>
 *   <li>只在**后台线程**持锁：锁内是同步网络请求（最长几十秒）。本项目网络全部走
 *       CenterThreadPool（IO 协程池，不设线程上限），阻塞等锁不会把线程池占干净，也不会死锁。</li>
 *   <li>锁是**可重入**的（同一个 Object 监视器）：refreshCookie 里再调 confirmPendingRefresh()
 *       是安全的。</li>
 *   <li>不要在这把锁里做除刷新以外的事情（尤其是拉用户信息这类可能失败又慢的请求）。</li>
 * </ul>
 */
public final class RefreshGuard {

    private RefreshGuard() {
    }

    /**
     * Cookie 刷新 / APP token 刷新 / confirm 补发的唯一互斥锁。
     *
     * <p>Java 侧写 synchronized (RefreshGuard.LOCK) {...}，Kotlin 侧写
     * synchronized(RefreshGuard.LOCK) {...}——两边拿到的是同一个监视器。
     */
    public static final Object LOCK = new Object();

    /**
     * 这条请求路径是不是「刷新 / 确认刷新」接口。
     *
     * <p>这两个接口下发的 Set-Cookie 必须由 com.RobinNotBad.BiliClient.api.CookieRefreshApi
     * 自己解析并**原子落盘**：
     * <ul>
     *   <li>它要先把新 Cookie 与旧 Cookie 比对（确认真的换了会话）才敢 confirm，而通用落盘入口
     *       只会闷头把新值写进去；</li>
     *   <li>通用入口还有一道「旧账号代际丢弃」，confirm 前后的代际变化会把新 Cookie 丢掉——
     *       服务端已经作废旧会话、本地却还是旧的，这就是最典型的「必掉登录」。</li>
     * </ul>
     * 纯函数，JVM 单测直接给路径。
     */
    public static boolean isRefreshEndpoint(String path) {
        if (path == null || path.isEmpty()) return false;
        return path.endsWith("/x/passport-login/web/cookie/refresh")
                || path.endsWith("/x/passport-login/web/confirm/refresh");
    }
}

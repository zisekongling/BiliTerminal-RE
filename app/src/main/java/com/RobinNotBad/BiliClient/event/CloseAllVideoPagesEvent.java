package com.RobinNotBad.BiliClient.event;

/**
 * EventBus事件：关闭所有嵌套的视频详情页。
 * 当用户在多层嵌套的视频详情页中长按时触发，
 * 所有VideoInfoActivity实例收到此事件后调用finish()，
 * 最终返回到最初的页面（推荐页/热门页/搜索结果页）。
 */
public class CloseAllVideoPagesEvent {
    public CloseAllVideoPagesEvent() {
    }
}
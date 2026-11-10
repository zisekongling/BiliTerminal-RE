package com.RobinNotBad.BiliClient.activity.video.local

/**
 * 缓存里的文件夹页（26.11.10，反馈 #20）。
 *
 * 以前文件夹只是缓存页内部的一个「当前文件夹」状态：点进夹是把同一个 Activity 的适配器换个 scope，
 * 右滑 / 返回想退出文件夹时只能走就地重建（整表 `notifyDataSetChanged()` + 封面 `DiskCacheStrategy.NONE`
 * 重读一遍），夹里视频一多就明显卡顿；而且它没有自己的返回栈，右滑退出的语义也跟「离开缓存页」搅在一起。
 *
 * 现在它是独立 Activity：进来是 `startActivity`，退出就是 `finish()`，跟其它二级页一致。
 * 列表逻辑全部复用 [LocalListActivity]（它按本页参数决定画「顶层」还是「某个文件夹」），
 * 这里只声明入口参数，不要再复制一份列表实现。
 */
class LocalFolderActivity : LocalListActivity() {
    companion object {
        /** 要展示哪个文件夹，值为 [com.RobinNotBad.BiliClient.model.VideoFolder.name]。 */
        const val EXTRA_FOLDER_NAME = "folder_name"
    }
}

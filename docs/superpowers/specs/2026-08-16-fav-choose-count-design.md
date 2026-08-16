# 设计文档：选择收藏夹页面显示数量/上限

- 日期：2026-08-16
- 项目：ReBiliClient
- 状态：已获用户确认（上限规则：默认收藏夹 50000，自建 1000）

## 背景与目标

"我的-收藏夹"页（`FavoriteFolderListActivity` + `FavoriteFolderAdapter`）在每个收藏夹卡片上显示 `videoCount/maxCount`（`FavoriteFolderAdapter.kt:108`），数据来自 `FavoriteApi.getFavoriteFolders`（getBoxList 接口的 `count` / `max_count` 字段）。

"选择收藏夹"页（`AddFavoriteActivity` + `FolderChooseAdapter`）在视频详情点收藏时弹出，目前只显示收藏夹名称，不显示数量与上限。用户希望在"选择收藏夹"页也显示 `数量/上限`，与"我的-收藏夹"页一致。

## 用户已确认的决策

- 上限不依赖接口返回：**默认收藏夹 50000，用户自建收藏夹 1000**。
- 不改动共享布局 `cell_choose.xml`（被 SearchHistory/QualityChoose/PageChoose/ListChoose/Suggestions 共 5 个适配器共用），为选择收藏夹单独新建布局。
- 数据来源：现有 `getFavoriteState` 使用的 `x/v3/fav/folder/created/list-all` 接口已返回 `media_count`（数量），无需新增网络请求。
- 默认收藏夹识别：`index == 0`（与 `FavoriteApi.getFavoriteFolders` 现有 `isDefault` 逻辑一致；list-all 响应中默认收藏夹恒排第一，attr bit1=0 佐证）。

## 改动内容

### 1. `FavoriteApi.getFavoriteState`（Java，`app/src/main/java/com/RobinNotBad/BiliClient/api/FavoriteApi.java:177`）

在现有签名基础上新增两个输出参数，沿用并行 List 传参风格：

```java
public static void getFavoriteState(long aid, ArrayList<String> folderList, ArrayList<Long> fidList,
        ArrayList<Boolean> stateList, ArrayList<Integer> countList, ArrayList<Integer> maxCountList)
```

循环解析每个 folder 时追加：

```java
countList.add(folder.optInt("media_count", 0));
maxCountList.add(i == 0 ? 50000 : 1000);
```

### 2. 新增布局 `app/src/main/res/layout/cell_folder_choose.xml`

MaterialCardView 内同一行水平排布两个 TextView：

- `@+id/text`：收藏夹名称（左侧，weight=1，样式同 `cell_choose.xml`）。
- `@+id/text_count`：`数量/上限`（右侧，文本如 `22/1000`，`alpha=0.7`、`textSize=11sp`，样式对齐 `cell_favorite_folder_list.xml` 的 `text_itemcount`）。

不改动 `cell_choose.xml`。

### 3. `FolderChooseAdapter.kt`（`app/src/main/java/com/RobinNotBad/BiliClient/adapter/favorite/FolderChooseAdapter.kt`）

- 构造参数新增 `countList: ArrayList<Int>`、`maxCountList: ArrayList<Int>`。
- `onCreateViewHolder` 改用 `cell_folder_choose`。
- `FolderHolder` 新增 `count: TextView = itemView.findViewById(R.id.text_count)`。
- `onBindViewHolder` 中设置 `holder.count.text = countList[position].toString() + "/" + maxCountList[position]`（越界保护与现有 `chooseState`/`fidList` 一致）。

### 4. `AddFavoriteActivity.kt`（`app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/AddFavoriteActivity.kt`）

- 新增 `private val countList = ArrayList<Int>()`、`private val maxCountList = ArrayList<Int>()`。
- 调用 `getFavoriteState` 时传入；构造 `FolderChooseAdapter` 时传入。

## 错误处理与边界

- `media_count` 缺失时 `optInt` 兜底为 0，显示 `0/1000`，不崩溃。
- 列表索引越界保护沿用现有 `onBindViewHolder` 开头对 `folderList`/`chooseState`/`fidList` 的检查，同步校验 `countList`/`maxCountList`。
- 不改动 `cell_choose.xml` 及其余 5 个共用适配器，无回归风险。

## 测试

无集成测试；以 `./gradlew :app:assembleDebug` 编译通过 + 真机手测为准（收藏弹窗内每个收藏夹显示 `数量/上限`，默认收藏夹显示 `数量/50000`，自建显示 `数量/1000`）。

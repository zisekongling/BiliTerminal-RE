# 选择收藏夹页显示数量/上限 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在"添加收藏"弹窗（选择收藏夹页）的每个收藏夹项上显示 `数量/上限`，与"我的-收藏夹"页一致。

**Architecture:** 现有 `FavoriteApi.getFavoriteState` 使用的 `created/list-all` 接口已返回 `media_count`（数量），上限按固定规则计算（默认收藏夹 50000，自建 1000）。把解析循环抽成纯函数 `parseFavoriteState` 以便单测；为选择收藏夹单独新建布局 `cell_folder_choose.xml`（共享的 `cell_choose.xml` 被 5 个其他适配器使用，不改）。

**Tech Stack:** Java/Kotlin 混编、org.json、RecyclerView.Adapter、JUnit4（纯 JVM 单测）、Gradle 8.11.1。

## Global Constraints

- 上限规则（用户确认）：**默认收藏夹 50000，用户自建收藏夹 1000**。默认收藏夹以 `index == 0` 识别（与 `FavoriteApi.getFavoriteFolders` 现有 `isDefault` 逻辑一致）。
- 不改动共享布局 `cell_choose.xml` 及其余 5 个使用它的适配器。
- 文案硬编码中文，不改 `strings.xml`。
- 纯解析抽成可单测函数，补 `app/src/test/` 纯 JVM 单测（参考 `HotSearchApiTest`）。
- Linux/WSL 构建需覆盖 java.home：追加 `-Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64 --offline`（本机代理未开，依赖已缓存）。

---

### Task 1: API 解析重构并补单测

把 `getFavoriteState` 的解析循环抽成纯函数 `parseFavoriteState`，同时新增 `media_count` 与上限解析，新增 6 参数 `getFavoriteState` 重载；旧 4 参数重载暂时保留为委托（保证本任务结束时 App 仍可编译），Task 2 再删除。

**Files:**
- Modify: `app/src/main/java/com/RobinNotBad/BiliClient/api/FavoriteApi.java:177-191`
- Test: `app/src/test/java/com/RobinNotBad/BiliClient/api/FavoriteApiTest.kt`

**Interfaces:**
- Consumes: 无（改造现有方法）。
- Produces:
  - `FavoriteApi.parseFavoriteState(JSONObject data, ArrayList<String> folderList, ArrayList<Long> fidList, ArrayList<Boolean> stateList, ArrayList<Integer> countList, ArrayList<Integer> maxCountList)` — 纯静态解析，供单测与 6 参数 `getFavoriteState` 调用。
  - `FavoriteApi.getFavoriteState(long aid, ArrayList<String> folderList, ArrayList<Long> fidList, ArrayList<Boolean> stateList, ArrayList<Integer> countList, ArrayList<Integer> maxCountList)` — 6 参数重载，Task 2 的 `AddFavoriteActivity` 改用它。

- [ ] **Step 1: 写失败的单测**

创建 `app/src/test/java/com/RobinNotBad/BiliClient/api/FavoriteApiTest.kt`：

```kotlin
package com.RobinNotBad.BiliClient.api

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteApiTest {

    @Test
    fun parseFavoriteState_validResponse_fillsAllLists() {
        val json = """{"count":2,"list":[
            {"id":44233921,"fid":442339,"title":"默认收藏夹","fav_state":1,"media_count":85},
            {"id":936347621,"fid":9363476,"title":"自建收藏夹","fav_state":0,"media_count":2}
        ]}"""
        val folderList = ArrayList<String>()
        val fidList = ArrayList<Long>()
        val stateList = ArrayList<Boolean>()
        val countList = ArrayList<Int>()
        val maxCountList = ArrayList<Int>()

        FavoriteApi.parseFavoriteState(JSONObject(json), folderList, fidList, stateList, countList, maxCountList)

        assertEquals("应解析出2条", 2, folderList.size)
        assertEquals("默认收藏夹", folderList[0])
        assertEquals("自建收藏夹", folderList[1])
        assertEquals("fid", 442339L, fidList[0])
        assertEquals("fid", 9363476L, fidList[1])
        assertTrue("fav_state=1 已收藏", stateList[0])
        assertFalse("fav_state=0 未收藏", stateList[1])
        assertEquals("media_count", 85, countList[0])
        assertEquals("默认收藏夹上限", 50000, maxCountList[0])
        assertEquals("自建收藏夹上限", 1000, maxCountList[1])
    }

    @Test
    fun parseFavoriteState_mediaCountMissing_defaultsZero() {
        val json = """{"list":[{"fid":1,"title":"a","fav_state":0}]}"""
        val folderList = ArrayList<String>()
        val fidList = ArrayList<Long>()
        val stateList = ArrayList<Boolean>()
        val countList = ArrayList<Int>()
        val maxCountList = ArrayList<Int>()

        FavoriteApi.parseFavoriteState(JSONObject(json), folderList, fidList, stateList, countList, maxCountList)

        assertEquals("media_count 缺失兜底0", 0, countList[0])
        assertEquals("index0 即默认收藏夹", 50000, maxCountList[0])
    }

    @Test
    fun parseFavoriteState_nullOrNoList_leavesListsEmpty() {
        val folderList = ArrayList<String>()
        val fidList = ArrayList<Long>()
        val stateList = ArrayList<Boolean>()
        val countList = ArrayList<Int>()
        val maxCountList = ArrayList<Int>()

        FavoriteApi.parseFavoriteState(null, folderList, fidList, stateList, countList, maxCountList)
        assertTrue("data=null 不崩溃", folderList.isEmpty())

        val noList = JSONObject("""{"count":0}""")
        FavoriteApi.parseFavoriteState(noList, folderList, fidList, stateList, countList, maxCountList)
        assertTrue("无list字段不崩溃", folderList.isEmpty())
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `./gradlew :app:testDebugUnitTest --tests "com.RobinNotBad.BiliClient.api.FavoriteApiTest" -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64 --offline`

Expected: 编译失败，`parseFavoriteState` 无法解析（方法不存在）。

- [ ] **Step 3: 实现解析重构**

修改 `FavoriteApi.java` 的 `getFavoriteState`（原 177-191 行）为：

```java
public static void getFavoriteState(long aid, ArrayList<String> folderList, ArrayList<Long> fidList, ArrayList<Boolean> stateList) throws IOException, JSONException {
    getFavoriteState(aid, folderList, fidList, stateList, new ArrayList<Integer>(), new ArrayList<Integer>());
}

public static void getFavoriteState(long aid, ArrayList<String> folderList, ArrayList<Long> fidList, ArrayList<Boolean> stateList, ArrayList<Integer> countList, ArrayList<Integer> maxCountList) throws IOException, JSONException {
    String url = "https://api.bilibili.com/x/v3/fav/folder/created/list-all?type=2&jsonp=jsonp&rid=" + aid + "&up_mid=" + SharedPreferencesUtil.getLong("mid", 0);
    JSONObject result = NetWorkUtil.getJson(url);
    JSONObject data = result.getJSONObject("data");
    parseFavoriteState(data, folderList, fidList, stateList, countList, maxCountList);
}

public static void parseFavoriteState(JSONObject data, ArrayList<String> folderList, ArrayList<Long> fidList, ArrayList<Boolean> stateList, ArrayList<Integer> countList, ArrayList<Integer> maxCountList) {
    if (data == null) return;
    if (!data.has("list") || data.isNull("list")) return;
    JSONArray list = data.getJSONArray("list");
    for (int i = 0; i < list.length(); i++) {
        JSONObject folder = list.getJSONObject(i);
        folderList.add(folder.getString("title"));
        fidList.add(folder.getLong("fid"));
        stateList.add(folder.getInt("fav_state") == 1);
        countList.add(folder.optInt("media_count", 0));
        maxCountList.add(i == 0 ? 50000 : 1000);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `./gradlew :app:testDebugUnitTest --tests "com.RobinNotBad.BiliClient.api.FavoriteApiTest" -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64 --offline`

Expected: 3 个测试全部 PASS。

- [ ] **Step 5: 提交**

```bash
git add app/src/main/java/com/RobinNotBad/BiliClient/api/FavoriteApi.java app/src/test/java/com/RobinNotBad/BiliClient/api/FavoriteApiTest.kt
git commit -m "feat: 收藏夹解析支持数量/上限并抽取纯函数"
```

---

### Task 2: 布局 + 适配器 + 页面接线

新建选择收藏夹专用布局，`FolderChooseAdapter` 展示 `数量/上限`，`AddFavoriteActivity` 改用 6 参数 `getFavoriteState`，删除 Task 1 留下的 4 参数委托重载，最后 `assembleDebug` 编译验证。

**Files:**
- Create: `app/src/main/res/layout/cell_folder_choose.xml`
- Modify: `app/src/main/java/com/RobinNotBad/BiliClient/adapter/favorite/FolderChooseAdapter.kt`
- Modify: `app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/AddFavoriteActivity.kt`
- Modify: `app/src/main/java/com/RobinNotBad/BiliClient/api/FavoriteApi.java`（删 4 参数委托重载）

**Interfaces:**
- Consumes: Task 1 的 6 参数 `getFavoriteState` 与 `parseFavoriteState`。
- Produces: 布局资源 `R.layout.cell_folder_choose`、id `R.id.text_count`；`FolderChooseAdapter` 新构造签名（6 参数 + 2 计数列表 + aid）。

- [ ] **Step 1: 新建布局 `cell_folder_choose.xml`**

创建 `app/src/main/res/layout/cell_folder_choose.xml`：

```xml
<?xml version="1.0" encoding="utf-8"?>
<com.google.android.material.card.MaterialCardView xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:layout_marginHorizontal="@dimen/activity_padding_horizontal"
    android:layout_marginVertical="2dp"
    app:cardBackgroundColor="#dd262626"
    app:layout_constraintBottom_toBottomOf="parent"
    app:layout_constraintEnd_toEndOf="parent"
    app:layout_constraintStart_toStartOf="parent"
    app:layout_constraintTop_toTopOf="parent">

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginHorizontal="8dp"
        android:layout_marginVertical="8dp"
        android:gravity="center_vertical"
        android:orientation="horizontal">

        <TextView
            android:id="@+id/text"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:ellipsize="end"
            android:singleLine="true"
            android:text="文本文本文本" />

        <TextView
            android:id="@+id/text_count"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginStart="8dp"
            android:alpha="0.7"
            android:text="0/1000"
            android:textSize="11sp" />
    </LinearLayout>
</com.google.android.material.card.MaterialCardView>
```

- [ ] **Step 2: 修改 `FolderChooseAdapter.kt`**

构造参数追加两个列表（在 `aid` 之前）：

```kotlin
class FolderChooseAdapter(
    private val context: Context,
    private val folderList: ArrayList<String>,
    private val fidList: ArrayList<Long>,
    private val chooseState: ArrayList<Boolean>,
    private val countList: ArrayList<Int>,
    private val maxCountList: ArrayList<Int>,
    private val aid: Long
) : RecyclerView.Adapter<FolderChooseAdapter.FolderHolder>() {
```

`onCreateViewHolder` 改用新布局：

```kotlin
val view = LayoutInflater.from(this.context).inflate(R.layout.cell_folder_choose, parent, false)
```

`onBindViewHolder` 边界检查追加两条，并在设置名称后设置计数文本：

```kotlin
if (position < 0 || position >= folderList.size)
    return
if (position >= chooseState.size || position >= fidList.size)
    return
if (position >= countList.size || position >= maxCountList.size)
    return

val cardView = holder.itemView as MaterialCardView

holder.folder_name.text = folderList[position]
holder.count.text = countList[position].toString() + "/" + maxCountList[position]
setCardView(cardView, chooseState[position])
```

`FolderHolder` 追加 count 视图：

```kotlin
class FolderHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val folder_name: TextView = itemView.findViewById(R.id.text)
    val count: TextView = itemView.findViewById(R.id.text_count)
}
```

- [ ] **Step 3: 修改 `AddFavoriteActivity.kt`**

类成员追加两个列表，并把 `getFavoriteState` 与 `FolderChooseAdapter` 的调用改为 6 参数：

```kotlin
private val folderList = ArrayList<String>()
private val stateList = ArrayList<Boolean>()
private val fidList = ArrayList<Long>()
private val countList = ArrayList<Int>()
private val maxCountList = ArrayList<Int>()
```

```kotlin
FavoriteApi.getFavoriteState(aid, folderList, fidList, stateList, countList, maxCountList)
adapter = FolderChooseAdapter(this, folderList, fidList, stateList, countList, maxCountList, aid)
```

- [ ] **Step 4: 删除 `FavoriteApi.java` 的 4 参数委托重载**

```java
public static void getFavoriteState(long aid, ArrayList<String> folderList, ArrayList<Long> fidList, ArrayList<Boolean> stateList) throws IOException, JSONException {
    getFavoriteState(aid, folderList, fidList, stateList, new ArrayList<Integer>(), new ArrayList<Integer>());
}
```

（Task 1 加入的这段在 `getFavoriteState` 6 参数方法上方，Task 2 无调用方后整体删除。全仓库仅 `AddFavoriteActivity` 一处调用，已在 Step 3 改为 6 参数。）

- [ ] **Step 5: 编译验证**

Run: `./gradlew :app:assembleDebug -Dorg.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64 --offline`

Expected: BUILD SUCCESSFUL。若报 `parseFavoriteState`/`getFavoriteState` 未找到，说明调用方或重载没改全，检查 `AddFavoriteActivity.kt` 与 `FavoriteApi.java`。

- [ ] **Step 6: 提交**

```bash
git add app/src/main/res/layout/cell_folder_choose.xml app/src/main/java/com/RobinNotBad/BiliClient/adapter/favorite/FolderChooseAdapter.kt app/src/main/java/com/RobinNotBad/BiliClient/activity/video/info/AddFavoriteActivity.kt app/src/main/java/com/RobinNotBad/BiliClient/api/FavoriteApi.java
git commit -m "feat: 选择收藏夹页显示数量/上限"
```

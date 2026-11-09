# 审计与改动说明（APatch 系统模块界面增强）

本仓库是**本机自用二开分支**，不向上游提交 PR。上游：<https://github.com/bmax121/APatch>（GPL-3.0）。

## 1. 目标样本

| 项 | 值 |
| --- | --- |
| 包名 | `me.bmax.apatch` |
| versionName | `166daa0` |
| versionCode | `11142` |
| minSdk / targetSdk | 26 / 36 |
| 安装时间 | 2026-08-04 22:47:34（Google Play 安装） |
| APK 路径 | `<设备随机安装路径，已脱敏>` |
| APK SHA-256 | `1695a51f5741c602fab50f389949e0d356a639708fc97663b0fc8a2a2d47ee81` |
| APK 大小 | 5,850,198 字节 |

## 2. 与上游哪个提交对齐（为什么不是新版）

上游 `build.gradle.kts` 的版本规则是写死的：

```kotlin
fun getVersionCode(): Int {
    val commitCount = getGitCommitCount()      // git rev-list --count HEAD
    return 1 * 10000 + commitCount + 200
}
fun getVersionName(): String = getGitDescribe() // git rev-parse --short HEAD
```

本机 `versionCode = 11142` → 提交数 = 11142 − 10200 = **942**；`versionName = 166daa0` 正好是短哈希。
在上游仓库验证：

```text
$ git rev-list --count 166daa0
942
$ git log -1 --format='%H %ad %s' 166daa0
166daa081c5107e1036a3e5caa1380e8a1a863cb Wed Nov 12 14:48:58 2025 +0800 Revert apd: uid listen event move to post-data-fs
```

两个独立指标同时命中，可确认本机安装的就是 **`166daa0`（942 提交，2025-11-12）**，
不是 main 的最新版本。二开基线即此提交；`feat/apm-search-pin-sort-compact` 分支从它拉出。

## 3. 样本内定位点（R8 混淆后的 smali）

反编译：`apktool d -r base.apk`（classes.dex 已被 R8 全量改名，源码类名不可见）。

| 目标 | 混淆类/方法 | 证据（smali 行号） |
| --- | --- | --- |
| APModule 路由 | `Le1;`（`APModuleScreenDestination`） | `getRoute()` 返回 `"a_p_module_screen"`：`e1.smali:1193,1272`；`toString()`：`e1.smali:1430` |
| APModule 界面 Composable | `Lh9;->i(Lx80;Li1;Lde1;Lc51;Lgp0;Ljp0;Lld2;Landroid/content/Context;Lg00;I)V` | 方法体 `h9.smali:16618–17459`；方法内取标题串 `const v11, 0x7f0e0029`（R.string `APModule`）在 `h9.smali:17013` |
| APModule ViewModel | `Lj0;`（TAG `"ModuleViewModel"`） | `j0.smali:202` 常量 `"ModuleViewModel"`；耗时日志 `"load cost: "` 在 `j0.smali:462` |
| 列表排序逻辑 | `Ld0;`（`derivedStateOf` 的 lambda，`packed-switch` 分支） | `d0.smali:67` `Collator.getInstance(Locale.getDefault())`；`d0.smali:82` 构造比较器 `Lh1;`，随后 `Lew;->s0(List, Comparator)` 即 `sortedWith` |
| 相关字符串 id | `0x7f0e0029` = `apm`（APModule）、`0x7f0e002f` = `apm_empty`（No module installed） | `res/values/strings.xml` + `public.xml`（同版本 APK） |

结论：列表顺序完全由 ViewModel 的 `moduleList`（派生状态）决定，UI 只做渲染；
因此搜索/置顶/自定义排序放在 ViewModel 层实现，UI 不参与排序，改动面最小。

## 4. 源码改动

### 4.1 新增 `app/src/main/java/me/bmax/apatch/ui/viewmodel/APModuleUiPrefs.kt`

- `APModuleSortMode`：`DEFAULT / NAME / ENABLED_FIRST / CUSTOM`。
- `APModuleUiPrefs`：置顶集合、自定义顺序、排序模式、紧凑开关，全部写入 APatch 已有的
  `config` SharedPreferences，键名 `apm_ui_*`。不写系统属性、不动 `/data/adb`、不新增文件。
- `prefs` 采用 `runCatching` 兜底：Application 未初始化时返回 null，不 crash。

### 4.2 `APModuleViewModel.kt`

- `moduleList`（第 66 行起）：`搜索过滤 → 排序 → 置顶稳定重排`。
  - 过滤字段：`name / id / author / description`，`ignoreCase`。
  - 排序：`DEFAULT` 按 id（保持上游行为）、`NAME` 按名称、`ENABLED_FIRST` 已启用优先、`CUSTOM` 按保存的顺序（未知 id 排最后）。
  - 置顶：`sortedWith(compareByDescending { it.id in pinnedIds })`，依赖 `sortedWith` 的稳定性，置顶内部仍保持所选排序。
- `updateSearchQuery` / `updateSortMode` / `updateCompact` / `togglePin` / `moveModule` / `resetCustomOrder`。
- `moveModule` 以“当前可见列表”为基线交换相邻项，并自动切到 `CUSTOM`，避免搜索态下移动错位。

### 4.3 `APM.kt`

- 顶栏 `TopBar`：搜索开关、排序下拉菜单（四模式，选中打勾）、紧凑布局开关。
- `SearchField`（第 326 行）：单行输入框，带清空按钮；退出搜索态自动清空关键字。
- `ModuleList`（第 351 行）：空态区分“没有模块”和“没有匹配的模块”；紧凑模式调整列表间距与内边距。
- `ModuleItem`（第 592 行）：`combinedClickable` 长按（第 632 行）弹出菜单
  —— 置顶/取消置顶、上移、下移、重置排序与置顶；紧凑模式隐藏描述行、单行标题、缩小圆角与间距。
- 未改动：模块开关、卸载、更新检查、WebUI、Action 脚本、下载、Magisk 冲突提示、安全模式逻辑。

### 4.4 文案

`res/values/strings.xml` 与 `res/values-zh-rCN/strings.xml` 各新增 16 条 `apm_*` 文案。
其余语言回落到英文，不破坏 Weblate 结构。

## 5. 行为与边界

- 置顶/排序是**纯 UI 顺序**，不影响模块加载顺序、挂载顺序、`/data/adb/modules` 内容。
- 自定义顺序与置顶持久化在 SharedPreferences，卸载管理器即失效；不参与模块安装/卸载事务。
- 搜索只过滤展示，不会改变开关状态；搜索态下长按菜单仍可置顶/移动（作用于当前可见顺序）。
- 模块列表为空、Magisk 冲突、AndroidPatch 未安装等分支保持原逻辑。

## 6. 验证

- 编译验证：`./gradlew :app:compileDebugKotlin` **BUILD SUCCESSFUL**（JDK 21 / SDK 36 / Kotlin 2.2.21）。
- 构建机是 arm64 Android，Debian 环境里通过 `qemu-user-static` + `binfmt_misc` 运行 x86_64 的
  `aapt2`/`aidl`；资源编译、KSP（compose-destinations）、Kotlin 编译全部通过。
- 出包验证：用同版本官方 APK 的 `lib/arm64-v8a/*.so` 走预编译库路线执行 `assembleDebug`，
  产物 `APatch_11142_166daa0_apm-enhance-debug.apk`（25,173,866 字节，debug 签名），
  放在仓库内 `dist/`，已 gitignore，不入库。
- 产物核对：`aapt2 dump badging` 为 `versionCode=11142 / versionName=166daa0`；
  `aapt2 dump resources` 已包含 `apm_search`、`apm_pin`、`apm_move_up`、`apm_sort_custom`、
  `apm_compact` 等新增文案（中英双语），确认改动已打进 APK。
- 完整 `assembleRelease` 还需要 NDK 29.0.14206865、CMake 3.28 与 Rust `cargo-ndk`（`apd`），
  本机未装齐，故未出 release 包；构建步骤见 `BUILD.md`。


## 7. 上游关系

上游对模块页有自己的规划，这类“搜索/置顶/自定义排序/紧凑布局”属于个人使用偏好，
按上游口味大概率不会被合并，因此**不提交 PR**，仅在本仓库维护。上游版权与 GPL-3.0 许可保持不变。

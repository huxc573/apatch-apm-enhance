# CHANGELOG

## [二开 2.1] - 2026-10-30

### 修复：打开模块页整屏持续闪烁（无限重组）

- 根因：`APModuleViewModel.moduleList` 的 `derivedStateOf` lambda 末尾写了
  `.also { isRefreshing = false }`。`derivedStateOf` 会在组合/测量阶段被求值，
  在其中写入快照状态，会使读取该状态的 `PullToRefreshBox` 失效 → 触发重组 →
  再次求值 → 再次写入，形成自激循环，表现为整个界面每帧重绘、持续闪烁，
  并最终拖垮主线程。
- 修复：删除该写入。`isRefreshing` 现在只在 `fetchModuleList()` 的 IO 协程里置
  `true` / `false`，与上游原版一致。
- 同时给排序模式、紧凑开关、置顶集合、自定义顺序的状态初始化补了注释说明
  （只在 ViewModel 构造时读一次 SharedPreferences）。
- 自查规则：`derivedStateOf` / `remember` 计算块内禁止写任何 Compose 状态；
  出包前 `grep -n "derivedStateOf" -A5` 检查 lambda 体内有无赋值。

### 产物

`dist/apm-search-pin-sort-compact-release.apk`（5.6 MB，`versionCode 11148` /
`versionName 02c034a`，R8 混淆 release，自有签名，sha256
`687fed18e7e6412b1f543c579428aadf394941df5c4a4d71095f3fb43f35b041`）。
与二开 2 首包版本号相同、签名相同，可直接覆盖安装。

## [二开 2] - 2026-10-30

在二开 1 基础上按使用反馈重做交互与布局。

### 修改：紧凑布局（不再删除描述）

- 版式与内容完全保留（标题、版本/作者、**描述**、操作按钮都在），只压几何尺寸：
  卡片内边距 16→8dp、卡片间距 16→6dp、圆角 20→10dp、操作行留白 8→0dp、
  按钮高度 40→28dp、分隔线 1.5→1dp、描述字号 `bodySmall`→`labelSmall`。
- 开关在紧凑模式下换成 36×20dp 的自绘瘦身开关（Material3 的 Switch 最小宽度约 52dp）。
- 尺寸集中在 `ApmCardDimens` 一处定义，两种模式只差这一张表。

### 修改：长按 = 直接拖拽排序，不再弹菜单

- 长按卡片任意处出现触感反馈并进入拖拽，拖动即实时换位，松手后把当前可见顺序落成「自定义顺序」。
- 拖拽中该卡片的开关位置换成抓手图标，既提示可排队也不误触。
- **置顶改成常驻图钉**：标题区钉形图标，单击即置顶/取消，钉住后填充主题色。
- 删除长按菜单里的上移/下移/重置项；排序重置由顶栏排序菜单的「自定义顺序」重选与
  `resetCustomOrder` 兜底（顶栏仍保留四种排序模式）。

### 新增：自有 release 签名

- 生成 `apatch-apm-enhance.jks`（PKCS12，RSA 4096，有效期 100 年，alias `apatch-enhance`），
  项目内副本在 `dist/signing/`（gitignore），**规范目录备份在 `projects/apktools/`**。
- `APatchApp` 的启动签名校验改为白名单：官方签名 + 本机二开签名都允许运行，
  不再依赖 `BuildConfig.DEBUG` 绕过（release 包也能正常启动）。
- 签名口令放在不入库的 `signing.properties`，由根 `build.gradle.kts` 读入后交给
  `apksign` 插件；文件缺失时自动回落 debug 签名。

### 产物

`dist/apm-search-pin-sort-compact-release.apk`（5.7 MB，`versionCode 11148` /
`versionName 02c034a`，R8 混淆 release，自有签名）。

## [二开 1] - 2026-10-30

基线：上游 `bmax121/APatch@166daa0`（versionCode 11142，942 个提交，2025-11-12）。
本版本是本机自用二开，不提交上游 PR。

### 新增：系统模块（APModule）界面增强

- **搜索**：顶栏放大镜进入搜索态，按模块 `name / id / author / description` 过滤，
  大小写不敏感；清空关键字或退出搜索态即恢复完整列表；无结果时提示「没有匹配的模块」。
- **置顶**：长按模块卡片弹出菜单 → 「置顶 / 取消置顶」。置顶项始终排在列表最前，
  与排序模式正交（置顶内部仍按当前排序规则稳定排列），卡片标题前显示图钉标记。
- **自定义排序**：长按菜单 → 「上移 / 下移」，按当前可见顺序交换位置并自动切到
  「自定义顺序」模式；顶栏排序菜单提供 默认(ID) / 名称 / 已启用优先 / 自定义顺序 四种模式。
  「重置排序与置顶」清空自定义顺序和置顶集合。
- **紧凑布局**：顶栏列表图标一键开关。开启后收紧卡片间距/圆角/内边距、隐藏描述行、
  标题单行显示，适合模块数量多时快速浏览。

### 实现要点

- 新增 `APModuleUiPrefs`（`app/src/main/java/me/bmax/apatch/ui/viewmodel/APModuleUiPrefs.kt`），
  置顶集合、自定义顺序、排序模式、紧凑开关全部落在 APatch 现有 `config`
  SharedPreferences（键前缀 `apm_ui_`），不写系统属性、不改 `/data/adb`。
- `APModuleViewModel.moduleList` 改为 `过滤 → 排序 → 置顶稳定重排` 的纯内存流水线；
  排序用 `java.text.Collator`（与上游一致），依赖 `sortedWith` 的稳定性保证置顶不打乱组内顺序。
- `APM.kt` 顶栏拆分出 `TopBar` / `SearchField`，模块卡片使用 `combinedClickable`
  支持长按菜单；卡片布局按 `compact` 分支切换间距与描述行。
- 所有新增文案进 `values/strings.xml` 与 `values-zh-rCN/strings.xml`。

### 验证

- `./gradlew :app:compileDebugKotlin` 通过（JDK 21 / SDK 36；构建机为 arm64，
  通过 `qemu-user-static` + `binfmt_misc` 跑 x86_64 的 aapt2/aidl）。
- 未改 native、未改 apd、未改模块安装/卸载逻辑，与上游行为一致。
- 另用官方 APK 的 `lib/arm64-v8a/*.so` 走预编译库路线出过一版 debug 包
  （`assembleDebug`，25 MB，debug 签名，versionCode 11142）：仅用于装机前验证，
  签名与官方不同，覆盖安装需先卸载官方管理器。产物放在仓库内 `dist/`，已 gitignore，不入库。

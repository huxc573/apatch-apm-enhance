# CHANGELOG

## [二开 2.3] - 2026-10-30

### 交互重做：编辑模式下的排序与置顶

上一版把图钉和勾选框常驻在卡片上，既挤占空间，长按进入编辑模式后又**拖不动**。
本版按「平时干净、编辑时可用」重做：

- **进入方式不变**：长按任意卡片进入编辑模式；底部出现操作栏，卡片左侧出现勾选框，
  右侧开关临时替换为拖动手柄（抓手图标）。
- **排序**：
  - 底部栏新增「排序」按钮，点一下进入「按住即拖」状态（底部提示「按住卡片上下拖动即可调整顺序」），
    此时在卡片上按下并上下移动即可拖动换位，**不需要再长按一次**。
  - 也可以点任意卡片右侧的抓手图标单独切换该状态；抓手在「按住即拖」开启时高亮为主题色。
  - 拖拽中卡片放大 1.03 倍、带阴影、跟随手指，并触发震动反馈。
  - **拖动结束会把当前可见顺序整体写入自定义顺序**（`updateCustomOrderFromVisible`），
    不再出现「各段残留旧位置、下次拖动跳着换」的问题。
- **批量置顶**：勾选若干卡片后，底部栏「置顶 / 取消置顶」一次性生效；「全选 / 清空」切换选择集。
- **为何之前拖不动**：编辑模式下卡片外层是 `pointerInput`，但内层仍挂着
  `combinedClickable` / 子级 `clickable`，点击手势先消费了事件；加上
  `detectDragGesturesAfterLongPress` 自身还要再等一次长按，于是表现为「毫无反应」。
  本版在编辑模式下完全移除 `combinedClickable`，并在「按住即拖」状态改用
  `detectDragGestures`（无长按时延）。
- 排序后短窗口内（700 ms）忽略卡片点击，避免手指从底部按钮抬起的余波被卡片当成一次勾选。
- 紧凑模式沿用上一版结论：**只压几何尺寸，不改字号**。

### 2.3.2 重做：排序不再用拖拽当唯一入口

前几版把拖拽当成唯一排序方式，越改越坏。根因有两个：

1. `moveModule` 是**相邻交换**。拖动一次可能跨好几格，需要连发多次交换，
   每次都触发重组，而列表布局要等下一帧才更新，时序不可控 —— 向上拖时反复回弹。
2. 自定义手势 + 自定义偏移计算，和 LazyColumn 自身的布局/滚动两套机制打架。

本版的处理：

- `moveModule` 改为**插入式**（`remove` + `add` 到目标位置），一次数据变更到位；
  新增 `moveModuleTo(id, targetIndex)` 供拖拽使用。
- **编辑模式每张卡右侧改为 ↑ / ↓ 两个按钮 + 拖动把手**：
  - ↑↓ 每点一次只动一格，行为确定、必然生效 —— 恢复此前可用的交互，作为主要方式；
  - 把手保留"按住即拖"，作为额外便利，不再是唯一途径。
- 删掉多余的 `sortMode` 状态、底部「排序」按钮、以及抑制点击的 700ms 时序补丁。
- 拖拽目标改为"视觉中心落在哪张卡身上就插到哪里"，不再依赖相邻交换追赶。

### 产物

`dist/apm-search-pin-sort-compact-release.apk`，sha256
`75f05408ce2b834139bdd6f5acadba7994882d71999af1af90b6746818ca2f1e`，
versionCode 11142 / versionName 166daa0。

> 说明：本版未做实机交互验证（用户要求不再代为验证），排序建议先用 ↑↓ 按钮。

### 2.3.1 修正（同日实机验证）

- **向上拖动会来回飘**：`dragBy` 命中目标格后先改数据、再算偏移，而列表布局要等下一次测量才更新，
  于是补偿量算在旧布局上，向上拖时反复"换回去"。改为**先用「目标当前顶边 − 被拖卡片当前顶边」
  做等量反向补偿，再提交重排**，卡片始终贴合手指；每次换位补一次震动反馈。
- **右侧排序图标不能直接拖**：该图标上一版只挂了 `clickable`（切换"按住即拖"状态），
  本身不是拖拽把手。现在图标自身绑定 `detectDragGestures`，**按住即拖，无需先点、无需再长按**；
  底部「排序」按钮与"按住整张卡片拖动"作为额外入口保留。
- 实机验证：长按进编辑 → 直接拖第 4 张卡的把手向上，跨 3 位换到第 2 位（其余卡位不动）；
  再上移一位亦只动一格，无跳动。退出编辑模式后 `config.xml` 落盘为
  `wb2a;Surfing;zygisk_lsposed;ag2a;AdGuardHome;…`，与可见顺序一致。
  产物 sha256 `fa2a765cf7d5e3580ffb6b0e896edf5066e071f6b6d5ce865837fc2d7d3e9637`。

### 产物

`dist/apm-search-pin-sort-compact-release.apk`（5.9 MB，`versionCode 11142` /
`versionName 166daa0`，R8 混淆 release，自有签名，sha256
`f6df123e6e744cf8cad111d9911ddb3c374bbfea68dd1f4615341f0f019084f8`）。

### 验证（实机，me.bmax.apatch 11142）

- 长按进入编辑模式：勾选框、图钉、抓手、底部栏均出现；退出后全部消失。
- 点抓手后向下拖动首张卡片，AdGuard 从第 4 位移到第 2 位；再向上拖亦生效。
- 退出编辑模式后顺序保持，`config.xml` 中 `apm_ui_sort_mode=CUSTOM`，
  `apm_ui_custom_order` 首段为 `wb2a;zygisk_lsposed;Surfing;ag2a;AdGuardHome;…`，
  与拖动后的可见顺序一致（已落盘）。
- 未验证：批量置顶按钮的实际点击效果（留待日常使用确认）。

## [二开 2.2] - 2026-10-30

### 修复：安装包启动后即自杀重启（屏幕持续闪烁的真正原因）

二开 2.1 的判断是错的。二开 2.1 的源码里同样存在 `derivedStateOf` 中写状态那一行，
而当时代码同源的 debug 包运行正常，说明那不是本问题的原因。真因如下：

- 现象：打开 APatch 后屏幕一直闪。抓 logcat 发现 APatch 每约 130 ms 就被拉起又退出，
  ActivityManager 反复出现 `Process me.bmax.apatch has died`，
  日志为 `System.exit called, status: 0` + `VM exiting with result code 0`。
  **这不是崩溃，是主动退出**，出处是 `APatchApp` 中签名校验失败后的
  `while (true) { startActivity(ACTION_DELETE); exitProcess(0) }`。
- 根因：`verifyAppSignature()` 用 `&&` 组合 `signatureFromAPK()` 与 `signatureFromAPI()`。
  其中 `signatureFromAPK()` 用正则 `META-INF/...` 匹配 JAR 签名文件来取证书，
  但发布包由 apksigner 默认走 **v2 签名**，`META-INF` 下根本没有 `.RSA` 文件
  （实测 `apksigner verify -v`：v1=false，v2=true），该函数恒返回 `null`，
  `&&` 让整条校验恒为 `false`，于是每次启动都被判定为签名不合法并自杀。
- 为什么 debug 版没事：校验外层是 `!BuildConfig.DEBUG && ...`，debug 包直接短路跳过。
- 修复：改为 `apkSignature ?: apiSignature`，任一来源取到证书即可校验，
  两者都取不到才判失败；并新增 `signature mismatch` 告警日志，
  同时打印 apk / api / expected 三个哈希便于排查。
- 实机验证：同一 pid 存活 30 秒以上且 CPU 时间持续增长（0:00→0:03），
  `System.exit called` 计数为 0，进程死亡计数为 0；
  日志中可见 `apk=null api=rxg7y7…` 证明上述回退确实生效。

### 产物

`dist/apm-search-pin-sort-compact-release.apk`（5.6 MB，`versionCode 11148` /
`versionName 02c034a`，R8 混淆 release，自有签名，sha256
`2d50e9afa2b207133ea19052e28568bfc6baf56b63c4780e1d964c8ee2b3a980`）。
签名与版本号均未变，可直接覆盖安装。

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

## 2026-10-31 拖拽排序修复

### 修复

- `APM.kt` `startDrag()` 增加 `editMode` 守卫：非编辑态长按仍会进到
  `detectDragGesturesAfterLongPress` 的手势流里，原先会把「浏览时手指滑动」
  误判成拖拽并写坏自定义顺序。
- `endDrag()` 增加「顺序未变化就跳过」判断，避免一次误触把排序模式切成 CUSTOM；
  松手落盘时改用新的 `updateCustomOrder(order, includeHidden)`，搜索态下把被
  过滤掉的模块按原相对位置补回，不再从自定义顺序里被抹掉、松手后跳到末尾。
- `APModuleViewModel.updateCustomOrderFromVisible` 改为委托
  `updateCustomOrder(visibleIds, includeHidden = true)`，行为与 UI 一致。
- 修掉拖拽代码里两处此前无法编译的调用：`state.scrollBy` 缺
  `androidx.compose.foundation.gestures.scrollBy` 导入，`Dp.roundToPx` 改为
  `with(density) { ... }`（`LocalDensity.current` 渲染期读取，不在 lambda 外取值）。

### 构建与安装

- `./gradlew :app:assembleRelease`（Debian 容器 `/workspace`，JDK 21 / SDK 36 /
  NDK 29 预置的 `libs/arm64-v8a/*.so`）**BUILD SUCCESSFUL**。
- 产物 `dist/apm-drag-sort-fix-release.apk`（5,929,407 字节，本机二开签名），
  `versionCode=11142 / versionName=166daa0`，已 `pm install -r -d` 覆盖安装并启动，
  进程存活无崩溃。

## 2026-10-31 排序整体重写（换成成熟库）

### 之前自制拖拽的问题

影子列表 + 手动 translationY + 自算边缘滚动这套实现与 LazyColumn 的布局互相对抗，
并且每次换位都要我们自己折抵位移，容易出现跳位、乱滚、松手回弹，改动成本高于收益。

### 做法：改用 `sh.calvin.reorderable:reorderable:3.1.0`

- 选型依据：该库被 gkd、Mihon、Lawnchair、Home Assistant、Pocket Casts 等项目使用
  （README "Used By" 列表），Apache-2.0，专门解决 Compose LazyColumn 拖拽排序。
- 读了它的实现（`ReorderableLazyCollection.kt`）后按其设计使用：
  - 换位判定：被拖卡片矩形是否覆盖目标卡片中心（`shouldItemMove`）；
  - 每次换位后 `onMove` 回调，并 `Mutex` + `snapshotFlow(layoutInfo).take(2)` 等
    布局真正更新完再算下一格，从根本上避免跳位；
  - 边缘自动滚动用 `animateScrollBy` + 距离映射速度，且有 `maxScrollDistance` 上限；
  - 松手用 `Animatable` 把卡片弹回落位，由库负责。
- `APM.kt`：删除全部自制拖拽代码（`draggingId/dragOrder/dragDelta/autoScroll`、
  自算 `translationY`、手写 `scrollBy` 循环、`zIndex/graphicsLayer`），改为
  `rememberReorderableLazyListState` + `ReorderableItem` + 作用域内的
  `Modifier.longPressDraggableHandle`。
- `APModuleViewModel` 新增 `moveModuleByIds(id, targetIndex, visibleIds)`：
  接收库给出的绝对目标位置，统一走 `updateCustomOrder` 落盘；搜索态下保留被隐藏项。
- 拖拽仅在编辑模式生效（`enabled = editMode`），避免浏览时误改顺序。

### 修掉「一直转圈、没有下拉也在转」

`fetchModuleList()` 原先只在 `oldModuleList === modules` 这种极窄条件下复位
`isRefreshing`，列表内容没变化或解析中途抛异常时 `isRefreshing` 会永远为 true，
PullToRefreshBox 的指示器就一直转。现改为 `try/catch/finally`：
- `finally` 无条件 `isRefreshing = false`；
- 新增 `refreshError`，把刷新失败原因暴露给界面，而不是静默挂在刷新态。

### 构建与安装

- 新增依赖 `libs.reorderable`（`gradle/libs.versions.toml` + `app/build.gradle.kts`）。
- `./gradlew :app:assembleRelease` **BUILD SUCCESSFUL**（3m30s，含 R8）。
- `dist/apm-reorder-rewrite-release.apk`（5,946,783 字节），`pm install -r -d` 成功，
  启动后进程存活、无 FATAL。

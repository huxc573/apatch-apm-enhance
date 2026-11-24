# AGENTS.md

本文件面向在本仓库工作的 ZCode/自动化 Agent。只记录项目特有、容易踩坑的事实；通用 Android 开发规范不重复。

> 本文件是仓库内唯一的 Agent 说明；不要再恢复或新建 `.MEMORY.md`。

## 项目定位

- APatch（Android 内核 root 管理器）的**个人二开 fork**，不是上游官方发布，**不提交上游 PR**。
- 二开范围：主体是「系统模块（APModule）」列表界面（搜索、置顶、自定义排序、紧凑布局、编辑模式批量置顶 / 拖拽排序、下拉刷新）；另有 apd 分身 uid 修复与启动自愈同步（见 `CHANGELOG.md` 二开 2.4）。
- 包名 `me.bmax.apatch`；minSdk 26 / targetSdk 36；**仅支持 arm64-v8a**。
- 上游基线：`bmax121/APatch@166daa0`，对应上游 `versionCode 11142` / `versionName 166daa0`；GPL-3.0。二开版本号按日期规则（`YYMMDDNN` / `vYY.MM.DD_N`），与上游基线号无关；具体当前值以根 `build.gradle.kts` 或 `./gradlew printVersion` 为准，本文件不随每次发版更新。
- `origin` = 本 fork（`huxc573/apatch-apm-enhance`）。

## 目录与职责

| 路径 | 说明 |
| --- | --- |
| `app/src/main/java/me/bmax/apatch/ui/screen/APM.kt` | APModule 列表 UI（二开主战场：TopBar、搜索、编辑模式、Reorderable、PullToRefresh） |
| `app/src/main/java/me/bmax/apatch/ui/viewmodel/APModuleViewModel.kt` | 模块加载、搜索过滤、排序、置顶、自定义顺序、刷新状态 |
| `app/src/main/java/me/bmax/apatch/ui/viewmodel/APModuleUiPrefs.kt` | UI 偏好持久化（config SharedPreferences，键前缀 `apm_ui_*`） |
| `app/src/main/java/me/bmax/apatch/APatchApp.kt` | Application、AP/KP 状态机、release 签名白名单、启动 apd 自愈同步 |
| `app/src/main/java/me/bmax/apatch/util/APatchCli.kt` | root shell、`listModules` / `toggleModule` / `uninstallModule` / `hasMagisk` |
| `app/src/main/cpp/`、`app/src/main/aidl/` | JNI 与 AIDL（二开不应改动） |
| `apd/` | Rust 实现的 AndroidPatch daemon（交叉编译出 `libapd.so`）；`synchronize_package_uid()` 已修分身 uid |
| `scripts/` | `update-binary` / `updater-script` 模板 |
| `docs/` | 多语言模块开发文档（上游内容） |
| `CHANGELOG.md`、`AUDIT.md`、`BUILD.md` | 二开版本与事故记录、改动审计、构建/签名说明；改构建或 APModule 前先读 |

## 构建与检查

环境要求：JDK 21、Android SDK Platform 36、Build Tools 36.0.0、NDK 29.0.14206865、CMake 3.28+；完整打包还需 Rust + `cargo-ndk`。

```bash
# 快速 Kotlin 编译检查（仍需本地 Android SDK）
./gradlew :app:compileDebugKotlin

# 打包
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease   # 签名优先读本机统一密钥目录（见 BUILD.md §4），回落到仓库根 signing.properties；都缺失则 debug 签名

# 版本号核对
./gradlew printVersion

# lint（可选；lint.abortOnError=true，checkReleaseBuilds=false）
./gradlew :app:lintDebug
```

注意：

- `local.properties` 不入库，需本地写 `sdk.dir=...`。
- `preBuild` 会联网下载 `kpimg` / `libkptools.so` / `libkpatch.so`，并触发 `cargoBuild` 构建 `apd`；离线或无 Rust/NDK 时会失败。只想编译 Kotlin 时按 `BUILD.md` §3/§4 临时绕过，不要提交这些绕过改动。
- Windows 下 Gradle 不自动读取系统代理；GitHub Releases 下载失败时，在用户级 `gradle.properties` 配 `systemProp.http(s).proxyHost/Port`，或按 `BUILD.md` §4 使用预置 native 库。
- 仓库无单元测试/仪器测试，也没有 CI（workflow 已移除）。验证以编译 + 实机为准。
- 产物放 `dist/`（gitignore，不入库）。

## 关键约束（改动前必读）

1. **二开版本号必须管理器与 apd 同步。** 采用日期规则：`versionCode = YYMMDDNN`（如 `26112401`），`versionName = vYY.MM.DD_N`（如 `v26.11.24_1`）。根 `build.gradle.kts` 的 `getVersionCode()/getVersionName()` 与 `apd/build.rs` 的 `VERSION_CODE/VERSION_NAME` 必须同时修改且完全一致，否则 `apd -V` 与管理器版本不等，状态停在 `ANDROIDPATCH_NEED_UPDATE`，底栏「系统模块」入口消失。不要恢复上游“按 git 提交数自增”的算法。`kernelPatchVersion` / `kpimg` 是另一套，只有更换 KernelPatch 时才改；改它会触发重刷 boot。具体当前版本值以这两个文件为准，本文件只描述规则。
2. **APModule 页面有状态门禁。** 仅当 ap 状态为 `ANDROIDPATCH_INSTALLED` / `ANDROIDPATCH_NEED_UPDATE` 时渲染列表，否则只显示 “AndroidPatch not installed”。调试“列表不显示”先查版本号/安装状态，不要先改 UI。
3. **release 签名白名单。** `APatchApp.SIGNATURE_HASHES` 放行官方与本机二开证书。换 keystore 必须同步加入新证书哈希，否则 release 启动即弹卸载并 `exitProcess`。debug 包因 `!BuildConfig.DEBUG` 短路跳过校验。
4. **下拉刷新必须复位。** `fetchModuleList()` 在 `finally` 中把 `isRefreshing = false`；不要恢复“新旧列表引用相同才停转圈”的旧写法（内容不变时圈会一直转，见提交 `cc9a5d8`）。
5. **`derivedStateOf` 内禁止写状态。** `APModuleViewModel.moduleList` 的 lambda 在组合/测量阶段求值，写 `isRefreshing` 等 Compose 状态会导致“读取→失效→重组→再求值”自激循环，整屏闪烁直至 ANR（历史事故见 `CHANGELOG.md` 二开 2.1）。
6. **排序数据流固定：** `moduleList = 搜索过滤 → 排序模式 → 置顶稳定重排`；UI 只渲染，不参与排序。排序/置顶只影响展示顺序，**绝不**影响模块加载顺序、挂载顺序或 `/data/adb/modules` 内容。
7. **拖拽用 `sh.calvin.reorderable`，不要自制手势。** `rememberReorderableLazyListState` → `ReorderableItem(enabled = editMode)` → `longPressDraggableHandle` 必须挂在 `ReorderableItem` 作用域（它是该作用域的扩展）。非编辑态不要挂 `combinedClickable` 抢手势；移动逻辑用插入式（`remove + add`）的 `moveModuleByIds` / `moveModuleTo`，不要改回相邻交换（会回弹/跳位）。
8. **搜索态下改顺序不能丢隐藏项。** 拖拽/移动把可见顺序落盘时要用 `updateCustomOrder(..., includeHidden = true)` 把被过滤项按原顺序补回，否则未显示的模块会被从自定义顺序中抹掉。
9. **文案双写。** 新增 UI 文案同时加到 `res/values/strings.xml` 和 `res/values-zh-rCN/strings.xml`，key 用 `apm_` 前缀；其他语言回落英文，不要批量改 Weblate 管理的翻译文件。
10. **持久化边界。** 只用 `config` SharedPreferences（`APModuleUiPrefs`），不写系统属性、不新增 `/data/adb` 文件；卸载管理器即失效。
11. **apd 自愈同步不要删。** 应用启动时在读取 apd 版本前调用 `syncBundledApdIfNeeded()`，比较 APK 内置 `libapd.so` 与 `/data/adb/apd` 的 SHA-256，不一致就原子替换。这样版本号升级和同版本重建都只需“安装 APK → 打开一次 → 重启”，不会停在“新版本可升级”。它只动 apd，不碰 `kpimg` / boot。
12. **apd 只更新 user 0 的 uid。** `synchronize_package_uid()` 只能匹配 `uid < 100000` 的条目；分身/多用户 uid 是 `userId * 100000 + appId`（如 `99910201`），`/data/system/packages.list` 只有 user 0 条目，必须原样保留。改动这里要在带 Flyme 平行空间/多用户的真机上验证。

## 代码约定

- Kotlin official style；Compose + Material3；路由用 compose-destinations（`@Destination<RootGraph>`，KSP 生成）。
- 注释重点写“为什么”（尤其排序、手势、刷新状态），与现有中文注释风格一致。
- 改动尽量限于 APModule 相关文件；不要顺手改 native / 模块安装卸载流程。`apd` 只在修 daemon bug 时改，并同步版本号、实机验证。
- 行为变更同步更新 `CHANGELOG.md`（二开版本小节）；新的踩坑结论、事故过程与构建验证结果也记录到 `CHANGELOG.md`，不要只写在代码注释里。
- 文档脱敏：`AGENTS.md`、`README.md`、`BUILD.md`、`CHANGELOG.md` 不写设备 IP、密钥目录绝对路径、口令/token、设备序列号；本地环境信息放不入库文件。改动文档后、公开发布前做一次扫描。

## 真机验证（release 必做）

- release 包必须实机确认进程存活 ≥30 秒，不能只看 `BUILD SUCCESSFUL`。历史事故：v2-only 签名使 `signatureFromAPK()` 返回 null，签名校验恒失败，App 每 ~130ms 自杀重启（详见 `CHANGELOG.md` 二开 2.2）。
- 常用排查：`adb logcat` 过滤 `APatch` / `ModuleViewModel`，关注 `System.exit called`、`Process me.bmax.apatch has died`、`load cost:`。
- 安装新 APK 后打开一次，启动会自动同步 `/data/adb/apd`；重启后确认 `apd -V` 与管理器 versionCode 一致，再验证功能。
- 二开包与官方包签名不同；从官方包覆盖前需先卸载官方管理器（会清管理器侧 SuperKey 配置，`/data/adb` 模块数据不受影响）。同签名的二开旧包可直接覆盖。

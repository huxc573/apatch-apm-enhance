# CHANGELOG

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

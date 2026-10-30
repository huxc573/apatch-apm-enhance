## 二开说明（Downstream fork，不提交上游 PR）

本仓库是**本机自用的 APatch 二开分支**，不是上游官方发布，也不是待合并的 PR。

| 项 | 值 |
| --- | --- |
| 上游项目 | [bmax121/APatch](https://github.com/bmax121/APatch)（GPL-3.0） |
| 二开基线 | [`166daa0`](https://github.com/bmax121/APatch/commit/166daa081c5107e1036a3e5caa1380e8a1a863cb) — `Revert apd: uid listen event move to post-data-fs`（2025-11-12） |
| 基线版本号 | `versionCode 11142` / `versionName 166daa0`（942 个提交，与本机安装的 APatch 完全一致） |
| 本分支 | `feat/apm-search-pin-sort-compact` |
| 改动范围 | 仅「系统模块（APModule）」列表界面：搜索、置顶、自定义排序、紧凑布局 |

**为什么不做 PR**：这四项都是个人使用偏好，和上游模块页的 UI 规划不一致，按上游口味大概率
不会被合并；因此只在本仓库维护，不改上游行为、不改 native / apd / 模块安装流程。

**引用与许可**：上游代码与本文档遵循 GPL-3.0（见 [LICENSE](LICENSE)），版权归 bmax121 及
APatch 贡献者所有；本仓库仅在其基础上做上述界面增强。改动点、混淆样本定位与验证记录见
[AUDIT.md](AUDIT.md)，构建步骤见 [BUILD.md](BUILD.md)，版本记录见 [CHANGELOG.md](CHANGELOG.md)。

**安装提示**：自编译包使用本机 debug 签名，与官方包签名不同，覆盖安装前需先卸载官方管理器
（会清掉管理器侧的 SuperKey 配置，模块与 `/data/adb` 数据不受影响）。


<div align="center">
<a href="https://github.com/bmax121/APatch/releases/latest"><img src="https://images.weserv.nl/?url=https://raw.githubusercontent.com/bmax121/APatch/main/app/src/main/ic_launcher-playstore.png&mask=circle" style="width: 128px;" alt="logo"></a>

<h1 align="center">APatch</h1>

[![Latest Release](https://img.shields.io/github/v/release/bmax121/APatch?label=Release&logo=github)](https://github.com/bmax121/APatch/releases/latest)
[![Nightly Release](https://img.shields.io/badge/Nightly%20release-gray?logo=hackthebox&logoColor=fff)](https://nightly.link/bmax121/APatch/workflows/build/main/APatch)
[![Weblate](https://img.shields.io/badge/Localization-Weblate-teal?logo=weblate)](https://hosted.weblate.org/engage/APatch)
[![Channel](https://img.shields.io/badge/Follow-Telegram-blue.svg?logo=telegram)](https://t.me/APatchGroup)
[![GitHub License](https://img.shields.io/github/license/bmax121/APatch?logo=gnu)](/LICENSE)

</div>

The patching of Android kernel and Android system.

- A new kernel-based root solution for Android devices.
- APM: Support for modules similar to Magisk.
- KPM: Support for modules that allow you to inject any code into the kernel (Provides kernel function `inline-hook` and `syscall-table-hook`).
- APatch relies on [KernelPatch](https://github.com/bmax121/KernelPatch/).
- The APatch UI and the APModule source code have been derived and modified from [KernelSU](https://github.com/tiann/KernelSU).

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/me.bmax.apatch/)

Or download the latest APK from the [Releases Section](https://github.com/bmax121/APatch/releases/latest).

## Supported Versions

- Only supports the ARM64 architecture.
- Only supports Android kernel versions 3.18 - 6.12

Support for Samsung devices with security protection: Planned

## Requirement

Kernel configs:

- `CONFIG_KALLSYMS=y` and `CONFIG_KALLSYMS_ALL=y`

- `CONFIG_KALLSYMS=y` and `CONFIG_KALLSYMS_ALL=n`: Initial support

## Security Alert

The **SuperKey** has higher privileges than root access.  
Weak or compromised keys can lead to unauthorized control of your device.  
It is critical to use robust keys and safeguard them from exposure to maintain the security of your device.

## Translation

To help translate APatch or improve existing translations, please use [Weblate](https://hosted.weblate.org/engage/apatch/). PR of APatch translation is no longer accepted, because it will conflict with Weblate.

<div align="center">

[![Translation Status](https://hosted.weblate.org/widget/APatch/open-graph.png)](https://hosted.weblate.org/engage/APatch/)

</div>

## Get Help

### Usage

For usage, please refer to [our official documentation](https://apatch.dev).  
It's worth noting that the documentation is currently not quite complete, and the content may change at any time.  
Furthermore, we need more volunteers to [contribute to the documentation](https://github.com/AndroidPatch/APatchDocs) in other languages.

### Updates

- Telegram Channel: [@APatchUpdates](https://t.me/APatchChannel)

### Discussions

- Telegram Group: [@APatchDiscussions(EN/CN)](https://t.me/Apatch_discuss)
- Telegram Group: [中文](https://t.me/APatch_CN_Group)

### More Information

- [Documents](docs/)

## Credits

- [KernelPatch](https://github.com/bmax121/KernelPatch/): The core.
- [Magisk](https://github.com/topjohnwu/Magisk): magiskboot and magiskpolicy.
- [KernelSU](https://github.com/tiann/KernelSU): App UI, and Magisk module like support.

## License

APatch is licensed under the GNU General Public License v3 [GPL-3](http://www.gnu.org/copyleft/gpl.html).

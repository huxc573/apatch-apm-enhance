# 构建说明

基线 `166daa0` 的构建要求（来自上游 `build.gradle.kts`）：

| 组件 | 版本 |
| --- | --- |
| JDK | 21 |
| Android SDK Platform | 36 |
| Build Tools | 36.0.0 |
| NDK | 29.0.14206865 |
| CMake | 3.28.0+ |
| Rust + cargo-ndk | 构建 `apd`（`apd/`，产出 `libs/arm64-v8a/libapd.so`） |

## 1. 准备

```bash
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk          # 需装 platforms;android-36 build-tools;36.0.0 ndk;29.0.14206865 cmake;3.28.3
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$PATH
echo "sdk.dir=$ANDROID_HOME" > local.properties

# apd 需要 Rust 交叉编译
rustup target add aarch64-linux-android
cargo install cargo-ndk
```

## 2. 编译 / 打包

```bash
./gradlew :app:assembleDebug      # 调试包，debug 签名
# 或
./gradlew :app:assembleRelease    # 需要在 gradle.properties 或环境变量提供签名：
#   KEYSTORE_FILE / KEYSTORE_PASSWORD / KEY_ALIAS / KEY_PASSWORD
```

产物：`app/build/outputs/apk/<variant>/APatch_<versionCode>_<versionName>_on_<branch>.apk`。
`versionCode/versionName` 由 `git rev-list --count HEAD` 与 `git rev-parse --short HEAD`
自动计算，二开分支提交后会比 11142 增大，属正常。

## 3. 只做编译检查（不打包、不需要 NDK）

去掉 `ndkVersion` 与 `externalNativeBuild`、跳过 `buildApd` 后：

```bash
./gradlew :app:compileDebugKotlin
```

本次验证即用这种方式在 arm64 设备上跑通（x86_64 的 aapt2/aidl 由
`qemu-user-static` + `binfmt_misc` 转译），结果 `BUILD SUCCESSFUL`。

## 4. 用现成 native 库出包（不想装 NDK / Rust 时）

管理器自身的 JNI 库与版本强绑定，可直接复用同版本官方 APK 里的 `lib/arm64-v8a/*.so`：

```bash
unzip -j base.apk 'lib/arm64-v8a/*.so' -d app/libs/arm64-v8a/
```

然后在 `app/build.gradle.kts` 中临时注释 `externalNativeBuild`、`ndkVersion`，
并删掉 `mergeDebugJniLibFolders/mergeReleaseJniLibFolders dependsOn buildApd` 的绑定，
再执行 `assembleDebug`。产物功能与官方一致，只是签名不同（安装前需卸载官方管理器）。

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

二开版本号规则（日期 + 当天序号）：

```text
versionCode = YYMMDDNN        例如 2026-11-24 第 1 个包 = 26112401
versionName = vYY.MM.DD_N     例如 v26.11.24_1
```

根 `build.gradle.kts` 的 `getVersionCode()/getVersionName()` 与 `apd/build.rs` 的
`VERSION_CODE/VERSION_NAME` 必须同时修改且完全一致，否则 `apd -V` 与管理器版本不等，
状态会停在 `ANDROIDPATCH_NEED_UPDATE`，底栏「系统模块」入口消失。
`kernelPatchVersion` / `kpimg` 是另一套版本，只有更换 KernelPatch 时才改；不动它们
就不会提示重新修补 boot。

> 安装新 APK 后，应用启动时会比较内置 `libapd.so` 与 `/data/adb/apd` 的 SHA-256，
> 不一致就原子替换并 toast 提示重启。版本号升级和同版本重建都走这条路径，
> 不需要手动 adb 替换，也不会停在“新版本可升级”。

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

## 4. 签名（二开出包）

release 包用自有密钥，口令不入库：

| 项 | 值 |
| --- | --- |
| 密钥 | `apatch-apm-enhance.jks`（PKCS12，RSA 4096，100 年） |
| alias | `apatch-enhance` |
| 规范位置 | 本机统一密钥目录下的 `apatch-apm-enhance.jks`（具体路径不入库，见该目录 README） |
| 配置 | 同目录 `signing.properties`；根 `build.gradle.kts` 优先读 `local.properties` 的 `apatch.signing.properties`（不入库），读不到再回落仓库根 `signing.properties` |
| 手机侧原件 | 本机 Debian chroot 的 `projects/apatch-apm-enhance`（不入库） |

本机路径写在 `local.properties`（gitignore，不入库）：

```properties
apatch.signing.properties=<密钥目录>/signing.properties
```

启动时的签名校验（`APatchApp.SIGNATURE_HASHES`）同时放行官方签名与本机二开签名，
所以 release 包不需要 `BuildConfig.DEBUG` 也能正常启动。换密钥时必须同步改
`SIGNATURE_HASHES`，否则 App 启动即弹卸载框。

```bash
# 重新生成（口令、alias 需与 signing.properties 一致）
KEYSTORE='<本机密钥目录>/apatch-apm-enhance.jks'
keytool -genkeypair -v -keystore "$KEYSTORE" \
  -alias apatch-enhance -keyalg RSA -keysize 4096 -validity 36500 \
  -storetype PKCS12 -storepass '<口令>' -keypass '<口令>' \
  -dname 'CN=apatch-apm-enhance, OU=Personal Build, O=apatch-apm-enhance, L=Local, ST=Local, C=CN'
# 证书 DER 的 SHA-256 → Base64，即 SIGNATURE_HASHES 里那一串
keytool -exportcert -alias apatch-enhance -keystore "$KEYSTORE" \
  -storepass '<口令>' -file apm.crt
python -c "import hashlib,base64;print(base64.b64encode(hashlib.sha256(open('apm.crt','rb').read()).digest()).decode())"
```

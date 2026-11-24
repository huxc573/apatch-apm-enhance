import com.android.build.api.dsl.ApplicationDefaultConfig
import com.android.build.api.dsl.CommonExtension
import com.android.build.gradle.api.AndroidBasePlugin

plugins {
    alias(libs.plugins.agp.app) apply false
    alias(libs.plugins.agp.lib) apply false
    alias(libs.plugins.kotlin) apply false
    alias(libs.plugins.kotlin.compose.compiler) apply false
}

project.ext.set("kernelPatchVersion", "0.12.2")

// 二开 release 签名：口令不进版本库。
// 优先读 local.properties 的 apatch.signing.properties（本机路径，不入库），
// 没有再回落仓库根 signing.properties（手机/其他机器构建用）；
// 两者都没有时交给 apksign 插件回落到 debug 签名。
val localProperties = java.util.Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
val externalSigningFile = localProperties.getProperty("apatch.signing.properties")
    ?.let { java.io.File(it) }
val localSigningFile = externalSigningFile?.takeIf { it.exists() }
    ?: rootProject.file("signing.properties").takeIf { it.exists() }
if (localSigningFile != null) {
    java.util.Properties().apply {
        localSigningFile.inputStream().use { load(it) }
    }.forEach { (key, value) -> project.ext.set(key.toString(), value) }
}

val androidMinSdkVersion = 26
val androidTargetSdkVersion = 36
val androidCompileSdkVersion = 36
val androidBuildToolsVersion = "36.0.0"
val androidCompileNdkVersion = "29.0.14206865"
val managerVersionCode by extra(getVersionCode())
val managerVersionName by extra(getVersionName())
val branchname by extra(getbranch())
fun Project.exec(command: String) = providers.exec {
    commandLine(command.split(" "))
}.standardOutput.asText.get().trim()

fun getbranch(): String {
    return "apm-enhance"
}

/**
 * 二开版本号规则：日期 + 当天第几个包。
 *
 * - versionCode = YYMMDD + 两位当天序号，例如 2026-11-24 的第 1 个包 = 26112401。
 * - versionName = vYY.MM.DD_N，例如 v26.11.24_1。
 *
 * 这个版本号只代表管理器与 apd 的二开版本，与 KernelPatch 无关。
 * `apd/build.rs` 的 VERSION_CODE / VERSION_NAME 必须与这里完全一致，
 * 否则会一直停在 ANDROIDPATCH_NEED_UPDATE。
 *
 * 内核补丁版本仍是 kernelPatchVersion（当前 0.12.2）。只要 kpimg 不变，
 * 内核补丁卡片就保持原样，不会提示重新修补 boot。
 *
 * versionCode 必须单调递增，不要回退日期；YYMMDDNN 到 2099 年都低于
 * Android 的 2,147,483,647 上限。
 */
fun getVersionCode(): Int {
    return 26112401
}

fun getVersionName(): String {
    return "v26.11.24_1"
}

tasks.register("printVersion") {
    doLast {
        println("Version code: $managerVersionCode")
        println("Version name: $managerVersionName")
    }
}

subprojects {
    plugins.withType(AndroidBasePlugin::class.java) {
        extensions.configure(CommonExtension::class.java) {
            compileSdk = androidCompileSdkVersion
            buildToolsVersion = androidBuildToolsVersion
            ndkVersion = androidCompileNdkVersion

            defaultConfig {
                minSdk = androidMinSdkVersion
                if (this is ApplicationDefaultConfig) {
                    targetSdk = androidTargetSdkVersion
                    versionCode = managerVersionCode
                    versionName = managerVersionName
                }
            }

            lint {
                abortOnError = true
                checkReleaseBuilds = false
            }
        }
    }
}

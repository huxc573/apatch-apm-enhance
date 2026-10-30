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

// 二开 release 签名：口令不进版本库，从本机 signing.properties 读，缺失时交给 apksign 插件回落到 debug 签名
val localSigningFile = rootProject.file("signing.properties")
if (localSigningFile.exists()) {
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

/** 二开基线：上游 bmax121/APatch@166daa0，对应 versionCode 11142。 */
val BASE_COMMIT = "166daa081c5107e1036a3e5caa1380e8a1a863cb"

/** 基线提交的上游提交数，固定值，避免依赖 git 子进程（构建沙箱里会撞 dubious ownership）。 */
val BASE_COMMIT_COUNT = 942

fun getGitCommitCount(): Int {
    return 942
}

fun getGitDescribe(): String {
    return "166daa0"
}

fun getbranch(): String {
    return "apm-enhance"
}

/**
 * 二开说明：版本号必须与本机已安装的系统补丁（11142）保持一致。
 *
 * 上游原始算法是 `1*10000 + 提交数 + 200`，会随分支上新增的提交一起上涨。
 * 但 APatch 底栏的「系统模块」入口只有当状态为 ANDROIDPATCH_INSTALLED 时才显示，
 * 而该状态要求管理器版本号与内核里的补丁版本号相同（见 APatchApp 的状态判定：
 * `mgv.toInt() != Version.installedApdVInt` 即置为 ANDROIDPATCH_NEED_UPDATE）。
 * 本机系统补丁是 11142，一旦管理器版本号变成 11148 之类，模块入口就会整个消失。
 *
 * 因此这里写死基线提交（bmax121/APatch@166daa0）的提交数 942，得到 11142。
 * 不使用 git 命令，因为构建沙箱里 gradle 子进程会撞 dubious ownership 而失败。
 */
fun getVersionCode(): Int {
    val major = 1
    return major * 10000 + 942 + 200
}

fun getVersionName(): String {
    return getGitDescribe()
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

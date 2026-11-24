package me.bmax.apatch

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.topjohnwu.superuser.CallbackList
import me.bmax.apatch.ui.CrashHandleActivity
import me.bmax.apatch.util.APatchCli
import me.bmax.apatch.util.APatchKeyHelper
import me.bmax.apatch.util.Version
import me.bmax.apatch.util.getRootShell
import me.bmax.apatch.util.rootShellForResult
import me.bmax.apatch.util.verifyAppSignature
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.system.exitProcess

lateinit var apApp: APApplication

const val TAG = "APatch"

class APApplication : Application(), Thread.UncaughtExceptionHandler {
    lateinit var okhttpClient: OkHttpClient

    init {
        Thread.setDefaultUncaughtExceptionHandler(this)
    }

    enum class State {
        UNKNOWN_STATE,

        KERNELPATCH_INSTALLED, KERNELPATCH_NEED_UPDATE, KERNELPATCH_NEED_REBOOT, KERNELPATCH_UNINSTALLING,

        ANDROIDPATCH_NOT_INSTALLED, ANDROIDPATCH_INSTALLED, ANDROIDPATCH_INSTALLING, ANDROIDPATCH_NEED_UPDATE, ANDROIDPATCH_UNINSTALLING,
    }


    companion object {
        const val APD_PATH = "/data/adb/apd"

        /** 允许运行的签名白名单（证书 DER 的 SHA-256，Base64）。 */
        private val SIGNATURE_HASHES = arrayOf(
            "1x2twMoHvfWUODv7KkRRNKBzOfEqJwRKGzJpgaz18xk=", // 上游官方
            "rxg7yY7wlwsibbjFEnqtwHW8ZPU8DkrZ7EDDAbk1YD8=", // 本机二开
        )

        @Deprecated("No more KPatch ELF from 0.11.0-dev")
        const val KPATCH_PATH = "/data/adb/kpatch"
        const val SUPERCMD = "/system/bin/truncate"
        const val APATCH_FOLDER = "/data/adb/ap/"
        private const val APATCH_BIN_FOLDER = APATCH_FOLDER + "bin/"
        private const val APATCH_LOG_FOLDER = APATCH_FOLDER + "log/"
        private const val APD_LINK_PATH = APATCH_BIN_FOLDER + "apd"
        const val PACKAGE_CONFIG_FILE = APATCH_FOLDER + "package_config"
        const val SU_PATH_FILE = APATCH_FOLDER + "su_path"
        const val SAFEMODE_FILE = "/dev/.safemode"
        private const val NEED_REBOOT_FILE = "/dev/.need_reboot"
        const val GLOBAL_NAMESPACE_FILE = "/data/adb/.global_namespace_enable"
        const val LITE_MODE_FILE = "/data/adb/.litemode_enable"
        const val FORCE_OVERLAYFS_FILE = "/data/adb/.overlayfs_enable"
        const val KPMS_DIR = APATCH_FOLDER + "kpms/"

        @Deprecated("Use 'apd -V'")
        const val APATCH_VERSION_PATH = APATCH_FOLDER + "version"
        private const val MAGISKPOLICY_BIN_PATH = APATCH_BIN_FOLDER + "magiskpolicy"
        private const val BUSYBOX_BIN_PATH = APATCH_BIN_FOLDER + "busybox"
        private const val RESETPROP_BIN_PATH = APATCH_BIN_FOLDER + "resetprop"
        private const val MAGISKBOOT_BIN_PATH = APATCH_BIN_FOLDER + "magiskboot"
        const val DEFAULT_SCONTEXT = "u:r:untrusted_app:s0"
        const val MAGISK_SCONTEXT = "u:r:magisk:s0"

        private const val DEFAULT_SU_PATH = "/system/bin/kp"
        private const val LEGACY_SU_PATH = "/system/bin/su"

        const val SP_NAME = "config"
        private const val SHOW_BACKUP_WARN = "show_backup_warning"
        lateinit var sharedPreferences: SharedPreferences

        private val logCallback: CallbackList<String?> = object : CallbackList<String?>() {
            override fun onAddElement(s: String?) {
                Log.d(TAG, s.toString())
            }
        }

        private val _kpStateLiveData = MutableLiveData(State.UNKNOWN_STATE)
        val kpStateLiveData: LiveData<State> = _kpStateLiveData

        private val _apStateLiveData = MutableLiveData(State.UNKNOWN_STATE)
        val apStateLiveData: LiveData<State> = _apStateLiveData

        @Suppress("DEPRECATION")
        fun uninstallApatch() {
            if (_apStateLiveData.value != State.ANDROIDPATCH_INSTALLED) return
            _apStateLiveData.value = State.ANDROIDPATCH_UNINSTALLING

            Natives.resetSuPath(DEFAULT_SU_PATH)

            val cmds = arrayOf(
                "rm -f $APD_PATH",
                "rm -f $KPATCH_PATH",
                "rm -rf $APATCH_BIN_FOLDER",
                "rm -rf $APATCH_LOG_FOLDER",
                "rm -rf $APATCH_VERSION_PATH",
            )

            val shell = getRootShell()
            shell.newJob().add(*cmds).to(logCallback, logCallback).exec()

            Log.d(TAG, "APatch uninstalled...")
            if (_kpStateLiveData.value == State.UNKNOWN_STATE) {
                _apStateLiveData.postValue(State.UNKNOWN_STATE)
            } else {
                _apStateLiveData.postValue(State.ANDROIDPATCH_NOT_INSTALLED)
            }
        }

        @Suppress("DEPRECATION")
        fun installApatch() {
            val state = _apStateLiveData.value
            if (state != State.ANDROIDPATCH_NOT_INSTALLED && state != State.ANDROIDPATCH_NEED_UPDATE) {
                return
            }
            _apStateLiveData.value = State.ANDROIDPATCH_INSTALLING
            val nativeDir = apApp.applicationInfo.nativeLibraryDir

            Natives.resetSuPath(LEGACY_SU_PATH)

            val cmds = arrayOf(
                "mkdir -p $APATCH_BIN_FOLDER",
                "mkdir -p $APATCH_LOG_FOLDER",

                "cp -f ${nativeDir}/libapd.so $APD_PATH",
                "chmod +x $APD_PATH",
                "ln -s $APD_PATH $APD_LINK_PATH",
                "restorecon $APD_PATH",

                "cp -f ${nativeDir}/libmagiskpolicy.so $MAGISKPOLICY_BIN_PATH",
                "chmod +x $MAGISKPOLICY_BIN_PATH",
                "cp -f ${nativeDir}/libresetprop.so $RESETPROP_BIN_PATH",
                "chmod +x $RESETPROP_BIN_PATH",
                "cp -f ${nativeDir}/libbusybox.so $BUSYBOX_BIN_PATH",
                "chmod +x $BUSYBOX_BIN_PATH",
                "cp -f ${nativeDir}/libmagiskboot.so $MAGISKBOOT_BIN_PATH",
                "chmod +x $MAGISKBOOT_BIN_PATH",


                "touch $PACKAGE_CONFIG_FILE",
                "touch $SU_PATH_FILE",
                "[ -s $SU_PATH_FILE ] || echo $LEGACY_SU_PATH > $SU_PATH_FILE",
                "echo ${Version.getManagerVersion().second} > $APATCH_VERSION_PATH",
                "restorecon -R $APATCH_FOLDER",

                "${nativeDir}/libmagiskpolicy.so --magisk --live",
            )

            val shell = getRootShell()
            shell.newJob().add(*cmds).to(logCallback, logCallback).exec()

            // clear shell cache
            APatchCli.refresh()

            Log.d(TAG, "APatch installed...")
            _apStateLiveData.postValue(State.ANDROIDPATCH_INSTALLED)
        }

        fun markNeedReboot() {
            val result = rootShellForResult("touch $NEED_REBOOT_FILE")
            _kpStateLiveData.postValue(State.KERNELPATCH_NEED_REBOOT)
            Log.d(TAG, "mark reboot ${result.code}")
        }


        var superKey: String = ""
            set(value) {
                field = value
                val ready = Natives.nativeReady(value)
                _kpStateLiveData.value =
                    if (ready) State.KERNELPATCH_INSTALLED else State.UNKNOWN_STATE
                _apStateLiveData.value =
                    if (ready) State.ANDROIDPATCH_NOT_INSTALLED else State.UNKNOWN_STATE
                Log.d(TAG, "state: " + _kpStateLiveData.value)
                if (!ready) return

                APatchKeyHelper.writeSPSuperKey(value)

                thread {
                    val rc = Natives.su(0, null)
                    if (!rc) {
                        Log.e(TAG, "Native.su failed")
                        return@thread
                    }

                    // KernelPatch version
                    //val buildV = Version.buildKPVUInt()
                    //val installedV = Version.installedKPVUInt()
                    //use build time to check update
                    val buildV = Version.getKpImg()
                    val installedV = Version.installedKPTime()


                    Log.d(TAG, "kp installed version: ${installedV}, build version: $buildV")

                    // use != instead of > to enable downgrade,
                    if (buildV != installedV) {
                        _kpStateLiveData.postValue(State.KERNELPATCH_NEED_UPDATE)
                    }
                    Log.d(TAG, "kp state: " + _kpStateLiveData.value)

                    if (File(NEED_REBOOT_FILE).exists()) {
                        _kpStateLiveData.postValue(State.KERNELPATCH_NEED_REBOOT)
                    }
                    Log.d(TAG, "kp state: " + _kpStateLiveData.value)

                    // 二开：先自愈同步 apd，再读版本。
                    // 版本号变化后 installApatch() 不会自动执行；启动时对比 APK 内置
                    // libapd.so 与已安装 apd，不一致就原子替换，让“安装 APK → 打开一次 →
                    // 重启”即可生效，不需要手点更新，也不需要重新 patch boot。
                    if (Version.installedApdVUInt() > 0) {
                        apApp.syncBundledApdIfNeeded()
                    }

                    // AndroidPatch version（必须在同步之后读，才能拿到新 apd 的版本）
                    val mgv = Version.getManagerVersion().second
                    val installedApdVInt = Version.installedApdVUInt()
                    Log.d(TAG, "manager version: $mgv, installed apd version: $installedApdVInt")

                    if (Version.installedApdVInt > 0) {
                        _apStateLiveData.postValue(State.ANDROIDPATCH_INSTALLED)
                    }

                    if (Version.installedApdVInt > 0 && mgv.toInt() != Version.installedApdVInt) {
                        _apStateLiveData.postValue(State.ANDROIDPATCH_NEED_UPDATE)
                        // su path
                        val suPathFile = File(SU_PATH_FILE)
                        if (suPathFile.exists()) {
                            val suPath = suPathFile.readLines()[0].trim()
                            if (Natives.suPath() != suPath) {
                                Log.d(TAG, "su path: $suPath")
                                Natives.resetSuPath(suPath)
                            }
                        }
                    }
                    Log.d(TAG, "ap state: " + _apStateLiveData.value)

                    return@thread
                }
            }
    }

    /**
     * 二开：安装新 APK 后 installApatch() 不会自动执行，APK 内置的 libapd.so
     * 也就不会落到 /data/adb/apd。启动时比较两者 SHA-256，不一致就用同目录
     * 临时文件原子替换，覆盖后提示重启。
     *
     * 版本号升级与同版本重建都走这条路径，不需要手点“更新”，也不动 boot。
     */
    fun syncBundledApdIfNeeded() {
        val bundled = File(applicationInfo.nativeLibraryDir, "libapd.so")
        val installed = File(APD_PATH)
        if (!bundled.exists()) {
            Log.w(TAG, "bundled apd missing: ${bundled.absolutePath}")
            return
        }
        val same = try {
            installed.exists() &&
                bundled.length() == installed.length() &&
                sha256(bundled) == sha256(installed)
        } catch (e: Exception) {
            Log.w(TAG, "compare apd failed", e)
            false
        }
        if (same) return

        val result = rootShellForResult(
            "cp -f '${bundled.absolutePath}' $APD_PATH.new && " +
                "chmod 755 $APD_PATH.new && " +
                "mv -f $APD_PATH.new $APD_PATH && " +
                "restorecon $APD_PATH"
        )
        Log.i(TAG, "sync bundled apd: code=${result.code}")
        if (result.isSuccess) {
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(
                    applicationContext,
                    getString(R.string.apm_apd_synced_reboot),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    override fun onCreate() {
        super.onCreate()
        apApp = this

        val isArm64 = Build.SUPPORTED_ABIS.any { it == "arm64-v8a" }
        if (!isArm64) {
            Toast.makeText(applicationContext, "Unsupported architecture!", Toast.LENGTH_LONG)
                .show()
            Thread.sleep(5000)
            exitProcess(0)
        }

        // 上游官方签名 + 本机二开签名（apktools 规范目录下的 apatch-apm-enhance.jks）都允许运行
        if (!BuildConfig.DEBUG && SIGNATURE_HASHES.none { verifyAppSignature(it) }) {
            while (true) {
                val intent = Intent(Intent.ACTION_DELETE)
                intent.data = "package:$packageName".toUri()
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                startActivity(intent)
                exitProcess(0)
            }
        }

        // TODO: We can't totally protect superkey from be stolen by root or LSPosed-like injection tools in user space, the only way is don't use superkey,
        // TODO: 1. make me root by kernel
        // TODO: 2. remove all usage of superkey
        sharedPreferences = getSharedPreferences(SP_NAME, Context.MODE_PRIVATE)
        APatchKeyHelper.setSharedPreferences(sharedPreferences)
        superKey = APatchKeyHelper.readSPSuperKey()

        okhttpClient =
            OkHttpClient.Builder().cache(Cache(File(cacheDir, "okhttp"), 10 * 1024 * 1024))
                .addInterceptor { block ->
                    block.proceed(
                        block.request().newBuilder()
                            .header("User-Agent", "APatch/${BuildConfig.VERSION_CODE}")
                            .header("Accept-Language", Locale.getDefault().toLanguageTag()).build()
                    )
                }.build()
    }

    fun getBackupWarningState(): Boolean {
        return sharedPreferences.getBoolean(SHOW_BACKUP_WARN, true)
    }

    fun updateBackupWarningState(state: Boolean) {
        sharedPreferences.edit { putBoolean(SHOW_BACKUP_WARN, state) }
    }

    override fun uncaughtException(t: Thread, e: Throwable) {
        val exceptionMessage = Log.getStackTraceString(e)
        val threadName = t.name
        Log.e(TAG, "Error on thread $threadName:\n $exceptionMessage")
        val intent = Intent(this, CrashHandleActivity::class.java).apply {
            putExtra("exception_message", exceptionMessage)
            putExtra("thread", threadName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        exitProcess(10)
    }
}

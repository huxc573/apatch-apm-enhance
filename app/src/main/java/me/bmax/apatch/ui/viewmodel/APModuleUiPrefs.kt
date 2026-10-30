package me.bmax.apatch.ui.viewmodel

import android.content.SharedPreferences
import me.bmax.apatch.APApplication

/**
 * APModule 列表的本地 UI 偏好：置顶、自定义排序、排序模式、紧凑布局。
 * 全部存在 APatch 自己的 config SharedPreferences 里，卸载即失效，不写系统属性。
 */
enum class APModuleSortMode {
    DEFAULT,
    NAME,
    ENABLED_FIRST,
    CUSTOM;

    companion object {
        fun from(raw: String?): APModuleSortMode =
            entries.firstOrNull { it.name == raw } ?: DEFAULT
    }
}

object APModuleUiPrefs {
    private const val KEY_PINNED = "apm_ui_pinned_ids"
    private const val KEY_ORDER = "apm_ui_custom_order"
    private const val KEY_SORT = "apm_ui_sort_mode"
    private const val KEY_COMPACT = "apm_ui_compact"
    private const val ORDER_SEP = "\u0001"

    /** Application.onCreate 之前访问会抛 UninitializedPropertyAccessException，这里兜底成 null。 */
    private val prefs: SharedPreferences?
        get() = runCatching { APApplication.sharedPreferences }.getOrNull()

    fun pinnedIds(): Set<String> =
        prefs?.getStringSet(KEY_PINNED, emptySet())?.toSet() ?: emptySet()

    fun savePinned(ids: Set<String>) {
        prefs?.edit()?.putStringSet(KEY_PINNED, ids)?.apply()
    }

    fun customOrder(): List<String> =
        prefs?.getString(KEY_ORDER, null)
            ?.split(ORDER_SEP)
            ?.filter { it.isNotEmpty() }
            ?: emptyList()

    fun saveCustomOrder(ids: List<String>) {
        prefs?.edit()?.putString(KEY_ORDER, ids.joinToString(ORDER_SEP))?.apply()
    }

    fun sortMode(): APModuleSortMode =
        APModuleSortMode.from(prefs?.getString(KEY_SORT, null))

    fun saveSortMode(mode: APModuleSortMode) {
        prefs?.edit()?.putString(KEY_SORT, mode.name)?.apply()
    }

    fun compact(): Boolean = prefs?.getBoolean(KEY_COMPACT, false) ?: false

    fun saveCompact(compact: Boolean) {
        prefs?.edit()?.putBoolean(KEY_COMPACT, compact)?.apply()
    }
}

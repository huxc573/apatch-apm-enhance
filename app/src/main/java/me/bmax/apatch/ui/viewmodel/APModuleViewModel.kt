package me.bmax.apatch.ui.viewmodel

import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.bmax.apatch.apApp
import me.bmax.apatch.util.listModules
import org.json.JSONArray
import org.json.JSONObject
import java.text.Collator
import java.util.Locale

class APModuleViewModel : ViewModel() {
    companion object {
        private const val TAG = "ModuleViewModel"
        private var modules by mutableStateOf<List<ModuleInfo>>(emptyList())
    }

    class ModuleInfo(
        val id: String,
        val name: String,
        val author: String,
        val version: String,
        val versionCode: Int,
        val description: String,
        val enabled: Boolean,
        val update: Boolean,
        val remove: Boolean,
        val updateJson: String,
        val hasWebUi: Boolean,
        val hasActionScript: Boolean,
    )

    data class ModuleUpdateInfo(
        val version: String,
        val versionCode: Int,
        val zipUrl: String,
        val changelog: String,
    )

    var isRefreshing by mutableStateOf(false)
        private set

    var searchQuery by mutableStateOf("")
        private set

    private var pinnedIds by mutableStateOf(APModuleUiPrefs.pinnedIds())
    private var customOrder by mutableStateOf(APModuleUiPrefs.customOrder())

    /**
     * 排序模式与紧凑开关的持久化值只在构造时读一次。
     * 直接用 `mutableStateOf(APModuleUiPrefs.sortMode())` 会在每次 Activity 重建、
     * 每次 ViewModel 新建时都去碰 SharedPreferences，且以属性委托初始化，
     * 一旦 prefs 读失败仍会静默回退，难以排查；这里保持一次读取 + 显式状态。
     */
    var sortMode by mutableStateOf(APModuleUiPrefs.sortMode())
        private set

    /** 紧凑布局开关。 */
    var compact by mutableStateOf(APModuleUiPrefs.compact())
        private set

    val moduleList by derivedStateOf {
        val collator = Collator.getInstance(Locale.getDefault())
        val query = searchQuery.trim()

        val filtered = if (query.isEmpty()) {
            modules
        } else {
            modules.filter { m ->
                m.name.contains(query, ignoreCase = true) ||
                    m.id.contains(query, ignoreCase = true) ||
                    m.author.contains(query, ignoreCase = true) ||
                    m.description.contains(query, ignoreCase = true)
            }
        }

        val comparator: Comparator<ModuleInfo> = when (sortMode) {
            APModuleSortMode.DEFAULT -> compareBy(collator) { it.id }

            APModuleSortMode.NAME -> compareBy(collator) { it.name.ifEmpty { it.id } }

            APModuleSortMode.ENABLED_FIRST ->
                compareByDescending<ModuleInfo> { it.enabled }
                    .thenBy(collator) { it.name.ifEmpty { it.id } }

            APModuleSortMode.CUSTOM -> {
                val index = customOrder.withIndex().associate { (i, id) -> id to i }
                compareBy<ModuleInfo> { index[it.id] ?: Int.MAX_VALUE }
                    .thenBy(collator) { it.name.ifEmpty { it.id } }
            }
        }

        // sortedWith 是稳定排序，置顶的二次排序不会打乱上面选定的顺序
        //
        // 注意：这里绝对不能写任何 Compose 状态（isRefreshing 等）。
        // derivedStateOf 的 lambda 会在组合/测量阶段被求值，在其中写快照状态会
        // 触发“读取状态 → 使读取方失效 → 重组 → 再次求值”的自激循环，
        // 表现为整个界面持续重绘闪烁，直至 ANR。
        filtered
            .sortedWith(comparator)
            .sortedWith(compareByDescending<ModuleInfo> { it.id in pinnedIds })
    }

    var isNeedRefresh by mutableStateOf(false)
        private set

    fun markNeedRefresh() {
        isNeedRefresh = true
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
    }

    fun updateSortMode(mode: APModuleSortMode) {
        sortMode = mode
        APModuleUiPrefs.saveSortMode(mode)

        // 首次切到自定义排序时，以当前可见顺序为基线
        if (mode == APModuleSortMode.CUSTOM && customOrder.isEmpty()) {
            customOrder = moduleList.map { it.id }
            APModuleUiPrefs.saveCustomOrder(customOrder)
        }
    }

    fun updateCompact(enabled: Boolean) {
        compact = enabled
        APModuleUiPrefs.saveCompact(enabled)
    }

    fun isPinned(id: String): Boolean = id in pinnedIds

    fun togglePin(id: String) {
        pinnedIds = if (id in pinnedIds) pinnedIds - id else pinnedIds + id
        APModuleUiPrefs.savePinned(pinnedIds)
    }

    /** 批量置顶：编辑模式下勾选多个模块后一次性置顶。 */
    fun pinAll(ids: Set<String>) {
        if (ids.isEmpty()) return
        pinnedIds = pinnedIds + ids
        APModuleUiPrefs.savePinned(pinnedIds)
    }

    /** 批量取消置顶。 */
    fun unpinAll(ids: Set<String>) {
        if (ids.isEmpty()) return
        pinnedIds = pinnedIds - ids
        APModuleUiPrefs.savePinned(pinnedIds)
    }

    /**
     * 在当前可见顺序上把 [id] 上移/下移 [delta] 位。
     *
     * 注意：这里是"插入到目标位置"（remove + add），不是相邻交换。
     * 拖拽一次可能跨好几格，用交换实现需要连发多次、每次都触发重组，
     * 是之前向上拖动乱跳的根源；一次性插入则只有一次数据变更。
     */
    fun moveModule(id: String, delta: Int) {
        if (delta == 0) return
        val order = moduleList.map { it.id }.toMutableList()
        val from = order.indexOf(id)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (to == from) return
        applyOrder(order, from, to)
    }

    /** 把 [id] 直接插到 [targetIndex]（拖拽排序用），语义与 [moveModule] 一致。 */
    fun moveModuleTo(id: String, targetIndex: Int) {
        val order = moduleList.map { it.id }.toMutableList()
        val from = order.indexOf(id)
        if (from < 0) return
        val to = targetIndex.coerceIn(0, order.lastIndex)
        if (to == from) return
        applyOrder(order, from, to)
    }

    private fun applyOrder(order: MutableList<String>, from: Int, to: Int) {
        val item = order.removeAt(from)
        order.add(to, item)

        customOrder = order
        sortMode = APModuleSortMode.CUSTOM
        APModuleUiPrefs.saveCustomOrder(order)
        APModuleUiPrefs.saveSortMode(APModuleSortMode.CUSTOM)
    }

    /**
     * 按当前可见顺序重排：长按拖拽直接把整段可见顺序落成自定义顺序。
     * 与 [moveModule] 的区别是列表各段之间不残留历史位置，拖完之后下一次拖拽行为可预期。
     */
    fun updateCustomOrderFromVisible(visibleIds: List<String>) {
        if (visibleIds.isEmpty()) return
        customOrder = visibleIds
        if (sortMode != APModuleSortMode.CUSTOM) {
            sortMode = APModuleSortMode.CUSTOM
            APModuleUiPrefs.saveSortMode(APModuleSortMode.CUSTOM)
        }
        APModuleUiPrefs.saveCustomOrder(visibleIds)
    }

    fun resetCustomOrder() {
        customOrder = emptyList()
        APModuleUiPrefs.saveCustomOrder(emptyList())
    }

    fun clearPinned() {
        pinnedIds = emptySet()
        APModuleUiPrefs.savePinned(pinnedIds)
    }

    fun fetchModuleList() {
        viewModelScope.launch(Dispatchers.IO) {
            isRefreshing = true

            val oldModuleList = modules

            val start = SystemClock.elapsedRealtime()

            kotlin.runCatching {

                val result = listModules()

                Log.i(TAG, "result: $result")

                val array = JSONArray(result)
                modules = (0 until array.length())
                    .asSequence()
                    .map { array.getJSONObject(it) }
                    .map { obj ->
                        ModuleInfo(
                            obj.getString("id"),

                            obj.optString("name"),
                            obj.optString("author", "Unknown"),
                            obj.optString("version", "Unknown"),
                            obj.optInt("versionCode", 0),
                            obj.optString("description"),
                            obj.getBoolean("enabled"),
                            obj.getBoolean("update"),
                            obj.getBoolean("remove"),
                            obj.optString("updateJson"),
                            obj.optBoolean("web"),
                            obj.optBoolean("action")
                        )
                    }.toList()
                isNeedRefresh = false
            }.onFailure { e ->
                Log.e(TAG, "fetchModuleList: ", e)
                isRefreshing = false
            }

            // when both old and new is kotlin.collections.EmptyList
            // moduleList update will don't trigger
            if (oldModuleList === modules) {
                isRefreshing = false
            }

            Log.i(TAG, "load cost: ${SystemClock.elapsedRealtime() - start}, modules: $modules")
        }
    }

    private fun sanitizeVersionString(version: String): String {
        return version.replace(Regex("[^a-zA-Z0-9.\\-_]"), "_")
    }

    fun checkUpdate(m: ModuleInfo): Triple<String, String, String> {
        val empty = Triple("", "", "")
        if (m.updateJson.isEmpty() || m.remove || m.update || !m.enabled) {
            return empty
        }
        // download updateJson
        val result = kotlin.runCatching {
            val url = m.updateJson
            Log.i(TAG, "checkUpdate url: $url")
            val response = apApp.okhttpClient
                .newCall(
                    okhttp3.Request.Builder()
                        .url(url)
                        .build()
                ).execute()
            Log.d(TAG, "checkUpdate code: ${response.code}")
            if (response.isSuccessful) {
                response.body?.string() ?: ""
            } else {
                ""
            }
        }.getOrDefault("")
        Log.i(TAG, "checkUpdate result: $result")

        if (result.isEmpty()) {
            return empty
        }

        val updateJson = kotlin.runCatching {
            JSONObject(result)
        }.getOrNull() ?: return empty

        val version = sanitizeVersionString(updateJson.optString("version", ""))
        val versionCode = updateJson.optInt("versionCode", 0)
        val zipUrl = updateJson.optString("zipUrl", "")
        val changelog = updateJson.optString("changelog", "")
        if (versionCode <= m.versionCode || zipUrl.isEmpty()) {
            return empty
        }

        return Triple(zipUrl, version, changelog)
    }
}

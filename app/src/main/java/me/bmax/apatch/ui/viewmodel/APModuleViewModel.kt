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

    /** 搜索关键字，空串表示不过滤。 */
    var searchQuery by mutableStateOf("")
        private set

    /** 当前排序模式。 */
    var sortMode by mutableStateOf(APModuleUiPrefs.sortMode())
        private set

    /** 紧凑布局开关。 */
    var compact by mutableStateOf(APModuleUiPrefs.compact())
        private set

    private var pinnedIds by mutableStateOf(APModuleUiPrefs.pinnedIds())
    private var customOrder by mutableStateOf(APModuleUiPrefs.customOrder())

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
        filtered
            .sortedWith(comparator)
            .sortedWith(compareByDescending<ModuleInfo> { it.id in pinnedIds })
            .also { isRefreshing = false }
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

    /**
     * 在当前可见顺序上把 [id] 上移/下移 [delta] 位，并自动切到自定义排序。
     */
    fun moveModule(id: String, delta: Int) {
        val order = moduleList.map { it.id }.toMutableList()
        val from = order.indexOf(id)
        if (from < 0) return
        val to = from + delta
        if (to !in order.indices) return

        val tmp = order[from]
        order[from] = order[to]
        order[to] = tmp

        customOrder = order
        sortMode = APModuleSortMode.CUSTOM
        APModuleUiPrefs.saveCustomOrder(order)
        APModuleUiPrefs.saveSortMode(APModuleSortMode.CUSTOM)
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

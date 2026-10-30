package me.bmax.apatch.ui.screen

import android.app.Activity.RESULT_OK
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.net.toUri
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.ExecuteAPMActionScreenDestination
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.bmax.apatch.APApplication
import me.bmax.apatch.R
import me.bmax.apatch.apApp
import me.bmax.apatch.ui.WebUIActivity
import me.bmax.apatch.ui.component.ConfirmResult
import me.bmax.apatch.ui.component.ModuleRemoveButton
import me.bmax.apatch.ui.component.ModuleStateIndicator
import me.bmax.apatch.ui.component.ModuleUpdateButton
import me.bmax.apatch.ui.component.rememberConfirmDialog
import me.bmax.apatch.ui.component.rememberLoadingDialog
import me.bmax.apatch.ui.viewmodel.APModuleSortMode
import me.bmax.apatch.ui.viewmodel.APModuleViewModel
import me.bmax.apatch.util.DownloadListener
import me.bmax.apatch.util.download
import me.bmax.apatch.util.hasMagisk
import me.bmax.apatch.util.reboot
import me.bmax.apatch.util.toggleModule
import me.bmax.apatch.util.ui.LocalSnackbarHost
import me.bmax.apatch.util.uninstallModule

@Destination<RootGraph>
@Composable
fun APModuleScreen(navigator: DestinationsNavigator) {
    val snackBarHost = LocalSnackbarHost.current
    val context = LocalContext.current

    val state by APApplication.apStateLiveData.observeAsState(APApplication.State.UNKNOWN_STATE)
    if (state != APApplication.State.ANDROIDPATCH_INSTALLED && state != APApplication.State.ANDROIDPATCH_NEED_UPDATE) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row {
                Text(
                    text = stringResource(id = R.string.apm_not_installed),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
        return
    }

    val viewModel = viewModel<APModuleViewModel>()

    LaunchedEffect(Unit) {
        if (viewModel.moduleList.isEmpty() || viewModel.isNeedRefresh) {
            viewModel.fetchModuleList()
        }
    }

    var searchActive by rememberSaveable { mutableStateOf(false) }

    // 编辑模式：长按任意卡片进入，底部出现批量置顶操作栏，卡片可滑动排序
    var editMode by rememberSaveable { mutableStateOf(false) }
    // 编辑模式下被勾选的模块，用于批量置顶
    var checkedIds by remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()

    val webUILauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { viewModel.fetchModuleList() }
    //TODO: FIXME -> val isSafeMode = Natives.getSafeMode()
    val isSafeMode = false
    val hasMagisk = hasMagisk()
    val hideInstallButton = isSafeMode || hasMagisk

    val moduleListState = rememberLazyListState()

    Scaffold(
        topBar = {
            Column {
                TopBar(
                    searchActive = searchActive,
                    onToggleSearch = {
                        searchActive = !searchActive
                        if (!searchActive) viewModel.updateSearchQuery("")
                    },
                    sortMode = viewModel.sortMode,
                    onSortModeChange = { viewModel.updateSortMode(it) },
                    compact = viewModel.compact,
                    onToggleCompact = { viewModel.updateCompact(!viewModel.compact) },
                )
                if (searchActive) {
                    SearchField(
                        query = viewModel.searchQuery,
                        onQueryChange = viewModel::updateSearchQuery
                    )
                }
            }
        }, bottomBar = {
            if (editMode) {
                EditModeBar(
                    checkedCount = checkedIds.size,
                    onPinChecked = {
                        viewModel.pinAll(checkedIds)
                        checkedIds = emptySet()
                    },
                    onUnpinChecked = {
                        viewModel.unpinAll(checkedIds)
                        checkedIds = emptySet()
                    },
                    onSelectAll = {
                        checkedIds = viewModel.moduleList.map { it.id }.toSet()
                    },
                    onClearSelection = { checkedIds = emptySet() },
                    onDone = {
                        editMode = false
                        checkedIds = emptySet()
                    }
                )
            }
        }, floatingActionButton = if (hideInstallButton) {
            { /* Empty */ }
        } else {
            {
                val selectZipLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) {
                    if (it.resultCode != RESULT_OK) {
                        return@rememberLauncherForActivityResult
                    }
                    val data = it.data ?: return@rememberLauncherForActivityResult
                    val uri = data.data ?: return@rememberLauncherForActivityResult

                    Log.i("ModuleScreen", "select zip result: $uri")

                    navigator.navigate(InstallScreenDestination(uri, MODULE_TYPE.APM))

                    viewModel.markNeedRefresh()
                }

                FloatingActionButton(
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    containerColor = MaterialTheme.colorScheme.primary,
                    onClick = {
                        // select the zip file to install
                        val intent = Intent(Intent.ACTION_GET_CONTENT)
                        intent.type = "application/zip"
                        selectZipLauncher.launch(intent)
                    }) {
                    Icon(
                        painter = painterResource(id = R.drawable.package_import),
                        contentDescription = null
                    )
                }
            }
        }, snackbarHost = { SnackbarHost(snackBarHost) }) { innerPadding ->
        when {
            hasMagisk -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.apm_magisk_conflict),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            else -> {
                ModuleList(
                    navigator,
                    viewModel = viewModel,
                    modifier = Modifier
                        .padding(innerPadding)
                        .fillMaxSize(),
                    state = moduleListState,
                    editMode = editMode,
                    checkedIds = checkedIds,
                    onCheckedChange = { id, isChecked ->
                        checkedIds = if (isChecked) checkedIds + id else checkedIds - id
                    },
                    onEnterEditMode = { editMode = true },
                    onExitEditMode = {
                        editMode = false
                        checkedIds = emptySet()
                    },
                    onInstallModule = {
                        navigator.navigate(InstallScreenDestination(it, MODULE_TYPE.APM))
                    },
                    onClickModule = { id, name, hasWebUi ->
                        if (hasWebUi) {
                            webUILauncher.launch(
                                Intent(
                                    context, WebUIActivity::class.java
                                ).setData("apatch://webui/$id".toUri()).putExtra("id", id)
                                    .putExtra("name", name)
                            )
                        }
                    },
                    snackBarHost = snackBarHost,
                    context = context
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    searchActive: Boolean,
    onToggleSearch: () -> Unit,
    sortMode: APModuleSortMode,
    onSortModeChange: (APModuleSortMode) -> Unit,
    compact: Boolean,
    onToggleCompact: () -> Unit,
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    TopAppBar(title = { Text(stringResource(R.string.apm)) }, actions = {
        IconButton(onClick = onToggleSearch) {
            Icon(
                imageVector = if (searchActive) Icons.Filled.Close else Icons.Filled.Search,
                contentDescription = stringResource(R.string.apm_search)
            )
        }

        Box {
            IconButton(onClick = { sortMenuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.Sort,
                    contentDescription = stringResource(R.string.apm_sort)
                )
            }
            DropdownMenu(
                expanded = sortMenuExpanded,
                onDismissRequest = { sortMenuExpanded = false }
            ) {
                APModuleSortMode.entries.forEach { mode ->
                    DropdownMenuItem(text = {
                        Text(stringResource(mode.labelRes()))
                    }, leadingIcon = {
                        if (sortMode == mode) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }, onClick = {
                        onSortModeChange(mode)
                        sortMenuExpanded = false
                    })
                }
            }
        }

        IconButton(onClick = onToggleCompact) {
            Icon(
                imageVector = Icons.Filled.ViewAgenda,
                contentDescription = stringResource(R.string.apm_compact),
                tint = if (compact) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    })
}

private fun APModuleSortMode.labelRes(): Int = when (this) {
    APModuleSortMode.DEFAULT -> R.string.apm_sort_default
    APModuleSortMode.NAME -> R.string.apm_sort_name
    APModuleSortMode.ENABLED_FIRST -> R.string.apm_sort_enabled
    APModuleSortMode.CUSTOM -> R.string.apm_sort_custom
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        placeholder = { Text(stringResource(R.string.apm_search_hint)) },
        leadingIcon = {
            Icon(imageVector = Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Filled.Clear, contentDescription = null)
                }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp)
    )
}

/** 卡片尺寸档位：标准模式与紧凑模式两套值。 */
private class ApmCardDimens(val compact: Boolean) {
    /** 卡片内边距 */
    val pad: Dp = if (compact) 8.dp else 16.dp

    /** 标题区与操作行之间的分隔线留白 */
    val dividerTop: Dp = if (compact) 2.dp else 8.dp

    /** 操作行垂直留白 */
    val actionVertical: Dp = if (compact) 0.dp else 8.dp

    /** 相邻卡片间距 */
    val itemSpacing: Dp = if (compact) 6.dp else 16.dp

    /** 卡片圆角 */
    val corner: Dp = if (compact) 10.dp else 20.dp

    /** 操作按钮高度与内容留白 */
    val buttonHeight: Dp = if (compact) 28.dp else 40.dp
    val buttonPadding: Dp = if (compact) 8.dp else 12.dp
    val buttonIcon: Dp = if (compact) 15.dp else 20.dp
    val buttonGap: Dp = if (compact) 4.dp else 6.dp
    val buttonBetween: Dp = if (compact) 6.dp else 12.dp

    /** 行内图标按钮与开关 */
    val iconButton: Dp = if (compact) 28.dp else 36.dp
    val iconSize: Dp = if (compact) 16.dp else 19.dp

    /** 拖拽中占位的抓手尺寸 */
    val handleSize: Dp = if (compact) 20.dp else 24.dp
}

/**
 * 编辑模式的底部操作栏：批量置顶 / 取消置顶 / 全选 / 完成。
 * 排序在每张卡片上用 ↑ ↓ 按钮完成（稳定可预期），拖把手是额外便利。
 */
@Composable
private fun EditModeBar(
    checkedCount: Int,
    onPinChecked: () -> Unit,
    onUnpinChecked: () -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDone: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 3.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.apm_selected_count, checkedCount),
                    style = MaterialTheme.typography.bodyMedium
                )
                TextButton(onClick = if (checkedCount == 0) onSelectAll else onClearSelection) {
                    Text(
                        stringResource(
                            if (checkedCount == 0) R.string.apm_select_all
                            else R.string.apm_clear_selection
                        )
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onPinChecked,
                    enabled = checkedCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.apm_pin_selected))
                }
                OutlinedButton(
                    onClick = onUnpinChecked,
                    enabled = checkedCount > 0,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.apm_unpin_selected))
                }
                TextButton(onClick = onDone) {
                    Text(stringResource(R.string.apm_edit_done))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModuleList(
    navigator: DestinationsNavigator,
    viewModel: APModuleViewModel,
    modifier: Modifier = Modifier,
    state: LazyListState,
    editMode: Boolean = false,
    checkedIds: Set<String> = emptySet(),
    onCheckedChange: (String, Boolean) -> Unit = { _, _ -> },
    onEnterEditMode: () -> Unit = {},
    onExitEditMode: () -> Unit = {},
    onInstallModule: (Uri) -> Unit,
    onClickModule: (id: String, name: String, hasWebUi: Boolean) -> Unit,
    snackBarHost: SnackbarHostState,
    context: Context
) {
    val failedEnable = stringResource(R.string.apm_failed_to_enable)
    val failedDisable = stringResource(R.string.apm_failed_to_disable)
    val failedUninstall = stringResource(R.string.apm_uninstall_failed)
    val successUninstall = stringResource(R.string.apm_uninstall_success)
    val reboot = stringResource(id = R.string.reboot)
    val rebootToApply = stringResource(id = R.string.apm_reboot_to_apply)
    val moduleStr = stringResource(id = R.string.apm)
    val uninstall = stringResource(id = R.string.apm_remove)
    val cancel = stringResource(id = android.R.string.cancel)
    val moduleUninstallConfirm = stringResource(id = R.string.apm_uninstall_confirm)
    val updateText = stringResource(R.string.apm_update)
    val changelogText = stringResource(R.string.apm_changelog)
    val downloadingText = stringResource(R.string.apm_downloading)
    val startDownloadingText = stringResource(R.string.apm_start_downloading)

    val loadingDialog = rememberLoadingDialog()
    val confirmDialog = rememberConfirmDialog()

    suspend fun onModuleUpdate(
        module: APModuleViewModel.ModuleInfo,
        changelogUrl: String,
        downloadUrl: String,
        fileName: String
    ) {
        val changelog = loadingDialog.withLoading {
            withContext(Dispatchers.IO) {
                if (Patterns.WEB_URL.matcher(changelogUrl).matches()) {
                    apApp.okhttpClient.newCall(
                        okhttp3.Request.Builder().url(changelogUrl).build()
                    ).execute().body!!.string()
                } else {
                    changelogUrl
                }
            }
        }


        if (changelog.isNotEmpty()) {
            // changelog is not empty, show it and wait for confirm
            val confirmResult = confirmDialog.awaitConfirm(
                changelogText,
                content = changelog,
                markdown = true,
                confirm = updateText,
            )

            if (confirmResult != ConfirmResult.Confirmed) {
                return
            }
        }

        withContext(Dispatchers.Main) {
            Toast.makeText(
                context, startDownloadingText.format(module.name), Toast.LENGTH_SHORT
            ).show()
        }

        val downloading = downloadingText.format(module.name)
        withContext(Dispatchers.IO) {
            download(
                context,
                downloadUrl,
                fileName,
                downloading,
                onDownloaded = onInstallModule,
                onDownloading = {
                    launch(Dispatchers.Main) {
                        Toast.makeText(context, downloading, Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }

    suspend fun onModuleUninstall(module: APModuleViewModel.ModuleInfo) {
        val confirmResult = confirmDialog.awaitConfirm(
            moduleStr,
            content = moduleUninstallConfirm.format(module.name),
            confirm = uninstall,
            dismiss = cancel
        )
        if (confirmResult != ConfirmResult.Confirmed) {
            return
        }

        val success = loadingDialog.withLoading {
            withContext(Dispatchers.IO) {
                uninstallModule(module.id)
            }
        }

        if (success) {
            viewModel.fetchModuleList()
        }
        val message = if (success) {
            successUninstall.format(module.name)
        } else {
            failedUninstall.format(module.name)
        }
        val actionLabel = if (success) {
            reboot
        } else {
            null
        }
        val result = snackBarHost.showSnackbar(
            message = message, actionLabel = actionLabel, duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) {
            reboot()
        }
    }

    val compact = viewModel.compact
    val searchQuery = viewModel.searchQuery
    val dimens = remember(compact) { ApmCardDimens(compact) }

    // ---- 拖拽排序状态 ----
    // 只保存"手指累计位移"。卡片实际画在：当前槽位 + 位移，位移由列表真实布局得出，
    // 不在代码里自己累加槽位，避免和 LazyColumn 的布局打架。
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    val haptics = LocalHapticFeedback.current

    fun startDrag(id: String) {
        draggingId = id
        dragDelta = 0f
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun dragBy(deltaY: Float) {
        val id = draggingId ?: return
        dragDelta += deltaY

        val info = state.layoutInfo
        val dragged = info.visibleItemsInfo.firstOrNull { it.key == id } ?: return

        // 卡片当前视觉顶端 = 它的真实槽位 + 手指位移
        val visualTop = dragged.offset + dragDelta
        val visualCenter = visualTop + dragged.size / 2f

        val list = viewModel.moduleList
        val from = list.indexOfFirst { it.id == id }
        if (from < 0) return

        // 收集"其它可见卡片"：带上它们在列表里的绝对索引
        val others = info.visibleItemsInfo
            .filter { it.key != id }
            .mapNotNull { vi ->
                val li = list.indexOfFirst { it.id == vi.key }
                if (li >= 0) Triple(li, vi.offset, vi.size) else null
            }
            .sortedBy { it.second }
        if (others.isEmpty()) return

        // 目标位置：中心点落在哪张其它卡片身上，就插到它的位置。
        // 中心点在所有其它卡片之上 -> 插到最前面那张之前；在之下 -> 插到最后那张之后。
        val hit = others.firstOrNull { visualCenter >= it.second && visualCenter <= it.second + it.third }
        val target = when {
            hit != null -> hit.first
            visualCenter < others.first().second -> others.first().first
            else -> others.last().first
        }
        if (target == from) return

        // 位移折抵：卡片在新位置的槽位与旧位置之差，抵掉后它仍停在手指下
        val targetSlot = others.firstOrNull { it.first == target }?.second
        if (targetSlot != null) dragDelta += dragged.offset - targetSlot

        viewModel.moveModuleTo(id, target)
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun endDrag() {
        draggingId = null
        dragDelta = 0f
    }

    PullToRefreshBox(
        modifier = modifier,
        onRefresh = { viewModel.fetchModuleList() },
        isRefreshing = viewModel.isRefreshing
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = state,
            verticalArrangement = Arrangement.spacedBy(dimens.itemSpacing),
            contentPadding = remember(dimens.itemSpacing) {
                PaddingValues(
                    start = if (compact) 8.dp else 16.dp,
                    top = if (compact) 6.dp else 16.dp,
                    end = if (compact) 8.dp else 16.dp,
                    bottom = 16.dp + 16.dp + 56.dp /*  Scaffold Fab Spacing + Fab container height */
                )
            },
        ) {
            when {
                viewModel.moduleList.isEmpty() -> {
                    item {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                stringResource(
                                    if (searchQuery.isNotEmpty()) R.string.apm_search_empty
                                    else R.string.apm_empty
                                ), textAlign = TextAlign.Center
                            )
                        }
                    }
                }

                else -> {
                    val visible = viewModel.moduleList
                    items(visible, key = { it.id }) { module ->
                        var isChecked by rememberSaveable(module) { mutableStateOf(module.enabled) }
                        val scope = rememberCoroutineScope()
                        val updatedModule by produceState(initialValue = Triple("", "", "")) {
                            scope.launch(Dispatchers.IO) {
                                value = viewModel.checkUpdate(module)
                            }
                        }
                        val isDragging = draggingId == module.id

                        ModuleItem(
                            navigator,
                            module,
                            isChecked,
                            updatedModule.first,
                            pinned = viewModel.isPinned(module.id),
                            dimens = dimens,
                            isDragging = isDragging,
                            isEditing = editMode,
                            editChecked = module.id in checkedIds,
                            onEditCheckedChange = { onCheckedChange(module.id, it) },
                            onTogglePin = { viewModel.togglePin(module.id) },
                            onMoveUp = { viewModel.moveModule(module.id, -1) },
                            onMoveDown = { viewModel.moveModule(module.id, 1) },
                            onEnterEditMode = onEnterEditMode,
                            onDragStart = { startDrag(module.id) },
                            onDrag = { dragBy(it) },
                            onDragEnd = { endDrag() },
                            onUninstall = {
                                scope.launch { onModuleUninstall(module) }
                            },
                            onCheckChanged = {
                                scope.launch {
                                    val success = loadingDialog.withLoading {
                                        withContext(Dispatchers.IO) {
                                            toggleModule(module.id, !isChecked)
                                        }
                                    }
                                    if (success) {
                                        isChecked = it
                                        viewModel.fetchModuleList()

                                        val result = snackBarHost.showSnackbar(
                                            message = rebootToApply,
                                            actionLabel = reboot,
                                            duration = SnackbarDuration.Long
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            reboot()
                                        }
                                    } else {
                                        val message = if (isChecked) failedDisable else failedEnable
                                        snackBarHost.showSnackbar(message.format(module.name))
                                    }
                                }
                            },
                            onUpdate = {
                                scope.launch {
                                    onModuleUpdate(
                                        module,
                                        updatedModule.third,
                                        updatedModule.first,
                                        "${module.name}-${updatedModule.second}.zip"
                                    )
                                }
                            },
                            onClick = {
                                // 编辑模式下点击卡片＝勾选，不再打开模块
                                if (editMode) {
                                    onCheckedChange(module.id, module.id !in checkedIds)
                                } else {
                                    onClickModule(it.id, it.name, it.hasWebUi)
                                }
                            },
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    if (isDragging) {
                                        translationY = dragDelta
                                        scaleX = 1.03f
                                        scaleY = 1.03f
                                        alpha = 0.97f
                                        shadowElevation = 16f
                                    }
                                })

                        // fix last item shadow incomplete in LazyColumn
                        Spacer(Modifier.height(1.dp))
                    }
                }
            }
        }

        DownloadListener(context, onInstallModule)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModuleItem(
    navigator: DestinationsNavigator,
    module: APModuleViewModel.ModuleInfo,
    isChecked: Boolean,
    updateUrl: String,
    pinned: Boolean,
    dimens: ApmCardDimens,
    isDragging: Boolean,
    isEditing: Boolean = false,
    editChecked: Boolean = false,
    onEditCheckedChange: (Boolean) -> Unit = {},
    onTogglePin: () -> Unit,
    onMoveUp: () -> Unit = {},
    onMoveDown: () -> Unit = {},
    onEnterEditMode: () -> Unit = {},
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onUninstall: (APModuleViewModel.ModuleInfo) -> Unit,
    onCheckChanged: (Boolean) -> Unit,
    onUpdate: (APModuleViewModel.ModuleInfo) -> Unit,
    onClick: (APModuleViewModel.ModuleInfo) -> Unit,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
) {
    val decoration = if (!module.remove) TextDecoration.None else TextDecoration.LineThrough
    val haptics = LocalHapticFeedback.current
    val moduleAuthor = stringResource(id = R.string.apm_author)
    val viewModel = viewModel<APModuleViewModel>()
    val compact = dimens.compact

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (compact) 0.dp else 1.dp,
        shape = RoundedCornerShape(dimens.corner)
    ) {

        Box {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isEditing) {
                            // 编辑模式：卡片内部处理点击(勾选)与长按拖动；
                            // 右侧把手另有自己的无长按拖拽手势（见下方图标）。
                            Modifier
                                .pointerInput(module.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { onDragStart() },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            onDrag(amount.y)
                                        },
                                        onDragEnd = { onDragEnd() },
                                        onDragCancel = { onDragEnd() }
                                    )
                                }
                                .clickable {
                                    // 编辑模式下点卡片＝勾选，不再打开模块
                                    onClick(module)
                                }
                        } else {
                            Modifier.combinedClickable(
                                onClick = { onClick(module) },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onEnterEditMode()
                                }
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(
                            start = dimens.pad,
                            end = if (compact) 2.dp else 6.dp,
                            top = dimens.pad,
                            bottom = if (compact) 2.dp else 6.dp
                        ),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 编辑模式：左侧勾选框，用于批量置顶
                        if (isEditing) {
                            Checkbox(
                                checked = editChecked,
                                onCheckedChange = onEditCheckedChange,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .size(dimens.iconButton)
                            )
                        }
                        Column(
                            modifier = Modifier
                                .alpha(alpha = alpha)
                                .weight(1f),
                            verticalArrangement = Arrangement.spacedBy(
                                if (compact) 0.dp else 2.dp
                            )
                        ) {
                            Text(
                                text = module.name,
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold
                                ),
                                maxLines = 2,
                                textDecoration = decoration,
                                overflow = TextOverflow.Ellipsis
                            )

                            Text(
                                text = "${module.version}, $moduleAuthor ${module.author}",
                                style = MaterialTheme.typography.bodySmall,
                                textDecoration = decoration,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // 图钉只在编辑模式出现，平时不占位也不干扰点击
                        if (isEditing) {
                            IconButton(
                                onClick = onTogglePin,
                                modifier = Modifier.size(dimens.iconButton)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PushPin,
                                    contentDescription = stringResource(
                                        if (pinned) R.string.apm_unpin else R.string.apm_pin
                                    ),
                                    modifier = Modifier.size(dimens.iconSize),
                                    tint = if (pinned) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                )
                            }
                        }

                        if (isEditing) {
                            // 编辑模式右侧：上移 / 下移 / 拖动把手。
                            // 上移下移每次只动一格，状态简单、点一下必然生效；
                            // 把手是连续拖动的额外便利。
                            Row(
                                modifier = Modifier.padding(end = if (compact) 2.dp else 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    MiniArrowButton(
                                        up = true,
                                        size = dimens.iconButton,
                                        iconSize = dimens.iconSize,
                                        onClick = onMoveUp
                                    )
                                    MiniArrowButton(
                                        up = false,
                                        size = dimens.iconButton,
                                        iconSize = dimens.iconSize,
                                        onClick = onMoveDown
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(dimens.iconButton)
                                        .clip(RoundedCornerShape(percent = 50))
                                        .pointerInput(module.id) {
                                            // 把手：按住即可拖，无需长按
                                            detectDragGestures(
                                                onDragStart = { onDragStart() },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    onDrag(amount.y)
                                                },
                                                onDragEnd = { onDragEnd() },
                                                onDragCancel = { onDragEnd() }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DragHandle,
                                        contentDescription = stringResource(R.string.apm_drag_handle),
                                        modifier = Modifier.size(dimens.handleSize),
                                        tint = if (isDragging) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                }
                            }
                        } else {
                            if (compact) {
                                SmallSwitch(
                                    enabled = !module.update,
                                    checked = isChecked,
                                    onCheckedChange = onCheckChanged,
                                    modifier = Modifier.padding(end = 2.dp)
                                )
                            } else {
                                Switch(
                                    enabled = !module.update,
                                    checked = isChecked,
                                    onCheckedChange = onCheckChanged,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                            }
                        }
                    }

                    if (module.description.isNotEmpty()) {
                        Text(
                            modifier = Modifier
                                .alpha(alpha = alpha)
                                .padding(horizontal = dimens.pad),
                            text = module.description,
                            // 紧凑模式只压几何尺寸，字号与标准模式保持一致
                            style = MaterialTheme.typography.bodySmall,
                            textDecoration = decoration,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }

                    HorizontalDivider(
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(top = dimens.dividerTop)
                    )

                    Row(
                        modifier = Modifier
                            .padding(horizontal = dimens.pad, vertical = dimens.actionVertical)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Spacer(modifier = Modifier.weight(1f))

                        if (updateUrl.isNotEmpty()) {
                            ModuleUpdateButton(
                                onClick = { onUpdate(module) }, compact = compact
                            )

                            Spacer(modifier = Modifier.width(dimens.buttonBetween))
                        }

                        if (module.hasWebUi) {
                            FilledTonalButton(
                                onClick = { onClick(module) },
                                enabled = true,
                                modifier = Modifier.height(dimens.buttonHeight),
                                contentPadding = PaddingValues(horizontal = dimens.buttonPadding)
                            ) {
                                Icon(
                                    modifier = Modifier.size(dimens.buttonIcon),
                                    painter = painterResource(id = R.drawable.settings),
                                    contentDescription = null
                                )

                                Spacer(modifier = Modifier.width(dimens.buttonGap))
                                Text(
                                    text = stringResource(id = R.string.apm_webui_open),
                                    style = if (compact) MaterialTheme.typography.labelMedium
                                    else MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Visible,
                                    softWrap = false
                                )
                            }

                            Spacer(modifier = Modifier.width(dimens.buttonBetween))
                        }

                        if (module.hasActionScript) {
                            FilledTonalButton(
                                onClick = {
                                    navigator.navigate(
                                        ExecuteAPMActionScreenDestination(module.id)
                                    )
                                    viewModel.markNeedRefresh()
                                },
                                enabled = true,
                                modifier = Modifier.height(dimens.buttonHeight),
                                contentPadding = PaddingValues(horizontal = dimens.buttonPadding)
                            ) {
                                Icon(
                                    modifier = Modifier.size(dimens.buttonIcon),
                                    painter = painterResource(id = R.drawable.settings),
                                    contentDescription = null
                                )

                                Spacer(modifier = Modifier.width(dimens.buttonGap))
                                Text(
                                    text = stringResource(id = R.string.apm_action),
                                    style = if (compact) MaterialTheme.typography.labelMedium
                                    else MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Visible,
                                    softWrap = false
                                )
                            }

                            Spacer(modifier = Modifier.width(dimens.buttonBetween))
                        }

                        ModuleRemoveButton(
                            enabled = !module.remove,
                            onClick = { onUninstall(module) },
                            compact = compact
                        )
                    }
                }

                if (module.remove) {
                    ModuleStateIndicator(R.drawable.trash)
                }
                if (module.update) {
                    ModuleStateIndicator(R.drawable.device_mobile_down)
                }
            }
        }
    }
}

/**
 * 编辑模式下的极小上/下移按钮：只画一个箭头，点一下动一格。
 */
@Composable
private fun MiniArrowButton(
    up: Boolean,
    size: Dp,
    iconSize: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(6.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (up) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
            contentDescription = stringResource(
                if (up) R.string.apm_move_up else R.string.apm_move_down
            ),
            modifier = Modifier.size(iconSize),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 紧凑模式专用的瘦身开关：Material3 的 Switch 最小宽度约 52dp，
 * 这里按 36x20dp 自己画轨道与滑块，只为在密集列表里省高度。
 */
@Composable
private fun SmallSwitch(
    enabled: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackWidth = 36.dp
    val trackHeight = 20.dp
    val thumbSize = 14.dp

    val colors = MaterialTheme.colorScheme
    val trackColor = when {
        !enabled -> colors.surfaceVariant.copy(alpha = 0.4f)
        checked -> colors.primary
        else -> colors.surfaceVariant
    }
    val thumbColor = if (checked) colors.onPrimary else colors.outline
    val position by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - 3.dp else 3.dp,
        label = "smallSwitchThumb"
    )

    val click = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) }

    Box(
        modifier = modifier
            .then(click)
            .size(trackWidth, trackHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(trackColor)
            .padding(3.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .offset(x = position)
                .size(thumbSize)
                .clip(RoundedCornerShape(percent = 50))
                .background(thumbColor)
        )
    }
}

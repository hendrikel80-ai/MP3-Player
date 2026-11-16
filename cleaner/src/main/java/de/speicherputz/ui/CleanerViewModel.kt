package de.speicherputz.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.speicherputz.scan.Category
import de.speicherputz.scan.DeleteReport
import de.speicherputz.scan.Deleter
import de.speicherputz.scan.FoundItem
import de.speicherputz.scan.Rules
import de.speicherputz.scan.ScanResult
import de.speicherputz.scan.Scanner
import de.speicherputz.system.Device
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ScanState {
    data object Idle : ScanState
    data class Scanning(val filesScanned: Int, val currentDir: String) : ScanState
    data class Done(val result: ScanResult) : ScanState
    data class Failed(val message: String) : ScanState
}

data class UiState(
    val hasStorageAccess: Boolean = false,
    val storage: Device.StorageOverview? = null,
    val scan: ScanState = ScanState.Idle,
    val selected: Set<String> = emptySet(),
    val deleting: Boolean = false,
    val lastReport: DeleteReport? = null,
    val hasUsageAccess: Boolean = false,
    val appCaches: List<Device.AppCache>? = null,
    val appCachesLoading: Boolean = false,
) {
    val result: ScanResult? get() = (scan as? ScanState.Done)?.result

    fun selectedItems(): List<FoundItem> {
        val all = result?.uniqueItems() ?: return emptyList()
        return selected.mapNotNull { all[it] }
    }

    fun selectedBytes(): Long = selectedItems().sumOf { it.size }

    fun selectedIn(category: Category): Int = result?.of(category)?.count { it.path in selected } ?: 0
}

class CleanerViewModel(app: Application) : AndroidViewModel(app) {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private var scanJob: Job? = null

    init {
        refreshStatus()
    }

    /** Nach Rückkehr aus den Einstellungen: Berechtigungen und Speicherstand neu lesen. */
    fun refreshStatus() {
        val context = getApplication<Application>()
        viewModelScope.launch {
            val storage = withContext(Dispatchers.IO) { Device.storageOverview(context) }
            _state.update {
                it.copy(
                    hasStorageAccess = Device.hasStorageAccess(context),
                    hasUsageAccess = Device.hasUsageAccess(context),
                    storage = storage,
                )
            }
        }
    }

    fun startScan() {
        if (scanJob?.isActive == true) return
        val context = getApplication<Application>()
        Device.clearOwnCache(context)
        _state.update { it.copy(scan = ScanState.Scanning(0, ""), selected = emptySet(), lastReport = null) }
        scanJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    Scanner(
                        root = Device.storageRoot,
                        apkNote = { Device.apkNote(context, it) },
                        onProgress = { count, dir -> _state.update { s -> s.copy(scan = ScanState.Scanning(count, dir)) } },
                    ).scan()
                }
                _state.update { it.copy(scan = ScanState.Done(result)) }
            } catch (e: CancellationException) {
                _state.update { it.copy(scan = ScanState.Idle) }
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(scan = ScanState.Failed(e.message ?: e.javaClass.simpleName)) }
            }
        }
    }

    fun cancelScan() {
        scanJob?.cancel()
        _state.update { it.copy(scan = ScanState.Idle) }
    }

    fun toggle(path: String) = _state.update {
        it.copy(selected = if (path in it.selected) it.selected - path else it.selected + path)
    }

    fun selectAll(category: Category) = _state.update { s ->
        val items = s.result?.of(category).orEmpty()
        // Bei Duplikaten bleibt das Original immer abgewählt
        val paths = items.filter { !(category == Category.DUPLICATES && it.isOriginal) }.map { it.path }
        s.copy(selected = s.selected + paths)
    }

    fun selectNone(category: Category) = _state.update { s ->
        s.copy(selected = s.selected - s.result?.of(category).orEmpty().map { it.path }.toSet())
    }

    /** Duplikat-Gruppen, in denen gerade alle Kopien ausgewählt sind. */
    fun fullySelectedDuplicateGroups(s: UiState = _state.value): Set<Int> =
        Rules.fullySelectedGroups(s.result?.of(Category.DUPLICATES).orEmpty(), s.selected)

    fun deleteSelected() {
        val s = _state.value
        val items = s.selectedItems()
        if (items.isEmpty() || s.deleting || fullySelectedDuplicateGroups(s).isNotEmpty()) return
        val context = getApplication<Application>()
        _state.update { it.copy(deleting = true) }
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) {
                Deleter(Device.storageRoot).delete(items).also { Device.notifyMediaStore(context, it.deleted) }
            }
            val storage = withContext(Dispatchers.IO) { Device.storageOverview(context) }
            _state.update { st ->
                val deleted = report.deleted.toSet()
                val newResult = st.result?.let { r -> r.copy(items = removeDeleted(r.items, deleted)) }
                st.copy(
                    deleting = false,
                    lastReport = report,
                    selected = st.selected - deleted,
                    scan = newResult?.let { ScanState.Done(it) } ?: st.scan,
                    storage = storage,
                )
            }
        }
    }

    private fun removeDeleted(items: Map<Category, List<FoundItem>>, deleted: Set<String>): Map<Category, List<FoundItem>> =
        items.mapValues { (category, list) ->
            val remaining = list.filter { it.path !in deleted && deleted.none { d -> it.path.startsWith("$d/") } }
            if (category == Category.DUPLICATES) {
                // Gruppen mit nur noch einer Datei sind keine Duplikate mehr
                remaining.groupBy { it.groupId }.filterValues { it.size > 1 }.values.flatten()
            } else {
                remaining
            }
        }.filterValues { it.isNotEmpty() }

    fun dismissReport() = _state.update { it.copy(lastReport = null) }

    fun loadAppCaches() {
        val context = getApplication<Application>()
        val hasAccess = Device.hasUsageAccess(context)
        _state.update { it.copy(hasUsageAccess = hasAccess) }
        if (!hasAccess || _state.value.appCachesLoading) return
        _state.update { it.copy(appCachesLoading = true) }
        viewModelScope.launch {
            val caches = withContext(Dispatchers.IO) {
                try {
                    Device.appCaches(context)
                } catch (_: Exception) {
                    emptyList()
                }
            }
            _state.update { it.copy(appCaches = caches, appCachesLoading = false) }
        }
    }
}

package com.grimoire.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grimoire.android.data.GrimoireDatabase
import com.grimoire.android.data.GrimoireSearchRepository
import com.grimoire.android.data.SearchResponse
import com.grimoire.android.data.SourceKind
import com.grimoire.android.data.AppSettings
import com.grimoire.android.data.LineSpacing
import com.grimoire.android.data.ReadingSize
import com.grimoire.android.data.SettingsStore
import com.grimoire.android.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DatabaseState {
    CHECKING,
    NEEDS_DOWNLOAD,
    DOWNLOADING,
    READY,
    FAILED,
}

/** Trigger flows are separate from result flows so state updates don't re-trigger search. */
data class SearchUiState(
    val query: String = "",
    val source: SourceKind = SourceKind.ALL,
    val response: SearchResponse = SearchResponse(),
    val loading: Boolean = false,
)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class AppViewModel(application: Application) : AndroidViewModel(application) {

    private val app get() = getApplication<Application>()

    private val _dbState = MutableStateFlow(DatabaseState.CHECKING)
    val dbState: StateFlow<DatabaseState> = _dbState.asStateFlow()

    private val _downloadState = MutableStateFlow<GrimoireDatabase.DlState>(GrimoireDatabase.DlState.Idle)
    val downloadState: StateFlow<GrimoireDatabase.DlState> = _downloadState.asStateFlow()

    private val _query = MutableStateFlow("")
    private val _source = MutableStateFlow(SourceKind.ALL)
    private val _response = MutableStateFlow(SearchResponse())
    private val _loading = MutableStateFlow(false)

    val search: StateFlow<SearchUiState> = combine(_query, _source, _response, _loading) { q, s, r, loading ->
        SearchUiState(q, s, r, loading)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    /** Persisted app settings (theme, reading preferences). */
    val settings: StateFlow<AppSettings> =
        SettingsStore.settingsFlow(app)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun onThemeModeChange(mode: ThemeMode) =
        viewModelScope.launch { SettingsStore.setThemeMode(app, mode) }

    fun onDynamicColorChange(enabled: Boolean) =
        viewModelScope.launch { SettingsStore.setDynamicColor(app, enabled) }

    fun onReadingSizeChange(size: ReadingSize) =
        viewModelScope.launch { SettingsStore.setReadingSize(app, size) }

    fun onLineSpacingChange(spacing: LineSpacing) =
        viewModelScope.launch { SettingsStore.setLineSpacing(app, spacing) }

    private var downloadJob: Job? = null

    init {
        viewModelScope.launch {
            val ready = withContext(Dispatchers.IO) { GrimoireDatabase.isReady(app) }
            _dbState.value = if (ready) DatabaseState.READY else DatabaseState.NEEDS_DOWNLOAD
        }
        viewModelScope.launch {
            combine(_query, _source) { q, s -> q.trim() to s }
                .debounce(180)
                .distinctUntilChanged()
                .collectLatest { (q, s) ->
                    if (q.isEmpty()) {
                        _response.value = SearchResponse()
                        _loading.value = false
                        return@collectLatest
                    }
                    _loading.value = true
                    _response.value = GrimoireSearchRepository.search(q, s)
                    _loading.value = false
                }
        }
    }

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun onSourceChange(source: SourceKind) {
        _source.value = source
    }

    fun startDownload() {
        if (downloadJob?.isActive == true) return
        downloadJob = viewModelScope.launch {
            _dbState.value = DatabaseState.DOWNLOADING
            GrimoireDatabase.downloadAndVerify(app) { state ->
                _downloadState.value = state
                _dbState.value = when (state) {
                    is GrimoireDatabase.DlState.Ready -> DatabaseState.READY
                    is GrimoireDatabase.DlState.Failed -> DatabaseState.FAILED
                    else -> DatabaseState.DOWNLOADING
                }
            }
        }
    }

    fun deleteDatabaseAndRedownload() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { GrimoireDatabase.delete(app) }
            _dbState.value = DatabaseState.NEEDS_DOWNLOAD
            _downloadState.value = GrimoireDatabase.DlState.Idle
        }
    }
}

package com.grimoire.android.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.grimoire.android.data.AppSettings
import com.grimoire.android.data.FontFamily
import com.grimoire.android.data.GrimoireDatabase
import com.grimoire.android.data.GrimoireSearchRepository
import com.grimoire.android.data.LineSpacing
import com.grimoire.android.data.ReadingSize
import com.grimoire.android.data.ReadingTheme
import com.grimoire.android.data.SearchResponse
import com.grimoire.android.data.SettingsStore
import com.grimoire.android.data.SourceKind
import com.grimoire.android.data.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
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
import org.json.JSONArray
import org.json.JSONObject

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

    /** Bookmarked article keys: "source:slug". */
    private val _bookmarks = MutableStateFlow(setOf<String>())
    val bookmarks: StateFlow<Set<String>> = _bookmarks.asStateFlow()

    /** Reading progress: "source:slug" -> scroll position (0.0-1.0). */
    private val _readingProgress = MutableStateFlow(mapOf<String, Float>())
    val readingProgress: StateFlow<Map<String, Float>> = _readingProgress.asStateFlow()

    /** Recent search queries, most recent first. */
    private val _recentSearches = MutableStateFlow(emptyList<String>())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    init {
        viewModelScope.launch {
            _bookmarks.value = SettingsStore.loadBookmarks(app)
        }
        viewModelScope.launch {
            _readingProgress.value = SettingsStore.loadReadingProgress(app)
        }
        viewModelScope.launch {
            _recentSearches.value = SettingsStore.loadRecentSearches(app)
        }
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
                    // Add to recent searches
                    if (q.isNotEmpty()) {
                        addRecentSearch(q)
                    }
                }
        }
    }

    fun onThemeModeChange(mode: ThemeMode) =
        viewModelScope.launch { SettingsStore.setThemeMode(app, mode) }

    fun onDynamicColorChange(enabled: Boolean) =
        viewModelScope.launch { SettingsStore.setDynamicColor(app, enabled) }

    fun onReadingThemeChange(theme: ReadingTheme) =
        viewModelScope.launch { SettingsStore.setReadingTheme(app, theme) }

    fun onFontFamilyChange(family: FontFamily) =
        viewModelScope.launch { SettingsStore.setFontFamily(app, family) }

    fun onReadingSizeChange(size: ReadingSize) =
        viewModelScope.launch { SettingsStore.setReadingSize(app, size) }

    fun onLineSpacingChange(spacing: LineSpacing) =
        viewModelScope.launch { SettingsStore.setLineSpacing(app, spacing) }

    fun isBookmarked(source: String, slug: String): Boolean {
        val key = "$source:$slug"
        return _bookmarks.value.contains(key)
    }

    fun addBookmark(source: String, slug: String, title: String) {
        val key = "$source:$slug"
        viewModelScope.launch {
            val updated = _bookmarks.value + key
            _bookmarks.value = updated
            SettingsStore.saveBookmarks(app, updated)
        }
    }

    fun removeBookmark(source: String, slug: String) {
        val key = "$source:$slug"
        viewModelScope.launch {
            val updated = _bookmarks.value - key
            _bookmarks.value = updated
            SettingsStore.saveBookmarks(app, updated)
        }
    }

    fun addRecentSearch(query: String) {
        viewModelScope.launch {
            val current = _recentSearches.value
            val updated = (listOf(query) + current.filter { it != query }).take(10)
            _recentSearches.value = updated
            SettingsStore.saveRecentSearches(app, updated)
        }
    }

    fun clearRecentSearches() {
        viewModelScope.launch {
            _recentSearches.value = emptyList()
            SettingsStore.saveRecentSearches(app, emptyList())
        }
    }

    fun getReadingProgress(source: String, slug: String): Float {
        val key = "$source:$slug"
        return _readingProgress.value[key] ?: 0.0f
    }

    fun saveReadingProgress(source: String, slug: String, position: Float) {
        val key = "$source:$slug"
        viewModelScope.launch {
            val updated = _readingProgress.value + (key to position.coerceIn(0.0f, 1.0f))
            _readingProgress.value = updated
            SettingsStore.saveReadingProgress(app, updated)
        }
    }

    fun onQueryChange(query: String) {
        _query.value = query
    }

    fun onSourceChange(source: SourceKind) {
        _source.value = source
    }

    private var downloadJob: Job? = null

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

package com.geochron.atlas

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.geochron.atlas.data.AtlasData
import com.geochron.atlas.data.AtlasRepository
import com.geochron.atlas.data.BookmarkEntity
import com.geochron.atlas.data.NoteEntity
import com.geochron.atlas.data.PlaceInfo
import com.geochron.atlas.data.QuizProgressEntity
import com.geochron.atlas.data.RecentEntity
import com.geochron.atlas.data.SearchHistoryEntity
import com.geochron.atlas.data.StudyDatabase
import com.geochron.atlas.data.StudyRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AppViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("geochron", Context.MODE_PRIVATE)
    private val study = StudyRepository(StudyDatabase.get(app).dao())

    // ---- Atlas data (bundled, offline) ----
    private val _data = MutableStateFlow<AtlasData?>(null)
    val data: StateFlow<AtlasData?> = _data.asStateFlow()

    // ---- Preferences: Hindi default, dark mode ----
    private val _hindi = MutableStateFlow(prefs.getBoolean("hindi", true))
    val hindi: StateFlow<Boolean> = _hindi.asStateFlow()

    private val _dark = MutableStateFlow(prefs.getBoolean("dark", false))
    val dark: StateFlow<Boolean> = _dark.asStateFlow()

    fun setHindi(value: Boolean) {
        _hindi.value = value
        prefs.edit().putBoolean("hindi", value).apply()
    }

    fun setDark(value: Boolean) {
        _dark.value = value
        prefs.edit().putBoolean("dark", value).apply()
    }

    // ---- Selected place (info bottom-sheet, shared by Explore & Timeline) ----
    private val _selected = MutableStateFlow<PlaceInfo?>(null)
    val selected: StateFlow<PlaceInfo?> = _selected.asStateFlow()

    fun select(place: PlaceInfo?) {
        _selected.value = place
        if (place != null) viewModelScope.launch { study.addRecent(place) }
    }

    /** When set, Explore focuses this place (from Bookmarks / search / recents). */
    var focusPlace: PlaceInfo? = null

    /** Which atlas Explore should open with ("india" | "world"). */
    var exploreScope: String = "india"

    /** When true, ExploreScreen opens directly with the River category and River Flow Player active. */
    var startInRiverMode: Boolean = false

    /** Full-screen map mode (hides top bar & bottom navigation bar). */
    private val _isMapFullScreen = MutableStateFlow(false)
    val isMapFullScreen: StateFlow<Boolean> = _isMapFullScreen.asStateFlow()

    fun setMapFullScreen(value: Boolean) {
        _isMapFullScreen.value = value
    }

    // ---- Study data from Room ----
    val bookmarks: StateFlow<List<BookmarkEntity>> =
        study.bookmarks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val bookmarkIds: StateFlow<List<String>> =
        study.bookmarkIds.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val notes: StateFlow<List<NoteEntity>> =
        study.notes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recents: StateFlow<List<RecentEntity>> =
        study.recents.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val quizProgress: StateFlow<List<QuizProgressEntity>> =
        study.quizProgress.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val searchHistory: StateFlow<List<SearchHistoryEntity>> =
        study.searchHistory.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        viewModelScope.launch { _data.value = AtlasRepository.load(app) }
    }

    fun toggleBookmark(place: PlaceInfo) {
        val isB = bookmarkIds.value.contains(place.id)
        viewModelScope.launch { study.toggleBookmark(place, isB) }
    }

    fun saveNote(title: String, text: String, placeId: String? = null) {
        viewModelScope.launch { study.saveNote(title, text, placeId) }
    }

    fun deleteNote(note: NoteEntity) {
        viewModelScope.launch { study.deleteNote(note) }
    }

    fun recordSearch(query: String) {
        viewModelScope.launch { study.recordSearch(query) }
    }

    fun clearSearchHistory() {
        viewModelScope.launch { study.clearSearchHistory() }
    }

    fun recordQuizResult(mode: String, score: Int, asked: Int, wrongIds: List<String>) {
        viewModelScope.launch { study.recordQuizResult(mode, score, asked, wrongIds) }
    }
}

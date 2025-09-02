package com.example.varmap.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.varmap.data.PointEntity
import com.example.varmap.repository.PointRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.osmdroid.views.MapView
import kotlin.coroutines.cancellation.CancellationException

data class UiState(
    val points: List<PointEntity> = emptyList(),
    val centerLat: Double = 55.751244,
    val centerLon: Double = 37.618423,
    val showChatDrawer: Boolean = false,
    val showAddPointDialog: Pair<Double, Double>? = null,
    val selectedPoint: PointEntity? = null,
    val messages: List<String> = listOf("Привет!", "Добро пожаловать в чат.", "Готов к работе?"),
    val inputMessage: String = "",
    val locationGranted: Boolean = false,
    val isSyncing: Boolean = false,
    val isProgrammaticMove: Boolean = false
)

class MapViewModel(
    private val repo: PointRepository,
    private val state: SavedStateHandle
) : ViewModel() {

    private object Keys {
        const val LAT = "centerLat"
        const val LON = "centerLon"
        const val CHAT = "showChatDrawer"
        const val INPUT = "inputMessage"
        const val MSGS = "messages"
        const val SEL_NAME = "sel_name"
        const val SEL_LAT = "sel_lat"
        const val SEL_LON = "sel_lon"
        const val ADD_LAT = "add_lat"
        const val ADD_LON = "add_lon"
    }

    private val _ui = MutableStateFlow(
        UiState(
            centerLat = state[Keys.LAT] ?: 55.751244,
            centerLon = state[Keys.LON] ?: 37.618423,
            showChatDrawer = state[Keys.CHAT] ?: false,
            inputMessage = state[Keys.INPUT] ?: "",
            messages = state[Keys.MSGS] ?: listOf("Привет!", "Добро пожаловать в чат.", "Готов к работе?")
        ).let { base ->
            val sel = readSelected()
            val add = readAddDialog()
            base.copy(selectedPoint = sel, showAddPointDialog = add)
        }
    )
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            while (true) {
                try {
                    delay(60_000)
                    syncNow()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        viewModelScope.launch {
            repo.pointsFlow.collect { list ->
                _ui.update { it.copy(points = list) }
            }
        }
        viewModelScope.launch {
            flow {
                while (true) {
                    emit(Unit)
                    delay(10_000)
                }
            }.flatMapConcat {
                flow {
                    _ui.update { it.copy(isSyncing = true) }
                    try {
                        repo.syncDown()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        _ui.update { it.copy(isSyncing = false) }
                    }
                    emit(Unit)
                }
            }.launchIn(this)
        }

        viewModelScope.launch {
            repo.pointsFlow.collect { list ->
                _ui.update { it.copy(points = list) }
            }
        }

        viewModelScope.launch {
            ui.collect { s ->
                state[Keys.LAT] = s.centerLat
                state[Keys.LON] = s.centerLon
                state[Keys.CHAT] = s.showChatDrawer
                state[Keys.INPUT] = s.inputMessage
                state[Keys.MSGS] = s.messages
                writeSelected(s.selectedPoint)
                writeAddDialog(s.showAddPointDialog)
            }
        }
    }

    fun startProgrammaticMove() {
        _ui.update { it.copy(isProgrammaticMove = true) }
    }

    fun endProgrammaticMove() {
        _ui.update { it.copy(isProgrammaticMove = false) }
    }

    fun manualSync() {
        viewModelScope.launch {
            _ui.update { it.copy(isSyncing = true) }
            runCatching {
                repo.syncDown()
            }.onFailure { it.printStackTrace() }
            _ui.update { it.copy(isSyncing = false) }
        }
    }
    fun syncNow() {
        viewModelScope.launch {
            runCatching { repo.syncDown() }
        }
    }

    fun setLocationGranted(granted: Boolean) {
        _ui.update { it.copy(locationGranted = granted) }
    }

    fun setCenter(lat: Double, lon: Double) {
        _ui.update { it.copy(centerLat = lat, centerLon = lon) }
    }

    fun openChat(open: Boolean) { _ui.update { it.copy(showChatDrawer = open) } }

    fun setInputMessage(text: String) { _ui.update { it.copy(inputMessage = text) } }

    fun sendMessage() {
        val msg = ui.value.inputMessage.trim()
        if (msg.isNotEmpty()) {
            _ui.update { it.copy(messages = it.messages + msg, inputMessage = "") }
        }
    }

    fun showAddPointDialog(lat: Double, lon: Double) {
        _ui.update { it.copy(showAddPointDialog = lat to lon) }
    }

    fun hideAddPointDialog() {
        _ui.update { it.copy(showAddPointDialog = null) }
    }

    fun selectPoint(p: PointEntity?) { _ui.update { it.copy(selectedPoint = p) } }

    fun addPoint(name: String) {
        val pair = ui.value.showAddPointDialog ?: return
        val new = PointEntity(name = name, latitude = pair.first, longitude = pair.second)
        viewModelScope.launch {
            repo.addLocal(new)
            runCatching { repo.syncUp() }
        }
        hideAddPointDialog()
    }

    private fun writeSelected(p: PointEntity?) {
        if (p == null) {
            state[Keys.SEL_NAME] = null
            state[Keys.SEL_LAT] = null
            state[Keys.SEL_LON] = null
        } else {
            state[Keys.SEL_NAME] = p.name
            state[Keys.SEL_LAT] = p.latitude
            state[Keys.SEL_LON] = p.longitude
        }
    }
    private fun readSelected(): PointEntity? {
        val name: String? = state[Keys.SEL_NAME]
        val lat: Double? = state[Keys.SEL_LAT]
        val lon: Double? = state[Keys.SEL_LON]
        return if (name != null && lat != null && lon != null) PointEntity(name = name, latitude = lat, longitude = lon)
        else null
    }

    private fun writeAddDialog(p: Pair<Double, Double>?) {
        if (p == null) {
            state[Keys.ADD_LAT] = null
            state[Keys.ADD_LON] = null
        } else {
            state[Keys.ADD_LAT] = p.first
            state[Keys.ADD_LON] = p.second
        }
    }
    private fun readAddDialog(): Pair<Double, Double>? {
        val lat: Double? = state[Keys.ADD_LAT]
        val lon: Double? = state[Keys.ADD_LON]
        return if (lat != null && lon != null) lat to lon else null
    }

}


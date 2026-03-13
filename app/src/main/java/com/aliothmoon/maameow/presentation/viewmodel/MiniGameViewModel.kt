package com.aliothmoon.maameow.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.data.model.activity.MiniGame
import com.aliothmoon.maameow.data.resource.ActivityManager
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import com.aliothmoon.maameow.maa.task.MaaTaskType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.putJsonArray

private const val SECRET_FRONT_VALUE = "MiniGame@SecretFront"
private const val DEFAULT_TASK_NAME = "SS@Store@Begin"

data class MiniGameUiState(
    val selectedTaskName: String = DEFAULT_TASK_NAME,
    val selectedEnding: String = "A",
    val selectedEvent: String = "",
    val statusMessage: String = "",
)

class MiniGameViewModel(
    private val activityManager: ActivityManager,
    private val compositionService: MaaCompositionService,
) : ViewModel() {

    private val _state = MutableStateFlow(MiniGameUiState())
    val state: StateFlow<MiniGameUiState> = _state.asStateFlow()

    val miniGames: StateFlow<List<MiniGame>> = activityManager.miniGames

    val isSecretFront: Boolean
        get() = _state.value.selectedTaskName == SECRET_FRONT_VALUE

    fun onTaskSelected(value: String) {
        _state.update { it.copy(selectedTaskName = value) }
    }

    fun onEndingSelected(ending: String) {
        _state.update { it.copy(selectedEnding = ending) }
    }

    fun onEventSelected(event: String) {
        _state.update { it.copy(selectedEvent = event) }
    }

    fun getCurrentTip(): String {
        val game = miniGames.value.find { it.value == _state.value.selectedTaskName }
        return game?.tip ?: game?.tipKey ?: ""
    }

    fun isCurrentUnsupported(): Boolean {
        val game = miniGames.value.find { it.value == _state.value.selectedTaskName }
        return game?.isUnsupported == true
    }

    private fun buildTaskName(): String {
        val snapshot = _state.value
        if (snapshot.selectedTaskName == SECRET_FRONT_VALUE) {
            val base = "${snapshot.selectedTaskName}@Begin@Ending${snapshot.selectedEnding}"
            return if (snapshot.selectedEvent.isNotBlank()) "$base@${snapshot.selectedEvent}" else base
        }
        return snapshot.selectedTaskName
    }

    private fun buildTaskParams(): MaaTaskParams {
        val taskName = buildTaskName()
        val params = buildJsonObject {
            putJsonArray("task_names") { add(JsonPrimitive(taskName)) }
        }.toString()
        return MaaTaskParams(MaaTaskType.CUSTOM, params)
    }

    fun onStart() {
        if (isCurrentUnsupported()) {
            _state.update { it.copy(statusMessage = "当前版本不支持此任务") }
            return
        }

        viewModelScope.launch {
            val task = buildTaskParams()
            _state.update { it.copy(statusMessage = "正在启动...") }
            when (val result = compositionService.startCopilot(task)) {
                is MaaCompositionService.StartResult.Success -> {
                    _state.update { it.copy(statusMessage = "小游戏任务已启动") }
                }

                is MaaCompositionService.StartResult.ResourceError -> {
                    _state.update { it.copy(statusMessage = "资源加载失败，请重新初始化资源") }
                }

                is MaaCompositionService.StartResult.InitializationError -> {
                    _state.update { it.copy(statusMessage = "初始化失败: ${result.phase}") }
                }

                is MaaCompositionService.StartResult.ConnectionError -> {
                    _state.update { it.copy(statusMessage = "连接失败: ${result.phase}") }
                }

                is MaaCompositionService.StartResult.StartError -> {
                    _state.update { it.copy(statusMessage = "启动失败") }
                }

                is MaaCompositionService.StartResult.PortraitOrientationError -> {
                    _state.update { it.copy(statusMessage = "当前为竖屏,无法在前台模式运行") }
                }
            }
        }
    }

    fun onStop() {
        viewModelScope.launch {
            _state.update { it.copy(statusMessage = "正在停止...") }
            compositionService.stop()
            _state.update { it.copy(statusMessage = "已停止") }
        }
    }

    companion object {
        val ENDINGS = listOf("A", "B", "C", "D", "E")
        val EVENTS = listOf(
            "" to "未选择",
            "支援作战平台" to "支援作战平台",
            "游侠" to "游侠",
            "诡影迷踪" to "诡影迷踪",
        )
    }
}

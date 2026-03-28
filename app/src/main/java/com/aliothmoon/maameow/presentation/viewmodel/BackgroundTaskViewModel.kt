package com.aliothmoon.maameow.presentation.viewmodel

import android.view.Surface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aliothmoon.maameow.RemoteService
import com.aliothmoon.maameow.constant.Packages
import com.aliothmoon.maameow.data.model.LogItem
import com.aliothmoon.maameow.data.model.TaskTypeInfo
import com.aliothmoon.maameow.data.model.TaskParamProvider
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.MaaSessionLogger
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.domain.usecase.BuildTaskParamsUseCase
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.presentation.state.BackgroundTaskState
import com.aliothmoon.maameow.presentation.state.PreviewTouchMarker
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogConfirmAction
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogUiState
import com.aliothmoon.maameow.presentation.view.panel.PanelTab
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.ScheduledExecutionRequest
import com.aliothmoon.maameow.schedule.service.ScheduledLaunchCoordinator
import com.aliothmoon.maameow.schedule.service.ScheduleTriggerLogger
import kotlinx.coroutines.flow.drop
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference

class BackgroundTaskViewModel(
    val chainState: TaskChainState,
    private val buildTaskParams: BuildTaskParamsUseCase,
    private val compositionService: MaaCompositionService,
    private val sessionLogger: MaaSessionLogger,
    private val appSettingsManager: AppSettingsManager,
    scheduleRepository: ScheduleStrategyRepository,
    triggerLogger: ScheduleTriggerLogger,
) : ViewModel() {

    val coordinator = ScheduledLaunchCoordinator(
        scope = viewModelScope,
        scheduleRepository = scheduleRepository,
        compositionService = compositionService,
        appSettingsManager = appSettingsManager,
        chainState = chainState,
        triggerLogger = triggerLogger,
    )

    private val _state = MutableStateFlow(BackgroundTaskState())
    val state: StateFlow<BackgroundTaskState> = _state.asStateFlow()
    val logs: StateFlow<List<LogItem>> = sessionLogger.logs

    private val surfaceRef = AtomicReference<Surface>()


    private val touchPreviewController = TouchPreviewController(viewModelScope)
    val markers: StateFlow<List<PreviewTouchMarker>> = touchPreviewController.markers


    init {
        Timber.i("BackgroundTaskViewModel inited")
        observeServiceState()
        observeTaskEnd()
        observeTouchPreviewToggle()
    }

    private fun observeTouchPreviewToggle() {
        viewModelScope.launch {
            appSettingsManager.showTouchPreview.collect { enabled ->
                touchPreviewController.onTouchCallbackChange(enabled)
            }
        }
    }

    private fun observeServiceState() {
        viewModelScope.launch {
            RemoteServiceManager.state
                .drop(1)
                .collect { state ->
                    when (state) {
                        // 服务重连
                        is RemoteServiceManager.ServiceState.Connected -> {
                            onServiceReconnected(state.service)
                        }

                        is RemoteServiceManager.ServiceState.Error -> {
                            touchPreviewController.onClear()
                        }

                        else -> Unit
                    }
                }
        }
    }

    fun onServiceReconnected(srv: RemoteService) {
        if (surfaceRef.get() != null) {
            onMonitorSurfaceChanged(srv)
        }
        val enabled = appSettingsManager.showTouchPreview.value
        touchPreviewController.onTouchCallbackChange(enabled)
    }

    private fun observeTaskEnd() {
        viewModelScope.launch {
            var prev = compositionService.state.value
            compositionService.state.collect { current ->
                if (prev == MaaExecutionState.RUNNING
                    && (current == MaaExecutionState.IDLE || current == MaaExecutionState.ERROR)
                    && appSettingsManager.closeAppOnTaskEnd.value
                ) {
                    Timber.i("Task ended (%s), auto closing app", current)
                    compositionService.stopVirtualDisplay()
                }
                prev = current
            }
        }
    }


    // ==================== Scheduled Launch ====================

    fun onScheduledLaunch(request: ScheduledExecutionRequest) {
        coordinator.onLaunch(request)
    }

    fun onScheduledCountdownCancel() {
        coordinator.onCancel()
    }

    fun onScheduledStartNow() {
        coordinator.onStartNow()
    }

    fun onScheduledExecutionPageReady(requestId: String) {
        coordinator.onPageReady(requestId) { request ->
            _state.update {
                it.copy(
                    current = PanelTab.TASKS,
                    selectedNodeId = null,
                    isAddingTask = false,
                    isEditMode = false,
                    isProfileMode = false,
                )
            }
            startTasksInternal(request)
        }
    }

    // ==================== Surface ====================

    private fun onMonitorSurfaceChanged(
        service: RemoteService? = RemoteServiceManager.getInstanceOrNull()
    ) {
        val remote = service ?: return
        val surface = surfaceRef.get()
        Timber.d("onMonitorSurfaceChanged: surface=%s", surface)
        runCatching {
            remote.setMonitorSurface(surface)
        }.onFailure {
            Timber.w(it, "setMonitorSurface failed")
        }
    }

    fun onSurfaceAvailable(surface: Surface) {
        surfaceRef.set(surface)
        onMonitorSurfaceChanged()
    }

    fun onSurfaceDestroyed() {
        val surface = surfaceRef.getAndSet(null)
        onMonitorSurfaceChanged()
        surface?.release()
    }

    // ==================== Touch Input ====================

    fun onTouchDown(x: Int, y: Int) {
        runCatching {
            RemoteServiceManager.getInstanceOrNull()?.touchDown(x, y)
        }.onFailure {
            Timber.e(it, "touchDown failed at ($x, $y)")
        }
    }

    fun onTouchMove(x: Int, y: Int) {
        runCatching {
            RemoteServiceManager.getInstanceOrNull()?.touchMove(x, y)
        }.onFailure {
            Timber.e(it, "touchMove failed at ($x, $y)")
        }
    }

    fun onTouchUp(x: Int, y: Int) {
        runCatching {
            RemoteServiceManager.getInstanceOrNull()?.touchUp(x, y)
        }.onFailure {
            Timber.e(it, "touchUp failed at ($x, $y)")
        }
    }

    fun onScreenOff() {
        runCatching {
            RemoteServiceManager.getInstanceOrNull()?.setDisplayPower(false)
        }.onFailure {
            Timber.e(it, "onScreenOff failed")
        }
    }

    // ==================== Task Chain ====================

    fun onNodeEnabledChange(nodeId: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { chainState.setNodeEnabled(nodeId, enabled) }
                .onFailure { e ->
                    Timber.e(e, "Failed to update node enabled: ${e.message}")
                }
        }
    }

    fun onNodeMove(fromIndex: Int, toIndex: Int) {
        viewModelScope.launch {
            runCatching { chainState.reorderNodes(fromIndex, toIndex) }
                .onFailure { e ->
                    Timber.e(e, "Failed to reorder nodes: ${e.message}")
                }
        }
    }

    fun onNodeSelected(nodeId: String) {
        _state.update { it.copy(selectedNodeId = nodeId, isAddingTask = false) }
    }

    fun onToggleEditMode() {
        _state.update {
            it.copy(
                isEditMode = !it.isEditMode,
                isAddingTask = false,
                isProfileMode = false
            )
        }
        Timber.d("Edit mode toggled: %s", _state.value.isEditMode)
    }

    fun onToggleProfileMode() {
        _state.update {
            it.copy(
                isProfileMode = !it.isProfileMode,
                isEditMode = false,
                isAddingTask = false
            )
        }
        Timber.d("Profile mode toggled: %s", _state.value.isProfileMode)
    }

    fun onSwitchProfile(profileId: String) {
        viewModelScope.launch {
            chainState.switchProfile(profileId)
            _state.update { it.copy(selectedNodeId = null) }
        }
    }

    fun onCreateProfile() {
        viewModelScope.launch {
            chainState.createProfile()
            _state.update { it.copy(selectedNodeId = null) }
        }
    }

    fun onDeleteProfile(profileId: String) {
        viewModelScope.launch {
            chainState.deleteProfile(profileId)
            _state.update { it.copy(selectedNodeId = null) }
        }
    }

    fun onRenameProfile(profileId: String, name: String) {
        viewModelScope.launch {
            chainState.renameProfile(profileId, name)
        }
    }

    fun onDuplicateProfile(profileId: String) {
        viewModelScope.launch {
            chainState.duplicateProfile(profileId)
        }
    }

    fun onToggleAddingTask() {
        _state.update { it.copy(isAddingTask = !it.isAddingTask, selectedNodeId = null) }
        Timber.d("Adding task mode toggled: %s", _state.value.isAddingTask)
    }

    fun onAddNode(typeInfo: TaskTypeInfo) {
        viewModelScope.launch {
            val nodeId = chainState.addNode(typeInfo)
            _state.update { it.copy(isAddingTask = false, selectedNodeId = nodeId) }
        }
    }

    fun onRemoveNode(nodeId: String) {
        viewModelScope.launch {
            chainState.removeNode(nodeId)
            if (_state.value.selectedNodeId == nodeId) {
                _state.update { it.copy(selectedNodeId = null) }
            }
        }
    }

    fun onRenameNode(nodeId: String, newName: String) {
        viewModelScope.launch {
            chainState.renameNode(nodeId, newName)
        }
    }

    fun onNodeConfigChange(nodeId: String, config: TaskParamProvider) {
        viewModelScope.launch {
            chainState.updateNodeConfig(nodeId, config)
        }
    }

    // ==================== UI State ====================

    fun onToggleFullscreenMonitor() {
        _state.update { it.copy(isFullscreenMonitor = !it.isFullscreenMonitor) }
    }

    fun onTabChange(tab: PanelTab) {
        _state.update { it.copy(current = tab) }
    }

    // ==================== Task Execution ====================

    fun onStartTasks() {
        viewModelScope.launch {
            val message = startTasksInternal()
            if (message != null) {
                showStartFailedDialog(message)
            }
        }
    }

    private suspend fun doSwitchProfile(request: ScheduledExecutionRequest?) {
        if (request != null && chainState.activeProfileId.value != request.profileId) {
            chainState.switchProfile(request.profileId)
        }
    }

    private suspend fun startTasksInternal(request: ScheduledExecutionRequest? = null): String? {
        doSwitchProfile(request)
        val enabledNodes = chainState.chain.value
            .filter { it.enabled }
            .sortedBy { it.order }

        if (enabledNodes.isEmpty()) {
            Timber.w("No tasks enabled")
            if (request != null) {
                return "关联的任务配置中没有启用任务".also {
                    showStartFailedDialog(it)
                }
            } else {
                showDialog(
                    PanelDialogUiState(
                        type = PanelDialogType.WARNING,
                        title = "提示",
                        message = "请先选择要执行的任务",
                        confirmText = "知道了",
                        confirmAction = PanelDialogConfirmAction.DISMISS_ONLY
                    )
                )
                return "请先选择要执行的任务"
            }
        }

        val params = buildTaskParams()
        val result = compositionService.start(params) {
            if (request != null) {
                sessionLogger.appendAndWait(
                    "由定时任务「${request.strategyName}」触发",
                )
            }
        }
        if (result is MaaCompositionService.StartResult.Success
            && appSettingsManager.muteOnGameLaunch.value
        ) {
            onMuteGameSound()
            chainState.grantGameBatteryExemption()
        }

        val message = resolveStartFailureMessage(result)
        if (message != null) {
            Timber.w("Start failed: $result")
            if (request != null) {
                showStartFailedDialog(message)
            }
            return message
        }
        return null
    }

    fun onStopTasks() {
        viewModelScope.launch {
            compositionService.stop()
        }
    }

    fun onClearLogs() {
        sessionLogger.clearRuntimeLogs()
    }

    fun onMuteGameSound() {
        chainState.getClientTypeOrNull()?.let {
            val pkg = Packages[it] ?: return
            RemoteServiceManager.getInstanceOrNull()
                ?.setPlayAudioOpAllowed(pkg, false)
        }
    }

    fun onUnmuteGameSound() {
        chainState.getClientTypeOrNull()?.let {
            val pkg = Packages[it] ?: return
            RemoteServiceManager.getInstanceOrNull()
                ?.setPlayAudioOpAllowed(pkg, true)
        }
    }

    private fun resolveStartFailureMessage(result: MaaCompositionService.StartResult): String? {
        return when (result) {
            is MaaCompositionService.StartResult.Success -> null

            is MaaCompositionService.StartResult.ResourceError ->
                "资源加载失败，请尝试重新初始化资源"

            is MaaCompositionService.StartResult.InitializationError -> when (result.phase) {
                MaaCompositionService.StartResult.InitializationError.InitPhase.CREATE_INSTANCE ->
                    "MaaCore 初始化失败，请重启应用"

                MaaCompositionService.StartResult.InitializationError.InitPhase.SET_TOUCH_MODE ->
                    "触控模式设置失败，请重启应用"
            }

            is MaaCompositionService.StartResult.PortraitOrientationError ->
                "当前屏幕为竖屏，请切换为横屏后启动"

            is MaaCompositionService.StartResult.ConnectionError -> when (result.phase) {
                MaaCompositionService.StartResult.ConnectionError.ConnectPhase.DISPLAY_MODE ->
                    "显示模式设置失败，请重试"

                MaaCompositionService.StartResult.ConnectionError.ConnectPhase.VIRTUAL_DISPLAY ->
                    "虚拟屏幕启动失败，请检查服务权限"

                MaaCompositionService.StartResult.ConnectionError.ConnectPhase.MAA_CONNECT ->
                    "连接 MaaCore 超时，请重试"
            }

            is MaaCompositionService.StartResult.StartError ->
                "MaaCore 启动失败，请检查任务配置"
        }
    }

    private fun showStartFailedDialog(message: String) {
        showDialog(
            PanelDialogUiState(
                type = PanelDialogType.ERROR,
                title = "启动失败",
                message = message,
                confirmText = "查看日志",
                confirmAction = PanelDialogConfirmAction.GO_LOG
            )
        )
    }

    // ==================== Dialog ====================

    private fun showDialog(dialog: PanelDialogUiState) {
        _state.update { it.copy(dialog = dialog) }
    }

    fun onDialogDismiss() {
        _state.update { it.copy(dialog = null) }
    }

    fun onDialogConfirm() {
        when (state.value.dialog?.confirmAction) {
            PanelDialogConfirmAction.DISMISS_ONLY -> {
                onDialogDismiss()
            }

            PanelDialogConfirmAction.GO_LOG -> {
                onTabChange(PanelTab.LOG)
                onDialogDismiss()
            }

            PanelDialogConfirmAction.GO_LOG_AND_STOP -> {
                onTabChange(PanelTab.LOG)
                onDialogDismiss()
                viewModelScope.launch {
                    compositionService.stop()
                }
            }

            null -> Unit
        }
    }

    override fun onCleared() {
        coordinator.cancel()
        touchPreviewController.onClear()
        super.onCleared()
    }
}

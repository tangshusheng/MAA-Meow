package com.aliothmoon.maameow.presentation.view.background

import android.app.Activity
import android.content.res.Configuration
import android.content.pm.ActivityInfo
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer

import android.graphics.PixelFormat
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.aspectRatio
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.movableContentOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.constant.DefaultDisplayConfig
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.UnifiedStateDispatcher
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.presentation.components.AdaptiveTaskPromptDialog
import com.aliothmoon.maameow.presentation.components.ShizukuPermissionDialog
import com.aliothmoon.maameow.presentation.view.panel.PanelHeader
import com.aliothmoon.maameow.presentation.view.panel.LogPanel
import com.aliothmoon.maameow.presentation.view.panel.PanelDialogType
import com.aliothmoon.maameow.presentation.view.panel.PanelTab
import com.aliothmoon.maameow.presentation.view.panel.TaskConfigPanel
import com.aliothmoon.maameow.presentation.view.panel.TaskListPanel
import com.aliothmoon.maameow.presentation.view.panel.AutoBattlePanel
import com.aliothmoon.maameow.presentation.view.panel.MiniGamePanel
import com.aliothmoon.maameow.presentation.viewmodel.BackgroundTaskViewModel
import com.aliothmoon.maameow.presentation.viewmodel.CopilotViewModel
import com.aliothmoon.maameow.presentation.viewmodel.MiniGameViewModel
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import com.aliothmoon.maameow.domain.service.AppWatchdog
import com.aliothmoon.maameow.overlay.screensaver.ScreenSaverOverlayManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import timber.log.Timber

@Composable
fun BackgroundTaskView(
    onFullscreenChanged: (Boolean) -> Unit = {},
    viewModel: BackgroundTaskViewModel = koinViewModel(),
    copilotViewModel: CopilotViewModel = koinInject(),
    miniGameViewModel: MiniGameViewModel = koinInject(),
    compositionService: MaaCompositionService = koinInject(),
    dispatcher: UnifiedStateDispatcher = koinInject(),
    permissionManager: PermissionManager = koinInject(),
    appSettingsManager: AppSettingsManager = koinInject(),
    screenSaverOverlayManager: ScreenSaverOverlayManager = koinInject(),
    appWatchdog: AppWatchdog = koinInject(),
) {
    val coroutineScope = rememberCoroutineScope()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val maaState by compositionService.state.collectAsStateWithLifecycle()
    val permissions by permissionManager.state.collectAsStateWithLifecycle()
    val muteOnGameLaunch by appSettingsManager.muteOnGameLaunch.collectAsStateWithLifecycle()
    val closeAppOnTaskEnd by appSettingsManager.closeAppOnTaskEnd.collectAsStateWithLifecycle()
    val useHardwareScreenOff by appSettingsManager.useHardwareScreenOff.collectAsStateWithLifecycle()
    val watchdogState by appWatchdog.state.collectAsStateWithLifecycle()
    var isRequestingRemoteAccess by remember { mutableStateOf(false) }
    var showCloseConfirm by remember { mutableStateOf(false) }
    var showMoreActions by remember { mutableStateOf(false) }

    val nodes by viewModel.chainState.chain.collectAsStateWithLifecycle()
    val selectedNode = nodes.find { it.id == state.selectedNodeId }
    val canShowTaskActions =
        state.currentTab == PanelTab.TASKS
                || state.currentTab == PanelTab.AUTO_BATTLE
                || state.currentTab == PanelTab.TOOLS

    val pagerState = rememberPagerState(
        initialPage = state.currentTab.ordinal, pageCount = { PanelTab.entries.size })

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { page ->
            val newTab = PanelTab.entries[page]
            if (newTab != state.currentTab) {
                viewModel.onTabChange(newTab)
            }
        }
    }

    LaunchedEffect(state.currentTab) {
        if (pagerState.currentPage != state.currentTab.ordinal) {
            pagerState.scrollToPage(state.currentTab.ordinal)
        }
    }
    val context = LocalContext.current

    if (!permissions.remoteAccessGranted) {
        ShizukuPermissionDialog(
            title = "需要${permissions.startupBackend.display}权限",
            message = "后台任务页面依赖${permissions.startupBackend.display}远程服务，授权成功前将持续显示该提示。",
            isRequesting = isRequestingRemoteAccess,
            onConfirm = {
                if (isRequestingRemoteAccess) return@ShizukuPermissionDialog
                coroutineScope.launch {
                    isRequestingRemoteAccess = true
                    val granted = permissionManager.requestRemoteAccess()
                    isRequestingRemoteAccess = false
                    if (!granted) {
                        Toast.makeText(
                            context,
                            "${permissions.remotePermissionLabel}未获取，请重试",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            })
    }


    LaunchedEffect(state.isFullscreenMonitor) {
        onFullscreenChanged(state.isFullscreenMonitor)
    }

    LaunchedEffect(Unit) {
        dispatcher.serviceDiedEvent.collect {
            Toast.makeText(context, "MaaService 异常关闭，请尝试重新启动", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(Unit) {
        appWatchdog.appDiedEvent.collect {
            Toast.makeText(context, "游戏进程未启动或被异常关闭", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(canShowTaskActions, showCloseConfirm, state.isFullscreenMonitor, state.dialog) {
        if (!canShowTaskActions || showCloseConfirm || state.isFullscreenMonitor || state.dialog != null) {
            showMoreActions = false
        }
    }


    var isSurfaceAvailable by remember { mutableStateOf(false) }
    var lastSentSurface by remember { mutableStateOf<Surface?>(null) }

    val previewContent = remember {
        movableContentOf {
            Box(
                modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        SurfaceView(ctx).apply {
                            holder.setFormat(PixelFormat.RGBA_8888)
                            holder.addCallback(object : SurfaceHolder.Callback {
                                override fun surfaceCreated(holder: SurfaceHolder) {
                                    isSurfaceAvailable = true
                                    // 延迟 50ms 设置固定大小，避开系统 Transition 冲突
                                    coroutineScope.launch {
                                        delay(50)
                                        holder.setFixedSize(
                                            DefaultDisplayConfig.WIDTH, DefaultDisplayConfig.HEIGHT
                                        )
                                    }
                                }

                                override fun surfaceChanged(
                                    holder: SurfaceHolder, format: Int, width: Int, height: Int
                                ) {
                                    Timber.d("Surface size changed to $width x $height")
                                    // 只有尺寸正确且 Surface 未发送过时才发送
                                    if (width == DefaultDisplayConfig.WIDTH && height == DefaultDisplayConfig.HEIGHT) {
                                        if (lastSentSurface != holder.surface) {
                                            lastSentSurface = holder.surface
                                            viewModel.onSurfaceAvailable(holder.surface)
                                        }
                                    }
                                }

                                override fun surfaceDestroyed(holder: SurfaceHolder) {
                                    isSurfaceAvailable = false
                                    lastSentSurface = null
                                    viewModel.onSurfaceDestroyed()
                                }
                            })
                        }
                    }, modifier = Modifier.aspectRatio(DefaultDisplayConfig.ASPECT_RATIO)
                )
            }
        }
    }

    val fullscreenProgress = remember { Animatable(0f) }
    val currentOrientation = LocalConfiguration.current.orientation
    val isLandscapeReady = currentOrientation == Configuration.ORIENTATION_LANDSCAPE
    var fullscreenAnimTarget by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(state.isFullscreenMonitor, isLandscapeReady) {
        fullscreenAnimTarget = if (state.isFullscreenMonitor && isLandscapeReady) 1f else 0f
    }

    LaunchedEffect(fullscreenAnimTarget) {
        if (fullscreenAnimTarget == 0f) {
            fullscreenProgress.snapTo(0f)
        } else {
            fullscreenProgress.snapTo(0f)
            fullscreenProgress.animateTo(1f, tween(300, easing = FastOutSlowInEasing))
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(3f)
            ) {
                if (!state.isFullscreenMonitor) {
                    VirtualDisplayPreview(
                        isRunning = maaState == MaaExecutionState.RUNNING,
                        isSurfaceAvailable = isSurfaceAvailable,
                        watchdogState = watchdogState,
                        onClick = { viewModel.onToggleFullscreenMonitor() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        previewContent()
                    }
                } else {
                    Spacer(
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(7f)
            ) {
                PanelHeader(
                    selectedTab = state.currentTab, onTabSelected = { tab ->
                        viewModel.onTabChange(tab)
                    }, showActions = false
                )

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    userScrollEnabled = true,
                    beyondViewportPageCount = PanelTab.entries.size - 1
                ) { page ->
                    when (page) {
                        0 -> {
                            Row(modifier = Modifier.fillMaxSize()) {
                                TaskListPanel(
                                    nodes = nodes,
                                    selectedNodeId = state.selectedNodeId,
                                    isEditMode = state.isEditMode,
                                    isAddingTask = state.isAddingTask,
                                    onNodeEnabledChange = { nodeId, enabled ->
                                        viewModel.onNodeEnabledChange(nodeId, enabled)
                                    },
                                    onNodeSelected = { nodeId ->
                                        viewModel.onNodeSelected(nodeId)
                                    },
                                    onNodeMove = { fromIndex, toIndex ->
                                        viewModel.onNodeMove(fromIndex, toIndex)
                                    },
                                    onToggleEditMode = { viewModel.onToggleEditMode() },
                                    onToggleAddingTask = { viewModel.onToggleAddingTask() },
                                    modifier = Modifier.fillMaxHeight(),
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Card(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(top = 10.dp)) {
                                        TaskConfigPanel(
                                            selectedNode = selectedNode,
                                            isEditMode = state.isEditMode,
                                            isAddingTask = state.isAddingTask,
                                            onConfigChange = { config ->
                                                val nodeId =
                                                    selectedNode?.id ?: return@TaskConfigPanel
                                                viewModel.onNodeConfigChange(nodeId, config)
                                            },
                                            onAddNode = { viewModel.onAddNode(it) },
                                            onRemoveNode = { viewModel.onRemoveNode(it) },
                                            onRenameNode = { id, name -> viewModel.onRenameNode(id, name) }
                                        )
                                    }
                                }
                            }
                        }

                        1 -> {
                            AutoBattlePanel(modifier = Modifier.fillMaxSize())
                        }

                        2 -> {
                            MiniGamePanel(modifier = Modifier.fillMaxSize())
                        }

                        3 -> {
                            val runtimeLogs by viewModel.runtimeLogs.collectAsStateWithLifecycle()
                            LogPanel(
                                logs = runtimeLogs,
                                onClearLogs = { viewModel.onClearLogs() },
                                onClose = { viewModel.onTabChange(PanelTab.TASKS) })
                        }
                    }
                }

                if (canShowTaskActions) {
                    Spacer(modifier = Modifier.height(12.dp))

                    val focusManager = LocalFocusManager.current

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                focusManager.clearFocus()
                                when (state.currentTab) {
                                    PanelTab.TASKS -> viewModel.onStartTasks()
                                    PanelTab.AUTO_BATTLE -> copilotViewModel.onStart()
                                    PanelTab.TOOLS -> miniGameViewModel.onStart()
                                    else -> {}
                                }
                            },
                            enabled = maaState != MaaExecutionState.RUNNING && maaState != MaaExecutionState.STARTING,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (maaState == MaaExecutionState.STARTING) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("开始任务")
                            }
                        }

                        OutlinedButton(
                            onClick = {
                                when (state.currentTab) {
                                    PanelTab.TASKS -> viewModel.onStopTasks()
                                    PanelTab.AUTO_BATTLE -> copilotViewModel.onStop()
                                    PanelTab.TOOLS -> miniGameViewModel.onStop()
                                    else -> {}
                                }
                            },
                            enabled = maaState == MaaExecutionState.RUNNING,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("停止任务")
                        }

                        IconButton(
                            onClick = { showMoreActions = !showMoreActions },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert, contentDescription = "更多操作"
                            )
                        }
                    }
                }
            }
        }

        BackHandler(enabled = showMoreActions) {
            showMoreActions = false
        }

        BackgroundMoreActionsOverlay(
            visible = showMoreActions,
            onDismissRequest = { showMoreActions = false },
            muteOnGameLaunch = muteOnGameLaunch,
            onMuteOnGameLaunchChange = {
                coroutineScope.launch { appSettingsManager.setMuteOnGameLaunch(it) }
            },
            closeAppOnTaskEnd = closeAppOnTaskEnd,
            onCloseAppOnTaskEndChange = {
                coroutineScope.launch { appSettingsManager.setCloseAppOnTaskEnd(it) }
            },
            useHardwareScreenOff = useHardwareScreenOff,
            onUseHardwareScreenOffChange = {
                coroutineScope.launch { appSettingsManager.setUseHardwareScreenOff(it) }
            },
            onMuteGameSound = viewModel::onMuteGameSound,
            onUnmuteGameSound = viewModel::onUnmuteGameSound,
            onScreenOff = {
                if (useHardwareScreenOff) {
                    viewModel.onScreenOff()
                } else {
                    screenSaverOverlayManager.show()
                }
            },
            onCloseApp = {
                showMoreActions = false
                if (maaState == MaaExecutionState.RUNNING) {
                    showCloseConfirm = true
                } else {
                    coroutineScope.launch { compositionService.stopVirtualDisplay() }
                }
            })

        // 全屏预览
        if (state.isFullscreenMonitor) {
            val activity = context as? Activity

            DisposableEffect(Unit) {
                val window = activity?.window
                val controller = window?.let {
                    WindowCompat.getInsetsController(it, it.decorView)
                }
                controller?.hide(WindowInsetsCompat.Type.systemBars())
                controller?.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                onDispose {
                    controller?.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            DisposableEffect(Unit) {
                val originalOrientation = activity?.requestedOrientation
                onDispose {
                    if (originalOrientation != null) {
                        activity.requestedOrientation = originalOrientation
                    }
                }
            }

            LaunchedEffect(Unit) {
                val current = activity?.resources?.configuration?.orientation
                if (current != Configuration.ORIENTATION_LANDSCAPE) {
                    activity?.requestedOrientation =
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
            }

            BackHandler { viewModel.onToggleFullscreenMonitor() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = fullscreenProgress.value
                    }
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                val coords = viewToVirtualDisplay(
                                    change.position.x, change.position.y, size.width, size.height
                                ) ?: continue
                                when (event.type) {
                                    PointerEventType.Press -> viewModel.onTouchDown(
                                        coords.first, coords.second
                                    )

                                    PointerEventType.Move -> {
                                        if (change.pressed) {
                                            viewModel.onTouchMove(coords.first, coords.second)
                                        }
                                    }

                                    PointerEventType.Release -> viewModel.onTouchUp(
                                        coords.first, coords.second
                                    )
                                }
                                change.consume()
                            }
                        }
                    }, contentAlignment = Alignment.Center
            ) {
                previewContent()

                IconButton(
                    onClick = { viewModel.onToggleFullscreenMonitor() },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 8.dp, end = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "关闭",
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        state.dialog?.let { dialog ->
            val confirmColor = when (dialog.type) {
                PanelDialogType.SUCCESS -> MaterialTheme.colorScheme.primary
                PanelDialogType.WARNING -> MaterialTheme.colorScheme.tertiary
                PanelDialogType.ERROR -> MaterialTheme.colorScheme.error
            }
            AdaptiveTaskPromptDialog(
                visible = true,
                title = dialog.title,
                message = AnnotatedString(dialog.message),
                onDismissRequest = viewModel::onDialogDismiss,
                onConfirm = viewModel::onDialogConfirm,
                confirmText = dialog.confirmText,
                dismissText = dialog.dismissText,
                icon = when (dialog.type) {
                    PanelDialogType.SUCCESS -> Icons.Filled.CheckCircle
                    else -> Icons.Filled.Warning
                },
                iconTint = confirmColor,
                confirmColor = confirmColor,
            )
        }

        if (showCloseConfirm) {
            AdaptiveTaskPromptDialog(
                visible = true,
                title = "确认关闭",
                message = AnnotatedString("任务正在运行中，确认关闭应用吗？"),
                onDismissRequest = { showCloseConfirm = false },
                onConfirm = {
                    showCloseConfirm = false
                    coroutineScope.launch { compositionService.stopVirtualDisplay() }
                },
                confirmText = "确认关闭",
                dismissText = "取消",
                icon = Icons.Filled.Warning,
                iconTint = MaterialTheme.colorScheme.error,
                confirmColor = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun viewToVirtualDisplay(
    viewX: Float, viewY: Float, viewWidth: Int, viewHeight: Int
): Pair<Int, Int>? {
    val bufferW = DefaultDisplayConfig.WIDTH.toFloat()
    val bufferH = DefaultDisplayConfig.HEIGHT.toFloat()
    val scale = minOf(viewWidth / bufferW, viewHeight / bufferH)
    val offsetX = (viewWidth - bufferW * scale) / 2f
    val offsetY = (viewHeight - bufferH * scale) / 2f
    val vx = ((viewX - offsetX) / scale).toInt()
    val vy = ((viewY - offsetY) / scale).toInt()
    if (vx < 0 || vx >= bufferW.toInt() || vy < 0 || vy >= bufferH.toInt()) return null
    return vx to vy
}

@Composable
private fun BackgroundMoreActionsOverlay(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    muteOnGameLaunch: Boolean,
    onMuteOnGameLaunchChange: (Boolean) -> Unit,
    closeAppOnTaskEnd: Boolean,
    onCloseAppOnTaskEndChange: (Boolean) -> Unit,
    useHardwareScreenOff: Boolean,
    onUseHardwareScreenOffChange: (Boolean) -> Unit,
    onMuteGameSound: () -> Unit,
    onUnmuteGameSound: () -> Unit,
    onScreenOff: () -> Unit,
    onCloseApp: () -> Unit,
) {
    val overlayInteractionSource = remember { MutableInteractionSource() }
    val cardInteractionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (visible) {
                    Modifier.clickable(
                        interactionSource = overlayInteractionSource,
                        indication = null,
                        onClick = onDismissRequest
                    )
                } else {
                    Modifier
                }
            )
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(animationSpec = tween(160)) + slideInVertically(
                animationSpec = tween(
                    220,
                    easing = FastOutSlowInEasing
                ),
                initialOffsetY = { it / 4 }),
            exit = fadeOut(animationSpec = tween(140)) + slideOutVertically(
                animationSpec = tween(
                    180,
                    easing = FastOutSlowInEasing
                ),
                targetOffsetY = { it / 4 }),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 64.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = cardInteractionSource, indication = null, onClick = {}),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = muteOnGameLaunch,
                            onCheckedChange = onMuteOnGameLaunchChange,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "游戏启动时静音", style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        FilledTonalButton(
                            onClick = onMuteGameSound,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("关闭声音", style = MaterialTheme.typography.bodySmall)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        FilledTonalButton(
                            onClick = onUnmuteGameSound,
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(36.dp)
                        ) {
                            Text("打开声音", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = closeAppOnTaskEnd,
                                onCheckedChange = onCloseAppOnTaskEndChange,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "任务结束后关闭游戏",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = useHardwareScreenOff,
                                onCheckedChange = onUseHardwareScreenOffChange,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "熄屏挂机时关闭屏幕",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onScreenOff,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("熄屏挂机")
                        }

                        OutlinedButton(
                            onClick = onCloseApp,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("关闭应用")
                        }
                    }
                }
            }
        }
    }
}

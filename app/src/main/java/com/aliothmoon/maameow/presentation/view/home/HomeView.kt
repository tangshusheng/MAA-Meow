package com.aliothmoon.maameow.presentation.view.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.constant.Routes
import com.aliothmoon.maameow.data.datasource.ResourceDownloader
import com.aliothmoon.maameow.data.preferences.AppSettingsManager
import com.aliothmoon.maameow.data.permission.PermissionState
import com.aliothmoon.maameow.domain.models.OverlayControlMode
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.models.RunMode
import com.aliothmoon.maameow.domain.state.ResourceInitState
import com.aliothmoon.maameow.manager.PermissionManager
import com.aliothmoon.maameow.manager.ShizukuInstallHelper
import com.aliothmoon.maameow.presentation.components.ResourceInitDialog
import dev.jeziellago.compose.markdowntext.MarkdownText
import com.aliothmoon.maameow.presentation.components.UpdateCard
import com.aliothmoon.maameow.presentation.state.StatusColorType
import com.aliothmoon.maameow.presentation.viewmodel.HomeViewModel
import com.aliothmoon.maameow.presentation.viewmodel.UpdateViewModel
import com.aliothmoon.maameow.utils.Misc
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import kotlinx.coroutines.launch
import timber.log.Timber


@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun HomeView(
    navController: NavController,
    viewModel: HomeViewModel = koinViewModel(),
    updateViewModel: UpdateViewModel = koinViewModel(),
    permissionManager: PermissionManager = koinInject(),
    appSettingsManager: AppSettingsManager = koinInject()
) {

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val permissionState by permissionManager.state.collectAsStateWithLifecycle()
    val resourceVersion by updateViewModel.currentResourceVersion.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val (width, height) = Misc.getScreenSize(context)

    val startupDialog by updateViewModel.startupUpdateDialog.collectAsStateWithLifecycle()

    // 启动时检查资源初始化
    LaunchedEffect(Unit) {
        viewModel.checkAndInitResource()
    }
    // 资源初始化完成后刷新版本号
    LaunchedEffect(uiState.resourceInitState) {
        if (uiState.resourceInitState is ResourceInitState.Ready) {
            updateViewModel.refreshResourceVersion()
            updateViewModel.checkUpdatesOnStartup()
        }
    }

    // 资源初始化弹窗
    ResourceInitDialog(
        state = uiState.resourceInitState,
        onRetry = { viewModel.onTryResourceInit() }
    )

    // 发现更新弹窗
    startupDialog?.let { result ->
        StartupUpdateDialog(
            appVersion = result.appUpdate?.version,
            resourceVersion = result.resourceUpdate?.version,
            appReleaseNote = result.appUpdate?.releaseNote,
            resourceReleaseNote = result.resourceUpdate?.releaseNote,
            onConfirm = {
                if (result.appUpdate != null) {
                    updateViewModel.confirmAppDownload(result.appUpdate.version)
                } else {
                    updateViewModel.confirmResourceDownload()
                }
                updateViewModel.dismissStartupDialog()
            },
            onDismiss = { updateViewModel.dismissStartupDialog() }
        )
    }

    if (uiState.showRunModeUnsupportedDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.onDismissRunModeUnsupportedDialog() },
            title = { Text("模式不支持") },
            text = { Text(uiState.runModeUnsupportedMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.onDismissRunModeUnsupportedDialog() }) {
                    Text("我知道了")
                }
            }
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = "MAA",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                actions = {
                    IconButton(onClick = {
                        navController.navigate(Routes.SETTINGS)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "设置"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = 8.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    ScreenInfoCard(
                        screenWidth = width,
                        screenHeight = height,
                        resourceVersion = resourceVersion,
                        serviceStatusColor = uiState.serviceStatusColor,
                        serviceStatusText = uiState.serviceStatusText,
                        serviceStatusLoading = uiState.serviceStatusLoading
                    )
                }

                item {
                    RunModeCard(
                        runMode = uiState.runMode,
                        onRunModeChange = { viewModel.onRunModeChange(it) },
                        changeEnabled = viewModel.checkRunModeChangeEnabled()
                    )
                }

                item {
                    UpdateCard()
                }

                item {
                    PermissionCard(
                        permissionState = permissionState,
                        isGranting = uiState.isGranting,
                        onRequestRemoteAccess = { viewModel.onRequestRemoteAccess(context) },
                        onRequestOverlay = { viewModel.onRequestOverlay(context) },
                        onRequestStorage = { viewModel.onRequestStorage(context) },
                        onRequestBatteryWhitelist = { viewModel.onRequestBatteryWhitelist(context) },
                        onRequestAccessibility = { viewModel.onRequestAccessibility(context) },
                        onRequestNotification = { viewModel.onRequestNotification(context) }
                    )
                }

                if (uiState.runMode == RunMode.FOREGROUND) {
                    item {
                        ForegroundModeSection(
                            overlayControlMode = uiState.overlayControlMode,
                            isShowControlOverlay = uiState.isShowControlOverlay,
                            isLoading = uiState.isLoading,
                            onChangeTo16x9Resolution = { viewModel.onChangeTo16x9Resolution(context) },
                            onResetResolution = { viewModel.onResetResolution(context) },
                            onControlOverlayModeChanged = { viewModel.onControlOverlayModeChanged(it) },
                            onToggleOverlay = {
                                if (uiState.isShowControlOverlay) {
                                    Timber.d("关闭悬浮窗")
                                    viewModel.onStopControlOverlay()
                                } else {
                                    Timber.d("开启悬浮窗模式")
                                    viewModel.onStartControlOverlay()
                                }
                            }
                        )
                    }
                }

                item {
                    OutlinedButton(
                        onClick = {
                            Timber.d("关闭所有服务")
                            viewModel.onStopAllServices()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = MaterialTheme.shapes.large,
                        enabled = !uiState.isLoading
                    ) {
                        Text(
                            text = "关闭所有服务",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }

        // Shizuku/Sui 检测
        val skipShizukuCheck by appSettingsManager.skipShizukuCheck.collectAsStateWithLifecycle()
        var shizukuStatus by remember {
            mutableStateOf(ShizukuInstallHelper.checkStatus(context))
        }
        LifecycleResumeEffect(Unit) {
            shizukuStatus = ShizukuInstallHelper.checkStatus(context)
            onPauseOrDispose {}
        }
        if (permissionState.startupBackend == RemoteBackend.SHIZUKU && !skipShizukuCheck) {
            val skipScope = rememberCoroutineScope()
            when (shizukuStatus) {
                ShizukuInstallHelper.ShizukuStatus.NOT_INSTALLED -> {
                    ShizukuStatusDialog(
                        title = "未检测到 Shizuku",
                        message = "本应用依赖 Shizuku 服务运行，检测到设备未安装 Shizuku，请先安装。",
                        icon = Icons.Rounded.Warning,
                        primaryText = "快捷安装 Shizuku",
                        onPrimaryClick = {
                            ShizukuInstallHelper.installShizuku(context)
                        },
                        showRootOption = permissionState.rootAvailable,
                        onRootClick = {
                            skipScope.launch {
                                permissionManager.setStartupBackend(RemoteBackend.ROOT)
                            }
                        },
                        onSkipClick = {
                            skipScope.launch {
                                appSettingsManager.setSkipShizukuCheck(true)
                            }
                        }
                    )
                }

                ShizukuInstallHelper.ShizukuStatus.APP_NOT_RUNNING -> {
                    ShizukuStatusDialog(
                        title = "Shizuku 未启动",
                        message = "检测到 Shizuku 已安装但服务未启动，请打开 Shizuku 应用并启动服务。",
                        icon = Icons.Rounded.Build,
                        primaryText = "打开 Shizuku",
                        onPrimaryClick = {
                            runCatching {
                                val intent =
                                    context.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
                                if (intent != null) context.startActivity(intent)
                            }
                        },
                        showRootOption = permissionState.rootAvailable,
                        onRootClick = {
                            skipScope.launch {
                                permissionManager.setStartupBackend(RemoteBackend.ROOT)
                            }
                        },
                        onSkipClick = {
                            skipScope.launch {
                                appSettingsManager.setSkipShizukuCheck(true)
                            }
                        }
                    )
                }

                ShizukuInstallHelper.ShizukuStatus.SUI_AVAILABLE -> {
                    ShizukuStatusDialog(
                        title = "检测到 Sui",
                        message = "当前使用 Sui 提供 Shizuku 服务，Sui 以 Root 权限运行，MaaMeow 可能无法正常工作，请以实际测试为主",
                        icon = Icons.Rounded.Info,
                        primaryText = "知道了",
                        onPrimaryClick = {
                            skipScope.launch { appSettingsManager.setSkipShizukuCheck(true) }
                        },
                        showSkipOption = false
                    )
                }

                ShizukuInstallHelper.ShizukuStatus.READY -> {}
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShizukuStatusDialog(
    title: String,
    message: String,
    icon: ImageVector,
    primaryText: String,
    onPrimaryClick: () -> Unit,
    showRootOption: Boolean = false,
    onRootClick: () -> Unit = {},
    showSkipOption: Boolean = true,
    onSkipClick: () -> Unit = {}
) {
    BasicAlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onPrimaryClick,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text(primaryText)
                    }

                    if (showRootOption) {
                        OutlinedButton(
                            onClick = onRootClick,
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text("切换至 Root 模式")
                        }
                    }

                    if (showSkipOption) {
                        TextButton(
                            onClick = onSkipClick,
                            shape = MaterialTheme.shapes.large
                        ) {
                            Text(
                                text = "跳过检查 (不推荐)",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenInfoCard(
    screenWidth: Int,
    screenHeight: Int,
    resourceVersion: String,
    serviceStatusColor: StatusColorType,
    serviceStatusText: String,
    serviceStatusLoading: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "屏幕分辨率",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$screenWidth × $screenHeight",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "资源版本",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = resourceVersion.ifBlank { "未安装" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (resourceVersion.isBlank())
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "服务状态",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val statusColor = when (serviceStatusColor) {
                    StatusColorType.PRIMARY -> MaterialTheme.colorScheme.primary
                    StatusColorType.WARNING -> Color(0xFFFF9800)
                    StatusColorType.ERROR -> MaterialTheme.colorScheme.error
                    StatusColorType.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = serviceStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = statusColor
                    )
                    if (serviceStatusLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = statusColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RunModeCard(
    runMode: RunMode,
    onRunModeChange: (Boolean) -> Unit,
    changeEnabled: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "运行模式",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = when (runMode) {
                        RunMode.FOREGROUND -> "前台模式：悬浮窗控制"
                        RunMode.BACKGROUND -> "后台模式：应用内运行"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = runMode.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Switch(
                    checked = runMode == RunMode.BACKGROUND,
                    enabled = changeEnabled,
                    onCheckedChange = onRunModeChange
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(
    title: String,
    granted: Boolean,
    onClick: () -> Unit,
    isLoading: Boolean = false,
    grantedText: String = "已授权",
    ungrantedText: String = "请求权限",
    contentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor
        )
        TextButton(
            onClick = onClick,
            enabled = !granted && !isLoading,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text(text = if (granted) grantedText else ungrantedText)
            }
        }
    }
}

@Composable
private fun PermissionCard(
    permissionState: PermissionState,
    isGranting: Boolean,
    onRequestRemoteAccess: () -> Unit,
    onRequestOverlay: () -> Unit,
    onRequestStorage: () -> Unit,
    onRequestBatteryWhitelist: () -> Unit,
    onRequestAccessibility: () -> Unit,
    onRequestNotification: () -> Unit
) {
    var expandedPermissions by remember { mutableStateOf(false) }
    val contentColor = MaterialTheme.colorScheme.onPrimaryContainer

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Text(
                text = "权限管理",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                modifier = Modifier.padding(bottom = 6.dp)
            )

            PermissionRow(
                title = permissionState.remotePermissionLabel,
                granted = permissionState.remoteAccessGranted,
                onClick = onRequestRemoteAccess,
                isLoading = isGranting,
                contentColor = contentColor
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expandedPermissions = !expandedPermissions }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expandedPermissions) "收起其他权限" else "展开其他权限",
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.7f)
                )
                Icon(
                    imageVector = if (expandedPermissions) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = contentColor.copy(alpha = 0.7f)
                )
            }

            AnimatedVisibility(visible = expandedPermissions) {
                Column {
                    PermissionRow(
                        title = "悬浮窗权限",
                        granted = permissionState.overlay,
                        onClick = onRequestOverlay,
                        contentColor = contentColor
                    )
                    PermissionRow(
                        title = "外部存储权限",
                        granted = permissionState.storage,
                        onClick = onRequestStorage,
                        contentColor = contentColor
                    )
                    PermissionRow(
                        title = "电源管理白名单",
                        granted = permissionState.batteryWhitelist,
                        onClick = onRequestBatteryWhitelist,
                        grantedText = "已添加",
                        contentColor = contentColor
                    )
                    PermissionRow(
                        title = "无障碍权限",
                        granted = permissionState.accessibility,
                        onClick = onRequestAccessibility,
                        ungrantedText = if (permissionState.remoteAccessGranted) "快捷授权" else "请求权限",
                        contentColor = contentColor
                    )
                    PermissionRow(
                        title = "通知权限",
                        granted = permissionState.notification,
                        onClick = onRequestNotification,
                        contentColor = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun ForegroundModeSection(
    overlayControlMode: OverlayControlMode,
    isShowControlOverlay: Boolean,
    isLoading: Boolean,
    onChangeTo16x9Resolution: () -> Unit,
    onResetResolution: () -> Unit,
    onControlOverlayModeChanged: (OverlayControlMode) -> Unit,
    onToggleOverlay: () -> Unit
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onChangeTo16x9Resolution,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "调整为适配分辨率",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            OutlinedButton(
                onClick = onResetResolution,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                shape = MaterialTheme.shapes.small
            ) {
                Text(
                    text = "重置分辨率",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "悬浮窗模式",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = if (overlayControlMode == OverlayControlMode.ACCESSIBILITY)
                            "音量上下键同时按切换面板"
                        else
                            "点击悬浮球切换面板",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f)
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = overlayControlMode.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Switch(
                        checked = overlayControlMode == OverlayControlMode.ACCESSIBILITY,
                        onCheckedChange = { isAccessibility ->
                            onControlOverlayModeChanged(
                                if (isAccessibility) OverlayControlMode.ACCESSIBILITY
                                else OverlayControlMode.FLOAT_BALL
                            )
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Button(
            onClick = onToggleOverlay,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isShowControlOverlay)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary
            ),
            shape = MaterialTheme.shapes.large,
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp
                )
            } else {
                Text(
                    text = if (isShowControlOverlay) "关闭操作面板" else "打开操作面板",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartupUpdateDialog(
    appVersion: String?,
    resourceVersion: String?,
    appReleaseNote: String?,
    resourceReleaseNote: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    var showReleaseNotes by remember { mutableStateOf(false) }
    val hasReleaseNotes = !appReleaseNote.isNullOrBlank() || !resourceReleaseNote.isNullOrBlank()

    BasicAlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "发现新版本",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                // 版本展示卡片
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        appVersion?.let {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("应用版本", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        resourceVersion?.let {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("资源版本", style = MaterialTheme.typography.bodyMedium)
                                val display = ResourceDownloader.formatVersionForDisplay(it)
                                Text(
                                    display,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (hasReleaseNotes) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showReleaseNotes = !showReleaseNotes }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (showReleaseNotes) "收起更新日志" else "查看更新日志",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(
                            imageVector = if (showReleaseNotes) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    AnimatedVisibility(visible = showReleaseNotes) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 200.dp)
                                .padding(top = 8.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            appReleaseNote?.takeIf { it.isNotBlank() }?.let { note ->
                                Text(
                                    "应用更新内容:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                MarkdownText(
                                    markdown = note,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            resourceReleaseNote?.takeIf { it.isNotBlank() }?.let { note ->
                                Text(
                                    "资源更新内容:",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                MarkdownText(
                                    markdown = note,
                                    modifier = Modifier.fillMaxWidth(),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.large,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50) // 保持原有的下载绿色，或根据需要改为主题色
                        )
                    ) {
                        Text("下载并更新")
                    }

                    TextButton(
                        onClick = onDismiss,
                        shape = MaterialTheme.shapes.large
                    ) {
                        Text("稍后更新", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

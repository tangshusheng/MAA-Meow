package com.aliothmoon.maameow.presentation.view.settings

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.BuildConfig
import com.aliothmoon.maameow.data.model.update.UpdateChannel
import com.aliothmoon.maameow.domain.models.RemoteBackend
import com.aliothmoon.maameow.domain.service.LogExportService
import com.aliothmoon.maameow.domain.service.ResourceInitService
import com.aliothmoon.maameow.domain.state.ResourceInitState
import com.aliothmoon.maameow.presentation.components.InfoCard
import com.aliothmoon.maameow.presentation.components.ReInitializeConfirmDialog
import com.aliothmoon.maameow.presentation.components.ResourceInitDialog
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.viewmodel.SettingsViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun SettingsView(
    navController: NavController,
    viewModel: SettingsViewModel = koinViewModel(),
    resourceInitService: ResourceInitService = koinInject(),
    logExportService: LogExportService = koinInject()
) {
    val resourceInitState by resourceInitService.state.collectAsStateWithLifecycle()
    val debugMode by viewModel.debugMode.collectAsStateWithLifecycle()
    val autoCheckUpdate by viewModel.autoCheckUpdate.collectAsStateWithLifecycle()
    val startupBackend by viewModel.startupBackend.collectAsStateWithLifecycle()
    val skipShizukuCheck by viewModel.skipShizukuCheck.collectAsStateWithLifecycle()
    val updateChannel by viewModel.updateChannel.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    var showReInitConfirm by remember { mutableStateOf(false) }
    var showDebugModeConfirm by remember { mutableStateOf(false) }

    if (showReInitConfirm) {
        ReInitializeConfirmDialog(
            onConfirm = {
                showReInitConfirm = false
                coroutineScope.launch {
                    resourceInitService.reInitialize()
                }
            },
            onDismiss = { showReInitConfirm = false }
        )
    }

    if (showDebugModeConfirm) {
        AlertDialog(
            onDismissRequest = { showDebugModeConfirm = false },
            title = {
                Text(
                    text = "启用调试模式",
                    style = MaterialTheme.typography.titleMedium
                )
            },
            text = {
                Text(
                    text = "启用调试模式后将重启服务以记录详细日志。\n\n请在重启后重新操作以复现问题，相关日志将被完整记录。",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(onClick = {
                    showDebugModeConfirm = false
                    viewModel.setDebugMode(true)
                }) {
                    Text("确认重启")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDebugModeConfirm = false }) {
                    Text("取消")
                }
            }
        )
    }

    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    if (resourceInitState is ResourceInitState.Extracting) {
        ResourceInitDialog(
            state = resourceInitState,
            onRetry = {}
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "设置",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.navigateUp() }
            )
        }
    ) { paddingValues ->
        val tertiaryContent = MaterialTheme.colorScheme.onTertiaryContainer
        val primaryContent = MaterialTheme.colorScheme.onPrimaryContainer

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // 更新管理
            item {
                val color = tertiaryContent
                InfoCard(
                    title = "",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = color
                ) {
                    Text(
                        text = "更新管理",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = color,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    SettingClickItem("重新初始化资源", "从内置资源包重新解压", color) {
                        showReInitConfirm = true
                    }
                    SettingsDivider(color)
                    SettingSwitchItem(
                        title = "启动时检查更新",
                        description = "启动应用时自动检查应用和资源更新",
                        contentColor = color,
                        checked = autoCheckUpdate,
                        onCheckedChange = { viewModel.setAutoCheckUpdate(it) }
                    )
                    SettingsDivider(color)
                    SettingChannelItem(
                        contentColor = color,
                        selectedChannel = updateChannel,
                        onChannelSelected = { viewModel.setUpdateChannel(it) }
                    )
                }
            }

            // 日志
            item {
                val color = MaterialTheme.colorScheme.onSecondaryContainer
                InfoCard(
                    title = "",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = color
                ) {
                    Text(
                        text = "日志",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = color,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    SettingClickItem("历史日志", "查看任务执行日志", color) {
                        navController.navigate("log_history")
                    }
                    SettingsDivider(color)
                    SettingClickItem("错误日志", "查看应用异常和错误记录", color) {
                        navController.navigate("error_log")
                    }
                    SettingsDivider(color)
                    SettingClickItem("导出日志压缩包", "打包所有日志为 ZIP 文件分享", color) {
                        coroutineScope.launch {
                            val intent = logExportService.exportAllLogs()
                            if (intent != null) {
                                context.startActivity(Intent.createChooser(intent, "导出日志"))
                            }
                        }
                    }
                    SettingsDivider(color)
                    SettingSwitchItem(
                        title = "调试模式",
                        description = "启用后记录详细日志信息",
                        contentColor = color,
                        checked = debugMode,
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showDebugModeConfirm = true
                            } else {
                                viewModel.setDebugMode(false)
                            }
                        }
                    )
                }
            }

            // 其他设置
            item {
                val color = MaterialTheme.colorScheme.onSurfaceVariant
                InfoCard(
                    title = "",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = color
                ) {
                    Text(
                        text = "其他设置",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = color,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    SettingRemoteBackendItem(
                        contentColor = color,
                        selectedBackend = startupBackend,
                        onBackendSelected = { viewModel.setStartupBackend(it) }
                    )
                    SettingsDivider(color)
                    SettingSwitchItem(
                        title = "跳过 Shizuku 检查",
                        contentColor = color,
                        checked = skipShizukuCheck,
                        enabled = startupBackend == RemoteBackend.SHIZUKU,
                        onCheckedChange = { viewModel.setSkipShizukuCheck(it) }
                    )
                }
            }

            // 关于
            item {
                InfoCard(
                    title = "",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = primaryContent
                ) {
                    Text(
                        text = "关于",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Medium,
                        color = primaryContent,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    SettingInfoRow("版本", BuildConfig.VERSION_NAME, primaryContent)
                    SettingsDivider(primaryContent)
                    SettingInfoRow("开发者", "Aliothmoon", primaryContent)
                    SettingsDivider(primaryContent)
                    SettingClickItem(
                        title = "问题反馈 QQ 群",
                        description = "遇到问题或有建议？欢迎加群交流反馈",
                        contentColor = primaryContent
                    ) {
                        uriHandler.openUri("https://qm.qq.com/q/j4CFbeDQXu")
                    }
                    SettingsDivider(primaryContent)
                    Text(
                        text = "⭐ 喜欢就给个 Star 吧",
                        style = MaterialTheme.typography.bodyMedium,
                        color = primaryContent,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                uriHandler.openUri("https://github.com/Aliothmoon/MAA-Meow")
                            }
                            .padding(vertical = 8.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SettingClickItem(
    title: String,
    description: String,
    contentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun SettingSwitchItem(
    title: String,
    description: String? = null,
    contentColor: Color,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor.copy(alpha = if (enabled) 1f else 0.6f)
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = if (enabled) 0.7f else 0.4f)
                )
            }

        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun SettingInfoRow(
    label: String,
    value: String,
    contentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun SettingsDivider(contentColor: Color) {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 8.dp),
        color = contentColor.copy(alpha = 0.1f)
    )
}

@Composable
private fun SettingChannelItem(
    contentColor: Color,
    selectedChannel: UpdateChannel,
    onChannelSelected: (UpdateChannel) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "更新渠道",
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            Text(
                text = "选择接收稳定版或公测版更新",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UpdateChannel.entries.forEach { channel ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .selectable(
                            selected = channel == selectedChannel,
                            onClick = { onChannelSelected(channel) },
                            role = Role.RadioButton
                        )
                ) {
                    RadioButton(
                        selected = channel == selectedChannel,
                        onClick = null
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = channel.displayName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRemoteBackendItem(
    contentColor: Color,
    selectedBackend: RemoteBackend,
    onBackendSelected: (RemoteBackend) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "启动模式",
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor
        )
        RemoteBackend.entries.forEach { backend ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .selectable(
                        selected = backend == selectedBackend,
                        onClick = { onBackendSelected(backend) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = backend == selectedBackend,
                    onClick = null
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = backend.display,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor
                )
            }
        }
    }
}

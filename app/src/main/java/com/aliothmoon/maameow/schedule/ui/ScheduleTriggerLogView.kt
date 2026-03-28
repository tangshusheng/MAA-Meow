package com.aliothmoon.maameow.schedule.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.model.TriggerLogEntry
import com.aliothmoon.maameow.schedule.service.ScheduleTriggerLogger.TriggerLogSummary
import org.koin.androidx.compose.koinViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ScheduleTriggerLogView(
    navController: NavController,
    viewModel: ScheduleTriggerLogViewModel = koinViewModel(),
) {
    val summaries by viewModel.summaries.collectAsStateWithLifecycle()
    val detail by viewModel.detail.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }
    var deleteConfirmFileName by remember { mutableStateOf<String?>(null) }

    // 详情模式
    if (detail.isNotEmpty()) {
        BackHandler { viewModel.clearDetail() }
        DetailView(
            entries = detail,
            onBack = { viewModel.clearDetail() }
        )
        return
    }

    // 列表模式
    Scaffold(
        topBar = {
            TopAppBar(
                title = "触发日志",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.popBackStack() },
                actions = {
                    if (summaries.isNotEmpty()) {
                        IconButton(onClick = { showClearConfirm = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "清空日志")
                        }
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            summaries.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "暂无触发记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(summaries, key = { it.fileName }) { summary ->
                        SummaryCard(
                            summary = summary,
                            onClick = { viewModel.loadDetail(summary.fileName) },
                            onDelete = { deleteConfirmFileName = summary.fileName }
                        )
                    }
                }
            }
        }

        if (showClearConfirm) {
            AlertDialog(
                onDismissRequest = { showClearConfirm = false },
                title = { Text("清空日志") },
                text = { Text("确定要清空所有触发日志吗？") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.clearAll()
                        showClearConfirm = false
                    }) { Text("清空", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { showClearConfirm = false }) { Text("取消") }
                }
            )
        }

        if (deleteConfirmFileName != null) {
            AlertDialog(
                onDismissRequest = { deleteConfirmFileName = null },
                title = { Text("删除日志") },
                text = { Text("确定要删除该触发日志吗？") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteLog(deleteConfirmFileName!!)
                        deleteConfirmFileName = null
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = {
                    TextButton(onClick = { deleteConfirmFileName = null }) { Text("取消") }
                }
            )
        }
    }
}

// ==================== 列表卡片 ====================

@Composable
private fun SummaryCard(
    summary: TriggerLogSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val resultColor = summary.footer?.result?.let { resultColor(it) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant
    val resultLabel = summary.footer?.result?.let { resultLabel(it) } ?: "进行中..."

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = summary.header.strategyName,
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = resultLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = resultColor
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = "预定: ${formatTime(summary.header.scheduledTimeMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "触发: ${formatTime(summary.header.actualTimeMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (summary.footer?.message != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = summary.footer.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ==================== 详情视图 ====================

@Composable
private fun DetailView(
    entries: List<TriggerLogEntry>,
    onBack: () -> Unit,
) {
    val header = entries.firstOrNull() as? TriggerLogEntry.Header

    Scaffold(
        topBar = {
            TopAppBar(
                title = header?.strategyName ?: "触发详情",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack,
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(entries) { entry ->
                when (entry) {
                    is TriggerLogEntry.Header -> {
                        Text(
                            text = "预定: ${formatTimeFull(entry.scheduledTimeMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "触发: ${formatTimeFull(entry.actualTimeMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    }

                    is TriggerLogEntry.Log -> {
                        Row {
                            Text(
                                text = formatTimeShort(entry.time),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = entry.message,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }

                    is TriggerLogEntry.Footer -> {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row {
                            Text(
                                text = formatTimeShort(entry.time),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "结果: ${resultLabel(entry.result)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = resultColor(entry.result)
                            )
                            if (entry.message != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = entry.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==================== 工具方法 ====================

@Composable
private fun resultColor(result: ExecutionResult) = when (result) {
    ExecutionResult.STARTED -> MaterialTheme.colorScheme.primary
    ExecutionResult.SKIPPED_BUSY,
    ExecutionResult.SKIPPED_LOCKED,
    ExecutionResult.CANCELLED -> MaterialTheme.colorScheme.tertiary

    ExecutionResult.FAILED_VALIDATION,
    ExecutionResult.FAILED_START,
    ExecutionResult.FAILED_UI_LAUNCH -> MaterialTheme.colorScheme.error
}

private fun resultLabel(result: ExecutionResult) = when (result) {
    ExecutionResult.STARTED -> "已启动"
    ExecutionResult.FAILED_VALIDATION -> "校验失败"
    ExecutionResult.FAILED_START -> "启动失败"
    ExecutionResult.FAILED_UI_LAUNCH -> "拉起失败"
    ExecutionResult.SKIPPED_BUSY -> "繁忙跳过"
    ExecutionResult.SKIPPED_LOCKED -> "锁屏跳过"
    ExecutionResult.CANCELLED -> "已取消"
}

private val dateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss")
private val fullDateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
private val shortTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

private fun formatTime(epochMs: Long): String {
    if (epochMs <= 0) return "--"
    return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(dateTimeFormatter)
}

private fun formatTimeFull(epochMs: Long): String {
    if (epochMs <= 0) return "--"
    return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(fullDateTimeFormatter)
}

private fun formatTimeShort(epochMs: Long): String {
    if (epochMs <= 0) return "--"
    return Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(shortTimeFormatter)
}

package com.aliothmoon.maameow.schedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.aliothmoon.maameow.presentation.components.TopAppBar
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipContent
import com.aliothmoon.maameow.presentation.components.tip.ExpandableTipIcon
import org.koin.androidx.compose.koinViewModel
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScheduleEditView(
    navController: NavController,
    strategyId: String?,
    viewModel: ScheduleEditViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showTimePicker by remember { mutableStateOf(false) }
    var editingTime by remember { mutableStateOf<LocalTime?>(null) }

    LaunchedEffect(strategyId) {
        viewModel.loadStrategy(strategyId)
    }

    var showPermissionDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.saveSuccess) {
        if (state.saveSuccess) {
            if (state.needBatteryOptimization || state.needExactAlarm) {
                showPermissionDialog = true
            } else {
                navController.popBackStack()
            }
        }
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onDismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = if (state.isNew) "新建策略" else "编辑策略",
                navigationIcon = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = { navController.popBackStack() },
                actions = {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(24.dp)
                                .padding(end = 16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        TextButton(onClick = { viewModel.onSave() }) {
                            Text("保存")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(padding)
        ) {
            item {
                SectionHeader("基本信息")
            }
            if (!state.isNew && state.strategyId != null) {
                item {
                    Text(
                        text = "ID: ${state.strategyId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            }
            item {
                OutlinedTextField(
                    value = state.name,
                    onValueChange = viewModel::onNameChanged,
                    label = { Text("策略名称") },
                    placeholder = { Text("如：日常任务") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            item {
                SectionHeader("执行日期")
            }
            item {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val chipColors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                    val allSelected = DayOfWeek.entries.all { it in state.daysOfWeek }
                    FilterChip(
                        selected = allSelected,
                        onClick = { viewModel.onToggleAllDays() },
                        label = { Text("每天") },
                        colors = chipColors
                    )
                    DayOfWeek.entries.forEach { day ->
                        FilterChip(
                            selected = day in state.daysOfWeek,
                            onClick = { viewModel.onToggleDay(day) },
                            label = { Text(dayDisplayName(day)) },
                            colors = chipColors
                        )
                    }
                }
            }

            item {
                SectionHeader("执行时间")
            }
            item {
                val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.executionTimes.forEach { time ->
                        InputChip(
                            selected = false,
                            onClick = {
                                editingTime = time
                                showTimePicker = true
                            },
                            label = { Text(time.format(timeFormatter)) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { viewModel.onRemoveTime(time) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        )
                    }
                    AssistChip(
                        onClick = {
                            editingTime = null
                            showTimePicker = true
                        },
                        label = { Text("添加时间") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            item {
                SectionHeader("任务配置")
            }
            item {
                if (state.profiles.isEmpty()) {
                    Text(
                        text = "请先在操作面板中创建任务配置",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        state.profiles.forEach { profile ->
                            FilterChip(
                                selected = profile.id == state.selectedProfileId,
                                onClick = { viewModel.onSelectProfile(profile.id) },
                                label = { Text(profile.name) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                )
                            )
                        }
                    }
                    // 显示选中 Profile 的已启用任务摘要
                    val selectedProfile = state.profiles.find { it.id == state.selectedProfileId }
                    val enabledTasks = selectedProfile?.chain
                        ?.filter { it.enabled }
                        ?.joinToString("、") { it.name }
                    if (!enabledTasks.isNullOrEmpty()) {
                        Text(
                            text = "已启用: $enabledTasks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                        )
                    }
                }
            }

            item {
                SectionHeader("高级选项")
                val (expanded, setExpanded) = remember { mutableStateOf(false) }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("强制启动", style = MaterialTheme.typography.bodyLarge)
                        ExpandableTipIcon(
                            modifier = Modifier.padding(start = 8.dp),
                            expanded = expanded,
                            onExpandedChange = { setExpanded(it) })
                    }
                    Switch(
                        checked = state.forceStart,
                        onCheckedChange = { viewModel.onForceStartChanged(it) }
                    )
                }
                ExpandableTipContent(
                    visible = expanded,
                    tipText = "强制启动任务，会中断正在运行的游戏和任务",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }

    if (showTimePicker) {
        TimePickerDialog(
            initialTime = editingTime,
            onDismiss = { showTimePicker = false },
            onConfirm = { time ->
                val old = editingTime
                if (old != null) {
                    viewModel.onReplaceTime(old, time)
                } else {
                    viewModel.onAddTime(time)
                }
                showTimePicker = false
            }
        )
    }

    if (showPermissionDialog) {
        val context = LocalContext.current
        val tips = buildList {
            if (state.needBatteryOptimization) add("关闭电池优化")
            if (state.needExactAlarm) add("允许精确闹钟")
        }
        AlertDialog(
            onDismissRequest = {
                showPermissionDialog = false
                navController.popBackStack()
            },
            title = { Text("权限提示") },
            text = {
                Text("为确保定时任务正常触发，请前往系统设置${tips.joinToString("、")}。")
            },
            confirmButton = {
                TextButton(onClick = {
                    if (state.needBatteryOptimization) {
                        runCatching {
                            context.startActivity(
                                Intent(
                                    android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                    android.net.Uri.parse("package:${context.packageName}")
                                )
                            )
                        }
                    } else if (state.needExactAlarm && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        runCatching {
                            context.startActivity(
                                Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                            )
                        }
                    }
                    showPermissionDialog = false
                    navController.popBackStack()
                }) { Text("前往设置") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showPermissionDialog = false
                    navController.popBackStack()
                }) { Text("稍后") }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialTime: LocalTime? = null,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime?.hour ?: 0,
        initialMinute = initialTime?.minute ?: 0
    )
    val configuration = LocalConfiguration.current
    var showDial by remember { mutableStateOf(configuration.screenHeightDp >= 400) }

    BasicAlertDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "选择时间",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 20.dp)
                )
                if (showDial) {
                    TimePicker(state = timePickerState)
                } else {
                    TimeInput(state = timePickerState)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { showDial = !showDial }) {
                        Text(if (showDial) "键盘输入" else "表盘选择")
                    }
                    Row {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        TextButton(onClick = {
                            onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
                        }) { Text("确认") }
                    }
                }
            }
        }
    }
}

private fun dayDisplayName(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY -> "周一"
    DayOfWeek.TUESDAY -> "周二"
    DayOfWeek.WEDNESDAY -> "周三"
    DayOfWeek.THURSDAY -> "周四"
    DayOfWeek.FRIDAY -> "周五"
    DayOfWeek.SATURDAY -> "周六"
    DayOfWeek.SUNDAY -> "周日"
}

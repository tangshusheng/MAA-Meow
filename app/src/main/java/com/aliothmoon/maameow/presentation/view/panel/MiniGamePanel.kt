package com.aliothmoon.maameow.presentation.view.panel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.aliothmoon.maameow.presentation.viewmodel.MiniGameViewModel
import org.koin.compose.koinInject

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MiniGamePanel(
    modifier: Modifier = Modifier,
    viewModel: MiniGameViewModel = koinInject()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val miniGames by viewModel.miniGames.collectAsStateWithLifecycle()

    val tip = viewModel.getCurrentTip()
    val isUnsupported = viewModel.isCurrentUnsupported()

    val tabTitleTextStyle = MaterialTheme.typography.bodySmall.copy(
        fontSize = 13.sp,
        lineHeight = 16.sp
    )
    val tabSubtitleTextStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.5.sp,
        lineHeight = 12.sp
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 4.dp)),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text(
                text = "小游戏",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        // 任务选择 - 卡片网格
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "选择任务",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                miniGames.chunked(2).forEach { rowGames ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        rowGames.forEach { game ->
                            val selected = state.selectedTaskName == game.value
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (game.isUnsupported) {
                                    MaterialTheme.colorScheme.errorContainer
                                } else if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surface
                                },
                                border = BorderStroke(
                                    width = 1.dp,
                                    color = if (game.isUnsupported && selected) {
                                        MaterialTheme.colorScheme.error
                                    } else if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.outlineVariant
                                    }
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .clickable { viewModel.onTaskSelected(game.value) }
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 8.dp, vertical = 6.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = game.display,
                                        style = tabTitleTextStyle,
                                        color = if (game.isUnsupported) {
                                            MaterialTheme.colorScheme.onErrorContainer
                                        } else if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (!game.isOpen && game.utcStartTime != 0L) {
                                        Spacer(modifier = Modifier.height(1.dp))
                                        Text(
                                            text = "未开放",
                                            style = tabSubtitleTextStyle,
                                            color = if (selected) {
                                                MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                            } else {
                                                MaterialTheme.colorScheme.outline
                                            },
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            modifier = Modifier.fillMaxWidth(),
                                        )
                                    }
                                }
                            }
                        }
                        if (rowGames.size < 2) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Tip 提示
        if (tip.isNotBlank()) {
            item {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isUnsupported) {
                        MaterialTheme.colorScheme.errorContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
                    border = if (isUnsupported) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    } else {
                        null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = tip,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isUnsupported) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        }

        // 隐秘战线配置
        if (viewModel.isSecretFront) {
            item {
                HorizontalDivider()
            }

            // 结局选择
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "结局",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        MiniGameViewModel.ENDINGS.forEach { ending ->
                            FilterChip(
                                selected = state.selectedEnding == ending,
                                onClick = { viewModel.onEndingSelected(ending) },
                                label = {
                                    Text(
                                        text = ending,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                ),
                                shape = RoundedCornerShape(8.dp),
                            )
                        }
                    }
                }
            }

            // 事件选择 - 卡片网格
            item {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "事件",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    MiniGameViewModel.EVENTS.chunked(2).forEach { rowEvents ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowEvents.forEach { (value, display) ->
                                val selected = state.selectedEvent == value
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    },
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.outlineVariant
                                        }
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .heightIn(min = 40.dp)
                                        .clickable { viewModel.onEventSelected(value) }
                                ) {
                                    Text(
                                        text = display,
                                        style = tabTitleTextStyle,
                                        color = if (selected) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp, vertical = 8.dp),
                                    )
                                }
                            }
                            if (rowEvents.size < 2) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // 状态消息
        if (state.statusMessage.isNotBlank()) {
            item {
                Text(
                    text = state.statusMessage,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

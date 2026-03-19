package com.aliothmoon.maameow.schedule.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aliothmoon.maameow.schedule.model.CountdownState
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import com.aliothmoon.maameow.schedule.service.ScheduleExecutionService

@Composable
fun CountdownDialog(
    state: CountdownState.Counting,
    onCancel: () -> Unit,
    onStartNow: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { /* 不可关闭 */ },
        title = { Text("定时任务即将执行") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = state.strategyName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(24.dp))
                Box(contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        progress = { state.remainingSeconds / 60f },
                        modifier = Modifier.size(120.dp),
                        strokeWidth = 8.dp,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Text(
                        text = "${state.remainingSeconds}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "秒后自动开始",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onStartNow) {
                Text("立即开始")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onCancel) {
                Text("取消")
            }
        }
    )
}

/** 发送取消倒计时的 Intent 到 ScheduleExecutionService */
fun sendCancelIntent(context: Context) {
    val intent = Intent(context, ScheduleExecutionService::class.java).apply {
        action = ScheduleAlarmManager.ACTION_SCHEDULE_CANCEL
    }
    context.startService(intent)
}

/** 发送跳过倒计时的 Intent 到 ScheduleExecutionService */
fun sendSkipCountdownIntent(context: Context) {
    val intent = Intent(context, ScheduleExecutionService::class.java).apply {
        action = ScheduleExecutionService.ACTION_SKIP_COUNTDOWN
    }
    context.startService(intent)
}

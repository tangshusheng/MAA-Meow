package com.aliothmoon.maameow.schedule.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import timber.log.Timber

/**
 * 接收闹钟触发和取消操作
 *
 * ACTION_SCHEDULE_TRIGGER: 启动 ScheduleExecutionService
 * ACTION_SCHEDULE_CANCEL: 通知 ScheduleExecutionService 取消当前倒计时
 */
class ScheduleReceiver : BroadcastReceiver() {

    companion object {
        private const val EXECUTION_SERVICE_CLASS =
            "com.aliothmoon.maameow.schedule.service.ScheduleExecutionService"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val strategyId = intent.getStringExtra(ScheduleAlarmManager.EXTRA_STRATEGY_ID) ?: return
        val scheduledTime = intent.getLongExtra(ScheduleAlarmManager.EXTRA_SCHEDULED_TIME, 0L)

        when (intent.action) {
            ScheduleAlarmManager.ACTION_SCHEDULE_TRIGGER -> {
                Timber.i("Schedule alarm triggered for strategy: %s", strategyId)
                val serviceIntent = Intent().apply {
                    setClassName(context, EXECUTION_SERVICE_CLASS)
                    action = ScheduleAlarmManager.ACTION_SCHEDULE_TRIGGER
                    putExtra(ScheduleAlarmManager.EXTRA_STRATEGY_ID, strategyId)
                    putExtra(ScheduleAlarmManager.EXTRA_SCHEDULED_TIME, scheduledTime)
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
            ScheduleAlarmManager.ACTION_SCHEDULE_CANCEL -> {
                Timber.i("Schedule cancel requested for strategy: %s", strategyId)
                val serviceIntent = Intent().apply {
                    setClassName(context, EXECUTION_SERVICE_CLASS)
                    action = ScheduleAlarmManager.ACTION_SCHEDULE_CANCEL
                    putExtra(ScheduleAlarmManager.EXTRA_STRATEGY_ID, strategyId)
                }
                context.startService(serviceIntent)
            }
        }
    }
}

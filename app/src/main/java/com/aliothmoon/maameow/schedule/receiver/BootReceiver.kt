package com.aliothmoon.maameow.schedule.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.service.ScheduleAlarmManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext
import timber.log.Timber

/**
 * 设备重启后恢复所有定时任务闹钟
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        Timber.i("Boot completed, rescheduling all strategies")

        val repository: ScheduleStrategyRepository = GlobalContext.get().get()
        val alarmManager = ScheduleAlarmManager(context)

        CoroutineScope(Dispatchers.IO).launch {
            // 等待 repository 从 DataStore 完成初始化加载 (isLoaded 变为 true)
            repository.isLoaded.filter { it }.first()

            val strategies = repository.strategies.value
            alarmManager.rescheduleAll(strategies)
            Timber.i("Boot reschedule complete: %d strategies", strategies.size)
        }
    }
}

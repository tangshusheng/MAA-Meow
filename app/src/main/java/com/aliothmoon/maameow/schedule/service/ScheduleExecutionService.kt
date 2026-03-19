package com.aliothmoon.maameow.schedule.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.CountDownTimer
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.aliothmoon.maameow.R
import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.data.model.LogLevel
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.service.MaaCompositionService
import com.aliothmoon.maameow.domain.service.RuntimeLogCenter
import com.aliothmoon.maameow.domain.state.MaaExecutionState
import com.aliothmoon.maameow.domain.usecase.BuildTaskParamsUseCase
import com.aliothmoon.maameow.manager.RemoteServiceManager
import com.aliothmoon.maameow.schedule.data.ScheduleStrategyRepository
import com.aliothmoon.maameow.schedule.model.CountdownState
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.schedule.receiver.ScheduleReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.koin.android.ext.android.inject
import timber.log.Timber
import java.io.File

/**
 * 定时任务执行前台服务
 */
class ScheduleExecutionService : Service() {

    companion object {
        private const val TAG = "ScheduleExec"
        private const val NOTIFICATION_ID = 9001
        private const val RESULT_NOTIFICATION_ID = 9002
        private const val CHANNEL_ID = "schedule_execution"
        private const val COUNTDOWN_SECONDS = 60

        const val ACTION_SKIP_COUNTDOWN = "com.aliothmoon.maameow.SCHEDULE_SKIP"

        /** 供 UI 层监听倒计时状态 */
        val countdownState = MutableStateFlow<CountdownState>(CountdownState.Idle)
    }

    private val repository: ScheduleStrategyRepository by inject()
    private val compositionService: MaaCompositionService by inject()
    private val buildTaskParams: BuildTaskParamsUseCase by inject()
    private val pathConfig: MaaPathConfig by inject()
    private val taskChainState: TaskChainState by inject()
    private val runtimeLogCenter: RuntimeLogCenter by inject()

    private var countdownTimer: CountDownTimer? = null
    private var currentStrategyId: String? = null
    private var currentScheduledTimeMs: Long = 0L
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ScheduleAlarmManager.ACTION_SCHEDULE_TRIGGER -> {
                val strategyId = intent.getStringExtra(ScheduleAlarmManager.EXTRA_STRATEGY_ID)
                val scheduledTime = intent.getLongExtra(ScheduleAlarmManager.EXTRA_SCHEDULED_TIME, 0L)
                if (strategyId != null) {
                    handleTrigger(strategyId, scheduledTime)
                } else {
                    Timber.w("$TAG: 收到触发指令但缺少 strategyId")
                    stopSelf()
                }
            }

            ScheduleAlarmManager.ACTION_SCHEDULE_CANCEL -> {
                cancelCountdown()
            }

            ACTION_SKIP_COUNTDOWN -> {
                skipCountdown()
            }

            else -> {
                Timber.w("$TAG: 未知 action: %s", intent?.action)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun handleTrigger(strategyId: String, scheduledTimeMs: Long) {
        // 并发冲突检查：如果已经在倒计时或执行中，跳过
        val currentS = countdownState.value
        if (currentS !is CountdownState.Idle) {
            Timber.w("$TAG: 系统繁忙 (state=$currentS)，跳过策略 [%s]", strategyId)
            recordSystemBusy(strategyId, scheduledTimeMs)
            return
        }

        startExecution(strategyId, scheduledTimeMs)
    }

    private fun recordSystemBusy(strategyId: String, scheduledTimeMs: Long) {
        serviceScope.launch {
            val strategy = repository.getById(strategyId)
            val logger = createLogger(strategyId, strategy?.name ?: "unknown", scheduledTimeMs)
            logger.error("触发", "系统繁忙（已有任务在倒计时或执行），跳过本次执行")
            val log = logger.endSession(ExecutionResult.SKIPPED_SYSTEM_BUSY)
            repository.recordExecution(log)

            if (strategy != null) {
                ScheduleAlarmManager(this@ScheduleExecutionService).scheduleNext(strategy)
            }
        }
    }

    private fun startExecution(strategyId: String, scheduledTimeMs: Long) {
        currentStrategyId = strategyId
        currentScheduledTimeMs = scheduledTimeMs

        // 适配 Android 14+ 前台服务类型
        val notification = buildCountdownNotification("准备执行定时任务...", COUNTDOWN_SECONDS)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        startCountdown(strategyId, scheduledTimeMs)
    }

    private fun startCountdown(strategyId: String, scheduledTimeMs: Long) {
        val strategy = repository.strategies.value.find { it.id == strategyId }
        val strategyName = strategy?.name ?: "未知策略"

        countdownState.value = CountdownState.Counting(strategyName, COUNTDOWN_SECONDS)

        countdownTimer = object : CountDownTimer(COUNTDOWN_SECONDS * 1000L, 1000L) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = (millisUntilFinished / 1000).toInt()
                countdownState.value = CountdownState.Counting(strategyName, seconds)
                updateNotification("定时任务「$strategyName」将在 ${seconds}s 后执行", seconds)
            }

            override fun onFinish() {
                countdownState.value = CountdownState.Executing
                serviceScope.launch {
                    executeStrategy(strategyId, scheduledTimeMs)
                }
            }
        }.start()
    }

    private fun cancelCountdown() {
        countdownTimer?.cancel()
        countdownTimer = null
        countdownState.value = CountdownState.Idle

        val strategyId = currentStrategyId
        if (strategyId != null) {
            serviceScope.launch {
                val strategy = repository.getById(strategyId)
                val logger = createLogger(strategyId, strategy?.name ?: "unknown", currentScheduledTimeMs)
                logger.info("取消", "用户取消了定时任务执行")
                val log = logger.endSession(ExecutionResult.CANCELLED_BY_USER)
                repository.recordExecution(log)

                if (strategy != null) {
                    ScheduleAlarmManager(this@ScheduleExecutionService).scheduleNext(strategy)
                }
                stopSelf()
            }
        } else {
            stopSelf()
        }
    }

    private fun skipCountdown() {
        countdownTimer?.cancel()
        countdownTimer = null
        countdownState.value = CountdownState.Executing

        val strategyId = currentStrategyId
        if (strategyId != null) {
            serviceScope.launch {
                executeStrategy(strategyId, currentScheduledTimeMs)
            }
        }
    }

    private suspend fun executeStrategy(strategyId: String, scheduledTimeMs: Long) {
        val strategy = repository.getById(strategyId)
        if (strategy == null) {
            Timber.w("$TAG: 策略未找到: %s", strategyId)
            countdownState.value = CountdownState.Idle
            stopSelf()
            return
        }

        val logger = createLogger(strategyId, strategy.name, scheduledTimeMs)
        logger.info("触发", "定时任务触发: 策略「${strategy.name}」")

        try {
            acquireWakeLock()

            // 1. 检查 Shizuku 连接
            logger.info("连接", "检查 Shizuku 连接状态")
            val connected = checkShizukuConnection(logger)
            if (!connected) {
                val log = logger.endSession(ExecutionResult.SKIPPED_NO_SERVICE, errorMessage = "Shizuku 服务不可用")
                repository.recordExecution(log)
                showResultNotification("定时任务失败", "「${strategy.name}」: Shizuku 服务不可用")
                runtimeLogCenter.startSession(listOf("定时任务"))
                runtimeLogCenter.append("定时任务「${strategy.name}」: Shizuku 服务不可用", LogLevel.ERROR)
                runtimeLogCenter.endSession("SCHEDULE_SKIPPED")
                return
            }

            // 2. 检查是否有任务正在运行 (MaaCompositionService 层面)
            logger.info("状态", "检查任务运行状态")
            val currentState = compositionService.state.value
            if (currentState == MaaExecutionState.RUNNING || currentState == MaaExecutionState.STARTING) {
                logger.warn("状态", "MaaCore 正在运行其他任务，跳过本次定时执行")
                val log = logger.endSession(ExecutionResult.SKIPPED_BUSY, errorMessage = "有任务正在运行")
                repository.recordExecution(log)
                showResultNotification("定时任务跳过", "「${strategy.name}」: 有任务正在运行")
                runtimeLogCenter.startSession(listOf("定时任务"))
                runtimeLogCenter.append("定时任务「${strategy.name}」: 有任务正在运行，跳过", LogLevel.WARNING)
                runtimeLogCenter.endSession("SCHEDULE_SKIPPED")
                return
            }

            // 3. 从 Profile 实时读取任务链
            val profile = taskChainState.profiles.value.find { it.id == strategy.profileId }
            if (profile == null) {
                logger.error("配置", "关联的任务配置已被删除 (profileId=${strategy.profileId})")
                val log = logger.endSession(ExecutionResult.FAILED, errorMessage = "关联的任务配置已被删除")
                repository.recordExecution(log)
                showResultNotification("定时任务失败", "「${strategy.name}」: 关联的任务配置已被删除")
                runtimeLogCenter.startSession(listOf("定时任务"))
                runtimeLogCenter.append("定时任务「${strategy.name}」: 关联的任务配置已被删除", LogLevel.ERROR)
                runtimeLogCenter.endSession("SCHEDULE_FAILED")
                return
            }

            val chain = profile.chain
            val enabledNodes = chain.filter { it.enabled }.sortedBy { it.order }
            logger.info("配置", "加载配置「${profile.name}」: ${chain.size} 个任务节点, ${enabledNodes.size} 个已启用")

            if (enabledNodes.isEmpty()) {
                logger.warn("配置", "无启用的任务节点")
                val log = logger.endSession(ExecutionResult.FAILED, errorMessage = "无启用的任务节点")
                repository.recordExecution(log)
                showResultNotification("定时任务失败", "「${strategy.name}」: 无启用的任务节点")
                runtimeLogCenter.startSession(listOf("定时任务"))
                runtimeLogCenter.append("定时任务「${strategy.name}」: 无启用的任务节点", LogLevel.ERROR)
                runtimeLogCenter.endSession("SCHEDULE_FAILED")
                return
            }

            val taskParams = buildTaskParams.buildFrom(chain)
            val clientType = chain
                .firstNotNullOfOrNull { it.config as? WakeUpConfig }
                ?.clientType ?: "Official"

            logger.info("配置", "客户端类型: $clientType, 任务数: ${taskParams.size}")

            // 4. 更新通知
            updateNotification("正在执行「${strategy.name}」...", -1)

            // 5. 执行任务
            logger.info("执行", "调用 MaaCompositionService.start()")
            when (val result = compositionService.start(taskParams, clientType)) {
                is MaaCompositionService.StartResult.Success -> {
                    runtimeLogCenter.append("定时任务「${strategy.name}」触发执行（配置: ${profile.name}）", LogLevel.INFO)
                    logger.info("执行", "MaaCore 启动成功, version=${result.version}")
                    waitForCompletion(logger)
                    logger.info("完成", "任务执行完成")
                    val log = logger.endSession(ExecutionResult.SUCCESS, taskCount = taskParams.size)
                    repository.recordExecution(log)
                    showResultNotification("定时任务完成", "「${strategy.name}」执行成功")
                }

                else -> {
                    val errorMsg = "启动失败: $result"
                    logger.error("执行", errorMsg)
                    val log = logger.endSession(
                        ExecutionResult.FAILED,
                        taskCount = taskParams.size,
                        errorMessage = errorMsg
                    )
                    repository.recordExecution(log)
                    showResultNotification("定时任务失败", "「${strategy.name}」: $errorMsg")
                    runtimeLogCenter.append("定时任务「${strategy.name}」启动失败: $errorMsg", LogLevel.ERROR)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: 执行异常")
            logger.error("异常", "执行异常: ${e.message}")
            val log = logger.endSession(ExecutionResult.FAILED, errorMessage = e.message)
            repository.recordExecution(log)
            showResultNotification("定时任务异常", "「${strategy.name}」: ${e.message}")
            runtimeLogCenter.append("定时任务「${strategy.name}」执行异常: ${e.message}", LogLevel.ERROR)
        } finally {
            runCatching { compositionService.stopVirtualDisplay() }
            releaseWakeLock()
            scheduleNextAndStop(strategy)
        }
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MaaMeow:ScheduleExecution").apply {
            acquire(2 * 3600 * 1000L) // 最多持有 2 小时
        }
        Timber.i("$TAG: WakeLock acquired")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
        Timber.i("$TAG: WakeLock released")
    }

    private suspend fun checkShizukuConnection(logger: ScheduleExecutionLogger): Boolean {
        val currentState = RemoteServiceManager.state.value
        if (currentState is RemoteServiceManager.ServiceState.Connected) {
            return true
        }

        RemoteServiceManager.bind()
        return try {
            withTimeout(30_000) {
                RemoteServiceManager.state
                    .first { it is RemoteServiceManager.ServiceState.Connected }
            }
            true
        } catch (e: Exception) {
            logger.error("连接", "Shizuku 绑定超时 (30s)")
            false
        }
    }

    private suspend fun waitForCompletion(logger: ScheduleExecutionLogger) {
        compositionService.state
            .first { it == MaaExecutionState.IDLE || it == MaaExecutionState.ERROR }
        logger.info("等待", "任务状态已变为终态")
    }

    private fun scheduleNextAndStop(strategy: ScheduleStrategy) {
        ScheduleAlarmManager(this).scheduleNext(strategy)
        countdownState.value = CountdownState.Idle
        // 延迟停止前台服务，保持心跳存活避免远程服务被看门狗销毁
        updateNotification("执行完毕", -1)
        serviceScope.launch {
            delay(60_000L)
            stopSelf()
        }
    }

    // ---- 通知 ----

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "定时任务",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "定时任务执行通知"
        }
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(channel)
    }

    private fun buildCountdownNotification(text: String, seconds: Int): Notification {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle("定时任务")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)

        if (seconds >= 0) {
            builder.setProgress(COUNTDOWN_SECONDS, COUNTDOWN_SECONDS - seconds, false)
        }

        // 取消按钮
        val cancelIntent = Intent(this, ScheduleReceiver::class.java).apply {
            action = ScheduleAlarmManager.ACTION_SCHEDULE_CANCEL
            putExtra(ScheduleAlarmManager.EXTRA_STRATEGY_ID, currentStrategyId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            this,
            NOTIFICATION_ID + 1,
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, "取消", cancelPendingIntent)

        // 立即开始按钮
        val skipIntent = Intent(this, ScheduleExecutionService::class.java).apply {
            action = ACTION_SKIP_COUNTDOWN
        }
        val skipPendingIntent = PendingIntent.getService(
            this,
            NOTIFICATION_ID + 2,
            skipIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.addAction(0, "立即开始", skipPendingIntent)

        return builder.build()
    }

    private fun updateNotification(text: String, seconds: Int) {
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle("定时任务")
            .setContentText(text)
            .setOngoing(true)
            .setSilent(true)

        if (seconds >= 0) {
            builder.setProgress(COUNTDOWN_SECONDS, COUNTDOWN_SECONDS - seconds, false)

            val cancelIntent = Intent(this, ScheduleReceiver::class.java).apply {
                action = ScheduleAlarmManager.ACTION_SCHEDULE_CANCEL
                putExtra(ScheduleAlarmManager.EXTRA_STRATEGY_ID, currentStrategyId)
            }
            val cancelPendingIntent = PendingIntent.getBroadcast(
                this,
                NOTIFICATION_ID + 1,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "取消", cancelPendingIntent)

            val skipIntent = Intent(this, ScheduleExecutionService::class.java).apply {
                action = ACTION_SKIP_COUNTDOWN
            }
            val skipPendingIntent = PendingIntent.getService(
                this,
                NOTIFICATION_ID + 2,
                skipIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, "立即开始", skipPendingIntent)
        } else {
            builder.setProgress(0, 0, true)
        }

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    private fun showResultNotification(title: String, text: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(RESULT_NOTIFICATION_ID, notification)
    }

    private fun createLogger(
        strategyId: String,
        name: String,
        scheduledTime: Long
    ): ScheduleExecutionLogger {
        val logDir = File(pathConfig.debugDir)
        return ScheduleExecutionLogger(logDir).also {
            it.startSession(strategyId, name, scheduledTime)
        }
    }

    override fun onDestroy() {
        countdownTimer?.cancel()
        releaseWakeLock()
        serviceScope.cancel()
        countdownState.value = CountdownState.Idle
        super.onDestroy()
    }
}

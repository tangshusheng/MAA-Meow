package com.aliothmoon.maameow.schedule.service

import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.model.PhaseLog
import com.aliothmoon.maameow.schedule.model.PhaseLogLevel
import com.aliothmoon.maameow.schedule.model.ScheduleExecutionLog
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 定时任务执行日志记录器
 *
 * 记录调度层的全链路日志（触发、倒计时、连接、执行、完成等），
 * 同时输出到文本文件和结构化 ScheduleExecutionLog 对象。
 */
class ScheduleExecutionLogger(private val logDir: File) {

    companion object {
        private const val TAG = "ScheduleExecutionLogger"
        private const val SUBDIR = "schedule"
        private val FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
        private val DISPLAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm:ss")
    }

    @Volatile
    private var currentLog: ScheduleExecutionLog? = null

    @Volatile
    private var phases = mutableListOf<PhaseLog>()

    @Volatile
    private var fileWriter: BufferedWriter? = null

    private val lock = Any()

    /**
     * 开始新的执行日志会话
     */
    fun startSession(strategyId: String, strategyName: String, scheduledTimeMs: Long) {
        synchronized(lock) {
            try {
                // 关闭上一个未完成的会话
                closeInternal()

                val triggerTime = System.currentTimeMillis()

                // 准备日志子目录
                val scheduleDir = File(logDir, SUBDIR).apply {
                    if (!exists()) mkdirs()
                }

                // 文件名：{strategyId}_{yyyyMMdd_HHmmss}.log
                val timestamp = Instant.ofEpochMilli(triggerTime)
                    .atZone(ZoneId.systemDefault())
                    .format(FILE_DATE_FORMAT)
                // strategyId 可能含有非文件系统安全字符，做简单替换
                val safeId = strategyId.replace(Regex("[^A-Za-z0-9_\\-]"), "_")
                val logFile = File(scheduleDir, "${safeId}_${timestamp}.log")

                Timber.i("$TAG: 开始新会话，文件: ${logFile.absolutePath}")

                fileWriter = BufferedWriter(FileWriter(logFile, false))

                phases = mutableListOf()

                currentLog = ScheduleExecutionLog(
                    strategyId = strategyId,
                    strategyName = strategyName,
                    scheduledTime = scheduledTimeMs,
                    actualTriggerTime = triggerTime,
                    result = ExecutionResult.SUCCESS // 占位，endSession 会覆盖
                )

                // 写入文件头
                val scheduledDisplay = Instant.ofEpochMilli(scheduledTimeMs)
                    .atZone(ZoneId.systemDefault())
                    .format(DISPLAY_DATE_FORMAT)
                val triggerDisplay = Instant.ofEpochMilli(triggerTime)
                    .atZone(ZoneId.systemDefault())
                    .format(DISPLAY_DATE_FORMAT)

                writeLine("========================================")
                writeLine("定时任务执行日志")
                writeLine("策略: $strategyName")
                writeLine("计划时间: $scheduledDisplay")
                writeLine("触发时间: $triggerDisplay")
                writeLine("========================================")
                writeLine("")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 启动会话失败")
            }
        }
    }

    /**
     * 记录一个阶段日志
     */
    fun log(phase: String, level: PhaseLogLevel, message: String) {
        synchronized(lock) {
            try {
                val now = System.currentTimeMillis()
                val phaseLog = PhaseLog(
                    timestamp = now,
                    phase = phase,
                    level = level,
                    message = message
                )
                phases.add(phaseLog)

                val timeStr = Instant.ofEpochMilli(now)
                    .atZone(ZoneId.systemDefault())
                    .format(TIME_FORMAT)
                val levelStr = level.name.padEnd(7)
                writeLine("[$levelStr]  $timeStr  $phase: $message")

                Timber.d("$TAG: [$level] $phase: $message")
            } catch (e: Exception) {
                Timber.e(e, "$TAG: 写入日志失败")
            }
        }
    }

    /** 记录 INFO 级别日志 */
    fun info(phase: String, message: String) = log(phase, PhaseLogLevel.INFO, message)

    /** 记录 WARNING 级别日志 */
    fun warn(phase: String, message: String) = log(phase, PhaseLogLevel.WARNING, message)

    /** 记录 ERROR 级别日志 */
    fun error(phase: String, message: String) = log(phase, PhaseLogLevel.ERROR, message)

    /**
     * 完成会话，返回最终的 ScheduleExecutionLog
     */
    fun endSession(
        result: ExecutionResult,
        taskCount: Int = 0,
        errorMessage: String? = null
    ): ScheduleExecutionLog {
        synchronized(lock) {
            val base = currentLog ?: ScheduleExecutionLog(
                strategyId = "unknown",
                strategyName = "unknown",
                scheduledTime = 0L,
                actualTriggerTime = System.currentTimeMillis(),
                result = result
            )

            val endTime = System.currentTimeMillis()
            val durationMs = endTime - base.actualTriggerTime
            val durationStr = formatDuration(durationMs)

            val endDisplay = Instant.ofEpochMilli(endTime)
                .atZone(ZoneId.systemDefault())
                .format(DISPLAY_DATE_FORMAT)

            // 写入文件尾
            writeLine("")
            writeLine("========================================")
            writeLine("执行结果: ${result.name}")
            if (errorMessage != null) {
                writeLine("错误信息: $errorMessage")
            }
            writeLine("结束时间: $endDisplay")
            writeLine("总耗时: $durationStr")
            writeLine("========================================")

            val finalLog = base.copy(
                executionEndTime = endTime,
                result = result,
                phases = phases.toList(),
                taskCount = taskCount,
                errorMessage = errorMessage
            )

            currentLog = finalLog

            Timber.i("$TAG: 会话结束，结果: $result，耗时: $durationStr")

            closeInternal()

            return finalLog
        }
    }

    /**
     * 关闭文件资源（异常退出时调用）
     */
    fun close() {
        synchronized(lock) {
            closeInternal()
        }
    }

    private fun closeInternal() {
        try {
            fileWriter?.close()
        } catch (e: Exception) {
            Timber.w(e, "$TAG: 关闭 writer 时出错")
        }
        fileWriter = null
    }

    private fun writeLine(line: String) {
        try {
            fileWriter?.apply {
                write(line)
                newLine()
                flush()
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: writeLine 失败")
        }
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return if (minutes > 0) "${minutes}m ${seconds}s" else "${seconds}s"
    }
}

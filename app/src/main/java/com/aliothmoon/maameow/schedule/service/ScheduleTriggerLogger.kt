package com.aliothmoon.maameow.schedule.service

import com.aliothmoon.maameow.data.config.MaaPathConfig
import com.aliothmoon.maameow.schedule.model.ExecutionResult
import com.aliothmoon.maameow.schedule.model.TriggerLogEntry
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 定时触发链路日志。
 *
 * 每次触发生成一个 JSON Lines 文件（Header / Log* / Footer），
 * 记录从闹钟触发到任务启动（或失败）的完整链路。
 */
class ScheduleTriggerLogger(private val pathConfig: MaaPathConfig) {

    private companion object {
        private const val TAG = "TriggerLogger"
        private const val LOG_PREFIX = "trigger_"
        private const val LOG_EXTENSION = ".log"
        private const val MAX_LOG_FILES = 100
        private val FILE_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")
    }

    private val json = JsonUtils.common

    private val logDir: File
        get() = File(pathConfig.debugDir, "schedule").apply {
            if (!exists()) mkdirs()
        }

    private var writer: BufferedWriter? = null
    private var currentFileName: String? = null

    // ========== 会话式 API ==========

    @Synchronized
    fun begin(strategyId: String, strategyName: String, scheduledTimeMs: Long) {
        // 关闭上一个未正常结束的会话
        closeWriter()
        try {
            val now = System.currentTimeMillis()
            val timeStr = Instant.ofEpochMilli(now)
                .atZone(ZoneId.systemDefault())
                .format(FILE_DATE_FORMAT)
            val fileName = "$LOG_PREFIX${timeStr}$LOG_EXTENSION"
            currentFileName = fileName
            val file = File(logDir, fileName)
            writer = BufferedWriter(FileWriter(file, true))

            val header = TriggerLogEntry.Header(
                strategyId = strategyId,
                strategyName = strategyName,
                scheduledTimeMs = scheduledTimeMs,
                actualTimeMs = now,
            )
            writeLine(json.encodeToString<TriggerLogEntry>(header))
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to begin trigger log")
            closeWriter()
        }
    }

    @Synchronized
    fun append(message: String) {
        val w = writer ?: return
        try {
            val entry = TriggerLogEntry.Log(
                time = System.currentTimeMillis(),
                message = message,
            )
            writeLine(json.encodeToString<TriggerLogEntry>(entry))
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to append trigger log")
        }
    }

    @Synchronized
    fun end(result: ExecutionResult, message: String? = null) {
        val w = writer ?: return
        try {
            val footer = TriggerLogEntry.Footer(
                time = System.currentTimeMillis(),
                result = result,
                message = message,
            )
            writeLine(json.encodeToString<TriggerLogEntry>(footer))
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to write trigger log footer")
        } finally {
            closeWriter()
            cleanup()
        }
    }

    // ========== 读取 API（供 UI） ==========

    data class TriggerLogSummary(
        val fileName: String,
        val header: TriggerLogEntry.Header,
        val footer: TriggerLogEntry.Footer?,
    )

    suspend fun getLogSummaries(): List<TriggerLogSummary> = withContext(Dispatchers.IO) {
        try {
            logDir.listFiles { file ->
                file.isFile
                        && file.name.startsWith(LOG_PREFIX)
                        && file.name.endsWith(LOG_EXTENSION)
            }?.mapNotNull { file ->
                parseSummary(file)
            }?.sortedByDescending { it.header.actualTimeMs } ?: emptyList()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to list trigger logs")
            emptyList()
        }
    }

    suspend fun readLogFile(fileName: String): List<TriggerLogEntry> =
        withContext(Dispatchers.IO) {
            try {
                val file = File(logDir, fileName)
                if (!file.exists()) return@withContext emptyList()
                file.readLines().mapNotNull { line ->
                    if (line.isBlank()) return@mapNotNull null
                    try {
                        json.decodeFromString<TriggerLogEntry>(line)
                    } catch (e: Exception) {
                        Timber.w("$TAG: Failed to parse line: $line")
                        null
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "$TAG: Failed to read trigger log: $fileName")
                emptyList()
            }
        }

    suspend fun deleteLog(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            File(logDir, fileName).delete()
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to delete trigger log: $fileName")
            false
        }
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        try {
            logDir.listFiles { file ->
                file.isFile
                        && file.name.startsWith(LOG_PREFIX)
                        && file.name.endsWith(LOG_EXTENSION)
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to clear trigger logs")
        }
    }

    // ========== 内部 ==========

    private fun writeLine(line: String) {
        writer?.apply {
            write(line)
            newLine()
            flush()
        }
    }

    private fun closeWriter() {
        try {
            writer?.close()
        } catch (_: Exception) {
        }
        writer = null
        currentFileName = null
    }

    private fun cleanup() {
        try {
            val files = logDir.listFiles { file ->
                file.isFile
                        && file.name.startsWith(LOG_PREFIX)
                        && file.name.endsWith(LOG_EXTENSION)
            }?.sortedByDescending { it.lastModified() } ?: return

            if (files.size > MAX_LOG_FILES) {
                files.drop(MAX_LOG_FILES).forEach { it.delete() }
            }
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to cleanup old trigger logs")
        }
    }

    private fun parseSummary(file: File): TriggerLogSummary? {
        return try {
            val lines = file.readLines().filter { it.isNotBlank() }
            if (lines.isEmpty()) return null
            val header = json.decodeFromString<TriggerLogEntry>(lines.first())
            if (header !is TriggerLogEntry.Header) return null
            val footer = lines.lastOrNull()?.let {
                try {
                    val entry = json.decodeFromString<TriggerLogEntry>(it)
                    entry as? TriggerLogEntry.Footer
                } catch (_: Exception) {
                    null
                }
            }
            TriggerLogSummary(
                fileName = file.name,
                header = header,
                footer = footer,
            )
        } catch (e: Exception) {
            Timber.w(e, "$TAG: Failed to parse summary: ${file.name}")
            null
        }
    }
}

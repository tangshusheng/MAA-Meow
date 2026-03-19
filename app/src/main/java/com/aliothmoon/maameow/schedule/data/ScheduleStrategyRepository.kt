package com.aliothmoon.maameow.schedule.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.aliothmoon.maameow.schedule.model.ScheduleExecutionLog
import com.aliothmoon.maameow.schedule.model.ScheduleStrategy
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import timber.log.Timber

class ScheduleStrategyRepository(private val context: Context) {

    companion object {
        private val Context.store: DataStore<Preferences> by preferencesDataStore(name = "schedule_strategies")
        private val STRATEGIES_KEY = stringPreferencesKey("strategies")
        private val LOGS_KEY = stringPreferencesKey("execution_logs")
        private const val MAX_LOGS_PER_STRATEGY = 20
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = JsonUtils.common

    private val _isLoaded = MutableStateFlow(false)
    val isLoaded: StateFlow<Boolean> = _isLoaded.asStateFlow()

    /** 从 DataStore 自动同步的策略列表 */
    val strategies: StateFlow<List<ScheduleStrategy>> = context.store.data
        .map { prefs ->
            val list = decodeStrategies(prefs[STRATEGIES_KEY])
            _isLoaded.value = true
            list
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    /** 从 DataStore 自动同步的执行日志 */
    val logs: StateFlow<List<ScheduleExecutionLog>> = context.store.data
        .map { prefs -> decodeLogs(raw = prefs[LOGS_KEY]) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // ---- 策略 CRUD ----

    suspend fun add(strategy: ScheduleStrategy) {
        context.store.edit { prefs ->
            val current = decodeStrategies(prefs[STRATEGIES_KEY]).toMutableList()
            current.add(strategy)
            prefs[STRATEGIES_KEY] = json.encodeToString<List<ScheduleStrategy>>(current)
            Timber.d("添加调度策略: %s (%s)", strategy.name, strategy.id)
        }
    }

    suspend fun update(strategy: ScheduleStrategy) {
        context.store.edit { prefs ->
            val current = decodeStrategies(prefs[STRATEGIES_KEY]).toMutableList()
            val idx = current.indexOfFirst { it.id == strategy.id }
            if (idx >= 0) {
                current[idx] = strategy
                prefs[STRATEGIES_KEY] = json.encodeToString<List<ScheduleStrategy>>(current)
                Timber.d("更新调度策略: %s (%s)", strategy.name, strategy.id)
            }
        }
    }

    suspend fun remove(strategyId: String) {
        context.store.edit { prefs ->
            val current = decodeStrategies(prefs[STRATEGIES_KEY]).toMutableList()
            if (current.removeAll { it.id == strategyId }) {
                prefs[STRATEGIES_KEY] = json.encodeToString<List<ScheduleStrategy>>(current)
                Timber.d("删除调度策略: %s", strategyId)
            }

            // 同步清理该策略的日志
            val currentLogs = decodeLogs(prefs[LOGS_KEY]).toMutableList()
            if (currentLogs.removeAll { it.strategyId == strategyId }) {
                prefs[LOGS_KEY] = json.encodeToString<List<ScheduleExecutionLog>>(currentLogs)
            }
        }
    }

    suspend fun setEnabled(strategyId: String, enabled: Boolean) {
        context.store.edit { prefs ->
            val current = decodeStrategies(prefs[STRATEGIES_KEY]).toMutableList()
            val idx = current.indexOfFirst { it.id == strategyId }
            if (idx >= 0) {
                current[idx] = current[idx].copy(enabled = enabled)
                prefs[STRATEGIES_KEY] = json.encodeToString<List<ScheduleStrategy>>(current)
                Timber.d("策略 %s 启用状态 -> %s", strategyId, enabled)
            }
        }
    }

    suspend fun getById(strategyId: String): ScheduleStrategy? {
        return strategies.value.find { it.id == strategyId }
    }

    // ---- 执行日志 ----

    suspend fun recordExecution(log: ScheduleExecutionLog) {
        context.store.edit { prefs ->
            val current = decodeLogs(prefs[LOGS_KEY]).toMutableList()
            current.add(log)
            // 每条策略保留最新的 MAX_LOGS_PER_STRATEGY 条
            val strategyLogs = current.filter { it.strategyId == log.strategyId }
            if (strategyLogs.size > MAX_LOGS_PER_STRATEGY) {
                val overflow = strategyLogs
                    .sortedBy { it.actualTriggerTime }
                    .take(strategyLogs.size - MAX_LOGS_PER_STRATEGY)
                    .map { it.id }
                    .toSet()
                current.removeAll { it.id in overflow }
            }
            prefs[LOGS_KEY] = json.encodeToString<List<ScheduleExecutionLog>>(current)
            Timber.d("记录执行日志: 策略=%s 结果=%s", log.strategyId, log.result)
        }
    }

    fun logsForStrategy(strategyId: String): Flow<List<ScheduleExecutionLog>> {
        return logs.map { all -> all.filter { it.strategyId == strategyId } }
    }

    // ---- 私有辅助 ----

    private fun decodeStrategies(raw: String?): List<ScheduleStrategy> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ScheduleStrategy>>(raw)
        }.getOrElse {
            Timber.w(it, "解析调度策略失败，返回空列表")
            emptyList()
        }
    }

    private fun decodeLogs(raw: String?): List<ScheduleExecutionLog> {
        if (raw.isNullOrEmpty()) return emptyList()
        return runCatching {
            json.decodeFromString<List<ScheduleExecutionLog>>(raw)
        }.getOrElse {
            Timber.w(it, "解析执行日志失败，返回空列表")
            emptyList()
        }
    }
}

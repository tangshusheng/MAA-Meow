package com.aliothmoon.maameow.schedule.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class ScheduleExecutionLog(
    val id: String = UUID.randomUUID().toString(),
    val strategyId: String,
    val strategyName: String,
    val scheduledTime: Long,
    val actualTriggerTime: Long,
    val executionStartTime: Long? = null,
    val executionEndTime: Long? = null,
    val result: ExecutionResult,
    val phases: List<PhaseLog> = emptyList(),
    val taskCount: Int = 0,
    val errorMessage: String? = null
)

@Serializable
data class PhaseLog(
    val timestamp: Long,
    val phase: String,
    val level: PhaseLogLevel,
    val message: String
)

@Serializable
enum class PhaseLogLevel { INFO, WARNING, ERROR }

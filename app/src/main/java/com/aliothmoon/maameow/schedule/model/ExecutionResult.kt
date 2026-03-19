package com.aliothmoon.maameow.schedule.model

import kotlinx.serialization.Serializable

@Serializable
enum class ExecutionResult {
    SUCCESS,
    FAILED,
    SKIPPED_BUSY,
    SKIPPED_NO_SERVICE,
    CANCELLED_BY_USER,
    SKIPPED_SYSTEM_BUSY
}

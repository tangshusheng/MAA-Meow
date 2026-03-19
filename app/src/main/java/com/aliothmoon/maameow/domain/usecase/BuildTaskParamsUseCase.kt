package com.aliothmoon.maameow.domain.usecase

import com.aliothmoon.maameow.data.model.MallConfig
import com.aliothmoon.maameow.data.model.TaskChainNode
import com.aliothmoon.maameow.data.model.WakeUpConfig
import com.aliothmoon.maameow.data.preferences.TaskChainState
import com.aliothmoon.maameow.domain.models.resolveMallCreditFightAvailability
import com.aliothmoon.maameow.maa.task.MaaTaskParams
import timber.log.Timber

class BuildTaskParamsUseCase(private val chainState: TaskChainState) {
    operator fun invoke(): List<MaaTaskParams> {
        return buildFrom(chainState.chain.value)
    }

    fun buildFrom(chain: List<TaskChainNode>): List<MaaTaskParams> {
        val enabledNodes = chain
            .filter { it.enabled }
            .sortedBy { it.order }
        val creditFightAvailability = resolveMallCreditFightAvailability(enabledNodes)
        val clientType = enabledNodes.firstNotNullOfOrNull { (it.config as? WakeUpConfig)?.clientType } ?: "Official"

        if (!creditFightAvailability.isAvailable && enabledNodes.any { (it.config as? MallConfig)?.creditFight == true }) {
            Timber.w(
                "Credit fight disabled because a fight task has no resolvable active stage. task=%s order=%d",
                creditFightAvailability.blockingTaskName ?: "unknown",
                creditFightAvailability.blockingTaskOrder ?: -1,
            )
        }

        return enabledNodes.map { node ->
            when (val config = node.config) {
                is MallConfig -> config.toTaskParams(
                    creditFightEnabled = config.creditFight && creditFightAvailability.isAvailable,
                    clientType = clientType,
                )

                else -> config.toTaskParams()
            }
        }
    }
}

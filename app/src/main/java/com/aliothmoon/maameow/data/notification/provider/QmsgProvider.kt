package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

class QmsgProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "Qmsg"

    override suspend fun send(title: String, content: String): Boolean {
        val settings = settingsManager.settings.first()
        val server = settings.qmsgServer.takeIf { it.isNotBlank() }?.trimEnd('/') ?: return false
        val key = settings.qmsgKey.takeIf { it.isNotBlank() } ?: return false
        val body = JsonUtils.common.encodeToString(
            QmsgRequest(
                msg = content,
                qq = settings.qmsgUser,
                bot = settings.qmsgBot,
            )
        )

        return runCatching {
            httpClient.post("$server/jsend/$key", body).use { response ->
                val responseBody = response.body.string()
                response.isSuccessful && JsonUtils.common.decodeFromString<QmsgResponse>(responseBody).success
            }
        }.getOrElse {
            Timber.e(it, "Qmsg send failed")
            false
        }
    }

    @Serializable
    private data class QmsgRequest(
        val msg: String,
        val qq: String,
        val bot: String,
    )

    @Serializable
    private data class QmsgResponse(
        @SerialName("success") val success: Boolean = false,
    )
}

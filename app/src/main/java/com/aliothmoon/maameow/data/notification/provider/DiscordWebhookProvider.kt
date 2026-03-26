package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import timber.log.Timber

class DiscordWebhookProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "Discord Webhook"

    override suspend fun send(title: String, content: String): Boolean {
        val settings = settingsManager.settings.first()
        val webhookUrl = settings.discordWebhookUrl.takeIf { it.isNotBlank() } ?: return false
        val body = JsonUtils.common.encodeToString(DiscordWebhookRequest(content = content))

        return runCatching {
            httpClient.post(webhookUrl, body).use { response ->
                val responseBody = response.body.string()
                if (response.isSuccessful && responseBody.isEmpty()) {
                    return@use true
                }

                val errorResponse = runCatching {
                    JsonUtils.common.decodeFromString<DiscordWebhookErrorResponse>(responseBody)
                }.getOrNull()

                if (errorResponse == null) {
                    Timber.w("Discord Webhook failed with non-JSON response")
                } else {
                    Timber.w(
                        "Discord Webhook failed: %s (%s)",
                        errorResponse.message,
                        errorResponse.code
                    )
                }
                false
            }
        }.getOrElse {
            Timber.e(it, "Discord Webhook send failed")
            false
        }
    }

    @Serializable
    private data class DiscordWebhookRequest(
        val content: String,
    )

    @Serializable
    private data class DiscordWebhookErrorResponse(
        val message: String? = null,
        val code: Int? = null,
    )
}

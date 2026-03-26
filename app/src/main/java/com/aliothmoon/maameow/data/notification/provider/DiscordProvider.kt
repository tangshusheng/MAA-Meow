package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import timber.log.Timber

class DiscordProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "Discord"

    override suspend fun send(title: String, content: String): Boolean {
        val settings = settingsManager.settings.first()
        val botToken = settings.discordBotToken.takeIf { it.isNotBlank() } ?: return false
        val userId = settings.discordUserId.takeIf { it.isNotBlank() } ?: return false
        val channelId = createDmChannel(botToken, userId) ?: return false

        return runCatching {
            httpClient.postForm(
                url = "https://discord.com/api/v9/channels/$channelId/messages",
                params = mapOf("content" to content),
                headers = discordHeaders(botToken)
            ).use { response ->
                response.isSuccessful
            }
        }.getOrElse {
            Timber.e(it, "Discord send failed")
            false
        }
    }

    private suspend fun createDmChannel(botToken: String, userId: String): String? {
        val body = JsonUtils.common.encodeToString(
            DiscordCreateChannelRequest(recipientId = userId)
        )

        return runCatching {
            httpClient.post(
                url = "https://discord.com/api/v9/users/@me/channels",
                body = body,
                headers = discordHeaders(botToken)
            ).use { response ->
                if (!response.isSuccessful) {
                    return@use null
                }

                val responseBody = response.body.string()
                JsonUtils.common.decodeFromString<DiscordCreateChannelResponse>(responseBody).id
            }
        }.getOrElse {
            Timber.e(it, "Discord create DM channel failed")
            null
        }
    }

    private fun discordHeaders(botToken: String): Map<String, String> = mapOf(
        "Authorization" to "Bot $botToken",
        "User-Agent" to "DiscordBot"
    )

    @Serializable
    private data class DiscordCreateChannelRequest(
        @SerialName("recipient_id") val recipientId: String,
    )

    @Serializable
    private data class DiscordCreateChannelResponse(
        val id: String? = null,
    )
}

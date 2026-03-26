package com.aliothmoon.maameow.data.notification.provider

import com.aliothmoon.maameow.data.api.HttpClientHelper
import com.aliothmoon.maameow.data.notification.NotificationSettingsManager
import com.aliothmoon.maameow.utils.JsonUtils
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import timber.log.Timber
import java.net.URI

class GotifyProvider(
    private val httpClient: HttpClientHelper,
    private val settingsManager: NotificationSettingsManager
) : NotificationProvider {

    override val id = "Gotify"

    override suspend fun send(title: String, content: String): Boolean {
        val settings = settingsManager.settings.first()
        val server = settings.gotifyServer.takeIf { it.isNotBlank() }?.trim() ?: return false
        val token = settings.gotifyToken.takeIf { it.isNotBlank() } ?: return false
        val baseUri = runCatching { URI.create(server) }.getOrNull() ?: return false
        if (baseUri.scheme !in setOf("http", "https")) {
            return false
        }

        val url = baseUri.resolve("/message").toString()
        val body = JsonUtils.common.encodeToString(
            GotifyRequest(
                title = title,
                message = content,
            )
        )

        return runCatching {
            httpClient.post(
                url = url,
                body = body,
                headers = mapOf("X-Gotify-Key" to token)
            ).use { response ->
                val responseBody = response.body.string()
                response.isSuccessful && JsonUtils.common.decodeFromString<GotifyResponse>(responseBody).id != null
            }
        }.getOrElse {
            Timber.e(it, "Gotify send failed")
            false
        }
    }

    @Serializable
    private data class GotifyRequest(
        val title: String,
        val message: String,
    )

    @Serializable
    private data class GotifyResponse(
        val id: Int? = null,
    )
}

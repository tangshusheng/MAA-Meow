package com.aliothmoon.maameow.data.model.update

enum class UpdateChannel(
    val value: String,
    val displayName: String
) {
    STABLE("stable", "稳定版"),
    BETA("beta", "公测版")
}

package com.nuvio.app.features.anime

internal expect object AnimeSettingsStorage {
    fun loadMalClientId(): String?
    fun saveMalClientId(clientId: String)
}

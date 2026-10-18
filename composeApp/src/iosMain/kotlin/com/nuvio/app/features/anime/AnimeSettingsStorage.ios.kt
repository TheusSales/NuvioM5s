package com.nuvio.app.features.anime

import platform.Foundation.NSUserDefaults

internal actual object AnimeSettingsStorage {
    private const val malClientIdKey = "nexo_mal_client_id"

    actual fun loadMalClientId(): String? =
        NSUserDefaults.standardUserDefaults.stringForKey(malClientIdKey)

    actual fun saveMalClientId(clientId: String) {
        NSUserDefaults.standardUserDefaults.setObject(clientId, forKey = malClientIdKey)
    }
}

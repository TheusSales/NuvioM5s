package com.nuvio.app.features.anime

import android.content.Context
import android.content.SharedPreferences

internal actual object AnimeSettingsStorage {
    private const val preferencesName = "nexo_anime_settings"
    private const val malClientIdKey = "mal_client_id"

    private var preferences: SharedPreferences? = null

    fun initialize(context: Context) {
        preferences = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
    }

    actual fun loadMalClientId(): String? = preferences?.getString(malClientIdKey, null)

    actual fun saveMalClientId(clientId: String) {
        preferences?.edit()?.putString(malClientIdKey, clientId)?.apply()
    }
}

package com.nuvio.app.features.anime

import co.touchlab.kermit.Logger
import com.nuvio.app.core.time.EpisodeReleaseDatePlatform
import com.nuvio.app.features.addons.AddAddonResult
import com.nuvio.app.features.addons.AddonRepository
import com.nuvio.app.features.addons.httpGetText
import com.nuvio.app.features.addons.httpRequestRaw
import com.nuvio.app.features.home.MetaPreview
import com.nuvio.app.features.home.PosterShape
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

enum class AnimeSource {
    KITSU,
    MY_ANIME_LIST,
}

/** Anime rows shown on the Anime tab. Titles are resolved by the UI from [AnimeRowKind]. */
enum class AnimeRowKind {
    SEASON,
    AIRING,
    POPULAR,
    TOP_RATED,
    MOVIES,
    UPCOMING,
}

data class AnimeRow(
    val kind: AnimeRowKind,
    val items: List<MetaPreview>,
)

data class AnimeUiState(
    val source: AnimeSource = AnimeSource.KITSU,
    val isLoading: Boolean = false,
    val rows: List<AnimeRow> = emptyList(),
    val hasError: Boolean = false,
    val kitsuAddonInstalled: Boolean = true,
    val isInstallingKitsuAddon: Boolean = false,
    val malClientId: String = "",
)

sealed interface MalClientIdResult {
    data object Saved : MalClientIdResult
    data object Cleared : MalClientIdResult
    data object Invalid : MalClientIdResult
    data object NetworkError : MalClientIdResult
}

/**
 * Anime catalog for the Anime tab. With a MyAnimeList Client ID it shows MAL rankings and scores
 * (`mal:<id>`); without one it falls back to the public Kitsu API (`kitsu:<id>`). The Anime Kitsu
 * addon resolves both id kinds to metadata whose episodes use `kitsu:` ids, which stream addons such
 * as Torrentio accept.
 */
object AnimeCatalogRepository {
    const val KITSU_ADDON_MANIFEST_URL = "https://anime-kitsu.strem.fun/manifest.json"
    const val MAL_API_CONFIG_URL = "https://myanimelist.net/apiconfig"
    private const val KITSU_API = "https://kitsu.io/api/edge"
    private const val MAL_API = "https://api.myanimelist.net/v2"
    private const val ROW_LIMIT = 20
    private const val KITSU_FIELDS =
        "fields%5Banime%5D=canonicalTitle,titles,synopsis,subtype,startDate,posterImage,coverImage,averageRating"
    private const val MAL_FIELDS = "id,title,main_picture,alternative_titles,mean,media_type,start_date,synopsis"

    private val log = Logger.withTag("AnimeCatalog")
    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var hasLoadedSettings = false
    private var loadJob: Job? = null

    private val _uiState = MutableStateFlow(AnimeUiState())
    val uiState: StateFlow<AnimeUiState> = _uiState.asStateFlow()

    fun ensureLoaded() {
        if (!hasLoadedSettings) {
            hasLoadedSettings = true
            val clientId = AnimeSettingsStorage.loadMalClientId()?.trim().orEmpty()
            _uiState.update { it.copy(malClientId = clientId, source = sourceFor(clientId)) }
        }
        refreshKitsuAddonState()
        val state = _uiState.value
        if (state.isLoading || state.rows.isNotEmpty()) return
        reload()
    }

    fun reload() {
        val clientId = _uiState.value.malClientId
        val source = sourceFor(clientId)
        _uiState.update { it.copy(isLoading = true, hasError = false, source = source) }
        loadJob?.cancel()
        loadJob = scope.launch {
            val rows = rowKindsFor(source)
                .map { kind -> async { kind to runCatching { fetchRow(source, kind, clientId) } } }
                .awaitAll()
                .mapNotNull { (kind, result) ->
                    result.onFailure { log.w(it) { "$source row $kind failed" } }
                    result.getOrNull()?.takeIf { it.isNotEmpty() }?.let { AnimeRow(kind, it) }
                }
            _uiState.update {
                it.copy(isLoading = false, rows = rows, hasError = rows.isEmpty())
            }
        }
    }

    /** Validates the Client ID against the MAL API before saving it; a blank value switches back to Kitsu. */
    suspend fun updateMalClientId(rawClientId: String): MalClientIdResult {
        val clientId = rawClientId.trim()
        if (clientId.isNotEmpty()) {
            val status = runCatching {
                httpRequestRaw(
                    method = "GET",
                    url = "$MAL_API/anime/5114?fields=id",
                    headers = mapOf("X-MAL-CLIENT-ID" to clientId),
                    body = "",
                ).status
            }.getOrElse { return MalClientIdResult.NetworkError }
            if (status in listOf(400, 401, 403)) return MalClientIdResult.Invalid
            if (status !in 200..299) return MalClientIdResult.NetworkError
        }
        AnimeSettingsStorage.saveMalClientId(clientId)
        _uiState.update { it.copy(malClientId = clientId, rows = emptyList()) }
        reload()
        return if (clientId.isEmpty()) MalClientIdResult.Cleared else MalClientIdResult.Saved
    }

    fun refreshKitsuAddonState() {
        val installed = AddonRepository.uiState.value.addons.any { addon ->
            addon.manifestUrl.equals(KITSU_ADDON_MANIFEST_URL, ignoreCase = true) ||
                addon.manifest?.id == "community.anime.kitsu"
        }
        _uiState.update { it.copy(kitsuAddonInstalled = installed) }
    }

    suspend fun installKitsuAddon(): AddAddonResult {
        _uiState.update { it.copy(isInstallingKitsuAddon = true) }
        val result = AddonRepository.addAddon(KITSU_ADDON_MANIFEST_URL)
        _uiState.update { it.copy(isInstallingKitsuAddon = false) }
        refreshKitsuAddonState()
        return result
    }

    private fun sourceFor(clientId: String): AnimeSource =
        if (clientId.isBlank()) AnimeSource.KITSU else AnimeSource.MY_ANIME_LIST

    // Kitsu has no useful "airing now" ranking (it mirrors the all-time popular list), so it is MAL-only.
    internal fun rowKindsFor(source: AnimeSource): List<AnimeRowKind> = when (source) {
        AnimeSource.MY_ANIME_LIST -> AnimeRowKind.entries
        AnimeSource.KITSU -> AnimeRowKind.entries - AnimeRowKind.AIRING
    }

    private suspend fun fetchRow(source: AnimeSource, kind: AnimeRowKind, clientId: String): List<MetaPreview> =
        when (source) {
            AnimeSource.KITSU -> {
                val payload = httpGetText(kitsuRowUrl(kind))
                json.decodeFromString<KitsuAnimeResponse>(payload).data.mapNotNull { it.toMetaPreview() }
            }
            AnimeSource.MY_ANIME_LIST -> {
                val response = httpRequestRaw(
                    method = "GET",
                    url = malRowUrl(kind),
                    headers = mapOf("X-MAL-CLIENT-ID" to clientId),
                    body = "",
                )
                check(response.status in 200..299) { "MAL HTTP ${response.status}" }
                json.decodeFromString<MalAnimeListResponse>(response.body).data.mapNotNull { it.node.toMetaPreview() }
            }
        }

    internal fun kitsuRowUrl(kind: AnimeRowKind, todayIsoDate: String? = null): String {
        val page = "page%5Blimit%5D=$ROW_LIMIT"
        return when (kind) {
            AnimeRowKind.SEASON -> {
                val (season, year) = currentSeason(todayIsoDate ?: today())
                "$KITSU_API/anime?filter%5Bseason%5D=$season&filter%5BseasonYear%5D=$year&sort=-userCount&$page&$KITSU_FIELDS"
            }
            AnimeRowKind.AIRING -> "$KITSU_API/anime?filter%5Bstatus%5D=current&sort=-userCount&$page&$KITSU_FIELDS"
            AnimeRowKind.POPULAR -> "$KITSU_API/anime?sort=popularityRank&$page&$KITSU_FIELDS"
            AnimeRowKind.TOP_RATED -> "$KITSU_API/anime?sort=ratingRank&$page&$KITSU_FIELDS"
            AnimeRowKind.MOVIES -> "$KITSU_API/anime?filter%5Bsubtype%5D=movie&sort=popularityRank&$page&$KITSU_FIELDS"
            AnimeRowKind.UPCOMING -> "$KITSU_API/anime?filter%5Bstatus%5D=upcoming&sort=-userCount&$page&$KITSU_FIELDS"
        }
    }

    internal fun malRowUrl(kind: AnimeRowKind, todayIsoDate: String? = null): String {
        val query = "fields=$MAL_FIELDS&limit=$ROW_LIMIT"
        return when (kind) {
            AnimeRowKind.SEASON -> {
                val (season, year) = currentSeason(todayIsoDate ?: today())
                "$MAL_API/anime/season/$year/$season?sort=anime_num_list_users&$query"
            }
            AnimeRowKind.AIRING -> "$MAL_API/anime/ranking?ranking_type=airing&$query"
            AnimeRowKind.POPULAR -> "$MAL_API/anime/ranking?ranking_type=bypopularity&$query"
            AnimeRowKind.TOP_RATED -> "$MAL_API/anime/ranking?ranking_type=all&$query"
            AnimeRowKind.MOVIES -> "$MAL_API/anime/ranking?ranking_type=movie&$query"
            AnimeRowKind.UPCOMING -> "$MAL_API/anime/ranking?ranking_type=upcoming&$query"
        }
    }

    /** Kitsu and MAL share seasons: winter = Jan–Mar, spring = Apr–Jun, summer = Jul–Sep, fall = Oct–Dec. */
    internal fun currentSeason(isoDate: String): Pair<String, Int> {
        val year = isoDate.take(4).toIntOrNull() ?: 2026
        val month = isoDate.drop(5).take(2).toIntOrNull() ?: 1
        val season = when (month) {
            in 1..3 -> "winter"
            in 4..6 -> "spring"
            in 7..9 -> "summer"
            else -> "fall"
        }
        return season to year
    }

    private fun today(): String =
        EpisodeReleaseDatePlatform.localIsoDateAtEpochMs(EpisodeReleaseDatePlatform.nowEpochMs()) ?: "2026-01-01"
}

@Serializable
internal data class KitsuAnimeResponse(
    val data: List<KitsuAnime> = emptyList(),
)

@Serializable
internal data class KitsuAnime(
    val id: String,
    val attributes: KitsuAnimeAttributes = KitsuAnimeAttributes(),
)

@Serializable
internal data class KitsuAnimeAttributes(
    val canonicalTitle: String? = null,
    val titles: Map<String, String?> = emptyMap(),
    val synopsis: String? = null,
    val subtype: String? = null,
    val startDate: String? = null,
    val averageRating: String? = null,
    val posterImage: KitsuImage? = null,
    val coverImage: KitsuImage? = null,
)

@Serializable
internal data class KitsuImage(
    val small: String? = null,
    val medium: String? = null,
    val large: String? = null,
    val original: String? = null,
)

internal fun KitsuAnime.toMetaPreview(): MetaPreview? {
    val attrs = attributes
    val title = attrs.titles["en"]?.takeIf { it.isNotBlank() }
        ?: attrs.titles["en_us"]?.takeIf { it.isNotBlank() }
        ?: attrs.canonicalTitle?.takeIf { it.isNotBlank() }
        ?: return null
    val isMovie = attrs.subtype.equals("movie", ignoreCase = true)
    return MetaPreview(
        id = "kitsu:$id",
        type = if (isMovie) "movie" else "series",
        name = title,
        poster = attrs.posterImage?.large ?: attrs.posterImage?.medium ?: attrs.posterImage?.original,
        banner = attrs.coverImage?.large ?: attrs.coverImage?.original,
        posterShape = PosterShape.Poster,
        description = attrs.synopsis,
        releaseInfo = attrs.startDate?.take(4),
        rawReleaseDate = attrs.startDate,
    )
}

@Serializable
internal data class MalAnimeListResponse(
    val data: List<MalAnimeEntry> = emptyList(),
)

@Serializable
internal data class MalAnimeEntry(
    val node: MalAnime,
)

@Serializable
internal data class MalAnime(
    val id: Long,
    val title: String? = null,
    @SerialName("main_picture") val mainPicture: MalPicture? = null,
    @SerialName("alternative_titles") val alternativeTitles: MalAlternativeTitles? = null,
    val mean: Double? = null,
    @SerialName("media_type") val mediaType: String? = null,
    @SerialName("start_date") val startDate: String? = null,
    val synopsis: String? = null,
)

@Serializable
internal data class MalPicture(
    val medium: String? = null,
    val large: String? = null,
)

@Serializable
internal data class MalAlternativeTitles(
    val en: String? = null,
)

internal fun MalAnime.toMetaPreview(): MetaPreview? {
    val name = alternativeTitles?.en?.takeIf { it.isNotBlank() }
        ?: title?.takeIf { it.isNotBlank() }
        ?: return null
    val year = startDate?.take(4)
    // The MAL score is the reason to pick this source, so it rides along in the release line.
    val score = mean?.let { "★ ${(it * 100).toLong() / 100.0}" }
    return MetaPreview(
        id = "mal:$id",
        type = if (mediaType.equals("movie", ignoreCase = true)) "movie" else "series",
        name = name,
        poster = mainPicture?.large ?: mainPicture?.medium,
        posterShape = PosterShape.Poster,
        description = synopsis,
        releaseInfo = listOfNotNull(year, score).joinToString(" · ").ifBlank { null },
        rawReleaseDate = startDate,
    )
}

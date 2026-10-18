package com.nuvio.app.features.anime

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnimeCatalogRepositoryTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `current season follows kitsu and mal quarters`() {
        assertEquals("winter" to 2026, AnimeCatalogRepository.currentSeason("2026-02-10"))
        assertEquals("spring" to 2026, AnimeCatalogRepository.currentSeason("2026-04-01"))
        assertEquals("summer" to 2026, AnimeCatalogRepository.currentSeason("2026-09-30"))
        assertEquals("fall" to 2026, AnimeCatalogRepository.currentSeason("2026-10-18"))
    }

    @Test
    fun `season urls use the current season`() {
        assertTrue(
            AnimeCatalogRepository.kitsuRowUrl(AnimeRowKind.SEASON, "2026-10-18")
                .contains("filter%5Bseason%5D=fall&filter%5BseasonYear%5D=2026"),
        )
        assertTrue(
            AnimeCatalogRepository.malRowUrl(AnimeRowKind.SEASON, "2026-10-18")
                .contains("/anime/season/2026/fall?"),
        )
    }

    @Test
    fun `airing row is only offered for mal`() {
        assertTrue(AnimeRowKind.AIRING in AnimeCatalogRepository.rowKindsFor(AnimeSource.MY_ANIME_LIST))
        assertFalse(AnimeRowKind.AIRING in AnimeCatalogRepository.rowKindsFor(AnimeSource.KITSU))
    }

    @Test
    fun `kitsu entries become kitsu ids with english title`() {
        val payload = """
            {"data":[
              {"id":"7442","attributes":{"canonicalTitle":"Shingeki no Kyojin","titles":{"en":"Attack on Titan","ja_jp":"進撃の巨人"},
               "subtype":"TV","startDate":"2013-04-07","posterImage":{"large":"https://media.kitsu.app/p.jpg"}}},
              {"id":"1","attributes":{"canonicalTitle":"Kimi no Na wa.","titles":{"en":""},"subtype":"movie","startDate":"2016-08-26"}}
            ]}
        """.trimIndent()
        val items = json.decodeFromString<KitsuAnimeResponse>(payload).data.mapNotNull { it.toMetaPreview() }

        assertEquals("kitsu:7442", items[0].id)
        assertEquals("series", items[0].type)
        assertEquals("Attack on Titan", items[0].name)
        assertEquals("2013", items[0].releaseInfo)
        assertEquals("movie", items[1].type)
        assertEquals("Kimi no Na wa.", items[1].name)
    }

    @Test
    fun `mal entries become mal ids with score in release line`() {
        val payload = """
            {"data":[
              {"node":{"id":16498,"title":"Shingeki no Kyojin","alternative_titles":{"en":"Attack on Titan"},
               "main_picture":{"medium":"m.jpg","large":"l.jpg"},"mean":8.556,"media_type":"tv","start_date":"2013-04-07"},
               "ranking":{"rank":1}},
              {"node":{"id":32281,"title":"Kimi no Na wa.","media_type":"movie"}}
            ],"paging":{}}
        """.trimIndent()
        val items = json.decodeFromString<MalAnimeListResponse>(payload).data.mapNotNull { it.node.toMetaPreview() }

        assertEquals("mal:16498", items[0].id)
        assertEquals("series", items[0].type)
        assertEquals("Attack on Titan", items[0].name)
        assertEquals("l.jpg", items[0].poster)
        assertEquals("2013 · ★ 8.55", items[0].releaseInfo)
        assertEquals("movie", items[1].type)
        assertEquals("Kimi no Na wa.", items[1].name)
    }
}

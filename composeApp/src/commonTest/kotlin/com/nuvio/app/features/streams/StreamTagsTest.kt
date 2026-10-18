package com.nuvio.app.features.streams

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StreamTagsTest {

    private val hexHash = "0123456789abcdef0123456789abcdef01234567"

    @Test
    fun `torrentio style release gets resolution hdr codec audio size and seeders`() {
        val tags = StreamTags.tagsFor(
            torrent(
                name = "Torrentio\n4k DV | HDR",
                title = "Dune.Part.Two.2024.2160p.WEB-DL.DV.HDR10.DDP5.1.Atmos.H265-FLUX\n👤 214 💾 18.4 GB ⚙️ ThePirateBay",
            ),
        )
        val labels = tags.map { it.label }

        assertEquals("4K", labels.first())
        assertTrue("DV · HDR10" in labels)
        assertTrue("WEB-DL" in labels)
        assertTrue("HEVC" in labels)
        assertTrue("Atmos" in labels)
        assertTrue("5.1" in labels)
        assertTrue("18.4 GB" in labels)
        assertEquals(StreamTagKind.SEEDERS_HIGH, tags.single { it.label == "👤 214" }.kind)
    }

    @Test
    fun `portuguese release is tagged as dubbed and dual audio wins over dubbed`() {
        val dubbed = StreamTags.tagsFor(torrent(title = "Filme.2023.1080p.WEB-DL.DUBLADO.x264\n👤 3"))
        assertEquals(StreamTagLanguage.DUBBED, dubbed.single { it.kind == StreamTagKind.LANGUAGE }.language)
        assertEquals(StreamTagKind.SEEDERS_LOW, dubbed.single { it.label == "👤 3" }.kind)

        val dual = StreamTags.tagsFor(torrent(title = "Serie.S01E01.1080p.Dual.Audio.PT-BR\n🇧🇷 / 🇺🇸"))
        assertEquals(StreamTagLanguage.DUAL_AUDIO, dual.single { it.kind == StreamTagKind.LANGUAGE }.language)
    }

    @Test
    fun `cam release is flagged as low quality`() {
        val tags = StreamTags.tagsFor(torrent(title = "Movie.2025.HDCAM.720p\n👤 40"))
        assertTrue(tags.any { it.kind == StreamTagKind.LOW_QUALITY && it.label == "CAM" })
        assertEquals(StreamTagKind.SEEDERS_MEDIUM, tags.single { it.label == "👤 40" }.kind)
    }

    @Test
    fun `cached debrid torrent shows the provider and english dub is not tagged as dubbed`() {
        val tags = StreamTags.tagsFor(
            torrent(title = "Anime.S01.1080p.BluRay.English.Dub").copy(
                debridCacheStatus = StreamDebridCacheStatus(
                    providerId = "torbox",
                    providerName = "Torbox",
                    state = StreamDebridCacheState.CACHED,
                ),
            ),
        )
        assertEquals(StreamTag(StreamTagKind.CACHED, "⚡ TB"), tags.first())
        assertFalse(tags.any { it.kind == StreamTagKind.LANGUAGE })
    }

    @Test
    fun `size can be left out when the card already shows a size badge`() {
        val tags = StreamTags.tagsFor(torrent(title = "Movie.1080p\n💾 2.1 GB"), includeSize = false)
        assertFalse(tags.any { it.kind == StreamTagKind.SIZE })
    }

    @Test
    fun `seeders are only shown for torrents`() {
        val direct = StreamItem(
            name = "Direct",
            title = "Movie.1080p 👤 99",
            url = "https://cdn.example.com/movie.mkv",
            addonName = "TestAddon",
            addonId = "test.addon",
        )
        assertFalse(StreamTags.tagsFor(direct).any { it.label.startsWith("👤") })
    }

    private fun torrent(name: String = "Torrentio", title: String): StreamItem = StreamItem(
        name = name,
        title = title,
        infoHash = hexHash,
        addonName = "Torrentio",
        addonId = "addon:torrentio",
    )
}

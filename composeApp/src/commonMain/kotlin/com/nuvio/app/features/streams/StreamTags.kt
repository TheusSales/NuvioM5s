package com.nuvio.app.features.streams

import com.nuvio.app.features.debrid.DebridProviders
import com.nuvio.app.features.debrid.DebridStreamAudioChannel
import com.nuvio.app.features.debrid.DebridStreamAudioTag
import com.nuvio.app.features.debrid.DebridStreamEncode
import com.nuvio.app.features.debrid.DebridStreamLanguage
import com.nuvio.app.features.debrid.DebridStreamMetadata
import com.nuvio.app.features.debrid.DebridStreamPreferences
import com.nuvio.app.features.debrid.DebridStreamQuality
import com.nuvio.app.features.debrid.DebridStreamResolution
import com.nuvio.app.features.debrid.DebridStreamVisualTag

enum class StreamTagKind {
    CACHED,
    RESOLUTION,
    LANGUAGE,
    HDR,
    SOURCE,
    LOW_QUALITY,
    CODEC,
    AUDIO,
    SIZE,
    SEEDERS_HIGH,
    SEEDERS_MEDIUM,
    SEEDERS_LOW,
}

/** Language tags are localized by the UI, everything else is shown as-is. */
enum class StreamTagLanguage {
    DUBBED,
    DUAL_AUDIO,
    MULTI,
}

data class StreamTag(
    val kind: StreamTagKind,
    val label: String = "",
    val language: StreamTagLanguage? = null,
)

/**
 * Automatic tags for a stream card, derived from the release name, addon text and debrid cache
 * state. They make the torrent list scannable without importing badge rules.
 */
internal object StreamTags {
    private val defaultPreferences = DebridStreamPreferences()

    private val seedersPattern = Regex("(?:👤|👥|🌱|seeders?\\s*[:=]?)\\s*(\\d{1,6})", RegexOption.IGNORE_CASE)
    private val sizePattern = Regex("(\\d+(?:[.,]\\d+)?)\\s*(TB|GB|MB|GiB|MiB)\\b", RegexOption.IGNORE_CASE)
    private val dubbedPattern = Regex(
        "(^|[^a-z])(dublado|pt[ ._-]?br|portugu[eê]s|brazilian)([^a-z]|$)|🇧🇷|🇵🇹",
        RegexOption.IGNORE_CASE,
    )
    private val dualAudioPattern = Regex("(^|[^a-z])(dual[ ._-]?(?:audio|áudio)?)([^a-z]|$)", RegexOption.IGNORE_CASE)
    // The shared release parser needs token boundaries, so it misses glued forms like "HDCAM" or "DDP5.1".
    private val lowQualityPattern = Regex(
        "(^|[^a-z])(hd)?(cam(rip)?|telesync|hdts|hdtc)([^a-z]|$)",
        RegexOption.IGNORE_CASE,
    )
    private val channelsPattern = Regex("(?:^|[^0-9])([5-7])\\.1(?:[^0-9]|$)")
    private val multiPattern = Regex("(^|[^a-z])(multi(?:[ ._-]?(?:audio|lang|sub))?)([^a-z]|$)|🌐", RegexOption.IGNORE_CASE)

    fun tagsFor(stream: StreamItem, includeSize: Boolean = true): List<StreamTag> {
        val facts = DebridStreamMetadata.facts(stream, defaultPreferences)
        val text = stream.tagSearchText()
        val tags = mutableListOf<StreamTag>()

        stream.cachedProviderLabel()?.let { tags += StreamTag(StreamTagKind.CACHED, "⚡ $it") }

        resolutionLabel(facts.resolution)?.let { tags += StreamTag(StreamTagKind.RESOLUTION, it) }

        languageTag(text, facts.languages)?.let { tags += StreamTag(StreamTagKind.LANGUAGE, language = it) }

        hdrLabel(facts.visualTags)?.let { tags += StreamTag(StreamTagKind.HDR, it) }
        if (DebridStreamVisualTag.IMAX in facts.visualTags) tags += StreamTag(StreamTagKind.HDR, "IMAX")
        if (DebridStreamVisualTag.THREE_D in facts.visualTags) tags += StreamTag(StreamTagKind.HDR, "3D")

        when (facts.quality) {
            DebridStreamQuality.CAM,
            DebridStreamQuality.TS,
            DebridStreamQuality.TC,
            DebridStreamQuality.SCR -> tags += StreamTag(StreamTagKind.LOW_QUALITY, facts.quality.label)
            DebridStreamQuality.BLURAY_REMUX -> tags += StreamTag(StreamTagKind.SOURCE, "REMUX")
            else -> if (lowQualityPattern.containsMatchIn(text)) {
                tags += StreamTag(StreamTagKind.LOW_QUALITY, "CAM")
            } else if (facts.quality != DebridStreamQuality.UNKNOWN) {
                tags += StreamTag(StreamTagKind.SOURCE, facts.quality.label)
            }
        }

        codecLabel(facts.encode)?.let { tags += StreamTag(StreamTagKind.CODEC, it) }

        audioLabel(facts.audioTags)?.let { tags += StreamTag(StreamTagKind.AUDIO, it) }
        val channels = channelLabel(facts.audioChannels)
            ?: channelsPattern.find(text)?.let { "${it.groupValues[1]}.1" }
        channels?.let { tags += StreamTag(StreamTagKind.AUDIO, it) }

        if (includeSize) {
            (facts.size?.let(::formatBytes) ?: sizeFromText(text))?.let {
                tags += StreamTag(StreamTagKind.SIZE, it)
            }
        }

        if (stream.isTorrentStream || stream.isDirectDebridStream) {
            seeders(text)?.let { count ->
                val kind = when {
                    count >= 50 -> StreamTagKind.SEEDERS_HIGH
                    count >= 10 -> StreamTagKind.SEEDERS_MEDIUM
                    else -> StreamTagKind.SEEDERS_LOW
                }
                tags += StreamTag(kind, "👤 $count")
            }
        }

        return tags
    }

    internal fun seeders(text: String): Int? =
        seedersPattern.find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()

    private fun languageTag(text: String, languages: List<DebridStreamLanguage>): StreamTagLanguage? = when {
        dualAudioPattern.containsMatchIn(text) -> StreamTagLanguage.DUAL_AUDIO
        DebridStreamLanguage.PT in languages || dubbedPattern.containsMatchIn(text) -> StreamTagLanguage.DUBBED
        DebridStreamLanguage.MULTI in languages || multiPattern.containsMatchIn(text) -> StreamTagLanguage.MULTI
        else -> null
    }

    private fun resolutionLabel(resolution: DebridStreamResolution): String? = when (resolution) {
        DebridStreamResolution.P2160 -> "4K"
        DebridStreamResolution.UNKNOWN -> null
        else -> resolution.label
    }

    private fun hdrLabel(tags: List<DebridStreamVisualTag>): String? {
        val dv = DebridStreamVisualTag.DV in tags
        val hdr = when {
            DebridStreamVisualTag.HDR10_PLUS in tags -> "HDR10+"
            DebridStreamVisualTag.HDR10 in tags -> "HDR10"
            DebridStreamVisualTag.HLG in tags -> "HLG"
            DebridStreamVisualTag.HDR in tags -> "HDR"
            else -> null
        }
        return when {
            dv && hdr != null -> "DV · $hdr"
            dv -> "DV"
            else -> hdr
        }
    }

    private fun codecLabel(encode: DebridStreamEncode): String? = when (encode) {
        DebridStreamEncode.HEVC -> "HEVC"
        DebridStreamEncode.AV1 -> "AV1"
        DebridStreamEncode.AVC -> "H.264"
        DebridStreamEncode.XVID -> "XviD"
        DebridStreamEncode.DIVX -> "DivX"
        else -> null
    }

    private fun audioLabel(tags: List<DebridStreamAudioTag>): String? {
        val best = listOf(
            DebridStreamAudioTag.ATMOS to "Atmos",
            DebridStreamAudioTag.TRUEHD to "TrueHD",
            DebridStreamAudioTag.DTS_X to "DTS:X",
            DebridStreamAudioTag.DTS_HD_MA to "DTS-HD MA",
            DebridStreamAudioTag.DTS_HD to "DTS-HD",
            DebridStreamAudioTag.DTS to "DTS",
            DebridStreamAudioTag.DD_PLUS to "DD+",
            DebridStreamAudioTag.DD to "DD",
            DebridStreamAudioTag.FLAC to "FLAC",
            DebridStreamAudioTag.OPUS to "Opus",
            DebridStreamAudioTag.AAC to "AAC",
        )
        return best.firstOrNull { (tag, _) -> tag in tags }?.second
    }

    private fun channelLabel(channels: List<DebridStreamAudioChannel>): String? = when {
        DebridStreamAudioChannel.CH_7_1 in channels -> "7.1"
        DebridStreamAudioChannel.CH_6_1 in channels -> "6.1"
        DebridStreamAudioChannel.CH_5_1 in channels -> "5.1"
        else -> null
    }

    private fun sizeFromText(text: String): String? {
        val match = sizePattern.find(text) ?: return null
        val value = match.groupValues[1].replace(',', '.')
        val unit = match.groupValues[2].uppercase().replace("IB", "B")
        return "$value $unit"
    }

    private fun formatBytes(bytes: Long): String? {
        if (bytes <= 0) return null
        val gb = bytes / 1_073_741_824.0
        return if (gb >= 1.0) {
            "${(gb * 10).toLong() / 10.0} GB"
        } else {
            "${(bytes / 1_048_576.0).toLong()} MB"
        }
    }

    private fun StreamItem.cachedProviderLabel(): String? {
        val status = debridCacheStatus ?: return null
        if (status.state != StreamDebridCacheState.CACHED) return null
        return DebridProviders.shortName(status.providerId)
            .ifBlank { status.providerName.trim() }
            .ifBlank { DebridProviders.displayName(status.providerId) }
    }

    private fun StreamItem.tagSearchText(): String = listOfNotNull(
        name,
        title,
        description,
        behaviorHints.filename,
        debridCacheStatus?.cachedName,
        clientResolve?.torrentName,
        clientResolve?.filename,
    ).joinToString("\n")
}

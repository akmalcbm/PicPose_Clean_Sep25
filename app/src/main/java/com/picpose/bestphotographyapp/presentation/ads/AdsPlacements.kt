package com.picpose.bestphotographyapp.presentation.ads

enum class AdFormat {
    NATIVE,
    INTERSTITIAL,
    REWARDED,
    BANNER
}

enum class AdPlacement(
    val key: String,
    val format: AdFormat,
    val description: String,
    val aliases: List<String> = emptyList()
) {
    HOME_NATIVE(
        key = "home_native",
        format = AdFormat.NATIVE,
        description = "Home feed native ad",
        aliases = listOf("native_ad", "native_1", "native_2")
    ),
    DETAIL_NATIVE(
        key = "detail_native",
        format = AdFormat.NATIVE,
        description = "Prompt/guide detail native ad",
        aliases = listOf("home_native", "native_ad", "native_1", "native_2", "native_3")
    ),
    HOME_INTERSTITIAL(
        key = "interstitial_home",
        format = AdFormat.INTERSTITIAL,
        description = "Interstitial on home navigation",
        aliases = listOf("home_interstitial", "interstitial_1", "interstitial_2")
    ),
    DETAIL_INTERSTITIAL(
        key = "interstitial_detail",
        format = AdFormat.INTERSTITIAL,
        description = "Interstitial on detail transitions",
        aliases = listOf("detail_interstitial", "interstitial_1", "interstitial_2")
    ),
    HOME_BANNER(
        key = "banner_home",
        format = AdFormat.BANNER,
        description = "Home banner ad",
        aliases = listOf("home_banner", "banner_1", "banner_other")
    ),
    REWARDED(
        key = "rewarded",
        format = AdFormat.REWARDED,
        description = "Rewarded placement",
        aliases = listOf("rewarded_ad", "rewarded_1")
    )
}

object AdsPlacementRegistry {

    private val canonicalByKey = AdPlacement.values().associateBy { it.key }

    private val aliasesToCanonical: Map<String, String> = buildMap {
        AdPlacement.values().forEach { placement ->
            // Canonical keys must always resolve to themselves.
            put(placement.key, placement.key)
        }
        AdPlacement.values().forEach { placement ->
            // Preserve first alias winner to avoid unstable remapping collisions.
            placement.aliases.forEach { alias ->
                putIfAbsent(alias, placement.key)
            }
        }
    }

    fun normalizeKey(inputKey: String): String {
        val trimmed = inputKey.trim()
        if (trimmed.isBlank()) return trimmed
        return aliasesToCanonical[trimmed] ?: trimmed
    }

    fun resolveCandidates(inputKey: String): List<String> {
        val normalized = normalizeKey(inputKey)
        val canonical = canonicalByKey[normalized]
        if (canonical == null) {
            return listOf(inputKey.trim(), normalized)
                .filter { it.isNotBlank() }
                .distinct()
        }

        return buildList {
            add(canonical.key)
            addAll(canonical.aliases)
            add(inputKey.trim())
        }
            .filter { it.isNotBlank() }
            .distinct()
    }

    fun placementForKey(inputKey: String): AdPlacement? {
        val normalized = normalizeKey(inputKey)
        return canonicalByKey[normalized]
    }
}

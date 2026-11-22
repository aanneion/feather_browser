package com.example.privacy

import android.net.Uri
import java.net.URI
import java.net.URLDecoder
import java.util.concurrent.atomic.AtomicInteger

/**
 * Lightweight, zero-dependency URL Tracking Parameter Sanitizer.
 *
 * Strips marketing, cross-site click IDs, and affiliate/referral surveillance parameters
 * (such as `utm_*`, `fbclid`, `gclid`, `msclkid`, `ttclid`, `igshid`, `si` on YouTube/Spotify, etc.)
 * before navigation while preserving functional query parameters (`q`, `v`, `t`, `list`,
 * `page`, `id`, OAuth `code`/`state`, signed storage URLs, etc.).
 */
object UrlSanitizer {

    // Known universal tracking query parameter names (lowercase)
    private val GLOBAL_TRACKING_PARAMS = hashSetOf(
        // UTM & Google Analytics / Ads
        "utm_source",
        "utm_medium",
        "utm_campaign",
        "utm_term",
        "utm_content",
        "utm_id",
        "utm_name",
        "utm_reader",
        "utm_viz_id",
        "utm_pubreferrer",
        "utm_swu",
        "gclid",
        "gclsrc",
        "dclid",
        "wbraid",
        "gbraid",
        "_ga",
        "_gl",
        "gad_source",
        "srsltid",

        // Meta / Facebook / Instagram / Threads
        "fbclid",
        "fb_action_ids",
        "fb_action_types",
        "fb_source",
        "fb_ref",
        "igshid",
        "igsh",

        // Microsoft / Bing / LinkedIn
        "msclkid",
        "li_fat_id",
        "trk",

        // TikTok / X (Twitter) / Reddit / Snapchat / Pinterest
        "ttclid",
        "twclid",
        "ScCid",
        "sccid",
        "epik",
        "rdt_cid",
        "share_id",

        // HubSpot / Mailchimp / Marketo / Klaviyo / Vero / Matomo
        "_hsenc",
        "_hsmi",
        "__hsfp",
        "__hssc",
        "__hstc",
        "mc_cid",
        "mc_eid",
        "mkt_tok",
        "_ke",
        "vero_conv",
        "vero_id",
        "pk_campaign",
        "pk_kwd",
        "pk_source",
        "pk_medium",
        "pk_content",
        "mtm_campaign",
        "mtm_kwd",
        "mtm_source",
        "mtm_medium",
        "mtm_content",
        "mtm_cid",
        "mtm_group",
        "mtm_placement",

        // Yandex / Yahoo / Brave / Misc Ad Networks
        "yclid",
        "ymclid",
        "ysclid",
        "_openstat",
        "oly_anon_id",
        "oly_enc_id",
        "rb_clickid",
        "s_cid",
        "wickedid"
    )

    // Domain-specific tracking parameters (host suffix -> parameter names)
    private val DOMAIN_SPECIFIC_PARAMS: Map<String, Set<String>> = mapOf(
        "youtube.com" to setOf("si", "pp", "feature"),
        "youtu.be" to setOf("si", "feature"),
        "spotify.com" to setOf("si", "nd"),
        "x.com" to setOf("s", "t"),
        "twitter.com" to setOf("s", "t"),
        "reddit.com" to setOf("utm_name", "correlation_id", "ref", "ref_source"),
        "amazon.com" to setOf(
            "pd_rd_r", "pd_rd_w", "pd_rd_wg",
            "pf_rd_p", "pf_rd_r", "pf_rd_s", "pf_rd_t", "pf_rd_i", "pf_rd_m",
            "qid", "sr", "dib", "dib_tag", "linkcode", "tag", "ascsubtag"
        )
    )

    // Parameters indicating a cryptographically signed URL (S3, GCS, Azure SAS, OAuth/SAML).
    // Never touch URLs carrying signatures because altering even one query param invalidates the signature.
    private val SIGNED_URL_GUARD_PARAMS = hashSetOf(
        "x-amz-signature",
        "x-amz-credential",
        "x-goog-signature",
        "sig",
        "signature",
        "samlrequest",
        "samlresponse",
        "oauth_signature"
    )

    data class SanitizeResult(
        val url: String,
        val removedCount: Int
    )

    private val sessionStrippedCount = AtomicInteger(0)

    fun getTotalStrippedInSession(): Int = sessionStrippedCount.get()

    /**
     * Cleans tracking query parameters from [rawUrl] when [enabled] is true.
     * Safe on both Android (with `android.net.Uri`) and pure JVM unit tests.
     */
    fun sanitize(rawUrl: String, enabled: Boolean = true): String {
        return sanitizeDetailed(rawUrl, enabled).url
    }

    fun sanitizeDetailed(rawUrl: String, enabled: Boolean = true): SanitizeResult {
        if (!enabled || rawUrl.isBlank()) return SanitizeResult(rawUrl, 0)
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return SanitizeResult(rawUrl, 0)
        }

        val qIndex = trimmed.indexOf('?')
        if (qIndex < 0) return SanitizeResult(rawUrl, 0)

        return try {
            val hashIndex = trimmed.indexOf('#', startIndex = qIndex + 1)
            val basePart = trimmed.substring(0, qIndex)
            val queryPart = if (hashIndex >= 0) {
                trimmed.substring(qIndex + 1, hashIndex)
            } else {
                trimmed.substring(qIndex + 1)
            }
            val fragmentPart = if (hashIndex >= 0) trimmed.substring(hashIndex) else ""

            if (queryPart.isEmpty()) return SanitizeResult(rawUrl, 0)

            val host = extractHost(basePart)
            val rawPairs = queryPart.split('&')

            // Check if URL is signed (e.g. AWS S3 presigned download, OAuth callback) — leave untouched
            for (pair in rawPairs) {
                if (pair.isEmpty()) continue
                val eqIdx = pair.indexOf('=')
                val rawKey = if (eqIdx >= 0) pair.substring(0, eqIdx) else pair
                val decodedKey = decodeComponentSafe(rawKey).lowercase()
                if (SIGNED_URL_GUARD_PARAMS.contains(decodedKey)) {
                    return SanitizeResult(rawUrl, 0)
                }
            }

            val domainSpecific = resolveDomainSpecificParams(host)
            val keptPairs = ArrayList<String>(rawPairs.size)
            var removed = 0

            for (pair in rawPairs) {
                if (pair.isEmpty()) continue
                val eqIdx = pair.indexOf('=')
                val rawKey = if (eqIdx >= 0) pair.substring(0, eqIdx) else pair
                val decodedKey = decodeComponentSafe(rawKey).lowercase()

                if (shouldStripParam(decodedKey, domainSpecific)) {
                    removed++
                } else {
                    keptPairs.add(pair)
                }
            }

            if (removed == 0) {
                SanitizeResult(rawUrl, 0)
            } else {
                sessionStrippedCount.addAndGet(removed)
                val rebuilt = buildString(trimmed.length) {
                    append(basePart)
                    if (keptPairs.isNotEmpty()) {
                        append('?')
                        keptPairs.joinTo(this, "&")
                    }
                    append(fragmentPart)
                }
                SanitizeResult(rebuilt, removed)
            }
        } catch (_: Throwable) {
            SanitizeResult(rawUrl, 0)
        }
    }

    private fun shouldStripParam(lowerKey: String, domainParams: Set<String>): Boolean {
        if (lowerKey.isBlank()) return false
        if (lowerKey.startsWith("utm_")) return true
        if (GLOBAL_TRACKING_PARAMS.contains(lowerKey)) return true
        if (domainParams.contains(lowerKey)) return true
        return false
    }

    private fun resolveDomainSpecificParams(host: String): Set<String> {
        if (host.isBlank()) return emptySet()
        val normalized = host.lowercase().removePrefix("www.").removePrefix("m.")
        for ((domain, params) in DOMAIN_SPECIFIC_PARAMS) {
            if (normalized == domain || normalized.endsWith(".$domain")) {
                return params
            }
        }
        return emptySet()
    }

    private fun extractHost(baseUrl: String): String {
        return try {
            URI(baseUrl).host?.lowercase().orEmpty()
        } catch (_: Throwable) {
            try {
                Uri.parse(baseUrl).host?.lowercase().orEmpty()
            } catch (_: Throwable) {
                val afterScheme = baseUrl.substringAfter("://", "")
                afterScheme.substringBefore('/').substringBefore(':').lowercase()
            }
        }
    }

    private fun decodeComponentSafe(value: String): String {
        return try {
            URLDecoder.decode(value, "UTF-8")
        } catch (_: Throwable) {
            value
        }
    }
}

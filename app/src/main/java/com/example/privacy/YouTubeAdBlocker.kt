package com.example.privacy

import android.net.Uri

/**
 * Specialized YouTube Ad Blocker that strips YouTube video prerolls, midrolls,
 * banner promos, and promoted video items, and dynamically fast-forwards/skips
 * unskippable ads using techniques aligned with Brave Browser & uBlock Origin.
 */
object YouTubeAdBlocker {

    fun isYouTube(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.contains("youtube.com") || lower.contains("youtube-nocookie.com") ||
            lower.contains("youtu.be")
    }

    fun isYouTubeAdRequest(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false

        // CRITICAL: NEVER block googlevideo.com in general! It carries the actual video
        // stream media chunks. Blocking regular googlevideo requests breaks playback and
        // triggers infinite buffering or playback errors. Ad-only media streams are
        // handled separately by isYouTubeAdMedia() before the caller reaches the generic
        // "never block googlevideo" guard.
        if (host.contains("googlevideo.com")) {
            return false
        }

        val path = uri.path?.lowercase() ?: ""
        // Never block core watch, embed, or player javascript endpoints.
        // In particular /youtubei/v1/get_watch and /youtubei/v1/player are REQUIRED:
        // their JSON response is pruned in-JS instead of being blocked here.
        if (path.contains("/watch") || path.contains("/embed") || path.endsWith(".js") || path.contains("/s/player/")) {
            return false
        }

        // Match Google / YouTube dedicated ad and tracking endpoints
        if (host.contains("youtube.com") || host.contains("youtube-nocookie.com")) {
            if (path.contains("/pagead/") ||
                path.contains("/api/stats/ads")) {
                return true
            }
        }

        if (host == "googleads.g.doubleclick.net" ||
            host == "pubads.g.doubleclick.net" ||
            host == "securepubads.g.doubleclick.net" ||
            host == "static.doubleclick.net" ||
            host == "ad.youtube.com" ||
            host == "ads.youtube.com" ||
            host.endsWith(".doubleclick.net")) {
            return true
        }

        return false
    }

    /**
     * True for the ad-only chunks inside a googlevideo media stream.
     *
     * Regular video/audio segments never carry an `adformat` query parameter; only the
     * preroll/midroll segments do. Blocking them removes the ad without touching the
     * content stream. This must be checked BEFORE the generic googlevideo allow rule.
     */
    fun isYouTubeAdMedia(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        if (!host.contains("googlevideo.com")) return false

        val query = uri.query?.lowercase() ?: return false
        val adFormat = uri.getQueryParameter("adformat")
        if (!adFormat.isNullOrBlank() && adFormat != "0") return true

        // TV/embedded clients request ad init playback through a dedicated endpoint.
        val path = uri.path?.lowercase() ?: ""
        if (path.contains("/initplayback") &&
            query.contains("source=youtube") &&
            query.contains("oad=")) {
            return true
        }

        return false
    }

    /**
     * JavaScript payload injected into YouTube web pages:
     * 1. CSS styling to hide all ad modules, overlays, badges, banners, and anti-adblock modals.
     * 2. JSON.parse interception plus a deep ad-pruner for playerResponse.* and [].playerResponse.*.
     * 3. fetch and XMLHttpRequest interception that both prunes parsed objects AND rewrites the
     *    raw response text (`"adPlacements"` -> `"no_ads"`) for /youtubei/v1/player,
     *    /youtubei/v1/get_watch, /youtubei/v1/next, /youtubei/v1/browse, /watch and /playlist.
     * 4. window.ytInitialPlayerResponse / ytInitialData proxies so the inline page config is
     *    cleaned before YouTube's player reads it.
     * 5. MutationObserver + interval skip engine that clicks real skip buttons and, as a
     *    last resort, mutes and repeatedly seeks unskippable ads to their end.
     */
    fun getYouTubeAdBlockScript(): String {
        return """
        (function() {
            if (window.__feather_yt_adblock_active) return;
            window.__feather_yt_adblock_active = true;

            // 1. Hide ad DOM containers with CSS
            const injectAdStyles = function() {
                if (document.getElementById('__feather_yt_ad_styles')) return;
                const style = document.createElement('style');
                style.id = '__feather_yt_ad_styles';
                style.textContent = `
                    .video-ads,
                    .ytp-ad-module,
                    .ytp-ad-overlay-container,
                    .ytp-ad-message-container,
                    .ytp-ad-progress,
                    .ytp-ad-progress-list,
                    .ytp-ad-preview-container,
                    .ytp-ad-image-overlay,
                    .ytp-ad-player-overlay,
                    .ytp-ad-action-interstitial,
                    ytm-promoted-sparkles-web-renderer,
                    ytd-promoted-sparkles-web-renderer,
                    ytd-promoted-video-renderer,
                    ytd-banner-promo-renderer,
                    ytd-statement-banner-renderer,
                    ytd-in-feed-ad-layout-renderer,
                    ytd-ad-slot-renderer,
                    ytm-promoted-item-renderer,
                    ytm-companion-ad-renderer,
                    ytm-paid-content-overlay-renderer,
                    #player-ads,
                    #masthead-ad,
                    .ad-container,
                    .ad-showing .ytp-ad-overlay-container,
                    ytd-enforcement-message-view-model,
                    tp-yt-paper-dialog[role="dialog"]:has(ytd-enforcement-message-view-model),
                    tp-yt-iron-overlay-backdrop {
                        display: none !important;
                        visibility: hidden !important;
                        opacity: 0 !important;
                        pointer-events: none !important;
                    }
                `;
                (document.head || document.documentElement).appendChild(style);
            };

            if (document.head || document.documentElement) {
                injectAdStyles();
            } else {
                document.addEventListener('DOMContentLoaded', injectAdStyles);
            }

            // 2. Deep ad-pruning helpers. uBlock Origin prunes the top level, the nested
            //    playerResponse object, and the same fields inside array responses
            //    (for example onResponseReceivedActions[].playerResponse). We do the same
            //    so feed, playlist and get_watch responses are covered too.
            const AD_OBJECT_KEYS = [
                'adPlacements',
                'adSlots',
                'playerAds',
                'adBreakHeartbeatParams',
                'adBreakService',
                'legacyImportant'
            ];

            const prunePlayerResponse = function(pr) {
                if (!pr || typeof pr !== 'object') return pr;
                try {
                    for (let i = 0; i < AD_OBJECT_KEYS.length; i++) {
                        if (Object.prototype.hasOwnProperty.call(pr, AD_OBJECT_KEYS[i])) {
                            delete pr[AD_OBJECT_KEYS[i]];
                        }
                    }
                } catch(e) {}
                return pr;
            };

            const pruneAdData = function(obj) {
                if (!obj || typeof obj !== 'object') return obj;
                try {
                    prunePlayerResponse(obj);
                    if (Array.isArray(obj)) {
                        for (let i = 0; i < obj.length; i++) {
                            const item = obj[i];
                            if (!item || typeof item !== 'object') continue;
                            prunePlayerResponse(item);
                            if (item.playerResponse) prunePlayerResponse(item.playerResponse);
                        }
                    } else {
                        if (obj.playerResponse) prunePlayerResponse(obj.playerResponse);
                        if (Array.isArray(obj.playerResponses)) {
                            for (let i = 0; i < obj.playerResponses.length; i++) {
                                prunePlayerResponse(obj.playerResponses[i]);
                            }
                        }
                    }
                } catch(e) {}
                return obj;
            };

            // Raw-text rewrite mirroring uBlock Origin's trusted-replace rules. YouTube
            // only looks for the exact keys "adPlacements" and "adSlots"; renaming them
            // removes the ad even if the response is parsed by code we cannot wrap.
            const replaceAdKeys = function(text) {
                if (typeof text !== 'string' || text.length === 0) return text;
                if (text.indexOf('adPlacements') === -1 &&
                    text.indexOf('adSlots') === -1 &&
                    text.indexOf('playerAds') === -1 &&
                    text.indexOf('adBreakHeartbeatParams') === -1 &&
                    text.indexOf('legacyImportant') === -1) {
                    return text;
                }
                try {
                    return text
                        .replace(/"adPlacements"/g, '"no_ads"')
                        .replace(/"adSlots"/g, '"no_ads"')
                        .replace(/"playerAds"/g, '"no_player_ads"')
                        .replace(/"adBreakHeartbeatParams"/g, '"no_ad_break"')
                        .replace(/"legacyImportant"/g, '"no_legacy_important"');
                } catch(e) {
                    return text;
                }
            };

            // /youtubei/v1/get_watch is the modern player endpoint; /watch? and
            // /playlist?list= cover SPA navigation responses. None of these are blocked
            // at the network layer, only their bodies are rewritten here.
            const AD_URL_MARKERS = [
                '/youtubei/v1/player',
                '/youtubei/v1/get_watch',
                '/youtubei/v1/next',
                '/youtubei/v1/browse',
                '/youtubei/v1/reel/reel_item_watch',
                '/playlist?list=',
                '/watch?'
            ];

            const isAdResponseUrl = function(url) {
                if (!url || typeof url !== 'string') return false;
                for (let i = 0; i < AD_URL_MARKERS.length; i++) {
                    if (url.indexOf(AD_URL_MARKERS[i]) !== -1) return true;
                }
                return false;
            };

            // 3. Intercept JSON.parse
            try {
                const origJSONParse = JSON.parse;
                JSON.parse = function() {
                    const data = origJSONParse.apply(this, arguments);
                    try { pruneAdData(data); } catch(e) {}
                    return data;
                };
            } catch(e) {}

            // 4. Intercept window.fetch and rewrite ad response bodies
            try {
                const origFetch = window.fetch;
                window.fetch = async function() {
                    const response = await origFetch.apply(this, arguments);
                    try {
                        const url = arguments[0];
                        const urlStr = typeof url === 'string' ? url : (url && url.url ? url.url : '');
                        if (isAdResponseUrl(urlStr)) {
                            const clone = response.clone();
                            const text = await clone.text();
                            const replaced = replaceAdKeys(text);
                            if (replaced !== text) {
                                // The cloned body is already decoded, so the original
                                // content-encoding/length no longer describe it.
                                const headers = new Headers(response.headers);
                                headers.delete('content-encoding');
                                headers.delete('content-length');
                                return new Response(replaced, {
                                    status: response.status,
                                    statusText: response.statusText,
                                    headers: headers
                                });
                            }
                        }
                    } catch(e) {}
                    return response;
                };
            } catch(e) {}

            // 5. Intercept XMLHttpRequest. Both responseText and response are patched so
            //    responseType='json' (where responseText throws) is still cleaned.
            try {
                const origOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    try {
                        this.__feather_url = typeof url === 'string'
                            ? url
                            : (url && url.url ? url.url : '');
                    } catch(e) {
                        this.__feather_url = '';
                    }
                    return origOpen.apply(this, arguments);
                };

                const patchXhrProperty = function(name) {
                    try {
                        const desc = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, name);
                        if (!desc || !desc.get) return;
                        Object.defineProperty(XMLHttpRequest.prototype, name, {
                            get: function() {
                                const value = desc.get.call(this);
                                try {
                                    if (this.__feather_url && isAdResponseUrl(this.__feather_url)) {
                                        if (typeof value === 'string') return replaceAdKeys(value);
                                        if (value && typeof value === 'object') return pruneAdData(value);
                                    }
                                } catch(e) {}
                                return value;
                            },
                            configurable: true
                        });
                    } catch(e) {}
                };
                patchXhrProperty('responseText');
                patchXhrProperty('response');
            } catch(e) {}

            // 6. Proxy the inline config globals so YouTube's own parser sees clean data.
            let internalPlayerResponse = window.ytInitialPlayerResponse;
            try {
                Object.defineProperty(window, 'ytInitialPlayerResponse', {
                    get: function() {
                        return internalPlayerResponse;
                    },
                    set: function(val) {
                        internalPlayerResponse = pruneAdData(val);
                    },
                    configurable: true
                });
                if (internalPlayerResponse) {
                    pruneAdData(internalPlayerResponse);
                }
            } catch(e) {}

            let internalInitialData = window.ytInitialData;
            try {
                Object.defineProperty(window, 'ytInitialData', {
                    get: function() {
                        return internalInitialData;
                    },
                    set: function(val) {
                        internalInitialData = pruneAdData(val);
                    },
                    configurable: true
                });
                if (internalInitialData) {
                    pruneAdData(internalInitialData);
                }
            } catch(e) {}

            // 7. Conservative ad skipper & unskippable-ad fast-forward engine
            //
            // IMPORTANT: this engine only reacts while the YouTube player *itself* reports an
            // active ad (.html5-video-player.ad-showing / .ad-interrupting). Earlier revisions
            // also matched loose descendants such as .ytp-ad-player-overlay, which stays in the
            // DOM during normal playback; that made the player mute itself, run at 16x and jump
            // to the end of the video whenever the user scrubbed or used the precise-seek
            // preview. Ad actions are also suspended while a drag/scrub gesture is in flight so
            // the system seek bar and the in-player preview stay smooth.
            const skipSelectors = [
                '.ytp-ad-skip-button-modern',
                '.ytp-ad-skip-button',
                '.ytp-skip-ad-button',
                '.ytp-ad-skip-button-slot button',
                'button.ytp-ad-skip-button',
                '.ytm-ad-skip-button',
                '.ytp-ad-overlay-close-button'
            ];

            let adHackApplied = false;
            let savedMuted = null;
            let savedPlaybackRate = null;
            let skipAdRequested = false;
            // Last state observed while normal content was playing. Seeding the restore
            // values from here (instead of from the first ad frame) prevents a muted ad
            // from leaving the real video muted after it ends.
            let lastContentMuted = false;
            let lastContentPlaybackRate = 1;

            const getPlayerElement = function() {
                return document.querySelector('.html5-video-player') || document.getElementById('movie_player');
            };

            const getAdVideo = function() {
                return document.querySelector('video.html5-main-video') || document.querySelector('video');
            };

            const isAdShowing = function(player) {
                try {
                    if (!player) return false;
                    return player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting');
                } catch(e) {
                    return false;
                }
            };

            // The user always wins: never touch the media element while a tap, drag, scrub or
            // precise-seek preview gesture is in progress.
            const isUserScrubbing = function(player, video) {
                try {
                    if (video && video.seeking) return true;
                    if (player && (player.classList.contains('ytp-scrubber-dragging') ||
                                   player.classList.contains('ytp-precise-seeking') ||
                                   player.classList.contains('ytp-scrubber-button-hover'))) return true;
                    if (document.querySelector('.ytp-scrubber-dragging, .ytp-precise-seeking')) return true;
                } catch(e) {}
                return false;
            };

            const restoreAdPlayback = function() {
                if (!adHackApplied) return;
                adHackApplied = false;
                skipAdRequested = false;
                try {
                    const video = getAdVideo();
                    if (video) {
                        if (savedMuted !== null) video.muted = savedMuted;
                        if (savedPlaybackRate !== null) video.playbackRate = savedPlaybackRate;
                    }
                } catch(e) {}
                savedMuted = null;
                savedPlaybackRate = null;
            };

            const handleVideoAds = function() {
                try {
                    // Dismiss the anti-adblock enforcement dialog if YouTube renders it
                    const enforcementModal = document.querySelector('ytd-enforcement-message-view-model, tp-yt-paper-dialog[role="dialog"]');
                    if (enforcementModal && (enforcementModal.textContent || '').indexOf('Ad blockers') !== -1) {
                        enforcementModal.remove();
                        document.querySelectorAll('tp-yt-iron-overlay-backdrop').forEach(function(b) { b.remove(); });
                        const modalVideo = getAdVideo();
                        if (modalVideo && modalVideo.paused) {
                            modalVideo.play().catch(function() {});
                        }
                    }

                    const player = getPlayerElement();
                    const video = getAdVideo();
                    const adShowing = isAdShowing(player);

                    // Publish a small, cheap-to-read ad state for the media bridge. This is
                    // what stops the notification from briefly switching to the ad's title,
                    // duration, or `ended` event while a preroll/midroll is being removed.
                    if (adShowing) {
                        if (window.__feather_yt_ad_active !== true) {
                            window.__feather_yt_ad_started_at = Date.now();
                        }
                        window.__feather_yt_ad_active = true;
                    } else {
                        if (window.__feather_yt_ad_active === true) {
                            window.__feather_last_ad_end_at = Date.now();
                        }
                        window.__feather_yt_ad_active = false;
                        skipAdRequested = false;
                        // Normal video: make sure nothing we did during an ad sticks around.
                        restoreAdPlayback();
                        // Remember the normal playback state so the next ad can restore it.
                        if (video) {
                            lastContentMuted = video.muted;
                            lastContentPlaybackRate = video.playbackRate;
                        }
                        return;
                    }

                    // 1. A real skip button always wins when it is present.
                    for (let i = 0; i < skipSelectors.length; i++) {
                        const btn = document.querySelector(skipSelectors[i]);
                        if (btn) {
                            btn.click();
                            return;
                        }
                    }

                    // 1b. Ask YouTube's own player to skip, once per ad. This is a no-op
                    //     for genuinely unskippable ads, which the fallback below handles.
                    if (!skipAdRequested) {
                        skipAdRequested = true;
                        try {
                            if (player && typeof player.skipAd === 'function') {
                                player.skipAd();
                            }
                        } catch(e) {}
                    }

                    // 2. Unskippable ad fallback: mute + fast-forward + repeatedly jump to the
                    //    end. Seeking on every tick (instead of once) also handles ad breaks
                    //    that contain several ads, because a new ad video starts back at 0.
                    //    Skipped entirely while the user is scrubbing.
                    if (!video || isUserScrubbing(player, video)) return;

                    if (!adHackApplied) {
                        savedMuted = lastContentMuted;
                        savedPlaybackRate = lastContentPlaybackRate;
                        adHackApplied = true;
                    }
                    video.muted = true;
                    try {
                        if (video.playbackRate < 8) {
                            video.playbackRate = 16.0;
                        }
                    } catch(e) {}
                    try {
                        const duration = video.duration;
                        if (isFinite(duration) && duration > 0) {
                            const target = Math.max(0, duration - 0.05);
                            if (video.currentTime < target - 0.25) {
                                video.currentTime = target;
                            }
                        }
                    } catch(e) {}
                } catch(e) {
                    restoreAdPlayback();
                }
            };

            let observedPlayer = null;
            let adObserver = null;
            const attachAdObserver = function() {
                try {
                    if (typeof MutationObserver !== 'function') return true;
                    const player = getPlayerElement();
                    if (!player) return false;
                    if (player === observedPlayer && adObserver) return true;
                    if (adObserver) {
                        try { adObserver.disconnect(); } catch(e) {}
                    }
                    adObserver = new MutationObserver(function() { handleVideoAds(); });
                    adObserver.observe(player, { attributes: true, attributeFilter: ['class'] });
                    observedPlayer = player;
                    return true;
                } catch(e) {
                    return true;
                }
            };

            // Cheap scheduling: the player element's class list changes exactly when an ad
            // starts or ends, so that is the only thing worth observing (never document.body,
            // which mutates thousands of times per minute and used to stall the UI thread
            // while the user was scrubbing). The slow interval doubles as a self-healing
            // fallback that re-attaches the observer when YouTube swaps the player node.
            attachAdObserver();
            setInterval(function() {
                attachAdObserver();
                handleVideoAds();
            }, 500);

            window.addEventListener('yt-navigate-finish', handleVideoAds);
            window.addEventListener('yt-page-data-updated', handleVideoAds);
        })();
        """.trimIndent()
    }
}

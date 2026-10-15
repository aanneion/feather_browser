package com.example.privacy

import android.net.Uri

/**
 * Specialized YouTube Ad Blocker that strips YouTube video prerolls, midrolls,
 * banner promos, promoted video items, and dynamically fast-forwards/skips unskippable ads
 * using techniques aligned with Brave Browser & uBlock Origin.
 */
object YouTubeAdBlocker {

    fun isYouTube(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.contains("youtube.com") || lower.contains("youtu.be")
    }

    fun isYouTubeAdRequest(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false

        // CRITICAL: NEVER block googlevideo.com! It carries the actual video stream media chunks.
        // Blocking googlevideo.com breaks playback and triggers infinite buffering or playback errors.
        if (host.contains("googlevideo.com")) {
            return false
        }

        val path = uri.path?.lowercase() ?: ""
        // Never block core watch, embed, or player javascript endpoints
        if (path.contains("/watch") || path.contains("/embed") || path.endsWith(".js") || path.contains("/s/player/")) {
            return false
        }

        // Match Google / YouTube dedicated ad and tracking endpoints
        if (host.contains("youtube.com")) {
            if (path.contains("/pagead/") ||
                path.contains("/api/stats/ads") ||
                path.contains("/ptracking")) {
                return true
            }
        }

        if (host == "googleads.g.doubleclick.net" ||
            host == "static.doubleclick.net" ||
            host == "ad.youtube.com" ||
            host == "ads.youtube.com" ||
            host.endsWith(".doubleclick.net")) {
            return true
        }

        return false
    }

    /**
     * JavaScript payload injected into YouTube web pages:
     * 1. CSS styling to hide all ad modules, overlays, badges, banners, and anti-adblock modals.
     * 2. JSON Response interception (window.fetch and XMLHttpRequest) to prune adPlacements, adSlots,
     *    playerAds from YouTube's internal API responses (/youtubei/v1/player and /youtubei/v1/next).
     * 3. Global JSON.parse interception and window.ytInitialPlayerResponse proxying.
     * 4. High-frequency MutationObserver + interval ad-skipper: fast-forwards ad video duration,
     *    mutes ads, triggers skip buttons, and closes anti-adblock modals.
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

            // 2. Helper to clean ad data structures from YouTube player JSON responses
            const pruneAdData = function(obj) {
                if (!obj || typeof obj !== 'object') return obj;
                try {
                    if (Array.isArray(obj.adPlacements)) obj.adPlacements = [];
                    if (Array.isArray(obj.adSlots)) obj.adSlots = [];
                    if (obj.playerAds) delete obj.playerAds;
                    if (obj.adBreakHeartbeatParams) delete obj.adBreakHeartbeatParams;
                    if (obj.adBreakService) delete obj.adBreakService;
                } catch(e) {}
                return obj;
            };

            // 3. Intercept JSON.parse
            try {
                const origJSONParse = JSON.parse;
                JSON.parse = function() {
                    const data = origJSONParse.apply(this, arguments);
                    return pruneAdData(data);
                };
            } catch(e) {}

            // 4. Intercept window.fetch for /youtubei/v1/player and /youtubei/v1/next
            try {
                const origFetch = window.fetch;
                window.fetch = async function() {
                    const response = await origFetch.apply(this, arguments);
                    try {
                        const url = arguments[0];
                        const urlStr = typeof url === 'string' ? url : (url && url.url ? url.url : '');
                        if (urlStr.includes('/youtubei/v1/player') || urlStr.includes('/youtubei/v1/next')) {
                            const clone = response.clone();
                            const json = await clone.json();
                            const cleaned = pruneAdData(json);
                            return new Response(JSON.stringify(cleaned), {
                                status: response.status,
                                statusText: response.statusText,
                                headers: response.headers
                            });
                        }
                    } catch(e) {}
                    return response;
                };
            } catch(e) {}

            // 5. Intercept XMLHttpRequest for YouTube API responses
            try {
                const origOpen = XMLHttpRequest.prototype.open;
                XMLHttpRequest.prototype.open = function(method, url) {
                    this.__feather_url = url;
                    return origOpen.apply(this, arguments);
                };
                const origResponseTextDesc = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'responseText');
                if (origResponseTextDesc && origResponseTextDesc.get) {
                    Object.defineProperty(XMLHttpRequest.prototype, 'responseText', {
                        get: function() {
                            const origText = origResponseTextDesc.get.call(this);
                            if (this.__feather_url && typeof this.__feather_url === 'string' &&
                               (this.__feather_url.includes('/youtubei/v1/player') || this.__feather_url.includes('/youtubei/v1/next'))) {
                                try {
                                    const parsed = JSON.parse(origText);
                                    return JSON.stringify(pruneAdData(parsed));
                                } catch(e) {}
                            }
                            return origText;
                        },
                        configurable: true
                    });
                }
            } catch(e) {}

            // 6. Proxy ytInitialPlayerResponse
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
            let adSeekDone = false;
            let savedMuted = null;
            let savedPlaybackRate = null;

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
                adSeekDone = false;
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

                    if (!isAdShowing(player)) {
                        // Normal video: make sure nothing we did during an ad sticks around.
                        restoreAdPlayback();
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

                    // 2. Unskippable ad fallback: mute + fast-forward + jump to the end.
                    //    Skipped entirely while the user is scrubbing.
                    if (!video || isUserScrubbing(player, video)) return;

                    if (!adHackApplied) {
                        savedMuted = video.muted;
                        savedPlaybackRate = video.playbackRate;
                        adHackApplied = true;
                        adSeekDone = false;
                    }
                    video.muted = true;
                    if (video.playbackRate < 8) {
                        video.playbackRate = 16.0;
                    }
                    if (!adSeekDone && isFinite(video.duration) && video.duration > 0) {
                        adSeekDone = true;
                        const target = video.duration - 0.05;
                        if (target > video.currentTime) {
                            video.currentTime = target;
                        }
                    }
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

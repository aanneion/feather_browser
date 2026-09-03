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

            // 7. Fast video ad skipper & auto fast-forward engine
            const skipSelectors = [
                '.ytp-ad-skip-button',
                '.ytp-ad-skip-button-modern',
                '.ytp-skip-ad-button',
                '.ytp-ad-skip-button-slot',
                'button.ytp-ad-skip-button',
                '.ytm-ad-skip-button',
                '[class*="skip-button"]',
                '.ytp-ad-preview-container',
                '.ytp-ad-overlay-close-button',
                '.ytp-ad-skip-button-container'
            ];

            const handleVideoAds = function() {
                try {
                    // Check for anti-adblock modal and dismiss it
                    const enforcementModal = document.querySelector('ytd-enforcement-message-view-model, tp-yt-paper-dialog[role="dialog"]');
                    if (enforcementModal) {
                        const hasAdblockText = (enforcementModal.textContent || '').includes('Ad blockers');
                        if (hasAdblockText) {
                            enforcementModal.remove();
                            const backdrops = document.querySelectorAll('tp-yt-iron-overlay-backdrop');
                            backdrops.forEach(b => b.remove());
                            const video = document.querySelector('video');
                            if (video && video.paused) {
                                video.play().catch(function() {});
                            }
                        }
                    }

                    const player = document.querySelector('.html5-video-player');
                    const isAdShowing = (player && (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting'))) ||
                                        document.querySelector('.ad-showing, .ad-interrupting, .ytp-ad-player-overlay');

                    if (isAdShowing) {
                        // 1. Immediately click skip buttons
                        for (let i = 0; i < skipSelectors.length; i++) {
                            const btn = document.querySelector(skipSelectors[i]);
                            if (btn) {
                                btn.click();
                                break;
                            }
                        }

                        // 2. Mute, speed up, and fast-forward ad
                        const video = document.querySelector('video.html5-main-video') || document.querySelector('video');
                        if (video) {
                            video.muted = true;
                            video.playbackRate = 16.0;
                            if (isFinite(video.duration) && video.duration > 0) {
                                video.currentTime = video.duration + 0.5;
                            } else {
                                video.currentTime = 999999;
                            }
                        }
                    }
                } catch(e) {}
            };

            // High frequency interval (100ms) + MutationObserver for instant response
            setInterval(handleVideoAds, 100);
            document.addEventListener('timeupdate', handleVideoAds, true);

            try {
                const observer = new MutationObserver(function() {
                    handleVideoAds();
                });
                const targetNode = document.body || document.documentElement;
                if (targetNode) {
                    observer.observe(targetNode, { childList: true, subtree: true, attributes: true, attributeFilter: ['class'] });
                }
            } catch(e) {}
        })();
        """.trimIndent()
    }
}

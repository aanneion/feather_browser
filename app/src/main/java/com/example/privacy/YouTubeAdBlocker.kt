package com.example.privacy

import android.net.Uri

/**
 * Specialized YouTube Ad Blocker that strips YouTube video prerolls, midrolls,
 * banner promos, promoted video items, and dynamically fast-forwards/skips unskippable ads.
 */
object YouTubeAdBlocker {

    fun isYouTube(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        val lower = url.lowercase()
        return lower.contains("youtube.com") || lower.contains("youtu.be")
    }

    fun isYouTubeAdRequest(uri: Uri): Boolean {
        val host = uri.host?.lowercase() ?: return false
        val pathAndQuery = ((uri.path ?: "") + (uri.query?.let { "?$it" } ?: "")).lowercase()

        // Match Google / YouTube ad and tracking endpoints
        if (host.contains("youtube.com") || host.contains("googlevideo.com")) {
            if (pathAndQuery.contains("/pagead/") ||
                pathAndQuery.contains("/api/stats/ads") ||
                pathAndQuery.contains("/youtubei/v1/player/ad_break") ||
                pathAndQuery.contains("/get_midroll_info") ||
                pathAndQuery.contains("/ptracking") ||
                pathAndQuery.contains("ctier=a") ||
                pathAndQuery.contains("&adformat=") ||
                pathAndQuery.contains("ad_type=") ||
                pathAndQuery.contains("ad_break")) {
                return true
            }
        }

        if (host == "googleads.g.doubleclick.net" ||
            host == "static.doubleclick.net" ||
            host == "ad.youtube.com" ||
            host.endsWith(".doubleclick.net")) {
            return true
        }

        return false
    }

    /**
     * JavaScript payload injected into YouTube web pages to remove ad slots from JSON
     * responses, hide cosmetic ad banners, and instantly skip/fast-forward video ads.
     */
    fun getYouTubeAdBlockScript(): String {
        return """
        (function() {
            if (window.__feather_yt_adblock_installed) return;
            window.__feather_yt_adblock_installed = true;

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
                    .ad-showing .ytp-ad-overlay-container {
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

            // 2. Intercept JSON.parse to remove ad placements and slots from YouTube player config
            try {
                const origJSONParse = JSON.parse;
                JSON.parse = function() {
                    const data = origJSONParse.apply(this, arguments);
                    try {
                        if (data && typeof data === 'object') {
                            if (Array.isArray(data.adPlacements)) data.adPlacements = [];
                            if (Array.isArray(data.adSlots)) data.adSlots = [];
                            if (data.playerAds) delete data.playerAds;
                            if (data.adBreakHeartbeatParams) delete data.adBreakHeartbeatParams;
                        }
                    } catch(e) {}
                    return data;
                };
            } catch(e) {}

            // Clean ytInitialPlayerResponse if already defined
            const cleanPlayerResponse = function() {
                try {
                    if (window.ytInitialPlayerResponse && typeof window.ytInitialPlayerResponse === 'object') {
                        if (Array.isArray(window.ytInitialPlayerResponse.adPlacements)) {
                            window.ytInitialPlayerResponse.adPlacements = [];
                        }
                        if (Array.isArray(window.ytInitialPlayerResponse.adSlots)) {
                            window.ytInitialPlayerResponse.adSlots = [];
                        }
                        if (window.ytInitialPlayerResponse.playerAds) {
                            delete window.ytInitialPlayerResponse.playerAds;
                        }
                    }
                } catch(e) {}
            };
            cleanPlayerResponse();

            // 3. Fast video ad skipper and auto-fast-forward
            const skipSelectors = [
                '.ytp-ad-skip-button',
                '.ytp-ad-skip-button-modern',
                '.ytp-skip-ad-button',
                '.ytp-ad-skip-button-slot',
                'button.ytp-ad-skip-button',
                '.ytm-ad-skip-button',
                '[class*="skip-button"]',
                '.ytp-ad-preview-container',
                '.ytp-ad-overlay-close-button'
            ];

            const handleVideoAds = function() {
                try {
                    const player = document.querySelector('.html5-video-player');
                    if (!player) return;

                    const isAdShowing = player.classList.contains('ad-showing') || 
                                        player.classList.contains('ad-interrupting');

                    if (isAdShowing) {
                        // Check for click-to-skip button first
                        for (let i = 0; i < skipSelectors.length; i++) {
                            const btn = document.querySelector(skipSelectors[i]);
                            if (btn) {
                                btn.click();
                                return;
                            }
                        }

                        const video = document.querySelector('video.html5-main-video') || document.querySelector('video');
                        if (video) {
                            video.muted = true;
                            video.playbackRate = 16.0;
                            if (isFinite(video.duration) && video.duration > 0) {
                                video.currentTime = video.duration + 0.1;
                            }
                            const skipSlot = document.querySelector('.ytp-ad-skip-button-container, .ytp-ad-player-overlay-skip-or-preview');
                            if (skipSlot) skipSlot.click();
                        }
                    }
                } catch(e) {}
            };

            setInterval(handleVideoAds, 500);
            document.addEventListener('timeupdate', handleVideoAds, true);
        })();
        """.trimIndent()
    }
}

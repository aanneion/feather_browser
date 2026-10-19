package com.example.privacy

import com.example.browser.FingerprintPreset

object FingerprintScriptGenerator {

    /**
     * Generates a lightweight, crash-safe anti-fingerprinting and identity emulation script.
     */
    fun generateInjectionScript(
        preset: FingerprintPreset,
        customUserAgent: String? = null,
        enableCanvasNoise: Boolean = true
    ): String {
        val platform = preset.platform
        val vendor = preset.vendor
        val cores = preset.hardwareConcurrency
        val memory = preset.deviceMemory
        val isMobile = preset.isMobile
        val uaPlatform = when {
            platform.contains("Win") -> "Windows"
            platform.contains("Mac") -> "macOS"
            platform.contains("Chrome") -> "Chrome OS"
            else -> "Android"
        }
        val presetKey = preset.name + "_" + Integer.toHexString(customUserAgent.orEmpty().hashCode())

        return """
        (function() {
            try {
                // Re-injecting the same identity is a no-op; switching profiles applies the
                // new identity immediately without stacking duplicate property descriptors.
                if (window.__feather_fp_key === '$presetKey') return;
                window.__feather_fp_key = '$presetKey';
                const targetPlatform = '$platform';
                const targetVendor = '$vendor';
                const targetCores = $cores;
                const targetMemory = $memory;
                const targetMobile = $isMobile;
                const targetUaPlatform = '$uaPlatform';

                // Prototype-level override (clean, native-like, passes Object.hasOwnProperty checks)
                if (typeof Navigator !== 'undefined' && Navigator.prototype) {
                    try {
                        Object.defineProperty(Navigator.prototype, 'platform', {
                            get: function() { return targetPlatform; },
                            configurable: true,
                            enumerable: true
                        });
                    } catch(e) {}

                    try {
                        Object.defineProperty(Navigator.prototype, 'vendor', {
                            get: function() { return targetVendor; },
                            configurable: true,
                            enumerable: true
                        });
                    } catch(e) {}

                    try {
                        Object.defineProperty(Navigator.prototype, 'hardwareConcurrency', {
                            get: function() { return targetCores; },
                            configurable: true,
                            enumerable: true
                        });
                    } catch(e) {}

                    if ('deviceMemory' in Navigator.prototype) {
                        try {
                            Object.defineProperty(Navigator.prototype, 'deviceMemory', {
                                get: function() { return targetMemory; },
                                configurable: true,
                                enumerable: true
                            });
                        } catch(e) {}
                    }

                    // Client Hints synchronization (Crucial for modern Chromium sites)
                    if (navigator.userAgentData) {
                        try {
                            const origUAD = navigator.userAgentData;
                            const spoofedUAD = {
                                brands: origUAD.brands || [
                                    { brand: 'Chromium', version: '131' },
                                    { brand: 'Google Chrome', version: '131' },
                                    { brand: 'Not_A Brand', version: '24' }
                                ],
                                mobile: targetMobile,
                                platform: targetUaPlatform,
                                getHighEntropyValues: function(hints) {
                                    return origUAD.getHighEntropyValues ? origUAD.getHighEntropyValues(hints).then(function(values) {
                                        return Object.assign({}, values, {
                                            platform: targetUaPlatform,
                                            mobile: targetMobile
                                        });
                                    }) : Promise.resolve({
                                        platform: targetUaPlatform,
                                        mobile: targetMobile
                                    });
                                },
                                toJSON: function() {
                                    return {
                                        brands: this.brands,
                                        mobile: this.mobile,
                                        platform: this.platform
                                    };
                                }
                            };
                            Object.defineProperty(Navigator.prototype, 'userAgentData', {
                                get: function() { return spoofedUAD; },
                                configurable: true,
                                enumerable: true
                            });
                        } catch(e) {}
                    }
                }
            } catch(e) { }
        })();
        """.trimIndent()
    }

    /**
     * Injects a background audio/video playback shim that prevents YouTube, SoundCloud, Spotify,
     * and HTML5 video players from pausing when switching tabs or backgrounding the app.
     */
    fun generateBackgroundPlayScript(
        includeBackgroundPlay: Boolean = true,
        includeMediaSession: Boolean = true
    ): String {
        val includeBg = if (includeBackgroundPlay) "true" else "false"
        val includeMedia = if (includeMediaSession) "true" else "false"
        return """
        (function() {
            try {
                const featherIncludeBg = $includeBg;
                const featherIncludeMedia = $includeMedia;

                // The background-play shim and the media-session bridge are independent.
                // Installing the media bridge must never spoof page visibility: doing so
                // makes ordinary foreground tabs report as visible while hidden.
                if (featherIncludeBg && !window.__feather_bg_play_active) {
                    window.__feather_bg_play_active = true;

                    // -----------------------------------------------------------------------------
                    // 1. Spoof the Page Visibility API so media sites report visible and focused
                    // -----------------------------------------------------------------------------
                    ['hidden', 'webkitHidden'].forEach(function(prop) {
                    try {
                        Object.defineProperty(document, prop, { get: function() { return false; }, configurable: true, enumerable: true });
                        Object.defineProperty(Document.prototype, prop, { get: function() { return false; }, configurable: true, enumerable: true });
                    } catch(e) {}
                });

                ['visibilityState', 'webkitVisibilityState'].forEach(function(prop) {
                    try {
                        Object.defineProperty(document, prop, { get: function() { return 'visible'; }, configurable: true, enumerable: true });
                        Object.defineProperty(Document.prototype, prop, { get: function() { return 'visible'; }, configurable: true, enumerable: true });
                    } catch(e) {}
                });

                try {
                    document.hasFocus = function() { return true; };
                    Document.prototype.hasFocus = function() { return true; };
                } catch(e) {}

                // Suppress visibility change events from reaching YouTube/media players
                try {
                    const origAddEventListener = EventTarget.prototype.addEventListener;
                    EventTarget.prototype.addEventListener = function(type, listener, options) {
                        if (type === 'visibilitychange' || type === 'webkitvisibilitychange') {
                            return;
                        }
                        return origAddEventListener.apply(this, arguments);
                    };
                    ['visibilitychange', 'webkitvisibilitychange'].forEach(function(evt) {
                        window.addEventListener(evt, function(e) { e.stopImmediatePropagation(); }, true);
                        document.addEventListener(evt, function(e) { e.stopImmediatePropagation(); }, true);
                    });
                } catch(e) {}

                // -----------------------------------------------------------------------------
                // 2. Spoof IntersectionObserver so offscreen players do not pause themselves
                // -----------------------------------------------------------------------------
                try {
                    if ('IntersectionObserver' in window) {
                        const OrigObserver = window.IntersectionObserver;
                        window.IntersectionObserver = function(callback, options) {
                            const wrappedCallback = function(entries, obs) {
                                try {
                                    const modified = entries.map(function(entry) {
                                        const target = entry.target;
                                        if (target && (target.tagName === 'VIDEO' || target.id === 'player' || (target.className && typeof target.className === 'string' && (target.className.indexOf('player') !== -1 || target.className.indexOf('video') !== -1)))) {
                                            return new Proxy(entry, {
                                                get: function(t, p) {
                                                    if (p === 'isIntersecting') return true;
                                                    if (p === 'intersectionRatio') return 1.0;
                                                    return t[p];
                                                }
                                            });
                                        }
                                        return entry;
                                    });
                                    return callback(modified, obs);
                                } catch(e) {
                                    return callback(entries, obs);
                                }
                            };
                            return new OrigObserver(wrappedCallback, options);
                        };
                        window.IntersectionObserver.prototype = OrigObserver.prototype;
                    }
                    } catch(e) {}
                }

                if (!featherIncludeMedia) return;
                if (window.__feather_media_active) return;
                window.__feather_media_active = true;

                // -----------------------------------------------------------------------------
                // 3. Media element helpers
                // -----------------------------------------------------------------------------
                function getMediaElements() {
                    try {
                        return Array.prototype.slice.call(document.querySelectorAll('video, audio'));
                    } catch(e) {
                        return [];
                    }
                }

                function getActiveMedia() {
                    const els = getMediaElements();
                    let first = els.length > 0 ? els[0] : null;
                    for (let i = 0; i < els.length; i++) {
                        const el = els[i];
                        if (!el.paused && !el.ended && el.currentTime > 0 && el.readyState >= 2) return el;
                    }
                    for (let i = 0; i < els.length; i++) {
                        const el = els[i];
                        if (!el.paused && !el.ended) return el;
                    }
                    return first;
                }

                function isPlayingNow() {
                    const els = getMediaElements();
                    for (let i = 0; i < els.length; i++) {
                        const el = els[i];
                        if (!el.paused && !el.ended && el.readyState >= 2) return true;
                    }
                    return false;
                }

                function normalizeUrl(u) {
                    if (!u) return '';
                    if (u.indexOf('//') === 0) return 'https:' + u;
                    if (u.indexOf('/') === 0) return 'https://www.youtube.com' + u;
                    return u;
                }

                /**
                 * Id of the video that is playing right now. Read from the live player/DOM and
                 * from the current URL only - never from og:url / canonical meta tags, which
                 * keep pointing at the first video of a YouTube SPA session.
                 */
                function getYouTubeVideoId() {
                    try {
                        const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                        if (player && typeof player.getVideoData === 'function') {
                            const data = player.getVideoData();
                            if (data && data.video_id) return data.video_id;
                        }
                    } catch(e) {}
                    try {
                        const watch = document.querySelector('ytd-watch-flexy, ytm-watch, ytm-single-column-watch-next-results-renderer, [video-id]');
                        const attr = watch && (watch.getAttribute('video-id') || watch.getAttribute('data-video-id'));
                        if (attr) return attr;
                    } catch(e) {}
                    try {
                        const searchMatch = window.location.search.match(/[?&]v=([a-zA-Z0-9_-]{11})/);
                        if (searchMatch) return searchMatch[1];
                        const pathMatch = window.location.pathname.match(/\/(?:shorts|live|v|embed)\/([a-zA-Z0-9_-]{11})/);
                        if (pathMatch) return pathMatch[1];
                    } catch(e) {}
                    try {
                        const response = window.ytInitialPlayerResponse;
                        const details = response && response.videoDetails;
                        if (details && details.videoId) return details.videoId;
                    } catch(e) {}
                    return '';
                }

                function isAdShowingNow() {
                    try {
                        const player = document.getElementById('movie_player') || document.querySelector('.html5-video-player');
                        if (player && player.classList) {
                            if (player.classList.contains('ad-showing') || player.classList.contains('ad-interrupting')) return true;
                        }
                        // Only trust the player's own ad state. Overlay/module selectors can
                        // remain in the DOM during normal playback on YouTube.
                        return !!document.querySelector('.html5-video-player.ad-showing, .html5-video-player.ad-interrupting');
                    } catch(e) {}
                    return false;
                }

                function isAdActive() {
                    return window.__feather_yt_ad_active === true || isAdShowingNow();
                }

                function isRecentAdEnd() {
                    const endedAt = window.__feather_last_ad_end_at || 0;
                    return endedAt > 0 && (Date.now() - endedAt) < 1500;
                }

                function getMediaSessionArtwork() {
                    try {
                        const metadata = navigator.mediaSession && navigator.mediaSession.metadata;
                        if (metadata && metadata.artwork && metadata.artwork.length > 0) {
                            for (let i = metadata.artwork.length - 1; i >= 0; i--) {
                                const src = metadata.artwork[i] && metadata.artwork[i].src;
                                if (src) return normalizeUrl(src);
                            }
                        }
                    } catch(e) {}
                    return '';
                }

                /**
                 * Thumbnail resolution for the notification / lock screen.
                 *
                 * The media session artwork is only trusted while it can be matched against the
                 * video that is playing right now: YouTube keeps its own metadata around across
                 * SPA navigations, which previously left the notification showing the thumbnail
                 * of the first video of the session. When the artwork cannot be verified the
                 * thumbnail URL is built from the live video id, which is always up to date.
                 */
                function getMediaThumbnail() {
                    const videoId = getYouTubeVideoId();
                    const sessionArtwork = getMediaSessionArtwork();
                    if (sessionArtwork && videoId) {
                        // Only artwork that can be traced back to the video playing right now is
                        // used as-is; anything else is treated as a leftover of a previous video
                        // (which is what used to freeze the notification thumbnail).
                        const artworkId = (sessionArtwork.match(/\/(?:vi|vi_webp)\/([a-zA-Z0-9_-]{11})\//) || [])[1];
                        if (artworkId === videoId) return sessionArtwork;
                        return 'https://i.ytimg.com/vi/' + videoId + '/hq720.jpg';
                    }
                    if (sessionArtwork) return sessionArtwork;
                    if (videoId) return 'https://i.ytimg.com/vi/' + videoId + '/hq720.jpg';
                    try {
                        const ogImage = document.querySelector('meta[property="og:image"]');
                        if (ogImage && ogImage.content) return normalizeUrl(ogImage.content);
                    } catch(e) {}
                    return '';
                }

                function getMediaTitle() {
                    try {
                        const metadata = navigator.mediaSession && navigator.mediaSession.metadata;
                        if (metadata && metadata.title && metadata.title.trim()) {
                            return metadata.title.trim();
                        }
                        const ytTitle = document.querySelector('h1.title, .slim-video-metadata-title, ytm-slim-video-metadata-section-renderer .slim-video-information-title, ytd-watch-metadata #title h1, .ytp-title-link, ytm-video-description-header-renderer .title, h2.video-title');
                        if (ytTitle && ytTitle.innerText && ytTitle.innerText.trim()) {
                            return ytTitle.innerText.trim();
                        }
                        const ogTitle = document.querySelector('meta[property="og:title"]');
                        if (ogTitle && ogTitle.content && ogTitle.content.trim()) {
                            return ogTitle.content.replace(/ - YouTube$/i, '').trim();
                        }
                        const docTitle = document.title.replace(/ - YouTube$/i, '').replace(/^\(\d+\)\s*/, '').trim();
                        if (docTitle && docTitle !== 'YouTube') return docTitle;
                    } catch(e) {}
                    return 'YouTube Video';
                }

                function getMediaArtist() {
                    try {
                        const metadata = navigator.mediaSession && navigator.mediaSession.metadata;
                        if (metadata && metadata.artist && metadata.artist.trim()) {
                            return metadata.artist.trim();
                        }
                        const ytAuthor = document.querySelector('.ytm-channel-thumbnail-with-profile-name .profile-name, #owner-name a, #channel-name a, ytd-channel-name a, .ytm-media-item-metadata .channel-name, .slim-owner-channel-name');
                        if (ytAuthor && ytAuthor.innerText && ytAuthor.innerText.trim()) {
                            return ytAuthor.innerText.trim();
                        }
                        const authorMeta = document.querySelector('meta[name="author"]');
                        if (authorMeta && authorMeta.content && authorMeta.content.trim()) {
                            return authorMeta.content.trim();
                        }
                    } catch(e) {}
                    return 'YouTube';
                }

                // -----------------------------------------------------------------------------
                // 4. Bridge: metadata + playback state for the Android media notification
                // -----------------------------------------------------------------------------
                let wasActivePlayback = false;

                function notifyMediaBridge(isPlaying) {
                    try {
                        if (!window.FeatherMediaBridge) return;
                        // Never publish ad metadata, ad duration, or ad playback state. The
                        // notification must keep showing the content video while a preroll
                        // or midroll is being skipped.
                        if (isAdActive()) return;
                        if (isPlaying) {
                            wasActivePlayback = true;
                            window.FeatherMediaBridge.updateMetadata(
                                getMediaTitle(),
                                getMediaArtist(),
                                'YouTube',
                                getMediaThumbnail()
                            );
                            window.FeatherMediaBridge.updatePlaybackState('PLAYING');
                        } else if (wasActivePlayback) {
                            window.FeatherMediaBridge.updatePlaybackState('PAUSED');
                        }
                    } catch(e) {}
                }

                // -----------------------------------------------------------------------------
                // 5. Position / duration reporting (system seek bar)
                // -----------------------------------------------------------------------------
                let lastPositionSeconds = -1;
                let lastDurationSeconds = -1;
                let lastPositionPushAt = 0;
                let positionFromSession = null;

                function pushPosition(positionSeconds, durationSeconds, force) {
                    try {
                        if (isAdActive()) return;
                        if (!window.FeatherMediaBridge || typeof window.FeatherMediaBridge.updateProgress !== 'function') return;
                        const now = Date.now();
                        const posRounded = Math.max(0, Math.round(positionSeconds));
                        const durRounded = Math.max(0, Math.round(durationSeconds));
                        const changed = Math.abs(posRounded - lastPositionSeconds) >= 1 || Math.abs(durRounded - lastDurationSeconds) >= 1;
                        if (!force) {
                            // Throttle to ~1 push/second, with a slow heartbeat so the notification
                            // recovers even if a single report gets lost.
                            if (now - lastPositionPushAt < 900) return;
                            if (!changed && now - lastPositionPushAt < 5000) return;
                        }
                        lastPositionPushAt = now;
                        lastPositionSeconds = posRounded;
                        lastDurationSeconds = durRounded;
                        window.FeatherMediaBridge.updateProgress(posRounded * 1000, durRounded * 1000);
                    } catch(e) {}
                }

                function publishPosition(force) {
                    try {
                        const media = getActiveMedia();
                        let position = -1;
                        let duration = 0;
                        if (media) {
                            if (typeof media.currentTime === 'number' && isFinite(media.currentTime) && media.currentTime >= 0) position = media.currentTime;
                            if (typeof media.duration === 'number' && isFinite(media.duration) && media.duration > 0) duration = media.duration;
                        }
                        if (positionFromSession) {
                            if (position < 0) position = positionFromSession.position;
                            if (duration <= 0) duration = positionFromSession.duration;
                        }
                        if (position < 0) return;
                        // Live streams report Infinity: without a duration the system seek bar
                        // stays hidden, which is exactly the behaviour we want.
                        if (!isFinite(duration) || duration < 0) duration = 0;
                        pushPosition(position, duration, !!force);
                    } catch(e) {}
                }

                // -----------------------------------------------------------------------------
                // 6. Media Session hooking (metadata, position state, action handlers)
                // -----------------------------------------------------------------------------
                window.__feather_actions = window.__feather_actions || {};

                try {
                    if ('mediaSession' in navigator) {
                        const ms = navigator.mediaSession;
                        let _currentMetadata = ms.metadata || null;

                        try {
                            const proto = Object.getPrototypeOf(ms) || ms;
                            const desc = Object.getOwnPropertyDescriptor(proto, 'metadata') || Object.getOwnPropertyDescriptor(ms, 'metadata');
                            Object.defineProperty(ms, 'metadata', {
                                get: function() {
                                    if (desc && desc.get) {
                                        try { return desc.get.call(this); } catch(e) {}
                                    }
                                    return _currentMetadata;
                                },
                                set: function(val) {
                                    _currentMetadata = val;
                                    if (desc && desc.set) {
                                        try { desc.set.call(this, val); } catch(e) {}
                                    }
                                    try {
                                        if (window.FeatherMediaBridge && val && !isAdActive()) {
                                            // Thumbnail comes from the resolver, which validates
                                            // the session artwork against the video playing now.
                                            window.FeatherMediaBridge.updateMetadata(
                                                val.title || getMediaTitle(),
                                                val.artist || getMediaArtist(),
                                                val.album || 'YouTube',
                                                getMediaThumbnail()
                                            );
                                            window.FeatherMediaBridge.updatePlaybackState(isPlayingNow() ? 'PLAYING' : 'PAUSED');
                                        }
                                    } catch(e) {}
                                },
                                configurable: true,
                                enumerable: true
                            });
                        } catch(e) {}

                        // setPositionState is the web platform's source of truth for "where is
                        // playback right now"; forward it to the Android media session so the
                        // notification seek bar follows the player instead of guessing.
                        try {
                            const origSetPositionState = ms.setPositionState;
                            if (typeof origSetPositionState === 'function') {
                                ms.setPositionState = function(state) {
                                    try {
                                        if (state) {
                                            const duration = (typeof state.duration === 'number' && isFinite(state.duration) && state.duration > 0) ? state.duration : 0;
                                            const position = (typeof state.position === 'number' && isFinite(state.position) && state.position >= 0) ? state.position : 0;
                                            positionFromSession = { position: position, duration: duration };
                                            pushPosition(position, duration, false);
                                        }
                                    } catch(e) {}
                                    try { return origSetPositionState.call(ms, state); } catch(e) {}
                                };
                            }
                        } catch(e) {}

                        try {
                            const origSetHandler = ms.setActionHandler;
                            if (typeof origSetHandler === 'function') {
                                ms.setActionHandler = function(action, handler) {
                                    window.__feather_actions[action] = handler;
                                    try {
                                        return origSetHandler.call(ms, action, handler);
                                    } catch(e) {}
                                };
                            }
                        } catch(e) {}
                    }
                } catch(e) {}

                // -----------------------------------------------------------------------------
                // 7. Media control methods callable from the notification bar
                // -----------------------------------------------------------------------------
                function scheduleTrackChecks() {
                    setTimeout(monitorMedia, 250);
                    setTimeout(monitorMedia, 700);
                    setTimeout(monitorMedia, 1500);
                    setTimeout(monitorMedia, 3000);
                }

                window.__feather_media_play = function() {
                    try {
                        if (window.__feather_actions && typeof window.__feather_actions['play'] === 'function') {
                            window.__feather_actions['play']();
                            return;
                        }
                    } catch(e) {}
                    try {
                        const moviePlayer = document.getElementById('movie_player');
                        if (moviePlayer && typeof moviePlayer.playVideo === 'function') {
                            moviePlayer.playVideo();
                            return;
                        }
                    } catch(e) {}
                    try {
                        const playBtn = document.querySelector('.ytp-play-button, .player-control-play-pause-icon, [aria-label*="Play"]');
                        if (playBtn) {
                            playBtn.click();
                            return;
                        }
                    } catch(e) {}
                    try {
                        getMediaElements().forEach(function(m) {
                            if (m.paused) m.play().catch(function() {});
                        });
                    } catch(e) {}
                };

                window.__feather_media_pause = function() {
                    try {
                        if (window.__feather_actions && typeof window.__feather_actions['pause'] === 'function') {
                            window.__feather_actions['pause']();
                        }
                    } catch(e) {}
                    try {
                        const moviePlayer = document.getElementById('movie_player');
                        if (moviePlayer && typeof moviePlayer.pauseVideo === 'function') {
                            moviePlayer.pauseVideo();
                        }
                    } catch(e) {}
                    try {
                        getMediaElements().forEach(function(m) {
                            if (!m.paused) m.pause();
                        });
                    } catch(e) {}
                };

                window.__feather_media_toggle = function() {
                    const video = getActiveMedia();
                    if (video) {
                        if (video.paused) {
                            window.__feather_media_play();
                        } else {
                            window.__feather_media_pause();
                        }
                    } else {
                        const btn = document.querySelector('.ytp-play-button, .player-control-play-pause-icon, [aria-label*="Play"], [aria-label*="Pause"]');
                        if (btn) btn.click();
                    }
                };

                /**
                 * Seek support for the system seek bar (Android media notification, lock screen,
                 * Bluetooth/headset, Android Auto). Uses YouTube's own seekto handler first so
                 * the player UI stays in sync with the jump.
                 */
                window.__feather_media_seek = function(positionMs) {
                    const seconds = Math.max(0, (typeof positionMs === 'number' ? positionMs : 0) / 1000);
                    try {
                        if (window.__feather_actions && typeof window.__feather_actions['seekto'] === 'function') {
                            window.__feather_actions['seekto']({ seekTime: seconds, fastSeek: false });
                            positionFromSession = { position: seconds, duration: (positionFromSession && positionFromSession.duration) || 0 };
                            scheduleTrackChecks();
                            return;
                        }
                    } catch(e) {}
                    try {
                        const moviePlayer = document.getElementById('movie_player');
                        if (moviePlayer && typeof moviePlayer.seekTo === 'function') {
                            moviePlayer.seekTo(seconds, true);
                            positionFromSession = { position: seconds, duration: (positionFromSession && positionFromSession.duration) || 0 };
                            scheduleTrackChecks();
                            return;
                        }
                    } catch(e) {}
                    try {
                        const media = getActiveMedia();
                        if (media) media.currentTime = seconds;
                    } catch(e) {}
                    scheduleTrackChecks();
                };

                /**
                 * Relative seek used by the notification / lock screen / headset skip buttons
                 * (Chromium exposes the same 10 second steps).
                 */
                window.__feather_media_seek_by = function(deltaSeconds) {
                    const delta = (typeof deltaSeconds === 'number' && isFinite(deltaSeconds)) ? deltaSeconds : 0;
                    const media = getActiveMedia();
                    let current = -1;
                    let duration = 0;
                    if (media) {
                        if (typeof media.currentTime === 'number' && isFinite(media.currentTime) && media.currentTime >= 0) current = media.currentTime;
                        if (typeof media.duration === 'number' && isFinite(media.duration) && media.duration > 0) duration = media.duration;
                    }
                    if (current < 0 && positionFromSession) current = positionFromSession.position;
                    if (duration <= 0 && positionFromSession) duration = positionFromSession.duration;
                    if (current < 0) return;
                    let target = current + delta;
                    if (target < 0) target = 0;
                    if (duration > 0) target = Math.min(target, Math.max(0, duration - 1));
                    window.__feather_media_seek(Math.round(target * 1000));
                };

                /**
                 * Playback speed, mirroring the playback speed control the system media player
                 * offers for Chromium based browsers.
                 */
                window.__feather_media_set_rate = function(rate) {
                    const value = (typeof rate === 'number' && isFinite(rate) && rate > 0) ? rate : 1;
                    try {
                        const moviePlayer = document.getElementById('movie_player');
                        if (moviePlayer && typeof moviePlayer.setPlaybackRate === 'function') {
                            if (typeof moviePlayer.getAvailablePlaybackRates === 'function') {
                                const available = moviePlayer.getAvailablePlaybackRates() || [];
                                if (available.indexOf(value) === -1) return;
                            }
                            moviePlayer.setPlaybackRate(value);
                            return;
                        }
                    } catch(e) {}
                    try {
                        getMediaElements().forEach(function(m) { m.playbackRate = value; });
                    } catch(e) {}
                };

                /**
                 * Temporary volume ducking (navigation prompts, alarms) - what Chromium does with
                 * audio focus instead of pausing playback outright.
                 */
                window.__feather_media_duck = function(duck) {
                    try {
                        getMediaElements().forEach(function(m) {
                            if (duck) {
                                if (typeof m.__feather_saved_volume !== 'number') {
                                    m.__feather_saved_volume = (typeof m.volume === 'number') ? m.volume : 1;
                                }
                                m.volume = Math.max(0, Math.min(1, m.__feather_saved_volume * 0.2));
                            } else if (typeof m.__feather_saved_volume === 'number') {
                                m.volume = m.__feather_saved_volume;
                                delete m.__feather_saved_volume;
                            }
                        });
                    } catch(e) {}
                };

                window.__feather_media_next = function() {
                    try {
                        if (window.__feather_actions && typeof window.__feather_actions['nexttrack'] === 'function') {
                            window.__feather_actions['nexttrack']();
                            scheduleTrackChecks();
                            return;
                        }
                    } catch(e) {}
                    const nextBtn = document.querySelector('.ytp-next-button, [aria-label*="Next"]');
                    if (nextBtn) {
                        nextBtn.click();
                    } else {
                        const recVideo = document.querySelector('ytd-compact-video-renderer a#thumbnail, ytm-compact-video-renderer a#thumbnail');
                        if (recVideo) recVideo.click();
                    }
                    scheduleTrackChecks();
                };

                window.__feather_media_prev = function() {
                    try {
                        if (window.__feather_actions && typeof window.__feather_actions['previoustrack'] === 'function') {
                            window.__feather_actions['previoustrack']();
                            scheduleTrackChecks();
                            return;
                        }
                    } catch(e) {}
                    const prevBtn = document.querySelector('.ytp-prev-button, [aria-label*="Previous"]');
                    if (prevBtn) {
                        prevBtn.click();
                    } else {
                        window.history.back();
                    }
                    scheduleTrackChecks();
                };

                // -----------------------------------------------------------------------------
                // 8. Periodic monitor & watchdog for HTML media elements
                // -----------------------------------------------------------------------------
                let lastReportedState = null;
                let lastReportedTitle = '';
                let lastReportedArt = '';
                let lastReportedVideoId = '';
                let lastAdActive = false;

                function hookMediaElement(el) {
                    if (!el || el.__feather_monitored) return;
                    el.__feather_monitored = true;

                    function handlePlay() {
                        if (isAdActive()) return;
                        wasActivePlayback = true;
                        notifyMediaBridge(true);
                        publishPosition(true);
                    }

                    function reportRate() {
                        if (isAdActive()) return;
                        try {
                            if (window.FeatherMediaBridge && typeof window.FeatherMediaBridge.updatePlaybackRate === 'function') {
                                window.FeatherMediaBridge.updatePlaybackRate(el.playbackRate);
                            }
                        } catch(e) {}
                    }

                    el.addEventListener('play', handlePlay);
                    el.addEventListener('playing', handlePlay);
                    el.addEventListener('ratechange', reportRate);
                    reportRate();
                    el.addEventListener('loadedmetadata', function() { publishPosition(true); });
                    el.addEventListener('durationchange', function() { publishPosition(true); });
                    el.addEventListener('seeked', function() {
                        // Keep the system seek bar in step with in-player seeks / double-tap
                        // and preview scrubs instead of waiting for the next poll tick.
                        publishPosition(true);
                    });
                    el.addEventListener('timeupdate', function() {
                        if (isAdActive()) return;
                        if (!el.paused && !el.ended && !wasActivePlayback) {
                            wasActivePlayback = true;
                            notifyMediaBridge(true);
                        }
                    });

                    el.addEventListener('pause', function() {
                        setTimeout(function() {
                            if (isAdActive()) return;
                            const anyPlaying = getMediaElements().some(function(m) {
                                return !m.paused && !m.ended;
                            });
                            if (!anyPlaying) {
                                notifyMediaBridge(false);
                            }
                        }, 250);
                    });

                    el.addEventListener('ended', function() {
                        // YouTube fires `ended` on the ad <video> as the ad is torn down.
                        // Treating that as the content ending stopped the notification and
                        // made it restart a moment later; wait for real content completion.
                        if (isAdActive() || isRecentAdEnd()) {
                            scheduleTrackChecks();
                            return;
                        }
                        wasActivePlayback = false;
                        if (window.FeatherMediaBridge) {
                            window.FeatherMediaBridge.onMediaEnded();
                        }
                    });
                }

                const monitorMedia = function() {
                    try {
                        if (isAdActive()) {
                            lastAdActive = true;
                            return;
                        }
                        if (lastAdActive) {
                            lastAdActive = false;
                            wasActivePlayback = true;
                            setTimeout(function() {
                                publishPosition(true);
                                notifyMediaBridge(isPlayingNow());
                            }, 80);
                        }

                        const els = getMediaElements();
                        let anyPlaying = false;
                        els.forEach(function(el) {
                            hookMediaElement(el);
                            if (!el.paused && !el.ended && el.currentTime > 0) {
                                anyPlaying = true;
                            }
                        });

                        if (anyPlaying) {
                            wasActivePlayback = true;
                        }

                        const currentTitle = getMediaTitle();
                        const currentArt = getMediaThumbnail();
                        const currentVideoId = getYouTubeVideoId();
                        const trackChanged = currentVideoId !== lastReportedVideoId;
                        if (anyPlaying !== lastReportedState || trackChanged || (anyPlaying && (currentTitle !== lastReportedTitle || currentArt !== lastReportedArt))) {
                            lastReportedState = anyPlaying;
                            lastReportedTitle = currentTitle;
                            lastReportedArt = currentArt;
                            lastReportedVideoId = currentVideoId;
                            if (anyPlaying || wasActivePlayback) {
                                notifyMediaBridge(anyPlaying);
                            }
                        }
                    } catch(e) {}
                };
                if (!window.__feather_media_interval) {
                    window.__feather_media_interval = setInterval(monitorMedia, 1500);
                }
                if (!window.__feather_media_position_interval) {
                    window.__feather_media_position_interval = setInterval(function() { publishPosition(false); }, 1000);
                }
                window.addEventListener('popstate', scheduleTrackChecks);
                window.addEventListener('yt-navigate-finish', scheduleTrackChecks);
                window.addEventListener('yt-page-data-updated', scheduleTrackChecks);
                monitorMedia();
            } catch(e) {}
        })();
        """.trimIndent()
    }

    /**
     * Media-session bridge only. It reports playback to the notification service without
     * pretending the page is visible, so background-play remains an explicit opt-in.
     */
    fun generateMediaSessionScript(): String =
        generateBackgroundPlayScript(includeBackgroundPlay = false, includeMediaSession = true)

    /**
     * Background-play visibility shim only. Media reporting is installed separately and is
     * always active, regardless of the background-play setting.
     */
    fun generateBackgroundPlayShimScript(): String =
        generateBackgroundPlayScript(includeBackgroundPlay = true, includeMediaSession = false)

    /**
     * Injects CSS and JS overrides to ensure websites adhere to the browser's theme setting.
     */
    fun generateThemeScript(isDark: Boolean): String {
        val targetScheme = if (isDark) "dark" else "light"
        return """
        (function() {
            try {
                const targetScheme = '$targetScheme';
                const isDark = $isDark;

                if (document.documentElement) {
                    document.documentElement.style.colorScheme = targetScheme;
                }
                let meta = document.querySelector('meta[name="color-scheme"]');
                if (!meta && document.head) {
                    meta = document.createElement('meta');
                    meta.name = 'color-scheme';
                    meta.content = targetScheme;
                    document.head.appendChild(meta);
                } else if (meta) {
                    meta.content = targetScheme;
                }

                const darkClasses = ['dark', 'theme-dark', 'dark-theme', 'theme--dark'];
                let style = document.getElementById('__feather_theme_override');

                if (isDark) {
                    if (style) {
                        style.remove();
                    }
                    if (document.documentElement) {
                        darkClasses.forEach(function(c) { document.documentElement.classList.add(c); });
                        document.documentElement.setAttribute('data-theme', 'dark');
                        document.documentElement.setAttribute('theme', 'dark');
                        document.documentElement.setAttribute('dark', '');
                        document.documentElement.setAttribute('data-color-mode', 'dark');
                    }
                    if (document.body) {
                        darkClasses.forEach(function(c) { document.body.classList.add(c); });
                        document.body.setAttribute('data-theme', 'dark');
                        document.body.setAttribute('theme', 'dark');
                        document.body.setAttribute('dark', '');
                        document.body.setAttribute('data-color-mode', 'dark');
                    }
                    try {
                        localStorage.setItem('yt-theme', 'dark');
                    } catch(e) {}
                } else {
                    if (!style) {
                        style = document.createElement('style');
                        style.id = '__feather_theme_override';
                        (document.head || document.documentElement).appendChild(style);
                    }
                    style.textContent = ':root, html, body { color-scheme: light !important; }';

                    if (document.documentElement) {
                        darkClasses.forEach(function(c) { document.documentElement.classList.remove(c); });
                        if (document.documentElement.getAttribute('data-theme') === 'dark') document.documentElement.setAttribute('data-theme', 'light');
                        if (document.documentElement.getAttribute('theme') === 'dark') document.documentElement.setAttribute('theme', 'light');
                        if (document.documentElement.hasAttribute('dark')) document.documentElement.removeAttribute('dark');
                        if (document.documentElement.getAttribute('data-color-mode') === 'dark') document.documentElement.setAttribute('data-color-mode', 'light');
                    }
                    if (document.body) {
                        darkClasses.forEach(function(c) { document.body.classList.remove(c); });
                        if (document.body.getAttribute('data-theme') === 'dark') document.body.setAttribute('data-theme', 'light');
                        if (document.body.getAttribute('theme') === 'dark') document.body.setAttribute('theme', 'light');
                        if (document.body.hasAttribute('dark')) document.body.removeAttribute('dark');
                        if (document.body.getAttribute('data-color-mode') === 'dark') document.body.setAttribute('data-color-mode', 'light');
                    }
                    try {
                        localStorage.setItem('yt-theme', 'light');
                    } catch(e) {}
                }
            } catch(e) {}
        })();
        """.trimIndent()
    }
}

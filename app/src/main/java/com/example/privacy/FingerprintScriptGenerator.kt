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
        val isStealth = preset == FingerprintPreset.ANONYMOUS_STEALTH

        return """
        (function() {
            try {
                if (window.__fp_injected) return;
                window.__fp_injected = true;
                
                Object.defineProperty(navigator, 'platform', {
                    get: function() { return '$platform'; },
                    configurable: true
                });
                
                Object.defineProperty(navigator, 'vendor', {
                    get: function() { return '$vendor'; },
                    configurable: true
                });
                
                Object.defineProperty(navigator, 'hardwareConcurrency', {
                    get: function() { return $cores; },
                    configurable: true
                });
                
                if ('deviceMemory' in navigator) {
                    Object.defineProperty(navigator, 'deviceMemory', {
                        get: function() { return $memory; },
                        configurable: true
                    });
                }

                Object.defineProperty(navigator, 'webdriver', {
                    get: function() { return false; },
                    configurable: true
                });

                ${if (isStealth) """
                if (window.HTMLCanvasElement && HTMLCanvasElement.prototype.toDataURL) {
                    const origToDataURL = HTMLCanvasElement.prototype.toDataURL;
                    HTMLCanvasElement.prototype.toDataURL = function() {
                        return origToDataURL.apply(this, arguments);
                    };
                }
                """ else ""}

            } catch(e) {
                console.warn("FP protection notice", e);
            }
        })();
        """.trimIndent()
    }

    /**
     * Injects a background audio/video playback shim that prevents YouTube, SoundCloud, Spotify,
     * and HTML5 video players from pausing when switching tabs or backgrounding the app.
     */
    fun generateBackgroundPlayScript(): String {
        return """
        (function() {
            try {
                if (window.__feather_bg_play_active) return;
                window.__feather_bg_play_active = true;

                // 1. Spoof Page Visibility API so websites (like YouTube) report visible and focused
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

                // 2. Prevent YouTube from auto-pausing on tab change: intercept visibilitychange and pagehide
                try {
                    const origAddEventListener = EventTarget.prototype.addEventListener;
                    EventTarget.prototype.addEventListener = function(type, listener, options) {
                        if (type === 'visibilitychange' || type === 'webkitvisibilitychange') {
                            return;
                        }
                        return origAddEventListener.apply(this, arguments);
                    };

                    ['visibilitychange', 'webkitvisibilitychange', 'pagehide'].forEach(function(evt) {
                        window.addEventListener(evt, function(e) {
                            if (e && e.stopImmediatePropagation) e.stopImmediatePropagation();
                            if (e && e.stopPropagation) e.stopPropagation();
                        }, true);
                        document.addEventListener(evt, function(e) {
                            if (e && e.stopImmediatePropagation) e.stopImmediatePropagation();
                            if (e && e.stopPropagation) e.stopPropagation();
                        }, true);
                    });
                } catch(e) {}

                // 3. Spoof IntersectionObserver for video / player elements so YouTube mobile doesn't pause when offscreen
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

                // 4. Media Session Hooking for Android notification metadata & controls
                window.__feather_actions = window.__feather_actions || {};

                function getMediaThumbnail() {
                    try {
                        if (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.artwork && navigator.mediaSession.metadata.artwork.length > 0) {
                            return navigator.mediaSession.metadata.artwork[navigator.mediaSession.metadata.artwork.length - 1].src || '';
                        }
                        const urlMatch = window.location.search.match(/[?&]v=([a-zA-Z0-9_-]{11})/);
                        if (urlMatch && urlMatch[1]) {
                            return 'https://img.youtube.com/vi/' + urlMatch[1] + '/hqdefault.jpg';
                        }
                    } catch(e) {}
                    return '';
                }

                function getMediaTitle() {
                    try {
                        if (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.title) {
                            return navigator.mediaSession.metadata.title;
                        }
                        const ytTitle = document.querySelector('h1.title, .slim-video-metadata-title, ytm-slim-video-metadata-section-renderer .slim-video-information-title, ytd-watch-metadata #title h1, .ytp-title-link, [class*="video-title"]');
                        if (ytTitle && ytTitle.innerText && ytTitle.innerText.trim()) {
                            return ytTitle.innerText.trim();
                        }
                        const docTitle = document.title.replace(/ - YouTube$/i, '').replace(/^\(\d+\)\s*/, '').trim();
                        if (docTitle && docTitle !== 'YouTube') return docTitle;
                    } catch(e) {}
                    return 'YouTube Video';
                }

                function getMediaArtist() {
                    try {
                        if (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.artist) {
                            return navigator.mediaSession.metadata.artist;
                        }
                        const ytAuthor = document.querySelector('.ytm-channel-thumbnail-with-profile-name .profile-name, #owner-name a, #channel-name a, ytd-channel-name a, .ytm-media-item-metadata .channel-name, [class*="channel-name"]');
                        if (ytAuthor && ytAuthor.innerText && ytAuthor.innerText.trim()) {
                            return ytAuthor.innerText.trim();
                        }
                    } catch(e) {}
                    return 'YouTube';
                }

                let wasActivePlayback = false;

                function notifyMediaBridge(isPlaying) {
                    try {
                        if (!window.FeatherMediaBridge) return;
                        if (isPlaying) {
                            wasActivePlayback = true;
                            const title = getMediaTitle();
                            const artist = getMediaArtist();
                            const art = getMediaThumbnail();
                            window.FeatherMediaBridge.updateMetadata(title, artist, 'YouTube', art);
                            window.FeatherMediaBridge.updatePlaybackState(true);
                        } else {
                            if (wasActivePlayback) {
                                window.FeatherMediaBridge.updatePlaybackState(false);
                            }
                        }
                    } catch(e) {}
                }

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
                                        if (window.FeatherMediaBridge && val) {
                                            let artUrl = '';
                                            if (val.artwork && val.artwork.length > 0) {
                                                artUrl = val.artwork[val.artwork.length - 1].src || '';
                                            }
                                            const t = val.title || getMediaTitle();
                                            const a = val.artist || getMediaArtist();
                                            window.FeatherMediaBridge.updateMetadata(
                                                t,
                                                a,
                                                val.album || 'YouTube',
                                                artUrl || getMediaThumbnail()
                                            );
                                            window.FeatherMediaBridge.updatePlaybackState(true);
                                        }
                                    } catch(e) {}
                                },
                                configurable: true,
                                enumerable: true
                            });
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

                // 5. Media control methods callable from notification bar
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
                        const playBtn = document.querySelector('.ytp-play-button, .player-control-play-pause-icon, ytm-custom-control-button, [aria-label*="Play"]');
                        if (playBtn) {
                            playBtn.click();
                            return;
                        }
                    } catch(e) {}
                    try {
                        const mediaEls = document.querySelectorAll('video, audio');
                        mediaEls.forEach(function(m) {
                            if (m.paused) m.play().catch(function() {});
                        });
                    } catch(e) {}
                };

                window.__feather_media_pause = function() {
                    try {
                        if (window.__feather_actions && typeof window.__feather_actions['pause'] === 'function') {
                            window.__feather_actions['pause']();
                            return;
                        }
                    } catch(e) {}
                    try {
                        const moviePlayer = document.getElementById('movie_player');
                        if (moviePlayer && typeof moviePlayer.pauseVideo === 'function') {
                            moviePlayer.pauseVideo();
                            return;
                        }
                    } catch(e) {}
                    try {
                        const pauseBtn = document.querySelector('.ytp-play-button, .player-control-play-pause-icon, ytm-custom-control-button, [aria-label*="Pause"]');
                        if (pauseBtn) {
                            pauseBtn.click();
                            return;
                        }
                    } catch(e) {}
                    try {
                        const mediaEls = document.querySelectorAll('video, audio');
                        mediaEls.forEach(function(m) {
                            if (!m.paused) m.pause();
                        });
                    } catch(e) {}
                };

                window.__feather_media_toggle = function() {
                    const video = document.querySelector('video, audio');
                    if (video) {
                        if (video.paused) {
                            window.__feather_media_play();
                        } else {
                            window.__feather_media_pause();
                        }
                    } else {
                        const btn = document.querySelector('.ytp-play-button, .player-control-play-pause-icon, ytm-custom-control-button, [aria-label*="Play"], [aria-label*="Pause"]');
                        if (btn) btn.click();
                    }
                };

                window.__feather_media_next = function() {
                    try {
                        if (window.__feather_actions && typeof window.__feather_actions['nexttrack'] === 'function') {
                            window.__feather_actions['nexttrack']();
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
                };

                window.__feather_media_prev = function() {
                    try {
                        if (window.__feather_actions && typeof window.__feather_actions['previoustrack'] === 'function') {
                            window.__feather_actions['previoustrack']();
                            return;
                        }
                    } catch(e) {}
                    const prevBtn = document.querySelector('.ytp-prev-button, [aria-label*="Previous"]');
                    if (prevBtn) {
                        prevBtn.click();
                    } else {
                        window.history.back();
                    }
                };

                // 6. Periodic monitor & watchdog for HTML media elements
                let lastReportedState = null;
                let lastReportedTitle = '';

                function hookMediaElement(el) {
                    if (!el || el.__feather_monitored) return;
                    el.__feather_monitored = true;

                    function handlePlay() {
                        wasActivePlayback = true;
                        notifyMediaBridge(true);
                    }

                    el.addEventListener('play', handlePlay);
                    el.addEventListener('playing', handlePlay);
                    el.addEventListener('timeupdate', function() {
                        if (!el.paused && !el.ended && !wasActivePlayback) {
                            wasActivePlayback = true;
                            notifyMediaBridge(true);
                        }
                    });

                    el.addEventListener('pause', function() {
                        setTimeout(function() {
                            const anyPlaying = Array.from(document.querySelectorAll('video, audio')).some(function(m) {
                                return !m.paused && !m.ended;
                            });
                            if (!anyPlaying) {
                                notifyMediaBridge(false);
                            }
                        }, 250);
                    });

                    el.addEventListener('ended', function() {
                        wasActivePlayback = false;
                        if (window.FeatherMediaBridge) {
                            window.FeatherMediaBridge.onMediaEnded();
                        }
                    });
                }

                const monitorMedia = function() {
                    try {
                        const els = document.querySelectorAll('video, audio');
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
                        if (anyPlaying !== lastReportedState || (anyPlaying && currentTitle !== lastReportedTitle)) {
                            lastReportedState = anyPlaying;
                            lastReportedTitle = currentTitle;
                            if (anyPlaying || wasActivePlayback) {
                                notifyMediaBridge(anyPlaying);
                            }
                        }
                    } catch(e) {}
                };
                setInterval(monitorMedia, 800);
                monitorMedia();
            } catch(e) {}
        })();
        """.trimIndent()
    }
}

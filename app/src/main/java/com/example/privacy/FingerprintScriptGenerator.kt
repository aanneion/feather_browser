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
                try {
                    const defineProp = function(obj, prop, value) {
                        try {
                            Object.defineProperty(obj, prop, {
                                get: function() { return value; },
                                set: function() {},
                                configurable: true,
                                enumerable: true
                            });
                        } catch(e) {}
                    };

                    defineProp(document, 'hidden', false);
                    defineProp(document, 'visibilityState', 'visible');
                    defineProp(document, 'webkitHidden', false);
                    defineProp(document, 'webkitVisibilityState', 'visible');
                    defineProp(Document.prototype, 'hidden', false);
                    defineProp(Document.prototype, 'visibilityState', 'visible');
                    defineProp(Document.prototype, 'webkitHidden', false);
                    defineProp(Document.prototype, 'webkitVisibilityState', 'visible');
                    
                    document.hasFocus = function() { return true; };
                    Document.prototype.hasFocus = function() { return true; };
                } catch(e) {}

                // 2. Prevent YouTube from auto-pausing on tab change: intercept visibilitychange event listeners
                try {
                    const origAddEventListener = EventTarget.prototype.addEventListener;
                    EventTarget.prototype.addEventListener = function(type, listener, options) {
                        if (type === 'visibilitychange' || type === 'webkitvisibilitychange') {
                            return;
                        }
                        return origAddEventListener.apply(this, arguments);
                    };

                    ['visibilitychange', 'webkitvisibilitychange'].forEach(function(evt) {
                        origAddEventListener.call(window, evt, function(e) {
                            if (e && e.stopImmediatePropagation) e.stopImmediatePropagation();
                            if (e && e.stopPropagation) e.stopPropagation();
                        }, true);
                        origAddEventListener.call(document, evt, function(e) {
                            if (e && e.stopImmediatePropagation) e.stopImmediatePropagation();
                            if (e && e.stopPropagation) e.stopPropagation();
                        }, true);
                    });
                } catch(e) {}

                // 3. Media Session Hooking for Android notification metadata & controls
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
                        const ytTitle = document.querySelector('h1.title, .slim-video-metadata-title, ytm-slim-video-metadata-section-renderer .slim-video-information-title, ytd-watch-metadata #title h1, .ytp-title-link');
                        if (ytTitle && ytTitle.innerText && ytTitle.innerText.trim()) {
                            return ytTitle.innerText.trim();
                        }
                        const docTitle = document.title.replace(/ - YouTube$/i, '').replace(/^\(\d+\)\s*/, '').trim();
                        if (docTitle && docTitle !== 'YouTube') return docTitle;
                    } catch(e) {}
                    return 'YouTube';
                }

                function getMediaArtist() {
                    try {
                        if (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.artist) {
                            return navigator.mediaSession.metadata.artist;
                        }
                        const ytAuthor = document.querySelector('.ytm-channel-thumbnail-with-profile-name .profile-name, #owner-name a, #channel-name a, ytd-channel-name a, .ytm-media-item-metadata .channel-name');
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
                        if (!isPlaying && !wasActivePlayback) return;

                        if (isPlaying) {
                            wasActivePlayback = true;
                            const title = getMediaTitle();
                            const artist = getMediaArtist();
                            const art = getMediaThumbnail();
                            window.FeatherMediaBridge.updateMetadata(title, artist, 'Feather Browser', art);
                        }
                        window.FeatherMediaBridge.updatePlaybackState(isPlaying);
                    } catch(e) {}
                }

                try {
                    if ('mediaSession' in navigator) {
                        const origMS = navigator.mediaSession;
                        try {
                            const origSetMetadata = Object.getOwnPropertyDescriptor(MediaSession.prototype, 'metadata')?.set;
                            if (origSetMetadata) {
                                Object.defineProperty(origMS, 'metadata', {
                                    set: function(val) {
                                        try {
                                            if (window.FeatherMediaBridge && val) {
                                                let artUrl = '';
                                                if (val.artwork && val.artwork.length > 0) {
                                                    artUrl = val.artwork[val.artwork.length - 1].src || '';
                                                }
                                                window.FeatherMediaBridge.updateMetadata(
                                                    val.title || getMediaTitle(),
                                                    val.artist || getMediaArtist(),
                                                    val.album || 'Feather Browser',
                                                    artUrl || getMediaThumbnail()
                                                );
                                            }
                                        } catch(e) {}
                                        return origSetMetadata.call(this, val);
                                    },
                                    configurable: true
                                });
                            }
                        } catch(e) {}

                        try {
                            const origSetHandler = origMS.setActionHandler.bind(origMS);
                            origMS.setActionHandler = function(action, handler) {
                                window.__feather_actions[action] = handler;
                                return origSetHandler(action, handler);
                            };
                        } catch(e) {}
                    }
                } catch(e) {}

                // 4. Media control methods callable from notification bar
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

                // 5. Periodic monitor & watchdog for HTML media elements
                let lastReportedState = null;
                let lastReportedTitle = '';

                function hookMediaElement(el) {
                    if (!el || el.__feather_monitored) return;
                    el.__feather_monitored = true;

                    ['play', 'playing'].forEach(function(evt) {
                        el.addEventListener(evt, function() {
                            wasActivePlayback = true;
                            notifyMediaBridge(true);
                        });
                    });

                    el.addEventListener('pause', function() {
                        const anyStillPlaying = Array.from(document.querySelectorAll('video, audio')).some(function(m) {
                            return !m.paused && !m.ended;
                        });
                        if (!anyStillPlaying) {
                            notifyMediaBridge(false);
                        }
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
                            if (!el.paused && !el.ended) {
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
                setInterval(monitorMedia, 1000);
                monitorMedia();
            } catch(e) {}
        })();
        """.trimIndent()
    }
}

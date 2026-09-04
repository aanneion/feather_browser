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

                // 1. Override Page Visibility API on both document instance and Document.prototype
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

                    // Override property event handlers
                    defineProp(document, 'onvisibilitychange', null);
                    defineProp(window, 'onpagehide', null);
                    defineProp(window, 'onblur', null);
                } catch(e) {}

                // 2. Intercept registration of visibility pause event listeners
                const origAddEventListener = EventTarget.prototype.addEventListener;
                EventTarget.prototype.addEventListener = function(type, listener, options) {
                    if (type === 'visibilitychange' || type === 'webkitvisibilitychange' || type === 'pagehide' || type === 'blur') {
                        return;
                    }
                    return origAddEventListener.apply(this, arguments);
                };

                // 3. Suppress any bubbling visibilitychange & blur events
                const suppressEvents = ['visibilitychange', 'webkitvisibilitychange', 'pagehide', 'blur', 'freeze'];
                suppressEvents.forEach(function(evt) {
                    window.addEventListener(evt, function(e) {
                        if (e && e.stopImmediatePropagation) e.stopImmediatePropagation();
                        if (e && e.stopPropagation) e.stopPropagation();
                    }, true);
                    document.addEventListener(evt, function(e) {
                        if (e && e.stopImmediatePropagation) e.stopImmediatePropagation();
                        if (e && e.stopPropagation) e.stopPropagation();
                    }, true);
                });

                // 4. Wrap IntersectionObserver so YouTube / video players never think they are offscreen
                if (window.IntersectionObserver) {
                    const OrigIO = window.IntersectionObserver;
                    window.IntersectionObserver = function(callback, options) {
                        const wrappedCb = function(entries, observer) {
                            const modEntries = entries.map(function(entry) {
                                return new Proxy(entry, {
                                    get: function(target, prop) {
                                        if (prop === 'isIntersecting') return true;
                                        if (prop === 'intersectionRatio') return 1.0;
                                        return target[prop];
                                    }
                                });
                            });
                            return callback(modEntries, observer);
                        };
                        return new OrigIO(wrappedCb, options);
                    };
                    window.IntersectionObserver.prototype = OrigIO.prototype;
                }

                // 5. Track genuine user interactions so we differentiate user pauses from backgrounding pauses
                window.__feather_explicit_pause = false;
                let lastUserTouchTime = 0;
                const recordUserTouch = function(e) {
                    if (e && e.isTrusted) {
                        lastUserTouchTime = Date.now();
                    }
                };
                ['pointerdown', 'mousedown', 'touchstart', 'click', 'keydown'].forEach(function(evt) {
                    window.addEventListener(evt, recordUserTouch, true);
                    document.addEventListener(evt, recordUserTouch, true);
                });

                // 6. Media Session API hooking for notification media player
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
                        const ytTitle = document.querySelector('h1.title, .slim-video-metadata-title, ytm-slim-video-metadata-section-renderer .slim-video-information-title, ytd-watch-metadata #title h1');
                        if (ytTitle && ytTitle.innerText && ytTitle.innerText.trim()) {
                            return ytTitle.innerText.trim();
                        }
                        const docTitle = document.title.replace(/ - YouTube$/i, '').replace(/^\(\d+\)\s*/, '').trim();
                        if (docTitle && docTitle !== 'YouTube') return docTitle;
                    } catch(e) {}
                    return 'Playing Audio';
                }

                function getMediaArtist() {
                    try {
                        if (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.artist) {
                            return navigator.mediaSession.metadata.artist;
                        }
                        const ytAuthor = document.querySelector('.ytm-channel-thumbnail-with-profile-name .profile-name, #owner-name a, #channel-name a, ytd-channel-name a');
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
                        // Never send notifications or metadata if media was never playing
                        if (!isPlaying && !wasActivePlayback) return;

                        if (isPlaying) {
                            wasActivePlayback = true;
                            const title = getMediaTitle();
                            const artist = getMediaArtist();
                            const art = getMediaThumbnail();
                            window.FeatherMediaBridge.updateMetadata(title, artist, 'Neon Browser', art);
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
                                            if (window.FeatherMediaBridge && val && wasActivePlayback) {
                                                let artUrl = '';
                                                if (val.artwork && val.artwork.length > 0) {
                                                    artUrl = val.artwork[val.artwork.length - 1].src || '';
                                                }
                                                window.FeatherMediaBridge.updateMetadata(
                                                    val.title || getMediaTitle(),
                                                    val.artist || getMediaArtist(),
                                                    val.album || 'Neon Browser',
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

                // 7. Direct media element tracking & action triggers
                window.__feather_media_play = function() {
                    window.__feather_explicit_pause = false;
                    try {
                        if (window.__feather_actions && typeof window.__feather_actions['play'] === 'function') {
                            window.__feather_actions['play']();
                            notifyMediaBridge(true);
                            return;
                        }
                    } catch(e) {}
                    try {
                        const moviePlayer = document.getElementById('movie_player');
                        if (moviePlayer && typeof moviePlayer.playVideo === 'function') {
                            moviePlayer.playVideo();
                            notifyMediaBridge(true);
                            return;
                        }
                    } catch(e) {}
                    try {
                        const mediaEls = document.querySelectorAll('video, audio');
                        if (mediaEls.length > 0) {
                            mediaEls.forEach(function(m) {
                                m.play().catch(function() {});
                            });
                            notifyMediaBridge(true);
                        }
                    } catch(e) {}
                    try {
                        const btn = document.querySelector('.ytp-play-button') || document.querySelector('[aria-label*="Play"]');
                        if (btn) btn.click();
                    } catch(e) {}
                };

                window.__feather_media_pause = function() {
                    window.__feather_explicit_pause = true;
                    try {
                        if (window.__feather_actions && typeof window.__feather_actions['pause'] === 'function') {
                            window.__feather_actions['pause']();
                            notifyMediaBridge(false);
                            return;
                        }
                    } catch(e) {}
                    try {
                        const moviePlayer = document.getElementById('movie_player');
                        if (moviePlayer && typeof moviePlayer.pauseVideo === 'function') {
                            moviePlayer.pauseVideo();
                            notifyMediaBridge(false);
                            return;
                        }
                    } catch(e) {}
                    try {
                        const mediaEls = document.querySelectorAll('video, audio');
                        if (mediaEls.length > 0) {
                            mediaEls.forEach(function(m) {
                                m.pause();
                            });
                            notifyMediaBridge(false);
                        }
                    } catch(e) {}
                    try {
                        const btn = document.querySelector('.ytp-play-button') || document.querySelector('[aria-label*="Pause"]');
                        if (btn) btn.click();
                    } catch(e) {}
                };

                window.__feather_media_next = function() {
                    try {
                        if (window.__feather_actions && typeof window.__feather_actions['nexttrack'] === 'function') {
                            window.__feather_actions['nexttrack']();
                            return;
                        }
                    } catch(e) {}
                    const nextBtn = document.querySelector('.ytp-next-button') || document.querySelector('[aria-label*="Next"]');
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
                    const prevBtn = document.querySelector('.ytp-prev-button') || document.querySelector('[aria-label*="Previous"]');
                    if (prevBtn) {
                        prevBtn.click();
                    } else {
                        window.history.back();
                    }
                };

                // 8. Hook HTMLMediaElement prototype play and pause with background auto-pause defense
                try {
                    const origPlay = HTMLMediaElement.prototype.play;
                    HTMLMediaElement.prototype.play = function() {
                        window.__feather_explicit_pause = false;
                        notifyMediaBridge(true);
                        return origPlay.apply(this, arguments);
                    };

                    const origPause = HTMLMediaElement.prototype.pause;
                    HTMLMediaElement.prototype.pause = function() {
                        // Check if the pause call was triggered by genuine user action or explicitly by media session bridge
                        const isExplicitUserPause = window.__feather_explicit_pause || (Date.now() - lastUserTouchTime < 800);
                        if (!isExplicitUserPause) {
                            // Suppress unwanted auto-pause triggered by YouTube visibility listeners or background state!
                            return;
                        }
                        notifyMediaBridge(false);
                        return origPause.apply(this, arguments);
                    };
                } catch(e) {}

                // 9. Periodic monitor & watchdog for HTML media elements
                let lastReportedState = null;
                let lastReportedTitle = '';

                const monitorMedia = function() {
                    try {
                        const els = document.querySelectorAll('video, audio');
                        let anyPlaying = false;
                        els.forEach(function(el) {
                            if (!el.paused && !el.ended) {
                                anyPlaying = true;
                            }
                            if (el.__feather_monitored) return;
                            el.__feather_monitored = true;

                            el.addEventListener('play', function() {
                                window.__feather_explicit_pause = false;
                                wasActivePlayback = true;
                                notifyMediaBridge(true);
                            });

                            el.addEventListener('pause', function() {
                                if (window.__feather_explicit_pause || (Date.now() - lastUserTouchTime < 800)) {
                                    notifyMediaBridge(false);
                                }
                            });

                            el.addEventListener('ended', function() {
                                wasActivePlayback = false;
                                if (window.FeatherMediaBridge) {
                                    window.FeatherMediaBridge.onMediaEnded();
                                }
                            });
                        });

                        if (anyPlaying) {
                            wasActivePlayback = true;
                        } else if (wasActivePlayback && !window.__feather_explicit_pause && (Date.now() - lastUserTouchTime > 1500)) {
                            // Video unexpectedly paused while in the background without user touch!
                            // Auto-resume background playback
                            const video = document.querySelector('video');
                            if (video && video.paused && !video.ended) {
                                video.play().catch(function() {});
                            }
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

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

                // 5. Media Session API hooking for notification media player
                window.__feather_actions = window.__feather_actions || {};

                if ('mediaSession' in navigator) {
                    const origMS = navigator.mediaSession;

                    let storedMeta = origMS.metadata;
                    Object.defineProperty(origMS, 'metadata', {
                        get: function() { return storedMeta; },
                        set: function(val) {
                            storedMeta = val;
                            if (window.FeatherMediaBridge && val) {
                                let artUrl = '';
                                if (val.artwork && val.artwork.length > 0) {
                                    artUrl = val.artwork[val.artwork.length - 1].src || '';
                                }
                                window.FeatherMediaBridge.updateMetadata(
                                    val.title || document.title || 'Playing Audio',
                                    val.artist || 'YouTube',
                                    val.album || '',
                                    artUrl
                                );
                            }
                        },
                        configurable: true,
                        enumerable: true
                    });

                    let storedState = origMS.playbackState || 'none';
                    Object.defineProperty(origMS, 'playbackState', {
                        get: function() { return storedState; },
                        set: function(val) {
                            storedState = val;
                            if (window.FeatherMediaBridge) {
                                window.FeatherMediaBridge.updatePlaybackState(val === 'playing');
                            }
                        },
                        configurable: true,
                        enumerable: true
                    });

                    const origSetHandler = origMS.setActionHandler.bind(origMS);
                    origMS.setActionHandler = function(action, handler) {
                        window.__feather_actions[action] = handler;
                        return origSetHandler(action, handler);
                    };
                }

                // 6. Direct media element tracking & action triggers
                window.__feather_media_play = function() {
                    try {
                        if (window.__feather_actions && typeof window.__feather_actions['play'] === 'function') {
                            window.__feather_actions['play']();
                            return;
                        }
                    } catch(e) {}
                    const v = document.querySelector('video, audio');
                    if (v) v.play();
                    const btn = document.querySelector('.ytp-play-button');
                    if (btn && (btn.getAttribute('data-title-no-tooltip') === 'Play' || btn.getAttribute('aria-label')?.includes('Play'))) {
                        btn.click();
                    }
                };

                window.__feather_media_pause = function() {
                    try {
                        if (window.__feather_actions && typeof window.__feather_actions['pause'] === 'function') {
                            window.__feather_actions['pause']();
                            return;
                        }
                    } catch(e) {}
                    const v = document.querySelector('video, audio');
                    if (v) v.pause();
                    const btn = document.querySelector('.ytp-play-button');
                    if (btn && (btn.getAttribute('data-title-no-tooltip') === 'Pause' || btn.getAttribute('aria-label')?.includes('Pause'))) {
                        btn.click();
                    }
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

                // Periodic monitor for HTML media elements
                const monitorMedia = function() {
                    const els = document.querySelectorAll('video, audio');
                    els.forEach(function(el) {
                        if (el.__feather_monitored) return;
                        el.__feather_monitored = true;

                        el.addEventListener('play', function() {
                            if (window.FeatherMediaBridge) {
                                const title = (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.title)
                                    || document.title.replace(' - YouTube', '').trim() || 'Playing Audio';
                                const artist = (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.artist)
                                    || 'YouTube';
                                window.FeatherMediaBridge.onMediaPlaying(title, artist);
                            }
                        });

                        el.addEventListener('pause', function() {
                            if (window.FeatherMediaBridge) {
                                window.FeatherMediaBridge.onMediaPaused();
                            }
                        });

                        el.addEventListener('ended', function() {
                            if (window.FeatherMediaBridge) {
                                window.FeatherMediaBridge.onMediaEnded();
                            }
                        });
                    });
                };
                setInterval(monitorMedia, 1500);
                monitorMedia();
            } catch(e) {}
        })();
        """.trimIndent()
    }
}

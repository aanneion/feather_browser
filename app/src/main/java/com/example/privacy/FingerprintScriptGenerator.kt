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

        return """
        (function() {
            try {
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
    fun generateBackgroundPlayScript(): String {
        return """
        (function() {
            try {
                if (window.__feather_bg_play_active) return;
                window.__feather_bg_play_active = true;

                // 1. Spoof Page Visibility API so media sites report visible and focused
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

                // Track genuine user interaction vs automatic tab-switch pause
                let isUserAction = false;
                let userInteractionTimer = null;
                let programmaticPauseAllowed = false;

                function markUserAction() {
                    isUserAction = true;
                    if (userInteractionTimer) clearTimeout(userInteractionTimer);
                    userInteractionTimer = setTimeout(function() {
                        isUserAction = false;
                    }, 1000);
                }

                ['click', 'pointerdown', 'touchend', 'keydown'].forEach(function(evt) {
                    window.addEventListener(evt, markUserAction, true);
                    document.addEventListener(evt, markUserAction, true);
                });

                // Wrap HTMLMediaElement pause to prevent unwanted tab switch pauses
                const origPlay = HTMLMediaElement.prototype.play;
                const origPause = HTMLMediaElement.prototype.pause;
                HTMLMediaElement.prototype.pause = function() {
                    if (programmaticPauseAllowed || isUserAction) {
                        return origPause.apply(this, arguments);
                    }
                    // Auto-pause detected from background/tab switch; immediately resume playback
                    const media = this;
                    origPause.apply(this, arguments);
                    setTimeout(function() {
                        if (!programmaticPauseAllowed && !isUserAction && media.paused && !media.ended) {
                            try {
                                origPlay.call(media).catch(function() {});
                            } catch(e) {}
                        }
                    }, 40);
                };

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
                        const ogImage = document.querySelector('meta[property="og:image"]');
                        if (ogImage && ogImage.content) {
                            return ogImage.content;
                        }
                        const urlMatch = window.location.search.match(/[?&]v=([a-zA-Z0-9_-]{11})/);
                        if (urlMatch && urlMatch[1]) {
                            return 'https://img.youtube.com/vi/' + urlMatch[1] + '/hqdefault.jpg';
                        }
                        const pathnameMatch = window.location.pathname.match(/\/shorts\/([a-zA-Z0-9_-]{11})/);
                        if (pathnameMatch && pathnameMatch[1]) {
                            return 'https://img.youtube.com/vi/' + pathnameMatch[1] + '/hqdefault.jpg';
                        }
                    } catch(e) {}
                    return '';
                }

                function getMediaTitle() {
                    try {
                        if (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.title) {
                            return navigator.mediaSession.metadata.title;
                        }
                        const ytTitle = document.querySelector('h1.title, .slim-video-metadata-title, ytm-slim-video-metadata-section-renderer .slim-video-information-title, ytd-watch-metadata #title h1, .ytp-title-link, [class*="video-title"], ytm-video-description-header-renderer .title, h2.video-title');
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
                        if (navigator.mediaSession && navigator.mediaSession.metadata && navigator.mediaSession.metadata.artist) {
                            return navigator.mediaSession.metadata.artist;
                        }
                        const ytAuthor = document.querySelector('.ytm-channel-thumbnail-with-profile-name .profile-name, #owner-name a, #channel-name a, ytd-channel-name a, .ytm-media-item-metadata .channel-name, .slim-owner-channel-name, [class*="channel-name"]');
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
                    programmaticPauseAllowed = true;
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
                        const pauseBtn = document.querySelector('.ytp-play-button, .player-control-play-pause-icon, ytm-custom-control-button, [aria-label*="Pause"]');
                        if (pauseBtn) {
                            pauseBtn.click();
                        }
                    } catch(e) {}
                    try {
                        const mediaEls = document.querySelectorAll('video, audio');
                        mediaEls.forEach(function(m) {
                            if (!m.paused) origPause.call(m);
                        });
                    } catch(e) {}
                    setTimeout(function() {
                        programmaticPauseAllowed = false;
                    }, 400);
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

    /**
     * Injects CSS and JS overrides to ensure websites adhere to the browser's theme setting
     * (e.g. Light mode when selected, preventing unwanted dark mode detection).
     */
    fun generateThemeScript(isDark: Boolean): String {
        val targetScheme = if (isDark) "dark" else "light"
        return """
        (function() {
            try {
                const targetScheme = '$targetScheme';
                const isDark = $isDark;

                // 1. Set documentElement color-scheme
                if (document.documentElement) {
                    document.documentElement.style.colorScheme = targetScheme;
                }

                // 2. Add or update meta color-scheme tag
                let meta = document.querySelector('meta[name="color-scheme"]');
                if (!meta) {
                    meta = document.createElement('meta');
                    meta.name = 'color-scheme';
                    if (document.head) document.head.appendChild(meta);
                }
                if (meta) {
                    meta.content = targetScheme;
                }

                // 3. Configure CSS overrides and DOM theme attributes
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

                // 4. Spoof window.matchMedia for prefers-color-scheme media queries
                const origMatchMedia = window.matchMedia;
                window.matchMedia = function(query) {
                    if (!query) return origMatchMedia ? origMatchMedia.call(window, query) : null;
                    const q = String(query).toLowerCase();
                    if (q.indexOf('prefers-color-scheme') !== -1) {
                        const isDarkQuery = q.indexOf('dark') !== -1;
                        const isLightQuery = q.indexOf('light') !== -1;
                        let matches = false;
                        if (isDark) {
                            matches = isDarkQuery && !isLightQuery;
                        } else {
                            matches = isLightQuery && !isDarkQuery;
                        }
                        const mql = origMatchMedia ? origMatchMedia.call(window, query) : {};
                        return {
                            matches: matches,
                            media: query,
                            onchange: null,
                            addListener: function(fn) { if (mql && mql.addListener) mql.addListener(fn); },
                            removeListener: function(fn) { if (mql && mql.removeListener) mql.removeListener(fn); },
                            addEventListener: function(type, fn, opt) { if (mql && mql.addEventListener) mql.addEventListener(type, fn, opt); },
                            removeEventListener: function(type, fn, opt) { if (mql && mql.removeEventListener) mql.removeEventListener(type, fn, opt); },
                            dispatchEvent: function(e) { return mql && mql.dispatchEvent ? mql.dispatchEvent(e) : true; }
                        };
                    }
                    return origMatchMedia ? origMatchMedia.call(window, query) : null;
                };
            } catch(e) {}
        })();
        """.trimIndent()
    }
}

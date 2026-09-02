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

                // 1. Override Page Visibility API so media sites always consider the tab active
                try {
                    Object.defineProperty(document, 'hidden', { get: function() { return false; }, configurable: true });
                    Object.defineProperty(document, 'visibilityState', { get: function() { return 'visible'; }, configurable: true });
                    Object.defineProperty(document, 'webkitHidden', { get: function() { return false; }, configurable: true });
                    Object.defineProperty(document, 'webkitVisibilityState', { get: function() { return 'visible'; }, configurable: true });
                    document.hasFocus = function() { return true; };
                } catch(e) {}

                // 2. Intercept registration of visibility pause event listeners
                const origAddEventListener = EventTarget.prototype.addEventListener;
                EventTarget.prototype.addEventListener = function(type, listener, options) {
                    if (type === 'visibilitychange' || type === 'webkitvisibilitychange' || type === 'pagehide') {
                        return;
                    }
                    return origAddEventListener.apply(this, arguments);
                };

                // 3. Suppress any existing or bubbling visibilitychange & blur events
                const suppressEvents = ['visibilitychange', 'webkitvisibilitychange', 'pagehide', 'blur'];
                suppressEvents.forEach(function(evt) {
                    window.addEventListener(evt, function(e) {
                        if (e && e.stopImmediatePropagation) e.stopImmediatePropagation();
                    }, true);
                    document.addEventListener(evt, function(e) {
                        if (e && e.stopImmediatePropagation) e.stopImmediatePropagation();
                    }, true);
                });
            } catch(e) {}
        })();
        """.trimIndent()
    }
}

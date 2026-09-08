package com.example.browser

/**
 * Built-in fingerprint presets that profile identities can impersonate.
 * All presets use authentic Chromium/Chrome device identities to ensure 100%
 * consistency between the browser engine (Blink/V8) and the User-Agent / Client Hints,
 * preventing bot-detection and security checks on modern web platforms (like Reddit, Cloudflare).
 */
enum class FingerprintPreset(
    val displayName: String,
    val description: String,
    val userAgent: String,
    val platform: String,
    val vendor: String,
    val hardwareConcurrency: Int,
    val deviceMemory: Int,
    val isMobile: Boolean = false
) {
    DEFAULT(
        displayName = "Native Mobile (Android)",
        description = "Standard Android Chromium mobile browser identity",
        userAgent = "", // Handled by default WebView UA
        platform = "Linux armv8l",
        vendor = "Google Inc.",
        hardwareConcurrency = 8,
        deviceMemory = 8,
        isMobile = true
    ),
    WINDOWS_DESKTOP(
        displayName = "Windows 11 Workstation (Chrome)",
        description = "High-spec Windows 11 desktop running Google Chrome",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        platform = "Win32",
        vendor = "Google Inc.",
        hardwareConcurrency = 16,
        deviceMemory = 16,
        isMobile = false
    ),
    MAC_DESKTOP(
        displayName = "macOS Sequoia (Chrome)",
        description = "Apple Silicon Mac running Google Chrome desktop",
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        platform = "MacIntel",
        vendor = "Google Inc.",
        hardwareConcurrency = 12,
        deviceMemory = 16,
        isMobile = false
    ),
    IPHONE_SAFARI(
        displayName = "Samsung Galaxy S24 Ultra",
        description = "Modern flagship Samsung mobile Chrome identity",
        userAgent = "Mozilla/5.0 (Linux; Android 14; SM-S928B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
        platform = "Linux armv8l",
        vendor = "Google Inc.",
        hardwareConcurrency = 8,
        deviceMemory = 12,
        isMobile = true
    ),
    LINUX_WORKSTATION(
        displayName = "Linux Workstation (Chrome)",
        description = "Clean Linux x86_64 desktop running Google Chrome",
        userAgent = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        platform = "Linux x86_64",
        vendor = "Google Inc.",
        hardwareConcurrency = 8,
        deviceMemory = 8,
        isMobile = false
    ),
    ANONYMOUS_STEALTH(
        displayName = "Google Pixel 9 Pro",
        description = "Pure Google Pixel Android 15 mobile Chrome identity",
        userAgent = "Mozilla/5.0 (Linux; Android 15; Pixel 9 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36",
        platform = "Linux aarch64",
        vendor = "Google Inc.",
        hardwareConcurrency = 8,
        deviceMemory = 16,
        isMobile = true
    );

    companion object {
        fun fromString(name: String?): FingerprintPreset {
            if (name.isNullOrBlank()) return DEFAULT
            return try {
                valueOf(name)
            } catch (e: Exception) {
                DEFAULT
            }
        }
    }
}


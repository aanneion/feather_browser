package com.example.browser

/**
 * Built-in fingerprint presets that profile identities can impersonate.
 */
enum class FingerprintPreset(
    val displayName: String,
    val description: String,
    val userAgent: String,
    val platform: String,
    val vendor: String,
    val hardwareConcurrency: Int,
    val deviceMemory: Int
) {
    DEFAULT(
        displayName = "Native Mobile (Android)",
        description = "Standard Android Chromium mobile browser identity",
        userAgent = "", // Handled by default WebView UA
        platform = "Linux armv8l",
        vendor = "Google Inc.",
        hardwareConcurrency = 8,
        deviceMemory = 8
    ),
    WINDOWS_DESKTOP(
        displayName = "Windows Desktop (Chrome)",
        description = "Emulates high-spec Windows 11 Desktop workstation",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        platform = "Win32",
        vendor = "Google Inc.",
        hardwareConcurrency = 16,
        deviceMemory = 16
    ),
    MAC_DESKTOP(
        displayName = "macOS Workstation (Safari)",
        description = "Emulates Apple Silicon Mac running macOS Sequoia",
        userAgent = "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
        platform = "MacIntel",
        vendor = "Apple Computer, Inc.",
        hardwareConcurrency = 12,
        deviceMemory = 16
    ),
    IPHONE_SAFARI(
        displayName = "iPhone iOS (Safari)",
        description = "Emulates iPhone 16 Pro running iOS 18",
        userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1",
        platform = "iPhone",
        vendor = "Apple Computer, Inc.",
        hardwareConcurrency = 6,
        deviceMemory = 6
    ),
    LINUX_WORKSTATION(
        displayName = "Linux Workstation (Firefox)",
        description = "Emulates privacy-focused Linux desktop environment",
        userAgent = "Mozilla/5.0 (X11; Linux x86_64; rv:130.0) Gecko/20100101 Firefox/130.0",
        platform = "Linux x86_64",
        vendor = "",
        hardwareConcurrency = 8,
        deviceMemory = 8
    ),
    ANONYMOUS_STEALTH(
        displayName = "Stealth Anonymous (Resistant)",
        description = "Active canvas & hardware noise injection with spoofed headers",
        userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0",
        platform = "Win32",
        vendor = "",
        hardwareConcurrency = 4,
        deviceMemory = 4
    )
}

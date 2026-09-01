# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.8] - 2026-09-01

### Added & Enhanced
- **🛡️ Isolated Multi-Profiles with Custom Fingerprint Presets**:
  - Independent browsing containers with 100% partitioned cookies, cache, local storage, history, bookmarks, and tab states.
  - Granular hardware and platform fingerprint spoofing per profile:
    - **Windows Desktop**: Emulates Windows 11 Chrome x64 architecture.
    - **macOS Safari**: Emulates AppleWebKit & Macintosh Intel platform headers.
    - **iPhone iOS**: Emulates Mobile Safari on iOS 17.
    - **Linux Firefox**: Emulates Gecko layout engine on X11 Linux.
    - **Stealth Anonymous**: Anti-fingerprinting shield spoofing Canvas 2D, WebGL vendor strings, AudioContext audio noise, hardware concurrency, and device memory.
- **Universal Fixes & Reliability**:
  - Applied universal URL navigation and tab state persistence across newly created and custom-fingerprinted profiles.
  - Implemented universal software keyboard auto-dismissal across all activity window tokens and soft input channels.
  - Enhanced WebView renderer crash self-healing with software rendering fallback for virtualized container environments.

## [1.0.7] - 2026-09-01

### Added
- **Dynamic Website Logo & Favicon Fetching for Shortcuts**:
  - Replaced static letter circles on the New Tab page with automatic high-resolution website favicon and logo resolution.
  - In-memory caching with smooth initial fallback ensuring high performance and zero lag.
- **Renderer Crash Self-Healing**:
  - Dynamic WebView recreation on renderer termination to prevent frozen or blank pages in virtualized/emulator environments.

## [1.0.3] - 2026-09-01

### Fixed
- **Web Page Light/Dark Theme Alignment**: Fixed issue where web pages rendered in dark mode despite Light theme being selected in browser settings. Synchronized WebView configuration context and algorithmic darkening so web pages accurately receive `@media (prefers-color-scheme: light)`.
- **Address Bar Focus & Keyboard Dismissal**:
  - Tapping on blank space on any web page or New Tab Page now instantly clears address bar focus and dismisses the soft keyboard.
  - Pressing the hardware Back button or performing the Back swipe gesture while editing the address bar now cleanly dismisses the keyboard, clears the cursor/pointer, and resets the input field without navigating away from the page.

## [1.0.2] - 2026-09-01

### Changed & Performance
- **🚀 91.5% APK Size Reduction (15.6 MB $\rightarrow$ 1.33 MB)**:
  - Enabled **R8 Full Code Optimization & Minification** (`isMinifyEnabled = true`) with aggressive dead-code elimination.
  - Enabled **Resource Shrinking** (`isShrinkResources = true`) to strip unused drawables, layouts, and strings.
  - Stripped unused heavy third-party dependencies and plugins (`Firebase AI`, `Firebase AppCheck`, `Retrofit`, `Moshi`).
  - Added targeted ProGuard rules for AndroidX Room, WebKit, and Kotlin Coroutines.

## [1.0.1] - 2026-09-01

### Added
- **Full-Screen Settings Window**: Redesigned settings into a standalone full-window experience with dedicated top app bar, back navigation, and complete isolation from browser chrome bars.
- **Glassmorphic UI Design**: Applied premium frosted-glass cards with subtle light-reflecting gradient borders and translucent surfaces.
- **Exit Browser Menu Action**: Added a direct "Exit Browser" action with error accenting at the bottom of the main menu sheet to cleanly close the app.

### Changed & Improved
- **Standard Browser URL Loading**: Removed blocking full-screen loading screen overlay, allowing previous web pages to stay interactable and visible while the progress bar animates on the top address bar.
- **Smooth 120Hz Settings Scrolling**: Eliminated bottom-sheet drag gesture collision and scroll lag by using native `LazyColumn` for high-framerate, jitter-free scrolling.

## [1.0.0] - 2026-09-01

### Added
- **Native Ad & Tracker Blocker (`ContentBlocker`)**: Zero-overhead request interceptor that blocks advertising domains, telemetry beacons, and tracking pixels out-of-the-box.
- **Material You Dynamic Theming**: Real-time wallpaper color extraction on Android 12+, with Light, Dark, and true AMOLED Pitch-Black (`#000000`) color palettes.
- **Multi-Profile Isolation**: Partitioned browser workspaces (e.g. Work, Personal, Shopping) with isolated cookies, web cache, bookmarks, and browsing history.
- **Incognito & Ephemeral Browsing**: One-tap private tabs with auto-purging session state on exit.
- **Modern Jetpack Compose UI**: Fast-out-slow-in smooth animations, collapsible address bar, and comprehensive bottom navigation bar.
- **Multi-Engine Search Selector**: Fast switcher between Google, DuckDuckGo, Bing, and Brave Search.
- **Productivity & Navigation Features**:
  - In-page text search with live match navigation (`FindInPageBar`).
  - Desktop site user-agent toggling.
  - Interactive tabs manager with swipe-to-dismiss and grid overview.
  - Privacy Shield sheet displaying real-time blocked ads/trackers counters.
  - Bookmark & History managers with full-text search.
  - Integrated downloads dialog with quick file opening.
- **Persistent Storage**: Room database integration for multi-profile bookmarks, history records, and browser preferences.
- **Automated CI/CD**: GitHub Actions workflows for continuous integration and automated release APK packaging with SHA256 checksums.

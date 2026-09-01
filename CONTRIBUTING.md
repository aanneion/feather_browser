# Contributing to Material Privacy Browser

Thank you for your interest in contributing to Material Privacy Browser! We welcome contributions from the community to help make this browser faster, safer, and more user-friendly.

---

## 📋 Table of Contents
1. [Code of Conduct](#code-of-conduct)
2. [How Can I Contribute?](#how-can-i-contribute)
   - [Reporting Bugs](#reporting-bugs)
   - [Suggesting Enhancements](#suggesting-enhancements)
   - [Submitting Pull Requests](#submitting-pull-requests)
3. [Development Workflow](#development-workflow)
   - [Prerequisites](#prerequisites)
   - [Building Locally](#building-locally)
   - [Running Tests](#running-tests)
4. [Coding Standards & Style Guide](#coding-standards--style-guide)

---

## Code of Conduct
By participating in this project, you agree to abide by our [Code of Conduct](CODE_OF_CONDUCT.md).

---

## How Can I Contribute?

### Reporting Bugs
- Search existing [GitHub Issues](https://github.com/aanneion/lightweight_browser/issues) to verify if the bug has already been reported.
- If not, open a new issue using the **Bug Report** template.
- Include device specifications (Android version, device model), steps to reproduce, and screenshots or logcat traces if applicable.

### Suggesting Enhancements
- Feature requests and UX enhancements are tracked via GitHub Issues using the **Feature Request** template.
- Clearly describe the problem you want solved or the feature you would like to see.

### Submitting Pull Requests
1. **Fork** the repository and create a descriptive branch:
   ```bash
   git checkout -b feature/awesome-feature
   # or
   git checkout -b fix/issue-description
   ```
2. Commit your changes following [Conventional Commits](https://www.conventionalcommits.org/):
   - `feat: add custom blocklist import`
   - `fix: prevent crash when opening pdf link`
   - `docs: update build instructions`
3. Ensure all tests pass locally:
   ```bash
   ./gradlew test assembleDebug
   ```
4. Push your branch to your fork and open a Pull Request against `main`.

---

## Development Workflow

### Prerequisites
- **Android Studio:** Hedgehog (2023.1.1) or newer / Ladybug
- **JDK:** OpenJDK 17 or 21
- **Android SDK:** Platform 34+ (compileSdk 36.1, minSdk 24)

### Building Locally
```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease
```

### Running Tests
```bash
./gradlew test
```

---

## Coding Standards & Style Guide
- **Language:** Kotlin (100%).
- **UI:** Jetpack Compose with Material Design 3 guidelines.
- **State:** Unidirectional Data Flow (UDF) using Kotlin Coroutines `StateFlow` and Android `ViewModel`.
- **Architecture:** Clean MVVM architecture separating UI, ViewModel, Repository, and Data sources.

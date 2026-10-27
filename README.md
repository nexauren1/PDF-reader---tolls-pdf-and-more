# NexaPDF for Android

**Native Android PDF toolkit** built with Kotlin and Jetpack Compose. The current milestone is an early development build (0.1.0), not a production release.

## Product goals

NexaPDF is designed as a privacy-first PDF workspace with an elegant dark interface, Portuguese and English UI, essential offline tools, and optional online capabilities added only when they are ready.

## Implemented in this first milestone

- Native Android project using Kotlin, Jetpack Compose, Material 3 and Android Storage Access Framework.
- Dark dashboard with tool categories, recent documents, reader screen, settings and language switch (Português / English).
- Open and render PDF pages using Android's PdfRenderer.
- Merge two or more PDF files using PDFBox-Android.
- Extract a selected page range to a new PDF.
- Rotate every page in a PDF by 90 degrees, preserving the source document.
- Convert selected JPG/PNG images into a PDF using Android's PdfDocument.
- Save output to a user-chosen location, share the source PDF and remember recently opened files locally.
- GitHub Actions workflow to build a debug APK and run unit tests.

The dashboard marks unfinished features as **in development**, rather than presenting them as working.

## Planned next

1. Add tests for PDF merge, extraction, rotation, image conversion, error paths and large documents.
2. Test the Android build on GitHub Actions and correct any build failures.
3. Add annotations and signatures, then evaluate compression and scanning implementations.
4. Add optional OCR/AI with explicit user consent, clear privacy information, and network/error handling.
5. Perform accessibility, performance, localization and device testing.
6. Prepare release signing, store listing, privacy policy, screenshots and a release checklist.

## Build

Requirements: JDK 17, Android SDK Platform 35, Android Build Tools 35.0.0, and Gradle 8.11.1.

From the project root:

```bash
gradle --no-daemon assembleDebug testDebugUnitTest
```

The workflow in `.github/workflows/android.yml` installs the build environment and uploads `app/build/outputs/apk/debug/app-debug.apk` as a CI artifact after a successful build.

## Project details

- Application ID: `com.nexauren.nexapdf`
- Minimum Android SDK: API 24
- Target / compile SDK: 35
- Current version: `0.1.0` (version code 1)
- Languages: Portuguese and English
- Processing strategy: local-first; online features are planned and not yet active.

## Important limitations

This is an initial development milestone. The Android debug APK and page-range unit tests **passed GitHub Actions CI on 27 October 2026** ([build run](https://github.com/nexauren1/PDF-reader---tolls-pdf-and-more/actions/runs/40489850244)). It has **not yet been installed and validated on physical devices**, and it is **not a Play Store release**. Some tools listed in the UI are deliberately marked in development. Do not use it as a substitute for a professionally validated secure document workflow until device and security testing is complete.

For PDF libraries and licensing notes, see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

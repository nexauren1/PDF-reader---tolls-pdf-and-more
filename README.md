# NexaPDF for Android

**Native Android PDF toolkit** built with Kotlin, Jetpack Compose, Material 3, and PDFBox-Android. This is an early development build (0.1.0), not a production or Play Store release.

## Product approach

NexaPDF is a privacy-first PDF workspace with a polished dark interface, Portuguese and English UI, local document processing, and optional platform services only where needed. The app requests files through Android's document picker instead of broad storage access.

## Features implemented in the current code

- **Read PDFs:** open documents, render a page preview, navigate page-by-page, and share through Android's system share sheet.
- **Search PDF text:** search locally across text-bearing pages, view short result snippets, and jump to a matching page. Search results are per page; on-page text highlighting is not implemented yet.
- **Organize pages:** join multiple PDFs, extract a page range, rotate every page, remove a page range, and reorder pages using a complete comma-separated page order.
- **Convert:** create a PDF from selected images. Very large source images are downsampled before processing to limit memory use.
- **Compress:** create a smaller, image-based PDF copy with quality presets. The result is accepted only if the file becomes smaller.
- **Annotate and sign:** add a short text note or a finger-drawn signature image to a selected page. These are visual additions, not cryptographic digital signatures; the content is placed near the lower page margin.
- **Protect PDFs:** create a password-protected copy using standard PDF password encryption.
- **Open protected PDFs:** ask for a password and use it transiently while the document is open.
- **Scan documents:** use Google ML Kit Document Scanner through Google Play services and save the scan as PDF. Scanner availability and first-use component downloads depend on the device and Google Play services.
- **Offline OCR:** use the bundled ML Kit text-recognition model on the device to add an invisible searchable text layer to scanned pages. OCR currently expects upright pages and is intended for image/scanned PDFs.
- **Local history:** remember recent document references and names in app preferences. PDF contents are not stored in the recent list.
- **Languages and theme:** Portuguese and English labels, dark theme, and a native bottom navigation bar.

The UI deliberately marks tools that are not implemented as in development.

## Important limitations

- Compression rasterizes each page. Text will not remain selectable/searchable, and interactive forms, hyperlinks, bookmarks, vector content, and some document semantics are not preserved.
- Text notes and drawn signatures are placed near the bottom margin; arbitrary drag-and-drop placement is not yet implemented.
- A drawn signature is an image and is not a qualified or cryptographic PDF signature.
- Password protection is standard PDF encryption, not DRM or a guarantee against all forms of copying.
- Merge currently targets unprotected source PDFs. Some operations may reject malformed, unsupported, secured, or unusually complex documents.
- OCR can substitute characters outside the built-in PDF font's supported range and may add duplicated text if run on a document that already contains selectable text.
- Real-device testing is still required, especially with encrypted, large, malformed, rotated, scanned, and low-memory cases.

## Build

Requirements: JDK 17, Android SDK Platform 36, Android Build Tools 36.0.0, and Gradle 8.11.1.

From the project root:

    gradle --no-daemon assembleDebug testDebugUnitTest

GitHub Actions builds the debug APK and runs unit tests on pushes and pull requests. A successful run uploads an artifact named nexapdf-debug-apk.

## Project details

- Application ID: com.nexauren.nexapdf
- Minimum Android SDK: API 24
- Compile / target SDK: 36
- Current version: 0.1.0 (version code 1)
- Languages: Portuguese and English
- Processing strategy: local-first; no Nexauren-operated document-processing backend is configured.
- Document scanner: optional Google Play services dependency.

## Release readiness

The debug build and unit tests have passed in GitHub Actions. The app still requires physical-device acceptance testing, dependency/license review, final branding checks, privacy/Data safety review, release signing, and staged Google Play testing.

See:
- ROADMAP.md for implementation and release gates
- RELEASE.md for safe release signing instructions
- PRIVACY_POLICY.md for the current privacy-policy draft
- STORE_LISTING.md for the Play Store listing draft
- THIRD_PARTY_NOTICES.md for dependency/license notes

**Do not treat the current debug APK as a production release.**
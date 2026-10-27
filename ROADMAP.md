# NexaPDF development and release roadmap

## Milestone 0.1 — Native local-first toolkit

- [x] Kotlin + Jetpack Compose project with dark UI and launcher icon
- [x] Portuguese / English UI and local recent-document list
- [x] Read/render PDF and navigate pages
- [x] Local text search with page snippets
- [x] Merge PDFs, extract page ranges, rotate pages, remove pages, reorder pages
- [x] Convert selected images to PDF with downsampling
- [x] Raster-based compression with clear loss-of-content warning and size check
- [x] Text annotation and finger-drawn signature image
- [x] PDF password protection and password prompts for supported workflows
- [x] Document scanner through Google ML Kit Document Scanner
- [x] On-device OCR text layer for upright scanned PDFs
- [x] GitHub Actions Android API 36 debug build and unit tests
- [ ] Install and test the latest APK on a physical Android device
- [ ] Validate operations with encrypted, large, rotated, malformed, and scanned PDFs
- [ ] Test on low-memory devices and different Android versions / screen sizes

## Milestone 0.2 — Refinement

- [ ] Add preview/highlight overlays for search results
- [ ] Add true drag-and-drop page ordering with page thumbnails
- [ ] Add free-position text/signature placement and richer annotation controls
- [ ] Improve compression to preserve document semantics where possible
- [ ] Add tests for end-to-end PDF operations using generated fixture documents
- [ ] Improve accessibility, progress/cancellation, and error reporting

## Milestone 0.3 — Compatibility and security review

- [ ] Review PDFBox-Android and all dependency licenses
- [ ] Verify password handling and encrypted output with independent PDF viewers
- [ ] Review data retention, temporary file cleanup, and permissions
- [ ] Complete physical-device acceptance testing
- [ ] Decide whether cloud OCR/AI is worth adding; keep it opt-in and clearly disclosed

## Store release gate

- [ ] Confirm product name, package identifier, trademarks, and domain availability
- [ ] Finalize public privacy policy and Google Play Data safety declarations against the exact binary
- [ ] Enable GitHub Pages and publish the policy site
- [ ] Create final store icon and screenshots from the real app
- [ ] Configure release signing secrets securely; never commit a keystore or passwords
- [ ] Build and verify a signed Android App Bundle
- [ ] Test an internal Google Play track and install/update flows
- [ ] Review listing, content rating, ads, app access, and applicable store declarations
- [ ] Publish to production only after owner approval

## Release rule

Do not mark the app production-ready until the Android build succeeds, the debug/release binary is installed on real target devices, and the core operations are verified with a test matrix. The current successful CI build is necessary but not a substitute for device testing.
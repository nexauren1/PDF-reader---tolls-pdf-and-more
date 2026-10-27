# NexaPDF development and release roadmap

## Milestone 0.1 — Native foundation

- [x] Kotlin + Jetpack Compose Android project
- [x] Dark mobile UI with Portuguese and English labels
- [x] PDF open/render/read and local recent list
- [x] Merge PDFs, extract page ranges, rotate all pages, images to PDF
- [x] Save output through Android's document picker
- [x] Add Android build workflow and basic page-range unit tests
- [x] Confirm GitHub Actions produces a debug APK (build passed on 27 Oct 2026)
- [ ] Install and test on a physical device

## Milestone 0.2 — Editing tools

- [ ] Add annotations and handwritten signatures
- [ ] Evaluate real compression behavior against sample documents
- [ ] Add robust progress, cancel, storage cleanup, and error handling
- [ ] Add integration tests using generated PDFs

## Milestone 0.3 — Scan and protection

- [ ] Add document scanning and crop correction
- [ ] Implement PDF password protection with tested encryption support
- [ ] Validate accessibility and low-memory devices

## Milestone 0.4 — Optional online services

- [ ] Decide whether OCR, translation or AI add enough user value
- [ ] Explicit user consent before any file upload
- [ ] Privacy policy, data retention and error handling
- [ ] Keep core local tools working offline

## Store release gate

- [ ] Confirm final product name, package ID and brand availability
- [ ] Test on supported Android versions and screen sizes
- [ ] Review dependency licenses and security
- [ ] Prepare store icon, screenshots, description and privacy policy
- [ ] Configure release signing outside the repository
- [ ] Build and verify signed Android App Bundle
- [ ] Test internal Play track before production release

**Release rule:** do not label the app production-ready until the Android build succeeds, an APK is installed on real device(s), and core PDF operations have been verified.

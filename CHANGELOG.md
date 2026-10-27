# Changelog

## 0.1.0 — Current development milestone · 27 October 2026

### Native foundation
- Kotlin + Jetpack Compose + Material 3 Android application.
- Dark NexaPDF dashboard, tool categories, recent documents, reader, settings, and Portuguese/English UI.
- Android document picker, local history, native sharing, and app launcher icon.
- GitHub Actions workflow for API 36 builds and unit tests.

### PDF workflows
- Open/read PDFs, preview a page, and navigate between pages.
- Search text locally across pages and navigate to matching pages.
- Merge PDFs, extract page ranges, rotate pages, remove ranges, and reorder pages.
- Convert selected images into PDF with image downsampling to reduce memory pressure.
- Compress PDF copies into raster pages with quality presets; only keep the result if the output is smaller.
- Add text notes and finger-drawn signature images to a selected page.
- Protect a PDF copy with a password and prompt for passwords on supported protected-document workflows.
- Scan pages to PDF through Google ML Kit Document Scanner.
- Add invisible searchable text with bundled on-device OCR for upright scanned pages.

### Robustness and documentation
- Validate page ranges and complete page-order permutations.
- Clean abandoned temporary files and remove a save result when the user cancels the save picker.
- Bound reader/OCR raster dimensions and downsample large input images.
- Add tests for page ranges, page order, password validation, OCR normalization, annotation text normalization, and search snippets.
- Add privacy policy and store-listing drafts, a static privacy site, a manual GitHub Pages workflow, and release signing instructions.

### Known limitations
- This remains a development build; no physical-device acceptance test has been recorded in the repository.
- Compression rasterizes content and does not preserve selectable text, forms, hyperlinks or vectors.
- Annotations/signatures are visual overlays at a fixed lower margin; drawn signatures are not cryptographic signatures.
- OCR is intended for upright scanned PDFs and may duplicate text if a text layer already exists.
- Some tools remain marked in development, and some secured or malformed PDFs may not be supported by every operation.
- A signed production bundle and public store release have not yet been completed.
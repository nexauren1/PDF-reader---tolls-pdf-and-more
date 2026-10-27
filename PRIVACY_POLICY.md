# NexaPDF for Android — Privacy Policy

**Effective date:** 27 October 2026  
**Applies to:** NexaPDF Android development version 0.1.x

NexaPDF is a PDF utility app developed under the Nexauren name. This document describes the behaviour intended for the current codebase. It must be reviewed against the final release build and Google Play Data safety declarations before publication.

## 1. What NexaPDF does

NexaPDF lets you open and read PDFs, combine or extract pages, rotate pages, convert selected images into PDFs, compress a PDF copy, add text or a drawn signature, protect a PDF with a password, scan documents, and add searchable text to scans with OCR.

## 2. Files and processing

- The core PDF operations are designed to run on your Android device. NexaPDF does not currently use a Nexauren-operated server to receive, store, or process your documents.
- You choose which files to open using Android's document picker. NexaPDF does not request broad access to all shared storage.
- Temporary working copies may be placed in the app's private cache while an operation runs. The app removes older NexaPDF temporary files during its normal cleanup. Android may also clear app cache.
- When you save a result, you choose the destination. The app does not automatically upload the result.
- The recent-documents list stores the selected document URI and display name locally in app preferences. It does not store the PDF contents.
- PDF passwords entered to open or protect a document are used in memory for that operation. The current implementation does not intentionally save those passwords in app preferences or the recent-documents list.
- OCR uses the bundled ML Kit text-recognition model to read page images on the device and add an invisible, searchable text layer to a copy. OCR is intended for upright scanned pages; the original remains unchanged.

## 3. Document scanner and third-party services

The optional document scanner uses Google ML Kit Document Scanner through Google Play services. Google Play services may need to download or update scanner components and are governed by Google's own terms and privacy practices. NexaPDF does not operate the scanner service. Review Google's current privacy information at [Google Privacy Policy](https://policies.google.com/privacy) and the [ML Kit documentation](https://developers.google.com/ml-kit/vision/doc-scanner).

NexaPDF also uses open-source and Android platform libraries for PDF processing, interface rendering, and OCR. Their licence and privacy documentation should be reviewed as part of every production release.

## 4. Information collection, advertising, and accounts

In the current development build, NexaPDF has no sign-in or account system, no Nexauren-operated cloud backend, and no advertising or analytics integration intentionally configured in the app.

Android, Google Play services, and their components may process technical or diagnostic information as part of their own operation. The developer should verify the exact behaviour of dependencies and complete the Google Play Data safety form against the final release APK/AAB.

## 5. Sharing and external destinations

If you choose to share a PDF, Android opens the system share sheet and passes the selected document to the destination you choose. The recipient's privacy practices then apply. Do not share sensitive documents with a destination you do not trust.

## 6. Data retention and deletion

Recent document references are stored locally. You can clear NexaPDF's app storage from Android Settings to remove local preferences and cached data; doing so may also remove settings and recent-document entries. Files that you saved to a separate location are not deleted by clearing NexaPDF's app storage and must be managed in that location.

## 7. Children's privacy

NexaPDF is a general-purpose document utility and is not specifically designed to collect personal information from children. The app has no account creation or profile system in the current development build.

## 8. Security limitations

Password protection uses standard PDF password encryption and permissions. It is not a guarantee against every method of copying or extracting content, and it is not a substitute for a cryptographic digital signature. Keep passwords safe; losing a password may make a protected document inaccessible.

## 9. Policy changes

This policy must be updated before any release that adds cloud processing, AI services, account sync, analytics, advertising, or other material data-handling changes.

## 10. Contact and support

For questions about NexaPDF's privacy practices, use the [repository support/issues page](https://github.com/nexauren1/PDF-reader---tolls-pdf-and-more/issues). Do not attach private documents, passwords, or other sensitive material to a public issue.

---

**Release note:** This is a policy draft for the development build. Before Google Play publication, confirm it against the exact release binary, all bundled SDKs, the app's permission list, and the required store declarations.

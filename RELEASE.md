# NexaPDF release checklist and signing

This workflow builds a **signed Android App Bundle (AAB)** and a signed release APK. It deliberately does not upload anything to Google Play; publishing remains a manual release decision.

## One-time signing setup

1. Create a release keystore on a trusted computer and keep a separate secure backup. Never commit the keystore or passwords to Git.
2. Example command (choose a strong unique password when prompted):

   ```bash
   keytool -genkeypair -v -keystore nexapdf-release.jks -keyalg RSA -keysize 4096 -validity 10000 -alias nexapdf
   ```

3. Base64-encode the keystore file without changing its bytes. On Linux:

   ```bash
   base64 -w 0 nexapdf-release.jks
   ```

   On PowerShell:

   ```powershell
   [Convert]::ToBase64String([IO.File]::ReadAllBytes("nexapdf-release.jks"))
   ```

4. In GitHub repository Settings → Secrets and variables → Actions, add these repository secrets:
   - `NEXAPDF_KEYSTORE_BASE64`: the Base64 string from step 3
   - `NEXAPDF_KEYSTORE_PASSWORD`: keystore password
   - `NEXAPDF_KEY_ALIAS`: the alias used in keytool
   - `NEXAPDF_KEY_PASSWORD`: password for the key alias

The workflow writes the decoded keystore only to the temporary runner's ignored build directory. Do not print secrets in Actions logs. Keep a secure independent backup; losing the upload key can complicate future updates.

## Build and retrieve the release

- Run Actions → **Signed Android release bundle** → Run workflow, or push a version tag such as `v0.2.0` after the code and version are approved.
- Download the `nexapdf-release-…` artifact from the workflow run.
- Inspect and test the signed APK on target devices before distribution.
- Upload the AAB to a Google Play internal testing track first. Do not proceed to production until the listing, Data safety form, privacy policy, content declarations, and install/update flows have been reviewed.

## Current release blockers

- Physical-device acceptance testing has not yet been recorded in the repository.
- Compression intentionally rasterizes pages and must be described accurately.
- Annotation and signature insertion need testing against varied and secured PDFs.
- Final name/package availability and dependency licensing must be checked.
- A final privacy policy must reflect the actual shipped features and any analytics, ads, or online services.

**Do not run the signed-release workflow until all four signing secrets are configured. Do not reuse passwords elsewhere.**

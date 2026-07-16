# Release signing setup

Release builds (`assembleRelease`, `bundleRelease`) require a real upload keystore. There is
no fallback: a release artifact task fails loudly if signing isn't fully configured, rather
than silently producing an unsigned or debug-signed output. Debug builds and tests are
unaffected and never require this setup.

## Security note

A previous release keystore and its `signing.properties` were briefly committed to this
repository's git history (see CHANGELOG.md, v0.12.4-preview) and must never be reused. If
you're setting up signing for the first time, generate a fresh keystore — do not resurrect
anything from git history.

## One-time local setup

1. Generate a keystore **outside this repository** (a dedicated directory like
   `~/.android-signing/<project>/` works well; anywhere outside the repo working tree is fine
   as long as it's never inside a git-tracked path):

   ```bash
   keytool -genkeypair -v \
     -keystore /path/outside/repo/upload-key.jks \
     -alias <your-alias> \
     -keyalg RSA -keysize 2048 -validity 10000
   ```

   Use the **same password for store and key** when prompted (or pass matching
   `-storepass`/`-keypass`) — `keytool` has a long-standing JKS quirk where a distinct key
   password can silently fall back to the store password on write, which then fails signing
   later with a confusing "Given final block not properly padded" error if the two don't
   actually match on disk. A single shared password avoids the ambiguity entirely.

2. Create `signing.properties` at the repo root (already gitignored — never commit it):

   ```properties
   storeFile=/path/outside/repo/upload-key.jks
   storePassword=<password>
   keyAlias=<your-alias>
   keyPassword=<password>
   ```

3. Confirm it's ignored: `git check-ignore -v signing.properties` should print a match.

4. Build: `./gradlew :app:assembleRelease`. If `signing.properties` is missing, incomplete, or
   `storeFile` doesn't resolve to an existing file, the build fails immediately with a clear
   error naming the problem — it will not produce an unsigned APK.

## Play upload key vs. app-signing key

For a Google Play release, the keystore set up here is the **upload key** only. Once Play App
Signing is enabled for the app, Google holds the actual app-signing key and re-signs uploads
with it; the local upload key is used only to authenticate uploads to Play Console. Losing the
upload key is recoverable (Google can help re-key it) as long as Play App Signing is enabled —
this is a materially different risk profile than losing an app-signing key, so don't skip
enabling Play App Signing.

## Verifying which key signed an APK

```bash
apksigner verify --print-certs path/to/app-release.apk
```

Compare the printed `SHA-256 digest` against the fingerprint recorded for the current signing
key (see CHANGELOG.md for the current and any superseded fingerprints).

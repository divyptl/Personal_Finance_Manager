# Release Checklist — WealthFlow

Run through this every time you cut a Play Store release.

## Build secrets

- [ ] `local.properties` contains a real `ANGELONE_API_KEY=…`
      (`local.properties` is gitignored — never commit it).
- [ ] `local.properties` contains `ANGELONE_CERT_PIN=sha256/…`
      computed via the openssl recipe in `app/build.gradle.kts`.
- [ ] Verify the pin string starts with `sha256/`. An empty pin disables
      pinning and `NetworkModule` will log a warning.

## Code

- [ ] All unit tests green: `./gradlew :app:testDebugUnitTest`
- [ ] All instrumentation tests green on a real device:
      `./gradlew :app:connectedDebugAndroidTest`
- [ ] No `Log.d` / `println` statements added since the last release.
- [ ] No new hardcoded strings (run `lint` — strings should live in
      `res/values/strings.xml`).

## Release build

- [ ] Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
- [ ] `./gradlew :app:bundleRelease` produces an `.aab`, not a raw APK.
- [ ] Install the release bundle on a clean device and verify:
      - [ ] First-time login flow works end-to-end.
      - [ ] SMS parsing produces a transaction (test with a self-sent
            SMS using a bank-format alphanumeric sender via an emulator).
      - [ ] CAS PDF parsing works on a real CAS file.
      - [ ] "Delete Data" actually wipes the encrypted prefs and database.
      - [ ] Token persistence survives an app restart.

## Play Console

- [ ] Privacy Policy URL points at the latest `PRIVACY_POLICY.md`.
- [ ] Data Safety form matches `docs/PLAY_STORE_DATA_SAFETY.md`.
- [ ] SMS Permissions Declaration submitted (and approved!) before any
      production rollout. If denied, remove SMS feature per
      `docs/PLAY_STORE_DATA_SAFETY.md`.
- [ ] Account-deletion declaration filled in.
- [ ] Pre-launch report (Play Console → Testing → Pre-launch report)
      shows zero crashes on the robo-test devices.
- [ ] Closed-track rollout to internal testers for 48 hours before
      promoting to production.

## Post-release

- [ ] Tag the release in git: `git tag v1.x.y && git push --tags`
- [ ] Save the upload key fingerprint somewhere safe — losing it locks
      you out of further updates.

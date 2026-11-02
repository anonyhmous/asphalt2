# Asphalt V5 – audit & hardening report

NOTE: this review was done by static analysis only. There is no Android SDK / Gradle / network in the review
environment, so the project was NOT compiled and the unit/rules tests were NOT executed. Build once in Android Studio
and fix any residual compile error before shipping (the places most likely to need a touch are listed at the bottom).

## A. What was wrong in the uploaded project (before changes)
| # | Severity | Finding |
|---|----------|---------|
| 1 | CRITICAL | firestore.rules let ANY signed-in user update/delete ANY driver/subscription and read every driver (name, phone, e-mail). A user could set `remainingDays=9999` or erase his own record. |
| 2 | CRITICAL | Trial logic was client side: a fresh install defaulted to 3 local days and `syncInitialProfile` used `localRemainingDays` as the initial balance even when the phone had already claimed the trial → clearing data / second Google account = new trial. |
| 3 | CRITICAL | Phone number was a free-text field – never verified, so "dual binding" did not exist (typing another number bypassed it). |
| 4 | CRITICAL | `activateSubscription()` credited days locally and `recordSubscription()` let the client write its own balance. |
| 5 | HIGH | Admin panel was visible to every driver; its Firestore listeners would crash the ViewModel for non-admins once rules are fixed. |
| 6 | HIGH | Session timing used `System.currentTimeMillis()` → moving the phone clock back extended a 24h session forever. End time was also persisted locally and trusted. |
| 7 | HIGH | AndroidManifest declared NO accessibility service, foreground service, application class, INTERNET / overlay / notification permissions → the app could not work at all. No accessibility-service XML. |
| 8 | HIGH | Build breakers: `R.string.firestore_database_id` and `default_web_client_id` did not exist; `debug.keystore` referenced but missing; google-services.json has no OAuth client. |
| 9 | MED | Accessibility scan (tree walk + parsing + OCR post-processing) ran on the MAIN thread on every event of any app; nodes never recycled (<API 33); per-callback unscoped coroutine scopes in the OCR engine; non-volatile flags shared across threads. |
| 10 | MED | Error handler logged e-mail/uid/provider info to Logcat. Backups were enabled (balance restorable from backup). |
| 11 | LOW | Dependency problems: retrofit 2.12 with okhttp 4.10 (version skew), firebase-ai, retrofit, moshi, okhttp, logging-interceptor, secrets plugin, recaptcha App Check – all unused; debug App Check provider shipped in release; `isMinifyEnabled=false`; same 2.1 MB PNG twice; a 9.8 MB nested old source ZIP + duplicate google-services files inside the project. |

## B. Changes made
### 1. Dual authentication + anti-cheat (server enforced)
* **Phone OTP** (`PhoneAuthHelper`, `PhoneVerifyScreen`): after Google sign-in the driver must verify his number; it is
  **linked** to the Google account. Firebase Auth itself refuses to link one number to two accounts.
* **firestore.rules rewritten** – everything is validated with server time (`request.time`):
  * driver doc can only be created with a verified phone + verified Google e-mail; 3 days only if neither the phone nor
    the device is in `claimed_trials`, and the claim docs are created in the SAME atomic batch (`existsAfter/getAfter`).
  * `claimed_trials` is create-only (no update / delete / list) → cannot be reset by clearing data, reinstalling or switching accounts.
  * balance can only change by (a) admin top-up, or (b) a session start that deducts exactly 1 day, stamps the server
    time and is refused while the previous 24h session is still running. Clients cannot touch plan/role/phone/e-mail.
  * `subscriptions` are admin-write only; drivers read only their own data; admin = document in `/admins/{uid}`.
* **TrustedClock**: session time = last server timestamp + monotonic `elapsedRealtime`; changing the phone clock has no effect.
  End time is always derived (start + 24h), never read from disk.
* Local cache is bound to the account (`bindToUser`) – another Google account never inherits days or a running session.
* Client-side `activateSubscription` / `recordSubscription` removed. App Check (Play Integrity; debug provider only in debug builds).
* `firestore.test.js` – emulator tests for the attack scenarios above.

### 2. Accessibility / OCR
* Manifest + `accessibility_service_config.xml` added; the system now only delivers events from Uber/DiDi/inDrive packages,
  throttled with `notificationTimeout`.
* Scanning moved to a single background thread, bursts of events coalesced into one scan, overlay/window calls posted to main.
* Node recycling on API < 33, `@Volatile` flags, OCR coroutines owned by the engine and cancelled in `close()`.
* The reader refuses to run without a server-confirmed, unexpired session.

### 3. Payment & contact
* `PaymentConfig` is the single source for InstaPay **01122104999** and WhatsApp **01128440045** (wa.me/201128440045).
* Clipboard copy kept (extra toast only < Android 13). Message now URL-encoded with `Uri.encode` (no literal "+"),
  includes name, phone, e-mail, **account id (UID)**, plan, days, amount; falls back WhatsApp → WhatsApp Business → browser.
  `<queries>` added so the intents resolve on Android 11+.

### 4. Cleanliness
* Profile→cloud mapping existed 3×, now `registerProfile()`; phone normalisation in `PhoneNumbers`; unused Firestore models removed.
* Gradle: unused libs/plugins removed, one Room version, explicit coroutines-play-services (needed for `await()`), R8 + resource
  shrinking enabled with keep rules, debug App Check only in `debugImplementation`, signing without secrets in the repo.
* Removed: `public/` (9.8 MB nested ZIP), `.env.example`, duplicate PNG; brand art converted to WebP (2.1 MB → 0.25 MB).

## C. Known limits (cannot be solved on the client alone)
* A new SIM + a new Google account + a factory-reset/new device can still obtain one new trial – that is a real new user.
* ANDROID_ID is a soft signal; App Check/Play Integrity makes spoofing it much harder but not impossible on rooted devices.
* Payments are verified manually by an admin (InstaPay has no public API). Fully automatic activation would need a backend.
* Starting a NEW 24h session needs internet (by design). Running inside an existing session works offline.
* Cloud Functions would allow rate-limiting ride writes and stricter server-side logic; Firestore rules alone cannot rate-limit.

## D. Check first when you build
1. `libs.versions.toml` pins AGP 9.1.1 / Kotlin 2.2.10 / KSP 2.3.5 / BoM 34.17.0 (kept as delivered, not verifiable offline).
2. Add SHA-1/256 → re-download `google-services.json` (otherwise Google Sign-In reports a configuration error).
3. `AsphaltOverlayManager` / `AsphaltPriceTagsOverlay` and the unchanged UI screens were reviewed only superficially.

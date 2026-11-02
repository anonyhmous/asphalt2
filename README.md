# Asphalt V5 (أسفلت)

Android (Kotlin / Compose) companion for Uber · DiDi · inDrive drivers.

## Before the first build (one-time Firebase setup)
1. Firebase console → Authentication → enable **Google** and **Phone** providers.
2. Project settings → add the **SHA-1 and SHA-256** of your debug AND release keys → download the new
   `google-services.json` into `app/` (it must now contain an OAuth web client, otherwise Google Sign-In cannot work).
3. App Check → register the app with **Play Integrity** (release) and add the debug token printed in Logcat (debug).
4. Firestore → publish `firestore.rules` (`firebase deploy --only firestore:rules`).
5. Create your admin: Firestore → collection `admins` → document id = **your Firebase Auth UID** (any field).
   Only this document makes the admin panel appear and allows crediting days.
6. Release signing: set `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` (or put `my-upload-key.jks` in the project root).

## Build
`./gradlew testDebugUnitTest assembleRelease`

## Rules tests (needs Node + Firebase emulator)
`npm i -D @firebase/rules-unit-testing firebase jest && firebase emulators:exec --only firestore "npx jest firestore.test.js"`

See `REVIEW.md` for the audit, what was changed and the known limits.

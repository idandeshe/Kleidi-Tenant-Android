# Claude Code Instructions — Kleidi Tenant Android

Native Android app for Kleidi **tenants** (the people renting): phone + SMS-code sign-in, their own
lease(s). A Kotlin / Jetpack Compose port of the tenant iOS app (`/kleidi-tenant-ios`), structured
like the staff Android app (`/kleidi-android`), talking to the tenant entrance of the same Fastify
API (server repo: `/workspace`, contract in its `docs/tenant-app.md`). Read `README.md` first.

## Rules

- **Mirror the tenant iOS app.** Same screens, flows, wording, labels and file split. When a feature
  changes on iOS, change it here in the same task, and the other way round. Where the tenant iOS app
  has no counterpart yet, follow the staff Android app's conventions.
- **Git:** never commit or push unless explicitly asked; never create branches. Stage only your own
  files.
- **Layering:** `:core` (pure Kotlin) ← `:api` (pure Kotlin, OkHttp) ← `:ui` (Compose kit) ← `:app`.
  No Android imports in `:core` / `:api`. No user-facing strings outside `:app`'s resources.
- **Localization:** every user-facing string lives in `app/src/main/res/values/strings.xml`
  (English) **and** `values-el/strings.xml` (Greek) — both, always. Server messages are never shown
  verbatim (`shell/ApiErrorText.kt` maps errors to copy). The language is the tenant's choice
  (`LanguagePreferences`, applied with `AppCompatDelegate.setApplicationLocales`), not the device's.
- **API contract:** models mirror `server/types/tenantAuth.types.ts`, `tenantPortal.types.ts`,
  `tenants.types.ts`; declare only the fields the app shows; enum-like values are
  `@JvmInline value class X(override val raw: String) : StringValue` in `:core` (never Kotlin enums —
  a new server value must not break decoding); unknown JSON keys are ignored. The tenant lease shape
  is deliberately narrow: a field is added on the server first, on purpose.
- **Headers:** every request sends `x-client: tenant-android` and `x-client-build: <versionCode>`;
  the session is `Authorization: Bearer` with the refresh token in the body. No cookies, no CSRF.
- **Dates / money:** `KleidiDateFormatter` (`DD/MM/YYYY`), `LeaseFormatting.money` — never
  `DateTimeFormatter.ofLocalizedDate` or hand-built strings.
- **Never log PII** (phone numbers, names), codes or tokens.
- **Versioning:** bump `versionCode` in `app/build.gradle.kts` for every Play upload (the server's
  `TENANT_ANDROID_MIN_SUPPORTED_BUILD` compares against it); `versionName` follows SemVer.

## Commands

```bash
./gradlew test                         # unit tests (core, api — JUnit 5; app — JUnit 4)
./gradlew :app:assembleDevDebug        # debug APK against the local server (emulator: 10.0.2.2:3000)
./gradlew :app:lintDevDebug
./gradlew :app:assembleProdRelease     # release build (unsigned unless a signing config is provided)
```

JDK 21 and the Android SDK (`ANDROID_HOME=/opt/android-sdk`) are installed in the server repo's dev
container. `local.properties` (not committed) holds `sdk.dir`.

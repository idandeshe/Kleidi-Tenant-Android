# Kleidi Tenant Android

Native Android app for Kleidi **tenants** — the people renting a property managed in Kleidi. A tenant
signs in with their mobile number and an SMS code and sees their own lease: this month's rent, the
lease details, and (in later phases) payments and maintenance issues. A Kotlin / Jetpack Compose
(Material 3) port of the tenant iOS app (`kleidi-tenant-ios`), built like the staff Android app
(`kleidi-android`). Android 8.0+ (API 26), phones first.

## Layout

| Path | What |
|------|------|
| `core/` | Pure Kotlin: `AppLanguage` (EL / EN, device default), lease values (`LeaseStatus`, `PaymentMethod`, `FloorLevel`), rent values (`RentMonthStatus`, `WireDeclarationStatus`) and issue values (`IssueCategory`, `IssueStatus` with `isOpen`, `IssueMessageKind`, `IssueAuthor`) with their label keys, `LeaseFormatting` (money, rent month, ordinals, which lease to show), `RentFormatting` (`YYYY-MM` → month name), `WireDeclarationRules` (proof types / 15 MB, HEIC detection, amount prefill + parsing, multipart field names), `IssueRules` (photo types / 10 MB, 5 report / 3 reply photos, multipart field names, when a reply can be sent), `KleidiDateFormatter` (`DD/MM/YYYY`, `DD/MM/YYYY HH:mm`), `PhoneNumber` (E.164). |
| `api/` | Pure Kotlin: `ApiClient` (tenant headers, Bearer, single-flight refresh, errors, events), serializable models (`Tenant`, `TenantConfig`, `TenantLease`, `TenantRent` / `TenantRentMonth`, `TenantWireDeclaration`, `TenantIssue` / `TenantIssueDetail` / `TenantIssueMessage`), `endpoints/TenantEndpoints.kt`, `endpoints/RentEndpoints.kt` (rent months, declarations, multipart `declareWire`, proof download), `endpoints/IssueEndpoints.kt` (issues list, multipart `reportIssue` / `replyToIssue`, detail, photo download), `Multipart.kt`. OkHttp + kotlinx.serialization. |
| `ui/` | Compose kit: `TenantTheme` (white ground, ink `#15202B`, accent `#1E5AA8`), cards, dashed card, pills, buttons, banners, `Loadable`. No strings. |
| `app/.../shell/` | `AppModel` (session lifecycle), `ServerEnvironment`, Keystore `AndroidTokenStore`, `LanguagePreferences` (DataStore + `setApplicationLocales`), `ApiErrorText` (error → copy), `RootScreen`, `MainScaffold` (bottom tabs + the Declare, Report-an-issue and issue-thread screens pushed over them). |
| `app/.../features/` | `onboarding/LanguageScreen`, `auth/` (`AuthFlow`, `PhoneScreen`, `CodeScreen`), `home/` (`HomeScreen`, `LeaseLabels`), `payments/` (`PaymentsScreen`, `RentMonthRow`, `DeclareWireScreen`, `ProofPicker`, `ProofViewer`, `RentLabels`), `issues/` (`IssuesScreen`, `ReportIssueScreen`, `IssueDetailScreen`, `IssuePhotoViewer`, `IssueLabels`), `profile/ProfileScreen`. |
| `app/src/main/res/values{,-el}/strings.xml` | Every user-facing string, English and Greek. |

## Getting started

Needs JDK 21 and the Android SDK (platform 37, build-tools 37) — both are in the Kleidi server repo's dev
container. Android Studio works too (open the folder).

```bash
echo "sdk.dir=/opt/android-sdk" > local.properties   # Android Studio writes this itself
./gradlew :app:assembleDevDebug                      # app/build/outputs/apk/dev/debug/app-dev-debug.apk
```

1. Run the Kleidi server locally (`npm run dev` in the server repo, port 3000) with the `SMS_PROVIDER`
   app setting set to `console` — the code is written to the server log.
2. Install the dev build on an emulator (`adb install -r app-dev-debug.apk`); it reaches the host at
   `http://10.0.2.2:3000`.
3. In the staff web app, link a tenant to an active lease (lease card → "Tenant app accounts"), then
   sign in with that tenant's mobile number.

| Flavor | Application id | Server |
|--------|----------------|--------|
| `dev` | `one.lab5.kleidi.tenant.dev` | `http://10.0.2.2:3000` (cleartext allowed) |
| `stage` | `one.lab5.kleidi.tenant.stage` | placeholder `https://stage.kleidi.example.com` — set it before the first stage build |
| `prod` | `one.lab5.kleidi.tenant` | `https://kleidi.seamphony.io` |

## Tests

```bash
./gradlew test                     # core + api (JUnit 5), app (JUnit 4)
./gradlew :app:lintDevDebug
./gradlew :app:assembleProdRelease # R8-minified release (unsigned unless a signing config is added)
```

The api tests run `ApiClient` against a scripted fake server (headers, refresh, session end, the
sign-in error codes). CI: `.github/workflows/ci.yml`.

## How the app talks to the server

The server treats the app as a *native tenant client* (server repo: `docs/tenant-app.md`):

- Every request sends `x-client: tenant-android` and `x-client-build: <versionCode>`.
- Sign-in is `POST /api/tenant/auth/request-code { phone }` then `POST /api/tenant/auth/verify-code
  { phone, code }`, which returns the tokens in the body. They are AES-GCM-encrypted with an Android
  Keystore key and kept in private preferences excluded from backups and device transfer.
- Requests carry `Authorization: Bearer <access token>` (15 minutes). The client refreshes a minute
  before expiry, and once on a 401; concurrent callers share a single refresh because the server rotates
  the refresh token. A rejected refresh — or a 403 `NOT_A_TENANT` — ends the session (sign-in screen,
  with a notice).
- `GET /api/tenant/config` is checked at launch; a build below `android.minSupportedBuild`, or any
  `426`, shows the forced-update screen (with the Play Store link). **Bump `versionCode` on every Play
  upload.** `smsConfigured: false` disables the "Send code" button with a notice.
- Rent (phase 2): `GET /api/tenant/leases/:id/rent` (the months, newest first, each with
  `status` paid / declared / due / overdue / upcoming, `confirmedByWire`, the latest `declaration`
  with its `declaredAt`, and `canDeclare`) and
  `POST /api/tenant/leases/:id/wire-declarations` — "I paid by bank transfer" as
  `multipart/form-data` with the text parts `periods` (JSON array of `YYYY-MM`), `declaredAmount`,
  `transferDate` (`YYYY-MM-DD`), `reference?` **before** the `proof` file (PDF / JPEG / PNG / WebP,
  max 15 MB; 5 declarations per 10 minutes). Camera shots and any photo that is not PNG / WebP
  (HEIC included) are re-encoded as JPEG 85 with the longest side at most 2000 px, upright per
  EXIF and without metadata. `GET /api/tenant/wire-declarations/:id/proof` returns the file bytes
  — "View proof" downloads them with the Bearer token and shows an image, or renders a PDF with
  `PdfRenderer`. Statuses are open `StringValue`s: a new server value never breaks decoding.
- Issues (phase 3): `GET /api/tenant/issues` (open first, then by last activity; each with
  `category`, `summary`, `status` new / in_progress / resolved / closed, `isUrgent`, `tenantUnread`,
  `canReply`), `POST /api/tenant/leases/:id/issues` — "Report an issue" as `multipart/form-data`
  with the text parts `category`, `description`, `isUrgent` **before** 0–5 `photo` files (JPEG /
  PNG / WebP, max 10 MB each, re-encoded like a proof photo; 5 reports per 10 minutes),
  `GET /api/tenant/issues/:id` (the thread — tenant / staff messages and status events; marks the
  issue seen), `POST /api/tenant/issues/:id/messages` (`body` then 0–3 `photo` parts; 409 once the
  issue is resolved / closed → the composer is replaced by "Report it again") and
  `GET /api/tenant/issues/:id/photos/:documentId` (bytes, fetched with the Bearer token). Staff
  are shown as "Property manager", never by name; `new` reads "Received" to the tenant.
- Language: chosen on the first screen (stored in DataStore, applied in-app with
  `AppCompatDelegate.setApplicationLocales`). After sign-in, if the tenant record's `language` differs
  from the phone's choice, the app sends `PATCH /api/tenant/me { language }` once; Profile → Language
  does the same.

## Status

Phase 1 — foundation: language choice, sign-in, Home ("My lease" with the rent card, "Pay online —
coming soon", lease details, "Report an issue"), Profile (language, sign out, sign out
everywhere), forced update.

Phase 2 — rent months + "I paid by bank transfer": the Home rent card shows the status pill of
the newest month ("Wire confirmed by property owner" for a month settled through a declaration)
and enables "I paid by bank transfer" when a month can be declared; the Payments tab lists the
months ("Rent"), each with its declaration ("Declared dd/mm/yyyy", "View proof", and "Declare
again" after a rejection); the
Declare screen (month multi-select, amount prefilled from the picked months, transfer date up to
today, optional reference, proof from camera / gallery / files) sends the multipart declaration.

Phase 3 — maintenance issues: the Issues tab lists the tenant's issues in "Open" and "Resolved"
groups (category icon, summary, status pill, urgent badge, "New reply from your property manager"
dot); "Report an issue" (from Home or Issues) is the category grid, the description, up to five
photos and the urgent checkbox; an issue opens as a thread (report card with photo thumbnails,
staff and tenant bubbles, centred status changes) with a photo + text composer while it is open.
Light theme only (the approved mock-up). Compiled and unit-tested; **not yet run on a device or
emulator** (the dev container has none).
